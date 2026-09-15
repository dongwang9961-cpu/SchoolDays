package com.schooldays.entities.auth;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record TenantRole(UUID tenantId, String role, Map<String, Object> metadata) {

    public TenantRole(UUID tenantId, String role) {
        this(tenantId, role, Map.of());
    }

    public TenantRole {
        metadata = metadata == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    public Set<UUID> siteIds() {
        LinkedHashSet<UUID> siteIds = new LinkedHashSet<>();
        addSiteId(siteIds, metadata.get("siteId"));
        Object rawSiteIds = metadata.get("siteIds");
        if (rawSiteIds instanceof Iterable<?> values) {
            values.forEach(value -> addSiteId(siteIds, value));
        } else {
            addSiteId(siteIds, rawSiteIds);
        }
        return Set.copyOf(siteIds);
    }

    public boolean appliesToSite(UUID siteId) {
        return siteId != null && siteIds().contains(siteId);
    }

    private void addSiteId(Set<UUID> siteIds, Object rawValue) {
        if (rawValue instanceof UUID value) {
            siteIds.add(value);
            return;
        }
        if (rawValue == null) {
            return;
        }
        try {
            siteIds.add(UUID.fromString(String.valueOf(rawValue)));
        } catch (IllegalArgumentException ignored) {
            // Ignore malformed metadata so a bad role row cannot widen access.
        }
    }
}
