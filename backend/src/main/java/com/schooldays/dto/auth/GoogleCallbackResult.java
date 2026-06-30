package com.schooldays.dto.auth;

public record GoogleCallbackResult(
        AuthResponse authResponse,
        String returnUrl
) {
}
