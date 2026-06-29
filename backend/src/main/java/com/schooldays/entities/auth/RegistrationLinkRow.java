package com.schooldays.entities.auth;

import java.util.UUID;

public record RegistrationLinkRow(
        UUID id,
        UUID tenantId,
        String email,
        String intendedRole
) {
}
