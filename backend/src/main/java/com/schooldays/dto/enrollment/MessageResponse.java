package com.schooldays.dto.enrollment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String messageType,
        String message,
        UUID enrollmentId,
        UUID childId,
        String childName,
        UUID classId,
        String className,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {
}
