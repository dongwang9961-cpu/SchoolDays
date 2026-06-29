package com.schooldays.dao.auth;

import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.TenantInvitations;
import com.schooldays.jooq.generated.tables.records.TenantInvitationsRecord;

@JooqRepositoryBean(tableClass = TenantInvitations.class)
public interface TenantInvitationRepository extends JooqRepository<TenantInvitationsRecord, UUID> {
}
