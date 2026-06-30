package com.schooldays.service.notification;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dao.notification.NotificationDao;
import com.schooldays.dto.notification.GmailConnectStartRequest;
import com.schooldays.dto.notification.GmailConnectStartResponse;
import com.schooldays.dto.notification.NotificationHistoryListResponse;
import com.schooldays.dto.notification.NotificationHistoryResponse;
import com.schooldays.dto.notification.NotificationProviderListResponse;
import com.schooldays.dto.notification.NotificationProviderResponse;
import com.schooldays.dto.notification.SendNotificationRequest;
import com.schooldays.jooq.generated.tables.records.EmailNotificationHistoryRecord;
import com.schooldays.jooq.generated.tables.records.NotificationProvidersRecord;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final NotificationDao notificationDao;
    private final GmailApiClient gmailApiClient;
    private final GmailStateSigner gmailStateSigner;
    private final TokenCipher tokenCipher;
    private final Set<String> allowedReturnOrigins;

    public NotificationService(
            NotificationDao notificationDao,
            GmailApiClient gmailApiClient,
            GmailStateSigner gmailStateSigner,
            TokenCipher tokenCipher,
            @Value("${schooldays.oauth.allowed-return-origins:${schooldays.cors.allowed-origins}}") List<String> allowedReturnOrigins
    ) {
        this.notificationDao = notificationDao;
        this.gmailApiClient = gmailApiClient;
        this.gmailStateSigner = gmailStateSigner;
        this.tokenCipher = tokenCipher;
        this.allowedReturnOrigins = allowedReturnOrigins == null ? Set.of() : Set.copyOf(allowedReturnOrigins);
    }

    public GmailConnectStartResponse startGmailConnection(UUID tenantId, UUID userId, GmailConnectStartRequest request) {
        requireConfigured();
        requireSender(tenantId, userId);
        return new GmailConnectStartResponse(gmailApiClient.authorizationUrl(gmailStateSigner.issue(
                tenantId,
                userId,
                safeReturnUrl(request == null ? "" : request.returnUrl())
        )));
    }

    @Transactional
    public String completeGmailConnection(String code, String state) {
        requireConfigured();
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gmail authorization code and state are required");
        }

        GmailStatePayload statePayload = gmailStateSigner.verify(state);
        requireSender(statePayload.tenantId(), statePayload.userId());
        Map<String, Object> tokens = gmailApiClient.exchangeAuthorizationCode(code);
        String accessToken = stringValue(tokens.get("access_token"));
        String refreshToken = stringValue(tokens.get("refresh_token"));
        if (accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google did not return an access token");
        }
        if (refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google did not return a refresh token. Reconnect Gmail and approve offline access.");
        }

        Map<String, Object> userInfo = gmailApiClient.userInfo(accessToken);
        String email = normalizeEmail(stringValue(userInfo.get("email")));
        if (email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Google did not return an email address");
        }

        OffsetDateTime now = OffsetDateTime.now();
        NotificationProvidersRecord provider = notificationDao.findGmailProvider(statePayload.tenantId(), statePayload.userId())
                .orElseGet(NotificationProvidersRecord::new);
        if (provider.getId() == null) {
            provider.setTenantId(statePayload.tenantId());
            provider.setProviderType("gmail_oauth");
            provider.setCreatedByUserId(statePayload.userId());
            provider.setCreatedAt(now);
        }
        provider.setStatus("active");
        provider.setFromEmail(email);
        provider.setFromName(stringValue(userInfo.get("name")));
        provider.setMetadata(providerMetadata(refreshToken, tokens, userInfo));
        provider.setUpdatedAt(now);
        notificationDao.saveProvider(provider);
        return safeReturnUrl(statePayload.returnUrl());
    }

    public NotificationProviderListResponse listProviders(UUID tenantId, UUID userId) {
        requireSender(tenantId, userId);
        return new NotificationProviderListResponse(notificationDao.listProviders(tenantId, userId)
                .stream()
                .map(NotificationProviderResponse::from)
                .toList());
    }

    @Transactional
    public NotificationHistoryResponse sendNotification(UUID tenantId, UUID userId, SendNotificationRequest request) {
        requireConfigured();
        requireSender(tenantId, userId);
        NotificationProvidersRecord provider = notificationDao.findGmailProvider(tenantId, userId)
                .filter(record -> "active".equalsIgnoreCase(record.getStatus()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Connect Gmail before sending notifications"));

        RecipientPlan recipients = resolveRecipients(tenantId, request);
        if (recipients.bccEmails().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one BCC recipient is required");
        }

        List<String> ccEmails = normalizeEmails(request.ccEmails());
        String accessToken = refreshAccessToken(provider);
        String rawMessage = rawMimeMessage(
                provider.getFromEmail(),
                provider.getFromName(),
                ccEmails,
                recipients.bccEmails(),
                request.subject().trim(),
                request.body(),
                request.bodyMimeType() == null || request.bodyMimeType().isBlank() ? "text/plain" : request.bodyMimeType(),
                request.templateEml()
        );
        Map<String, Object> sendResponse = gmailApiClient.sendRawMessage(accessToken, rawMessage);

        OffsetDateTime now = OffsetDateTime.now();
        EmailNotificationHistoryRecord history = new EmailNotificationHistoryRecord()
                .setTenantId(tenantId)
                .setSenderUserId(userId)
                .setProviderId(provider.getId())
                .setAudienceType(recipients.audienceType())
                .setSourceType(isBlank(request.templateEml()) ? "text" : "eml")
                .setCcEmails(JSONB.valueOf(OBJECT_MAPPER.valueToTree(ccEmails).toString()))
                .setBccRecipientCount(recipients.bccEmails().size())
                .setSubjectSnapshot(request.subject().trim())
                .setBodyBlobMimeType(isBlank(request.templateEml()) ? "text/plain" : "message/rfc822")
                .setExternalReference(stringValue(sendResponse == null ? null : sendResponse.get("id")))
                .setStatus("sent")
                .setSentAt(now)
                .setMetadata(historyMetadata(request.body(), recipients.bccEmails(), request.classId(), !isBlank(request.templateEml())))
                .setCreatedAt(now)
                .setUpdatedAt(now);
        return NotificationHistoryResponse.from(notificationDao.saveHistory(history));
    }

    public NotificationHistoryListResponse listHistory(UUID tenantId, UUID userId) {
        requireSender(tenantId, userId);
        return new NotificationHistoryListResponse(notificationDao.listHistory(tenantId, userId, 50)
                .stream()
                .map(NotificationHistoryResponse::from)
                .toList());
    }

    private void requireConfigured() {
        if (!gmailApiClient.configured()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gmail API is not configured");
        }
    }

    private String safeReturnUrl(String returnUrl) {
        if (returnUrl == null || returnUrl.isBlank()) {
            return "";
        }
        try {
            URI uri = URI.create(returnUrl);
            String origin = uri.getScheme() + "://" + uri.getAuthority();
            if (uri.getScheme() == null || uri.getAuthority() == null || !allowedReturnOrigins.contains(origin)) {
                return "";
            }
            return uri.toString();
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private void requireSender(UUID tenantId, UUID userId) {
        if (!notificationDao.tenantExists(tenantId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant was not found");
        }
        if (!notificationDao.userCanSendForTenant(tenantId, userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only school admins and teachers can send notifications");
        }
    }

    private RecipientPlan resolveRecipients(UUID tenantId, SendNotificationRequest request) {
        if (request.classId() != null) {
            if (!notificationDao.classBelongsToTenant(tenantId, request.classId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Class was not found");
            }
            return new RecipientPlan("class", normalizeEmails(notificationDao.parentEmailsForClass(tenantId, request.classId())));
        }
        return new RecipientPlan("manual", normalizeEmails(request.bccEmails()));
    }

    private List<String> normalizeEmails(List<String> emails) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String email : emails == null ? List.<String>of() : emails) {
            String value = normalizeEmail(email);
            if (!value.isBlank()) {
                if (!EMAIL_PATTERN.matcher(value).matches()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid email address: " + value);
                }
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private String refreshAccessToken(NotificationProvidersRecord provider) {
        String refreshToken = tokenCipher.decrypt(metadataText(provider, "refreshToken"));
        Map<String, Object> tokens = gmailApiClient.refreshAccessToken(refreshToken);
        String accessToken = stringValue(tokens.get("access_token"));
        if (accessToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to refresh Gmail access token");
        }
        return accessToken;
    }

    private JSONB providerMetadata(String refreshToken, Map<String, Object> tokens, Map<String, Object> userInfo) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        root.put("refreshToken", tokenCipher.encrypt(refreshToken));
        root.put("scope", stringValue(tokens.get("scope")));
        root.put("tokenType", stringValue(tokens.get("token_type")));
        root.put("googleSubject", stringValue(userInfo.get("sub")));
        return JSONB.valueOf(root.toString());
    }

    private JSONB historyMetadata(String body, List<String> bccEmails, UUID classId, boolean templateEml) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        if (!templateEml) {
            root.put("bodyBase64", Base64.getEncoder().encodeToString(body.getBytes(StandardCharsets.UTF_8)));
            root.put("bodyEncoding", "base64");
        }
        root.put("templateEml", templateEml);
        if (classId != null) {
            root.put("classId", classId.toString());
        }
        root.set("bccEmails", OBJECT_MAPPER.valueToTree(bccEmails));
        return JSONB.valueOf(root.toString());
    }

    private String metadataText(NotificationProvidersRecord provider, String fieldName) {
        try {
            String value = OBJECT_MAPPER.readTree(provider.getMetadata() == null ? "{}" : provider.getMetadata().data())
                    .path(fieldName)
                    .asText("");
            if (value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gmail provider must be reconnected");
            }
            return value;
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Gmail provider metadata is invalid", exception);
        }
    }

    private String rawMimeMessage(
            String fromEmail,
            String fromName,
            List<String> ccEmails,
            List<String> bccEmails,
            String subject,
            String body,
            String bodyMimeType,
            String templateEml
    ) {
        if (!isBlank(templateEml)) {
            return rawTemplateMessage(fromEmail, fromName, ccEmails, bccEmails, subject, templateEml);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("From: ").append(headerAddress(fromEmail, fromName)).append("\r\n");
        builder.append("To: ").append(headerAddress(fromEmail, fromName)).append("\r\n");
        if (!ccEmails.isEmpty()) {
            builder.append("Cc: ").append(String.join(", ", ccEmails)).append("\r\n");
        }
        builder.append("Bcc: ").append(String.join(", ", bccEmails)).append("\r\n");
        builder.append("Subject: ").append(encodeHeader(subject)).append("\r\n");
        builder.append("MIME-Version: 1.0\r\n");
        builder.append("Content-Type: ").append(safeMimeType(bodyMimeType)).append("; charset=UTF-8\r\n");
        builder.append("Content-Transfer-Encoding: 8bit\r\n");
        builder.append("\r\n");
        builder.append(body);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String rawTemplateMessage(
            String fromEmail,
            String fromName,
            List<String> ccEmails,
            List<String> bccEmails,
            String subject,
            String templateEml
    ) {
        String normalized = templateEml.replace("\r\n", "\n").replace("\r", "\n");
        String leadingBoundary = leadingBoundary(normalized);
        int separatorIndex = normalized.indexOf("\n\n");
        String originalHeaders = leadingBoundary.isBlank() && separatorIndex >= 0 ? normalized.substring(0, separatorIndex) : "";
        String originalBody = leadingBoundary.isBlank() && separatorIndex >= 0 ? normalized.substring(separatorIndex + 2) : normalized;

        StringBuilder builder = new StringBuilder();
        builder.append("From: ").append(headerAddress(fromEmail, fromName)).append("\r\n");
        builder.append("To: ").append(headerAddress(fromEmail, fromName)).append("\r\n");
        if (!ccEmails.isEmpty()) {
            builder.append("Cc: ").append(String.join(", ", ccEmails)).append("\r\n");
        }
        builder.append("Bcc: ").append(String.join(", ", bccEmails)).append("\r\n");
        builder.append("Subject: ").append(encodeHeader(subject)).append("\r\n");

        String preservedHeaders = leadingBoundary.isBlank()
                ? preservedTemplateHeaders(originalHeaders)
                : "MIME-Version: 1.0\r\nContent-Type: multipart/mixed; boundary=\"" + leadingBoundary + "\"\r\n";
        if (preservedHeaders.isBlank()) {
            builder.append("MIME-Version: 1.0\r\n");
            builder.append("Content-Type: text/plain; charset=UTF-8\r\n");
            builder.append("Content-Transfer-Encoding: 8bit\r\n");
        } else {
            builder.append(preservedHeaders);
        }
        builder.append("\r\n");
        builder.append(originalBody.replace("\n", "\r\n"));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    private String leadingBoundary(String value) {
        String trimmed = value == null ? "" : value.stripLeading();
        if (!trimmed.startsWith("--")) {
            return "";
        }
        int end = trimmed.indexOf('\n');
        String firstLine = (end >= 0 ? trimmed.substring(0, end) : trimmed).trim();
        if (firstLine.length() <= 2 || firstLine.endsWith("--")) {
            return "";
        }
        return firstLine.substring(2).trim();
    }

    private String preservedTemplateHeaders(String headerText) {
        List<String> unfoldedHeaders = unfoldHeaders(headerText);
        StringBuilder builder = new StringBuilder();
        boolean hasMimeVersion = false;
        for (String header : unfoldedHeaders) {
            int colon = header.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = header.substring(0, colon).trim();
            String lowerName = name.toLowerCase();
            if (Set.of("from", "to", "cc", "bcc", "subject", "date", "message-id", "reply-to").contains(lowerName)) {
                continue;
            }
            if ("mime-version".equals(lowerName)) {
                hasMimeVersion = true;
            }
            if (lowerName.startsWith("content-") || "mime-version".equals(lowerName)) {
                builder.append(header.replace("\n", " ").replace("\r", " ")).append("\r\n");
            }
        }
        if (!hasMimeVersion) {
            builder.insert(0, "MIME-Version: 1.0\r\n");
        }
        return builder.toString();
    }

    private List<String> unfoldHeaders(String headerText) {
        List<String> headers = new ArrayList<>();
        for (String line : headerText.split("\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && !headers.isEmpty()) {
                int lastIndex = headers.size() - 1;
                headers.set(lastIndex, headers.get(lastIndex) + " " + line.trim());
            } else if (!line.isBlank()) {
                headers.add(line);
            }
        }
        return headers;
    }

    private String headerAddress(String email, String name) {
        if (name == null || name.isBlank()) {
            return email;
        }
        return encodeHeader(name) + " <" + email + ">";
    }

    private String encodeHeader(String value) {
        if (value == null) {
            return "";
        }
        boolean ascii = value.chars().allMatch(character -> character >= 32 && character < 127);
        if (ascii) {
            return value.replace("\r", "").replace("\n", "");
        }
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private String safeMimeType(String mimeType) {
        String value = mimeType == null ? "text/plain" : mimeType.trim().toLowerCase();
        return value.equals("text/html") ? "text/html" : "text/plain";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record RecipientPlan(String audienceType, List<String> bccEmails) {
    }
}
