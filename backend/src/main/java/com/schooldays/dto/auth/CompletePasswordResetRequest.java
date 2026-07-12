package com.schooldays.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePasswordResetRequest(
        @NotBlank @Size(max = 200) String token,
        @NotBlank @Size(min = 8, max = 128) String password
) {
}
