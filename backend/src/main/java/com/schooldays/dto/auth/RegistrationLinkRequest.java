package com.schooldays.dto.auth;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegistrationLinkRequest(
        @NotNull UUID tenantId,
        @NotBlank @Email @Size(max = 320) String email
) {
}
