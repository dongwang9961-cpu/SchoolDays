package com.schooldays.dto.enrollment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.EnrollmentsRecord;

public record EnrollmentResponse(
        UUID id,
        Long seqId,
        UUID tenantId,
        UUID childId,
        UUID classId,
        String className,
        LocalDate classStartDate,
        LocalDate classEndDate,
        String status,
        List<UUID> selectedOptionalFeeItemIds,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static EnrollmentResponse from(
            EnrollmentsRecord record,
            List<UUID> selectedOptionalFeeItemIds,
            String className,
            LocalDate classStartDate,
            LocalDate classEndDate
    ) {
        return new EnrollmentResponse(
                record.getId(),
                record.getSeqId(),
                record.getTenantId(),
                record.getChildId(),
                record.getClassId(),
                className,
                classStartDate,
                classEndDate,
                record.getEnrollmentStatus(),
                selectedOptionalFeeItemIds == null ? List.of() : selectedOptionalFeeItemIds,
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    public static EnrollmentResponse from(EnrollmentsRecord record, List<UUID> selectedOptionalFeeItemIds) {
        return from(record, selectedOptionalFeeItemIds, null, null, null);
    }
}
