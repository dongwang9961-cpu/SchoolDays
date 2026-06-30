package com.schooldays.dto.child;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ChildRequest(
        @NotNull UUID tenantId,
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @NotNull LocalDate dateOfBirth,
        @NotBlank @Size(max = 50) String gender,
        @NotBlank @Size(max = 50) String grade,
        @NotBlank @Size(max = 200) String school,
        @NotEmpty List<@NotBlank @Size(max = 100) String> race,
        @Size(max = 1000) String note
) {
}
