package com.schooldays.dto.externalcheckin;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExternalCheckInRequest(
        @NotNull UUID tenantId,
        @NotBlank String externalStudentId,
        @NotNull UUID classId,
        @NotNull LocalDate checkDate,
        String studentName,
        String gender,
        String barcodeValue
) {
}
