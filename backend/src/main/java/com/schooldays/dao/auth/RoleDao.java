package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.Roles.ROLES;
import static com.schooldays.jooq.generated.tables.UserRoles.USER_ROLES;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.schooldays.entities.auth.TenantRole;
import com.schooldays.service.auth.InvalidAuthRequestException;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class RoleDao {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final DSLContext dsl;
    private final RoleRepository roleRepository;

    public RoleDao(DSLContext dsl, RoleRepository roleRepository) {
        this.dsl = dsl;
        this.roleRepository = roleRepository;
    }

    public List<TenantRole> findTenantRoles(UUID userId) {
        return dsl.select(USER_ROLES.TENANT_ID, ROLES.NAME, USER_ROLES.METADATA)
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                .where(USER_ROLES.USER_ID.eq(userId))
                .fetch(record -> new TenantRole(
                        record.get(USER_ROLES.TENANT_ID),
                        record.get(ROLES.NAME),
                        metadataMap(record.get(USER_ROLES.METADATA))
                ));
    }

    public boolean hasTenantRole(UUID userId, UUID tenantId, String roleName) {
        return findTenantRoles(userId).stream()
                .anyMatch(tenantRole -> tenantId.equals(tenantRole.tenantId()) && roleName.equals(tenantRole.role()));
    }

    public void assignRole(UUID userId, UUID tenantId, String roleName) {
        assignRole(userId, tenantId, roleName, null, false);
    }

    public void assignSiteManagerRole(UUID userId, UUID tenantId, UUID siteId) {
        if (siteId == null) {
            throw new InvalidAuthRequestException("A site must be selected for site manager access");
        }
        LinkedHashSet<String> siteIds = findTenantRoles(userId).stream()
                .filter(tenantRole -> tenantId.equals(tenantRole.tenantId()))
                .filter(tenantRole -> "SITE_MANAGER".equals(tenantRole.role()))
                .flatMap(tenantRole -> tenantRole.siteIds().stream())
                .map(UUID::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        siteIds.add(siteId.toString());
        assignRole(userId, tenantId, "SITE_MANAGER", Map.of(
                "siteId", siteId.toString(),
                "siteIds", List.copyOf(siteIds)
        ), true);
    }

    public boolean hasSiteManagerRole(UUID userId, UUID tenantId, UUID siteId) {
        return findTenantRoles(userId).stream()
                .anyMatch(tenantRole -> tenantId.equals(tenantRole.tenantId())
                        && "SITE_MANAGER".equals(tenantRole.role())
                        && tenantRole.appliesToSite(siteId));
    }

    private void assignRole(UUID userId, UUID tenantId, String roleName, Map<String, Object> metadata, boolean updateOnConflict) {
        UUID roleId = roleRepository.findByName(roleName)
                .map(record -> record.getId())
                .orElseThrow(() -> new InvalidAuthRequestException("Unknown role: " + roleName));

        var insert = dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE_ID, roleId)
                .set(USER_ROLES.TENANT_ID, tenantId);
        if (metadata != null) {
            insert.set(USER_ROLES.METADATA, metadataJson(metadata));
        }
        var onConflict = insert.onConflict(USER_ROLES.USER_ID, USER_ROLES.ROLE_ID, USER_ROLES.TENANT_ID);
        if (updateOnConflict && metadata != null) {
            onConflict.doUpdate()
                    .set(USER_ROLES.METADATA, metadataJson(metadata))
                    .execute();
            return;
        }
        onConflict.doNothing().execute();
    }

    private Map<String, Object> metadataMap(JSONB metadata) {
        if (metadata == null || metadata.data() == null || metadata.data().isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(metadata.data(), METADATA_TYPE);
            return parsed == null ? Map.of() : parsed;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private JSONB metadataJson(Map<String, Object> metadata) {
        try {
            return JSONB.valueOf(OBJECT_MAPPER.writeValueAsString(metadata == null ? Map.of() : metadata));
        } catch (Exception exception) {
            throw new InvalidAuthRequestException("Role metadata is invalid");
        }
    }
}
