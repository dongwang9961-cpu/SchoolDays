package com.schooldays.dao.auth;

import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.TeacherInvitations;
import com.schooldays.jooq.generated.tables.records.TeacherInvitationsRecord;

@JooqRepositoryBean(tableClass = TeacherInvitations.class)
public interface TeacherInvitationRepository extends JooqRepository<TeacherInvitationsRecord, UUID> {
}
