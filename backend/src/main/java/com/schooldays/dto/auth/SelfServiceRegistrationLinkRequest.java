package com.schooldays.dto.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SelfServiceRegistrationLinkRequest(
        @NotNull UUID tenantId,
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 50) String intendedRole
) {
}
