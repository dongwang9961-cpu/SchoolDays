package com.schooldays.service.student;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static com.schooldays.jooq.generated.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.schooldays.dao.student.StudentRosterDao;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StudentRosterServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static StudentRosterService studentRosterService;

    private UUID tenantId;
    private UUID parentUserId;
    private UUID childId;
    private UUID artClassId;
    private UUID musicClassId;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder()
                .setLocaleConfig("locale", "C")
                .start();
        Flyway.configure()
                .dataSource(postgres.getPostgresDatabase())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dsl = DSL.using(postgres.getPostgresDatabase(), SQLDialect.POSTGRES);
        studentRosterService = new StudentRosterService(new StudentRosterDao(dsl));
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(ENROLLMENTS).execute();
        dsl.deleteFrom(CHILDREN).execute();
        dsl.deleteFrom(CLASSES).execute();
        dsl.deleteFrom(PROGRAMS).execute();
        dsl.deleteFrom(SCHOOL_SITES).execute();
        dsl.deleteFrom(USERS).execute();
        dsl.deleteFrom(TENANTS).execute();

        tenantId = UUID.randomUUID();
        parentUserId = UUID.randomUUID();
        childId = UUID.randomUUID();
        artClassId = UUID.randomUUID();
        musicClassId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Roster Test School")
                .set(TENANTS.STATUS, "active")
                .execute();
        dsl.insertInto(USERS)
                .set(USERS.ID, parentUserId)
                .set(USERS.EMAIL, "parent-roster@example.com")
                .set(USERS.PHONE, "555-0199")
                .set(USERS.STATUS, "active")
                .execute();
        dsl.insertInto(SCHOOL_SITES)
                .set(SCHOOL_SITES.ID, siteId)
                .set(SCHOOL_SITES.TENANT_ID, tenantId)
                .set(SCHOOL_SITES.NAME, "Main Site")
                .set(SCHOOL_SITES.TIMEZONE, "America/Detroit")
                .set(SCHOOL_SITES.STATUS, "active")
                .execute();
        dsl.insertInto(PROGRAMS)
                .set(PROGRAMS.ID, programId)
                .set(PROGRAMS.TENANT_ID, tenantId)
                .set(PROGRAMS.SITE_ID, siteId)
                .set(PROGRAMS.NAME, "Summer Program")
                .set(PROGRAMS.STATUS, "active")
                .execute();
        insertClass(artClassId, programId, "Morning Art");
        insertClass(musicClassId, programId, "Music Lab");
        dsl.insertInto(CHILDREN)
                .set(CHILDREN.ID, childId)
                .set(CHILDREN.TENANT_ID, tenantId)
                .set(CHILDREN.PARENT_USER_ID, parentUserId)
                .set(CHILDREN.FIRST_NAME, "Avery")
                .set(CHILDREN.LAST_NAME, "Wang")
                .set(CHILDREN.DATE_OF_BIRTH, LocalDate.parse("2017-04-15"))
                .set(CHILDREN.STATUS, "active")
                .execute();
        insertEnrollment(artClassId, "2026-06-01T10:00:00Z");
        insertEnrollment(musicClassId, "2026-06-02T10:00:00Z");
    }

    @Test
    void allClassRosterShowsMultiClassStudentOnce() {
        var response = studentRosterService.listActiveClassStudents(tenantId, null);

        assertThat(response.students())
                .singleElement()
                .satisfies(student -> {
                    assertThat(student.childId()).isEqualTo(childId);
                    assertThat(student.childName()).isEqualTo("Avery Wang");
                    assertThat(student.classCount()).isEqualTo(2);
                    assertThat(student.classId()).isNull();
                    assertThat(student.enrollmentId()).isNull();
                    assertThat(student.className()).isEqualTo("Morning Art, Music Lab");
                    assertThat(student.enrolledAt()).isEqualTo(OffsetDateTime.parse("2026-06-02T10:00:00Z"));
                });
    }

    @Test
    void classFilteredRosterKeepsTheClassSpecificRow() {
        var response = studentRosterService.listActiveClassStudents(tenantId, artClassId);

        assertThat(response.students())
                .singleElement()
                .satisfies(student -> {
                    assertThat(student.childId()).isEqualTo(childId);
                    assertThat(student.classCount()).isEqualTo(1);
                    assertThat(student.classId()).isEqualTo(artClassId);
                    assertThat(student.enrollmentId()).isNotNull();
                    assertThat(student.className()).isEqualTo("Morning Art");
                });
    }

    private void insertClass(UUID classId, UUID programId, String name) {
        dsl.insertInto(CLASSES)
                .set(CLASSES.ID, classId)
                .set(CLASSES.TENANT_ID, tenantId)
                .set(CLASSES.PROGRAM_ID, programId)
                .set(CLASSES.NAME, name)
                .set(CLASSES.START_DATE, LocalDate.parse("2026-07-01"))
                .set(CLASSES.END_DATE, LocalDate.parse("2026-07-31"))
                .set(CLASSES.STATUS, "active")
                .execute();
    }

    private void insertEnrollment(UUID classId, String createdAt) {
        OffsetDateTime timestamp = OffsetDateTime.parse(createdAt);
        dsl.insertInto(ENROLLMENTS)
                .set(ENROLLMENTS.ID, UUID.randomUUID())
                .set(ENROLLMENTS.TENANT_ID, tenantId)
                .set(ENROLLMENTS.CHILD_ID, childId)
                .set(ENROLLMENTS.CLASS_ID, classId)
                .set(ENROLLMENTS.ENROLLMENT_STATUS, "enrolled")
                .set(ENROLLMENTS.CREATED_BY_USER_ID, parentUserId)
                .set(ENROLLMENTS.CREATED_AT, timestamp)
                .set(ENROLLMENTS.UPDATED_AT, timestamp)
                .execute();
    }
}
