package com.schooldays.dto.classroom;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateClassRequest(
        @NotNull
        UUID programId,

        @NotBlank
        @Size(max = 255)
        String name,

        @Size(max = 4000)
        String description,

        @NotBlank
        @Size(max = 50)
        String classType,

        List<String> weekdays,

        @Min(1)
        Integer capacity,

        @NotNull
        LocalDate startDate,

        @NotNull
        LocalDate endDate,

        @NotNull
        LocalTime startTime,

        @NotNull
        LocalTime endTime
) {
}
