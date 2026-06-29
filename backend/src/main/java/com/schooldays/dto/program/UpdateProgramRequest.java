package com.schooldays.dto.program;

import java.time.LocalDate;

import jakarta.validation.constraints.Size;

public record UpdateProgramRequest(
        @Size(max = 255)
        String name,

        @Size(max = 4000)
        String description,

        LocalDate startDate,

        LocalDate endDate
) {
}
