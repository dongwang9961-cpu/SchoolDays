package com.schooldays.dto.enrollment;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EnrollmentMessageResponse(
        UUID id,
        String messageType,
        String message,
        OffsetDateTime createdAt,
        OffsetDateTime readAt
) {
}
