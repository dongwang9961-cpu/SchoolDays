package com.schooldays.dto.enrollment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EnrollmentRequestResponse(
        UUID id,
        UUID childId,
        String childName,
        UUID classId,
        String className,
        LocalDate classStartDate,
        LocalDate classEndDate,
        String parentEmail,
        String status,
        OffsetDateTime requestedAt
) {
}
