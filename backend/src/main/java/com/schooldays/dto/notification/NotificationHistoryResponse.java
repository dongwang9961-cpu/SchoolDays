package com.schooldays.dto.notification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.jooq.generated.tables.records.EmailNotificationHistoryRecord;

public record NotificationHistoryResponse(
        UUID id,
        String audienceType,
        String sourceType,
        List<String> ccEmails,
        int bccRecipientCount,
        String subject,
        String status,
        String externalReference,
        OffsetDateTime sentAt,
        OffsetDateTime createdAt
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static NotificationHistoryResponse from(EmailNotificationHistoryRecord record) {
        return new NotificationHistoryResponse(
                record.getId(),
                record.getAudienceType(),
                record.getSourceType(),
                readStringArray(record.getCcEmails() == null ? "[]" : record.getCcEmails().data()),
                record.getBccRecipientCount(),
                record.getSubjectSnapshot(),
                record.getStatus(),
                record.getExternalReference(),
                record.getSentAt(),
                record.getCreatedAt()
        );
    }

    private static List<String> readStringArray(String json) {
        try {
            JsonNode node = OBJECT_MAPPER.readTree(json);
            if (!node.isArray()) {
                return List.of();
            }
            return OBJECT_MAPPER.convertValue(
                    node,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)
            );
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
