package com.schooldays.dto.auth;

public record InviteUserResultResponse(
        String email,
        String role,
        String outcome,
        String message
) {
}
