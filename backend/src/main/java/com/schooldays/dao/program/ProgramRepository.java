package com.schooldays.dao.program;

import java.util.List;
import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.Programs;
import com.schooldays.jooq.generated.tables.records.ProgramsRecord;

@JooqRepositoryBean(tableClass = Programs.class)
public interface ProgramRepository extends JooqRepository<ProgramsRecord, UUID> {

    List<ProgramsRecord> findByTenantIdAndSiteId(UUID tenantId, UUID siteId);
}
