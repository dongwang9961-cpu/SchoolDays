package com.schooldays.dto.auth;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteUserRequest(
        @NotBlank String role,
        @Size(min = 1) List<@NotBlank @jakarta.validation.constraints.Email @Size(max = 320) String> emails,
        UUID classId
) {
}
