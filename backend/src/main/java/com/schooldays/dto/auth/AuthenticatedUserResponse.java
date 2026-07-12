package com.schooldays.dto.auth;

import java.util.List;
import java.util.UUID;

import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.entities.auth.TenantRole;

public record AuthenticatedUserResponse(
        UUID id,
        String email,
        String phone,
        List<TenantRole> tenantRoles,
        List<String> authProviders
) {
    public static AuthenticatedUserResponse from(AuthenticatedUser user) {
        return from(user, List.of());
    }

    public static AuthenticatedUserResponse from(AuthenticatedUser user, List<String> authProviders) {
        return new AuthenticatedUserResponse(user.id(), user.email(), user.phone(), user.tenantRoles(), authProviders == null ? List.of() : List.copyOf(authProviders));
    }
}
