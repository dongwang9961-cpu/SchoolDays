package com.schooldays.dao.auth;

import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.Users;
import com.schooldays.jooq.generated.tables.records.UsersRecord;

@JooqRepositoryBean(tableClass = Users.class)
public interface UserRepository extends JooqRepository<UsersRecord, UUID> {

    Optional<UsersRecord> findByEmail(String email);
}
