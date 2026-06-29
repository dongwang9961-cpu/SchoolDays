package com.schooldays.entities.auth;

import java.util.UUID;

public record TenantInvitationRow(
        UUID id,
        String schoolName,
        String adminEmail,
        UUID tenantId
) {
}
