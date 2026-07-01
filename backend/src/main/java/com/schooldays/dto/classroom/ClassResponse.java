package com.schooldays.dto.classroom;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;

public record ClassResponse(
        UUID id,
        Long seqId,
        UUID tenantId,
        UUID programId,
        String name,
        String description,
        String classType,
        List<String> weekdays,
        Integer capacity,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime registrationClosesAt,
        LocalTime startTime,
        LocalTime endTime,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ClassResponse from(ClassesRecord record) {
        JsonNode metadata = metadata(record);
        return new ClassResponse(
                record.getId(),
                record.getSeqId(),
                record.getTenantId(),
                record.getProgramId(),
                record.getName(),
                text(metadata, "description"),
                text(metadata, "classType"),
                list(metadata, "weekdays"),
                record.getCapacity(),
                record.getStartDate(),
                record.getEndDate(),
                record.getRegistrationClosesAt(),
                time(metadata, "startTime"),
                time(metadata, "endTime"),
                record.getStatus(),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private static JsonNode metadata(ClassesRecord record) {
        try {
            return OBJECT_MAPPER.readTree(record.getMetadata() == null ? "{}" : record.getMetadata().data());
        } catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }

    private static LocalTime time(JsonNode root, String field) {
        String value = text(root, field);
        return value.isBlank() ? null : LocalTime.parse(value);
    }

    private static List<String> list(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (!value.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(value.spliterator(), false)
                .map(JsonNode::asText)
                .filter(item -> !item.isBlank())
                .toList();
    }
}
