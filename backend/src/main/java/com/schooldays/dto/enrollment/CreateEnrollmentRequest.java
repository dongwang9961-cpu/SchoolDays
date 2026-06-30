package com.schooldays.dto.enrollment;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateEnrollmentRequest(
        @NotNull UUID tenantId,
        @NotNull UUID classId,
        @NotEmpty List<UUID> childIds,
        List<UUID> optionalFeeItemIds
) {
}
