package com.schooldays.dto.child;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;

public record ChildResponse(
        UUID id,
        UUID tenantId,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        String status,
        String gender,
        String grade,
        String school,
        List<String> race,
        String note
) {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ChildResponse fromRecord(ChildrenRecord record) {
        JsonNode metadata = metadata(record);
        return new ChildResponse(
                record.getId(),
                record.getTenantId(),
                record.getFirstName(),
                record.getLastName(),
                record.getDateOfBirth(),
                record.getStatus(),
                text(metadata, "gender"),
                text(metadata, "grade"),
                text(metadata, "school"),
                race(metadata),
                text(metadata, "note")
        );
    }

    private static JsonNode metadata(ChildrenRecord record) {
        try {
            return record.getMetadata() == null
                    ? OBJECT_MAPPER.createObjectNode()
                    : OBJECT_MAPPER.readTree(record.getMetadata().data());
        } catch (Exception exception) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static String text(JsonNode metadata, String field) {
        JsonNode value = metadata.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private static List<String> race(JsonNode metadata) {
        JsonNode values = metadata.get("race");
        if (values == null || !values.isArray()) {
            return List.of();
        }
        return OBJECT_MAPPER.convertValue(
                values,
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, String.class)
        );
    }
}
