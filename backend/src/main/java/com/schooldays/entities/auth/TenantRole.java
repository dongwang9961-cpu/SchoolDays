package com.schooldays.entities.auth;

import java.util.UUID;

public record TenantRole(UUID tenantId, String role) {
}
