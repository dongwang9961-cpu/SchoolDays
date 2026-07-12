package com.schooldays.dto.auth;

import java.util.List;

import com.schooldays.entities.auth.AuthenticatedUser;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        AuthenticatedUserResponse user
) {
    public static AuthResponse bearer(AuthToken token, AuthenticatedUser user) {
        return bearer(token, user, List.of());
    }

    public static AuthResponse bearer(AuthToken token, AuthenticatedUser user, List<String> authProviders) {
        return new AuthResponse(
                "Bearer",
                token.accessToken(),
                token.expiresInSeconds(),
                AuthenticatedUserResponse.from(user, authProviders)
        );
    }
}
