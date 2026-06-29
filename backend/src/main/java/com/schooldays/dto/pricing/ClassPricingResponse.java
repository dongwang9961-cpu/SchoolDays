package com.schooldays.dto.pricing;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.ClassPricingRecord;

public record ClassPricingResponse(
        UUID id,
        UUID tenantId,
        UUID classId,
        String pricingType,
        String currency,
        Integer totalAmount,
        String status,
        List<ClassFeeItemResponse> feeItems,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static ClassPricingResponse from(ClassPricingRecord record, List<ClassFeeItemResponse> feeItems) {
        if (record == null) {
            return empty(feeItems);
        }
        return new ClassPricingResponse(
                record.getId(),
                record.getTenantId(),
                record.getClassId(),
                record.getPricingType(),
                record.getCurrency(),
                record.getTotalAmount(),
                record.getStatus(),
                feeItems,
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    public static ClassPricingResponse empty(List<ClassFeeItemResponse> feeItems) {
        return new ClassPricingResponse(
                null,
                null,
                null,
                "free",
                "USD",
                0,
                "active",
                feeItems == null ? List.of() : feeItems,
                null,
                null
        );
    }
}
