package com.schooldays.service.program;

import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dao.program.ProgramDao;
import com.schooldays.dto.program.CreateProgramRequest;
import com.schooldays.dto.program.ProgramListResponse;
import com.schooldays.dto.program.ProgramResponse;
import com.schooldays.dto.program.UpdateProgramRequest;
import com.schooldays.jooq.generated.tables.records.ProgramsRecord;
import com.schooldays.service.cache.SchoolDataCacheService;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProgramService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DSLContext dsl;
    private final ProgramDao programDao;
    private final SchoolDataCacheService cacheService;

    @Autowired
    public ProgramService(DSLContext dsl, ProgramDao programDao, SchoolDataCacheService cacheService) {
        this.dsl = dsl;
        this.programDao = programDao;
        this.cacheService = cacheService;
    }

    ProgramService(DSLContext dsl, ProgramDao programDao) {
        this(dsl, programDao, new SchoolDataCacheService());
    }

    public ProgramListResponse listPrograms(UUID tenantId, UUID siteId) {
        requireTenant(tenantId);
        requireSite(tenantId, siteId);
        List<ProgramResponse> programs = programDao.findByTenantAndSite(tenantId, siteId)
                .stream()
                .map(ProgramResponse::from)
                .toList();
        return new ProgramListResponse(programs);
    }

    public ProgramResponse createProgram(UUID tenantId, CreateProgramRequest request) {
        requireTenant(tenantId);
        requireSite(tenantId, request.siteId());
        validateDates(request.startDate(), request.endDate());

        OffsetDateTime now = OffsetDateTime.now();
        ProgramsRecord saved = programDao.save(new ProgramsRecord()
                .setTenantId(tenantId)
                .setSiteId(request.siteId())
                .setName(request.name().trim())
                .setStatus("active")
                .setMetadata(metadataFor(request.description(), request.startDate(), request.endDate()))
                .setCreatedAt(now)
                .setUpdatedAt(now));
        cacheService.clearClassCaches(tenantId);
        return ProgramResponse.from(saved);
    }

    public ProgramResponse updateProgram(UUID tenantId, UUID programId, UpdateProgramRequest request) {
        requireTenant(tenantId);
        ProgramsRecord record = programDao.findByTenantAndId(tenantId, programId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Program was not found"));

        ProgramResponse existing = ProgramResponse.from(record);
        String description = request.description() == null ? existing.description() : request.description();
        LocalDate startDate = request.startDate() == null ? existing.startDate() : request.startDate();
        LocalDate endDate = request.endDate() == null ? existing.endDate() : request.endDate();
        validateDates(startDate, endDate);

        if (request.name() != null) {
            record.setName(request.name().trim());
        }
        record.setMetadata(metadataFor(description, startDate, endDate));
        record.setUpdatedAt(OffsetDateTime.now());
        record.changed(true);
        ProgramsRecord saved = programDao.save(record);
        cacheService.clearClassCaches(tenantId);
        return ProgramResponse.from(saved);
    }

    private void requireTenant(UUID tenantId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(TENANTS)
                .where(TENANTS.ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant was not found");
        }
    }

    private void requireSite(UUID tenantId, UUID siteId) {
        boolean exists = dsl.fetchExists(dsl.selectOne()
                .from(SCHOOL_SITES)
                .where(SCHOOL_SITES.ID.eq(siteId))
                .and(SCHOOL_SITES.TENANT_ID.eq(tenantId)));
        if (!exists) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Site was not found");
        }
    }

    private void validateDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Program start date and end date are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Program end date cannot be before start date");
        }
    }

    private JSONB metadataFor(String description, LocalDate startDate, LocalDate endDate) {
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        if (description != null) {
            root.put("description", description.trim());
        }
        root.put("startDate", startDate.toString());
        root.put("endDate", endDate.toString());
        return JSONB.valueOf(root.toString());
    }
}
