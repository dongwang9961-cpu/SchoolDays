package com.schooldays.dao.auth;

import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.Roles;
import com.schooldays.jooq.generated.tables.records.RolesRecord;

@JooqRepositoryBean(tableClass = Roles.class)
public interface RoleRepository extends JooqRepository<RolesRecord, UUID> {

    Optional<RolesRecord> findByName(String name);
}
