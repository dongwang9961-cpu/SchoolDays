package com.schooldays.service.email;

public record SystemEmailMessage(
        String toEmail,
        String fromEmail,
        String fromName,
        String subject,
        String textBody,
        String htmlBody
) {
}
