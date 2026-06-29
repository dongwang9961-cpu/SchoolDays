package com.schooldays.dao.classroom;

import java.util.UUID;

import com.schooldays.jooq.JooqRepository;
import com.schooldays.jooq.JooqRepositoryBean;
import com.schooldays.jooq.generated.tables.Classes;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;

@JooqRepositoryBean(tableClass = Classes.class)
public interface ClassRepository extends JooqRepository<ClassesRecord, UUID> {
}
