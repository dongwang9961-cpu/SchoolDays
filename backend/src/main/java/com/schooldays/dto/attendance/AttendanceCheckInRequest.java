package com.schooldays.dto.attendance;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record AttendanceCheckInRequest(
        @NotNull UUID tenantId,
        @NotNull UUID childId,
        @NotNull UUID classId,
        @NotNull LocalDate classDate
) {
}
