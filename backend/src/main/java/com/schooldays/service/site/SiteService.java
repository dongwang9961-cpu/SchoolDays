package com.schooldays.service.site;

import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dto.site.CreateSiteRequest;
import com.schooldays.dto.site.SiteListResponse;
import com.schooldays.dto.site.SiteQuotaResponse;
import com.schooldays.dto.site.SiteResponse;
import com.schooldays.dto.site.UpdateSiteRequest;
import com.schooldays.jooq.generated.tables.records.TenantsRecord;
import com.schooldays.service.cache.SchoolDataCacheService;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SiteService {

    private static final int DEFAULT_MAX_SITES = 1;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final SchoolDataCacheService cacheService;

    @Autowired
    public SiteService(DSLContext dsl, SchoolDataCacheService cacheService) {
        this.dsl = dsl;
        this.cacheService = cacheService;
    }

    SiteService(DSLContext dsl) {
        this(dsl, new SchoolDataCacheService());
    }

    public SiteListResponse listSites(UUID tenantId) {
        TenantsRecord tenant = requireTenant(tenantId);
        List<SiteResponse> sites = dsl.selectFrom(SCHOOL_SITES)
                .where(SCHOOL_SITES.TENANT_ID.eq(tenantId))
                .orderBy(SCHOOL_SITES.SEQ_ID.asc())
                .fetch(SiteResponse::from);
        return new SiteListResponse(sites, quotaFor(tenant, sites.size()));
    }

    public SiteResponse createSite(UUID tenantId, CreateSiteRequest request) {
        TenantsRecord tenant = requireTenant(tenantId);
        SiteQuotaResponse quota = quotaFor(tenant, countSites(tenantId));
        if (!quota.unlimitedSites() && quota.remainingSites() != null && quota.remainingSites() <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This school has reached its site limit. Ask a platform admin to increase the tenant site limit."
            );
        }
        OffsetDateTime now = OffsetDateTime.now();
        var record = dsl.insertInto(SCHOOL_SITES)
                .set(SCHOOL_SITES.TENANT_ID, tenantId)
                .set(SCHOOL_SITES.NAME, request.name().trim())
                .set(SCHOOL_SITES.TIMEZONE, request.timezone().trim())
                .set(SCHOOL_SITES.STATUS, normalizeStatus(request.status()))
                .set(SCHOOL_SITES.METADATA, metadataFor(request))
                .set(SCHOOL_SITES.CREATED_AT, now)
                .set(SCHOOL_SITES.UPDATED_AT, now)
                .returning()
                .fetchOne();

        if (record == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Site could not be created");
        }
        cacheService.clearClassCaches(tenantId);
        return SiteResponse.from(record);
    }

    public SiteResponse updateSite(UUID tenantId, UUID siteId, UpdateSiteRequest request) {
        requireTenant(tenantId);
        var record = dsl.selectFrom(SCHOOL_SITES)
                .where(SCHOOL_SITES.ID.eq(siteId))
                .and(SCHOOL_SITES.TENANT_ID.eq(tenantId))
                .fetchOne();

        if (record == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site was not found");
        }
        if (request.name() != null) {
            record.setName(request.name().trim());
        }
        if (request.timezone() != null) {
            record.setTimezone(request.timezone().trim());
        }
        if (request.status() != null) {
            record.setStatus(normalizeStatus(request.status()));
        }
        record.setMetadata(metadataFor(request, record.getMetadata()));
        record.setUpdatedAt(OffsetDateTime.now());
        record.store();
        cacheService.clearClassCaches(tenantId);
        return SiteResponse.from(record);
    }

    private TenantsRecord requireTenant(UUID tenantId) {
        TenantsRecord tenant = dsl.selectFrom(TENANTS)
                .where(TENANTS.ID.eq(tenantId))
                .fetchOne();
        if (tenant == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant was not found");
        }
        return tenant;
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "active";
        }
        return status.trim().toLowerCase();
    }

    private JSONB metadataFor(CreateSiteRequest request) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        writeAddress(
                root,
                request.streetAddress(),
                request.suite(),
                request.city(),
                request.state(),
                request.zipCode(),
                request.googlePlaceId(),
                request.formattedAddress(),
                request.latitude(),
                request.longitude()
        );
        writeOwner(root, request.ownerFullName(), request.ownerPhone(), request.ownerEmail());
        put(root, "gradeLevelsServed", request.gradeLevelsServed());
        return JSONB.valueOf(root.toString());
    }

    private JSONB metadataFor(UpdateSiteRequest request, JSONB existingMetadata) {
        ObjectNode root = readObject(existingMetadata);
        writeAddress(
                root,
                request.streetAddress(),
                request.suite(),
                request.city(),
                request.state(),
                request.zipCode(),
                request.googlePlaceId(),
                request.formattedAddress(),
                request.latitude(),
                request.longitude()
        );
        writeOwner(root, request.ownerFullName(), request.ownerPhone(), request.ownerEmail());
        put(root, "gradeLevelsServed", request.gradeLevelsServed());
        return JSONB.valueOf(root.toString());
    }

    private ObjectNode readObject(JSONB metadata) {
        try {
            var node = OBJECT_MAPPER.readTree(metadata == null ? "{}" : metadata.data());
            return node != null && node.isObject() ? (ObjectNode) node : OBJECT_MAPPER.createObjectNode();
        } catch (Exception ignored) {
            return OBJECT_MAPPER.createObjectNode();
        }
    }

    private void writeAddress(
            ObjectNode root,
            String streetAddress,
            String suite,
            String city,
            String state,
            String zipCode,
            String googlePlaceId,
            String formattedAddress,
            String latitude,
            String longitude
    ) {
        ObjectNode address = root.withObject("/address");
        put(address, "streetAddress", streetAddress);
        put(address, "suite", suite);
        put(address, "city", city);
        put(address, "state", state);
        put(address, "zipCode", zipCode);
        put(address, "googlePlaceId", googlePlaceId);
        put(address, "formattedAddress", formattedAddress);
        put(address, "latitude", latitude);
        put(address, "longitude", longitude);
    }

    private void writeOwner(ObjectNode root, String ownerFullName, String ownerPhone, String ownerEmail) {
        ObjectNode owner = root.withObject("/owner");
        put(owner, "fullName", ownerFullName);
        put(owner, "phone", ownerPhone);
        put(owner, "email", ownerEmail);
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null) {
            node.put(field, value.trim());
        }
    }

    private int countSites(UUID tenantId) {
        return dsl.fetchCount(SCHOOL_SITES, SCHOOL_SITES.TENANT_ID.eq(tenantId));
    }

    private SiteQuotaResponse quotaFor(TenantsRecord tenant, int currentSiteCount) {
        SiteLimit siteLimit = siteLimitFor(tenant);
        if (siteLimit.unlimitedSites()) {
            return new SiteQuotaResponse(true, null, currentSiteCount, null);
        }
        int remainingSites = Math.max(siteLimit.maxSites() - currentSiteCount, 0);
        return new SiteQuotaResponse(false, siteLimit.maxSites(), currentSiteCount, remainingSites);
    }

    private SiteLimit siteLimitFor(TenantsRecord tenant) {
        JSONB metadata = tenant.getMetadata();
        if (metadata == null) {
            return SiteLimit.limited(DEFAULT_MAX_SITES);
        }

        String rawLimit = dsl.select(DSL.field(
                        "({0} #>> '{settings,max_sites}')",
                        String.class,
                        DSL.val(metadata)
                ))
                .fetchOneInto(String.class);

        if (rawLimit == null || rawLimit.isBlank()) {
            return SiteLimit.limited(DEFAULT_MAX_SITES);
        }
        if ("unlimited".equalsIgnoreCase(rawLimit.trim())) {
            return SiteLimit.unlimited();
        }
        try {
            int maxSites = Integer.parseInt(rawLimit.trim());
            if (maxSites < 1) {
                return SiteLimit.limited(DEFAULT_MAX_SITES);
            }
            return SiteLimit.limited(maxSites);
        } catch (NumberFormatException ignored) {
            return SiteLimit.limited(DEFAULT_MAX_SITES);
        }
    }

    private record SiteLimit(boolean unlimitedSites, int maxSites) {

        static SiteLimit unlimited() {
            return new SiteLimit(true, 0);
        }

        static SiteLimit limited(int maxSites) {
            return new SiteLimit(false, maxSites);
        }
    }
}
