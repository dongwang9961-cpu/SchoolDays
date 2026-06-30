package com.schooldays.service.email;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class ConfiguredSystemEmailService implements SystemEmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ConfiguredSystemEmailService.class);

    private final RestClient restClient;
    private final String provider;
    private final String apiKey;
    private final String resendSendUri;

    public ConfiguredSystemEmailService(
            @Value("${schooldays.system-email.provider:log}") String provider,
            @Value("${schooldays.system-email.api-key:}") String apiKey,
            @Value("${schooldays.system-email.resend.send-uri:https://api.resend.com/emails}") String resendSendUri
    ) {
        this.restClient = RestClient.create();
        this.provider = provider == null ? "log" : provider.trim().toLowerCase();
        this.apiKey = apiKey;
        this.resendSendUri = resendSendUri;
    }

    @Override
    public void send(SystemEmailMessage message) {
        if ("resend".equals(provider)) {
            sendWithResend(message);
            return;
        }
        LOGGER.info(
                "System email skipped by '{}' provider: to={}, from={}, subject={}, textBody={}",
                provider,
                message.toEmail(),
                message.fromEmail(),
                message.subject(),
                message.textBody()
        );
    }

    private void sendWithResend(SystemEmailMessage message) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new SystemEmailException("System email provider 'resend' requires an API key", null);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("from", message.fromName() + " <" + message.fromEmail() + ">");
        payload.put("to", message.toEmail());
        payload.put("subject", message.subject());
        payload.put("text", message.textBody());
        if (message.htmlBody() != null && !message.htmlBody().isBlank()) {
            payload.put("html", message.htmlBody());
        }

        try {
            restClient.post()
                    .uri(resendSendUri)
                    .headers(headers -> headers.setBearerAuth(apiKey))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new SystemEmailException("System registration email could not be sent", exception);
        }
    }
}
