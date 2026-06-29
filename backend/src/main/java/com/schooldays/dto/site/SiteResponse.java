package com.schooldays.dto.site;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.jooq.generated.tables.records.SchoolSitesRecord;

public record SiteResponse(
        UUID id,
        Long seqId,
        UUID tenantId,
        String name,
        String timezone,
        String status,
        String streetAddress,
        String suite,
        String city,
        String state,
        String zipCode,
        String googlePlaceId,
        String formattedAddress,
        String latitude,
        String longitude,
        String ownerFullName,
        String ownerPhone,
        String ownerEmail,
        String gradeLevelsServed,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static SiteResponse from(SchoolSitesRecord record) {
        JsonNode metadata = metadata(record);
        return new SiteResponse(
                record.getId(),
                record.getSeqId(),
                record.getTenantId(),
                record.getName(),
                record.getTimezone(),
                record.getStatus(),
                text(metadata, "address", "streetAddress"),
                text(metadata, "address", "suite"),
                text(metadata, "address", "city"),
                text(metadata, "address", "state"),
                text(metadata, "address", "zipCode"),
                text(metadata, "address", "googlePlaceId"),
                text(metadata, "address", "formattedAddress"),
                text(metadata, "address", "latitude"),
                text(metadata, "address", "longitude"),
                text(metadata, "owner", "fullName"),
                text(metadata, "owner", "phone"),
                text(metadata, "owner", "email"),
                text(metadata, "gradeLevelsServed"),
                record.getCreatedAt(),
                record.getUpdatedAt()
        );
    }

    private static JsonNode metadata(SchoolSitesRecord record) {
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

    private static String text(JsonNode root, String parent, String field) {
        return text(root.path(parent), field);
    }
}
