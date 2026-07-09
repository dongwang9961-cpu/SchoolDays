package com.schooldays.dto.externalcheckin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExternalCheckInRowResponse(
        UUID id,
        Long seqId,
        String externalStudentId,
        String studentName,
        String gender,
        UUID classId,
        String className,
        LocalDate checkDate,
        OffsetDateTime checkInTime,
        UUID checkedInByUserId,
        String checkedInByRole,
        String status,
        String barcodeValue,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
