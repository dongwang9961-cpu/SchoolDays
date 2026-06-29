package com.schooldays.dao.program;

import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.ProgramsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class ProgramDao {

    private final DSLContext dsl;
    private final ProgramRepository programRepository;

    public ProgramDao(DSLContext dsl, ProgramRepository programRepository) {
        this.dsl = dsl;
        this.programRepository = programRepository;
    }

    public List<ProgramsRecord> findByTenantAndSite(UUID tenantId, UUID siteId) {
        return dsl.selectFrom(PROGRAMS)
                .where(PROGRAMS.TENANT_ID.eq(tenantId))
                .and(PROGRAMS.SITE_ID.eq(siteId))
                .orderBy(PROGRAMS.SEQ_ID.asc())
                .fetch();
    }

    public Optional<ProgramsRecord> findByTenantAndId(UUID tenantId, UUID programId) {
        return programRepository.findById(programId)
                .filter(program -> tenantId.equals(program.getTenantId()));
    }

    public ProgramsRecord save(ProgramsRecord record) {
        return programRepository.save(record);
    }
}
