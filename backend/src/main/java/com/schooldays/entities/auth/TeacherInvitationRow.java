package com.schooldays.entities.auth;

import java.util.UUID;

public record TeacherInvitationRow(
        UUID id,
        UUID tenantId,
        String email
) {
}
