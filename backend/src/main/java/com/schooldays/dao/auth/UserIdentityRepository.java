package com.schooldays.dao.auth;

import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.UserIdentities;
import com.schooldays.jooq.generated.tables.records.UserIdentitiesRecord;

@JooqRepositoryBean(tableClass = UserIdentities.class)
public interface UserIdentityRepository extends JooqRepository<UserIdentitiesRecord, UUID> {

    Optional<UserIdentitiesRecord> findByProviderAndProviderSubject(String provider, String providerSubject);
}
