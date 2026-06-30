package com.schooldays.dto.notification;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.NotificationProvidersRecord;

public record NotificationProviderResponse(
        UUID id,
        String providerType,
        String status,
        String fromEmail,
        String fromName,
        OffsetDateTime updatedAt
) {
    public static NotificationProviderResponse from(NotificationProvidersRecord record) {
        return new NotificationProviderResponse(
                record.getId(),
                record.getProviderType(),
                record.getStatus(),
                record.getFromEmail(),
                record.getFromName(),
                record.getUpdatedAt()
        );
    }
}
