package com.schooldays.service.notification;

import java.util.UUID;

public record GmailStatePayload(UUID tenantId, UUID userId, long issuedAtEpochSecond, String returnUrl) {
}
