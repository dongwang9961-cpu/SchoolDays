package com.schooldays.service.notification;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.config.JwtKeyProperties;
import org.springframework.stereotype.Component;

@Component
public class GmailStateSigner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long STATE_TTL_SECONDS = 15 * 60;

    private final JwtKeyProperties jwtKeyProperties;

    public GmailStateSigner(JwtKeyProperties jwtKeyProperties) {
        this.jwtKeyProperties = jwtKeyProperties;
    }

    public String issue(UUID tenantId, UUID userId, String returnUrl) {
        try {
            GmailStatePayload payload = new GmailStatePayload(tenantId, userId, Instant.now().getEpochSecond(), returnUrl);
            String payloadPart = base64Url(OBJECT_MAPPER.writeValueAsBytes(payload));
            return payloadPart + "." + sign(payloadPart);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to create Gmail OAuth state", exception);
        }
    }

    public GmailStatePayload verify(String state) {
        try {
            String[] parts = state == null ? new String[0] : state.split("\\.", 2);
            if (parts.length != 2 || !constantTimeEquals(sign(parts[0]), parts[1])) {
                throw new IllegalArgumentException("Invalid Gmail OAuth state");
            }
            GmailStatePayload payload = OBJECT_MAPPER.readValue(Base64.getUrlDecoder().decode(parts[0]), GmailStatePayload.class);
            long age = Instant.now().getEpochSecond() - payload.issuedAtEpochSecond();
            if (age < 0 || age > STATE_TTL_SECONDS) {
                throw new IllegalArgumentException("Expired Gmail OAuth state");
            }
            return payload;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid Gmail OAuth state", exception);
        }
    }

    private String sign(String payloadPart) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(jwtKeyProperties.currentSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return base64Url(mac.doFinal(payloadPart.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private boolean constantTimeEquals(String left, String right) {
        return MessageDigestUtil.constantTimeEquals(left, right);
    }
}
