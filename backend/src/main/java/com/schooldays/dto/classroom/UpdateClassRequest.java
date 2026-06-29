package com.schooldays.dto.classroom;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateClassRequest(
        UUID programId,

        @Size(max = 255)
        String name,

        @Size(max = 4000)
        String description,

        @Size(max = 50)
        String classType,

        List<String> weekdays,

        @Min(1)
        Integer capacity,

        LocalDate startDate,

        LocalDate endDate,

        LocalTime startTime,

        LocalTime endTime
) {
}
