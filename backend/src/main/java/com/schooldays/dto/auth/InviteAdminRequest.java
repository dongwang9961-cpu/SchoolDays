package com.schooldays.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InviteAdminRequest(
        @NotBlank @Email @Size(max = 320) String email
) {
}
