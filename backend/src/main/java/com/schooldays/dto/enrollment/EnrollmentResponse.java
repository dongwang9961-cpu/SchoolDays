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
        String classStatus,
        String status,
        List<UUID> selectedOptionalFeeItemIds,
        List<EnrollmentMessageResponse> messages,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String siteName,
        String siteLocation
) {

    public static EnrollmentResponse from(
            EnrollmentsRecord record,
            List<UUID> selectedOptionalFeeItemIds,
            String className,
            LocalDate classStartDate,
            LocalDate classEndDate,
            String classStatus,
            String siteName,
            String siteLocation,
            List<EnrollmentMessageResponse> messages
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
                classStatus,
                record.getEnrollmentStatus(),
                selectedOptionalFeeItemIds == null ? List.of() : selectedOptionalFeeItemIds,
                messages == null ? List.of() : messages,
                record.getCreatedAt(),
                record.getUpdatedAt(),
                siteName,
                siteLocation
        );
    }

    public static EnrollmentResponse from(
            EnrollmentsRecord record,
            List<UUID> selectedOptionalFeeItemIds,
            String className,
            LocalDate classStartDate,
            LocalDate classEndDate,
            String classStatus
    ) {
        return from(record, selectedOptionalFeeItemIds, className, classStartDate, classEndDate, classStatus, null, null, List.of());
    }

    public static EnrollmentResponse from(EnrollmentsRecord record, List<UUID> selectedOptionalFeeItemIds) {
        return from(record, selectedOptionalFeeItemIds, null, null, null, null);
    }
}
