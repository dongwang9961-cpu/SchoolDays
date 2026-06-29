package com.schooldays.dao.auth;

import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.UserRegistrationLinks;
import com.schooldays.jooq.generated.tables.records.UserRegistrationLinksRecord;

@JooqRepositoryBean(tableClass = UserRegistrationLinks.class)
public interface RegistrationLinkRepository extends JooqRepository<UserRegistrationLinksRecord, UUID> {
}
