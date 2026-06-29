package com.schooldays.dto.auth;

public record AuthToken(String accessToken, long expiresInSeconds) {
}
