package com.schooldays.dao.student;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.util.List;
import java.util.UUID;

import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class StudentRosterDao {

    private final DSLContext dsl;

    public StudentRosterDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean classBelongsToTenant(UUID tenantId, UUID classId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId)));
    }

    public boolean siteBelongsToTenant(UUID tenantId, UUID siteId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(SCHOOL_SITES)
                .where(SCHOOL_SITES.TENANT_ID.eq(tenantId))
                .and(SCHOOL_SITES.ID.eq(siteId)));
    }

    public boolean classBelongsToSite(UUID tenantId, UUID classId, UUID siteId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .and(PROGRAMS.SITE_ID.eq(siteId)));
    }

    public List<? extends Record> listActiveClassStudents(UUID tenantId, UUID classId, UUID siteId) {
        Condition condition = ENROLLMENTS.TENANT_ID.eq(tenantId)
                .and(CHILDREN.STATUS.eq("active"))
                .and(CLASSES.STATUS.eq("active"))
                .and(ENROLLMENTS.ENROLLMENT_STATUS.notIn("cancelled", "rejected"));
        if (classId != null) {
            condition = condition.and(ENROLLMENTS.CLASS_ID.eq(classId));
        }
        if (siteId != null) {
            condition = condition.and(PROGRAMS.SITE_ID.eq(siteId));
        }

        return dsl.select(
                        ENROLLMENTS.ID,
                        ENROLLMENTS.ENROLLMENT_STATUS,
                        ENROLLMENTS.CREATED_AT,
                        CHILDREN.ID,
                        CHILDREN.FIRST_NAME,
                        CHILDREN.LAST_NAME,
                        CHILDREN.DATE_OF_BIRTH,
                        CHILDREN.PARENT_USER_ID,
                        CLASSES.ID,
                        CLASSES.NAME,
                        CLASSES.STATUS,
                        USERS.EMAIL,
                        USERS.PHONE
                )
                .from(ENROLLMENTS)
                .join(CHILDREN).on(CHILDREN.ID.eq(ENROLLMENTS.CHILD_ID))
                .join(CLASSES).on(CLASSES.ID.eq(ENROLLMENTS.CLASS_ID))
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .join(USERS).on(USERS.ID.eq(CHILDREN.PARENT_USER_ID))
                .where(condition)
                .orderBy(CHILDREN.LAST_NAME.asc(), CHILDREN.FIRST_NAME.asc(), CLASSES.NAME.asc(), ENROLLMENTS.SEQ_ID.asc())
                .fetch();
    }
}
