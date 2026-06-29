package com.schooldays.dto.program;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.jooq.generated.tables.records.ProgramsRecord;

public record ProgramResponse(
        UUID id,
        Long seqId,
        UUID tenantId,
        UUID siteId,
        String name,
        String status,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static ProgramResponse from(ProgramsRecord record) {
        JsonNode metadata = metadata(record);
        return new ProgramResponse(
                record.getId(),
                record.getSeqId(),
                record.getTenantId(),
                record.getSiteId(),
                record.getName(),
                record.getStatus(),
                text(metadata, "description"),
                date(metadata, "startDate"),
                date(metadata, "endDate"),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private static JsonNode metadata(ProgramsRecord record) {
        try {
            return OBJECT_MAPPER.readTree(record.getMetadata() == null ? "{}" : record.getMetadata().data());
        } catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private static LocalDate date(JsonNode root, String field) {
        String value = root.path(field).asText("");
        return value.isBlank() ? null : LocalDate.parse(value);
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText("");
    }
}
