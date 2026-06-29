package com.schooldays.entities.auth;

import java.util.UUID;

public record GoogleOAuthState(
        UUID tenantId
) {
}
