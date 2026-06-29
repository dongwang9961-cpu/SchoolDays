package com.schooldays.dto.school;

import java.util.UUID;

public record PublicSchoolResponse(
        UUID tenantId,
        String slug,
        String name
) {
}
