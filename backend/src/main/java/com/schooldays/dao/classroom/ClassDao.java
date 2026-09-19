package com.schooldays.dao.classroom;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;

import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class ClassDao {

    private final DSLContext dsl;
    private final ClassRepository classRepository;

    public ClassDao(DSLContext dsl, ClassRepository classRepository) {
        this.dsl = dsl;
        this.classRepository = classRepository;
    }

    public List<ClassesRecord> findByTenantAndSite(UUID tenantId, UUID siteId) {
        return dsl.select(CLASSES.fields())
                .from(CLASSES)
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(PROGRAMS.SITE_ID.eq(siteId))
                .orderBy(CLASSES.SEQ_ID.asc())
                .fetchInto(CLASSES);
    }

    public List<ClassesRecord> findAvailableForRegistration(UUID tenantId, OffsetDateTime now) {
        return dsl.select(CLASSES.fields())
                .from(CLASSES)
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .join(SCHOOL_SITES).on(SCHOOL_SITES.ID.eq(PROGRAMS.SITE_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.STATUS.eq("active"))
                .and(PROGRAMS.STATUS.eq("active"))
                .and(SCHOOL_SITES.STATUS.eq("active"))
                .and(CLASSES.REGISTRATION_OPENS_AT.isNull()
                        .or(CLASSES.REGISTRATION_OPENS_AT.le(now)))
                .and(CLASSES.REGISTRATION_CLOSES_AT.isNotNull()
                        .and(CLASSES.REGISTRATION_CLOSES_AT.ge(now))
                        .or(CLASSES.REGISTRATION_CLOSES_AT.isNull()
                                .and(CLASSES.END_DATE.ge(now.toLocalDate()))))
                .orderBy(CLASSES.START_DATE.asc(), CLASSES.SEQ_ID.asc())
                .fetchInto(CLASSES);
    }

    public List<? extends Record> findAvailableForRegistrationWithSite(UUID tenantId, OffsetDateTime now) {
        return dsl.select(CLASSES.fields())
                .select(SCHOOL_SITES.fields())
                .from(CLASSES)
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .join(SCHOOL_SITES).on(SCHOOL_SITES.ID.eq(PROGRAMS.SITE_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.STATUS.eq("active"))
                .and(PROGRAMS.STATUS.eq("active"))
                .and(SCHOOL_SITES.STATUS.eq("active"))
                .and(CLASSES.REGISTRATION_OPENS_AT.isNull()
                        .or(CLASSES.REGISTRATION_OPENS_AT.le(now)))
                .and(CLASSES.REGISTRATION_CLOSES_AT.isNotNull()
                        .and(CLASSES.REGISTRATION_CLOSES_AT.ge(now))
                        .or(CLASSES.REGISTRATION_CLOSES_AT.isNull()
                                .and(CLASSES.END_DATE.ge(now.toLocalDate()))))
                .orderBy(CLASSES.START_DATE.asc(), CLASSES.SEQ_ID.asc())
                .fetch();
    }

    public Optional<ClassesRecord> findByTenantAndId(UUID tenantId, UUID classId) {
        return classRepository.findById(classId)
                .filter(classRecord -> tenantId.equals(classRecord.getTenantId()));
    }

    public ClassesRecord save(ClassesRecord record) {
        return classRepository.save(record);
    }
}
