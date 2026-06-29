package com.schooldays.dao.classroom;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import org.jooq.DSLContext;
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

    public Optional<ClassesRecord> findByTenantAndId(UUID tenantId, UUID classId) {
        return classRepository.findById(classId)
                .filter(classRecord -> tenantId.equals(classRecord.getTenantId()));
    }

    public ClassesRecord save(ClassesRecord record) {
        return classRepository.save(record);
    }
}
