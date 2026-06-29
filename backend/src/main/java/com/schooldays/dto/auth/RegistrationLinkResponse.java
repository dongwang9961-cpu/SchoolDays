package com.schooldays.dto.auth;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegistrationLinkResponse(
        UUID tenantId,
        String email,
        String intendedRole,
        String token,
        String link,
        OffsetDateTime expiresAt
) {
}
