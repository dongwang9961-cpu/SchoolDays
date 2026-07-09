package com.schooldays.dto.externalcheckin;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExternalCheckInResponse(
        UUID id,
        Long seqId,
        UUID tenantId,
        String externalStudentId,
        UUID classId,
        String className,
        LocalDate checkDate,
        OffsetDateTime checkInTime,
        UUID checkedInByUserId,
        String checkedInByRole,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
