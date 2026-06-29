package com.schooldays.dto.program;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateProgramRequest(
        @NotNull
        UUID siteId,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 4000)
        String description,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate
) {
}
