package com.schooldays.dto.auth;

public record GoogleStartResponse(
        String authorizationUrl,
        String state
) {
}
