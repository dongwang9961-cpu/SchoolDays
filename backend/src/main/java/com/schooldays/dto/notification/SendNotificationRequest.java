package com.schooldays.dto.notification;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;

public record SendNotificationRequest(
        UUID classId,
        List<String> bccEmails,
        List<String> ccEmails,
        @NotBlank String subject,
        @NotBlank String body,
        String bodyMimeType,
        String templateEml
) {
}
