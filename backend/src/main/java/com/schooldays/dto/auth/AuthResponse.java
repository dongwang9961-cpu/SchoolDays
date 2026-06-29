package com.schooldays.dto.auth;

import com.schooldays.entities.auth.AuthenticatedUser;

public record AuthResponse(
        String tokenType,
        String accessToken,
        long expiresInSeconds,
        AuthenticatedUserResponse user
) {
    public static AuthResponse bearer(AuthToken token, AuthenticatedUser user) {
        return new AuthResponse(
                "Bearer",
                token.accessToken(),
                token.expiresInSeconds(),
                AuthenticatedUserResponse.from(user)
        );
    }
}
