package com.schooldays.tenants;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.Tenants;
import com.schooldays.jooq.generated.tables.records.TenantsRecord;

@JooqRepositoryBean(tableClass = Tenants.class)
public interface TenantRepository extends JooqRepository<TenantsRecord, UUID> {

    Optional<TenantsRecord> findByName(String name);

    List<TenantsRecord> findByStatus(String status);

    boolean existsByName(String name);
}
