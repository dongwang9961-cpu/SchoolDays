package com.schooldays.dto.pricing;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.jooq.generated.tables.records.ClassFeeItemsRecord;

public record ClassFeeItemResponse(
        UUID id,
        String category,
        String name,
        String currency,
        Integer fee,
        String note,
        String status
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ClassFeeItemResponse from(ClassFeeItemsRecord record) {
        JsonNode metadata = metadata(record);
        return new ClassFeeItemResponse(
                record.getId(),
                record.getFeeType(),
                record.getName(),
                text(metadata, "currency", "USD"),
                record.getAmount(),
                text(metadata, "note", ""),
                record.getStatus()
        );
    }

    public String feeType() {
        return category;
    }

    public Integer amount() {
        return fee;
    }

    public boolean required() {
        return "required_fees".equals(category);
    }

    private static JsonNode metadata(ClassFeeItemsRecord record) {
        try {
            return OBJECT_MAPPER.readTree(record.getMetadata() == null ? "{}" : record.getMetadata().data());
        } catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static String text(JsonNode node, String fieldName, String fallback) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return fallback;
        }
        return value.asText(fallback);
    }
}
