package com.schooldays.service.attendance;

import static com.schooldays.jooq.generated.tables.Attendance.ATTENDANCE;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static com.schooldays.jooq.generated.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import com.schooldays.dao.attendance.AttendanceDao;
import com.schooldays.dto.attendance.AttendanceCheckInRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AttendanceServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static AttendanceService attendanceService;

    private UUID tenantId;
    private UUID parentUserId;
    private UUID childId;
    private UUID classId;

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
        attendanceService = new AttendanceService(new AttendanceDao(dsl));
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(ATTENDANCE).execute();
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
        classId = UUID.randomUUID();
        UUID siteId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();

        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Attendance Test School")
                .set(TENANTS.STATUS, "active")
                .execute();
        dsl.insertInto(USERS)
                .set(USERS.ID, parentUserId)
                .set(USERS.EMAIL, "parent-attendance@example.com")
                .set(USERS.PHONE, "555-0100")
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
        dsl.insertInto(CLASSES)
                .set(CLASSES.ID, classId)
                .set(CLASSES.TENANT_ID, tenantId)
                .set(CLASSES.PROGRAM_ID, programId)
                .set(CLASSES.NAME, "Morning Art")
                .set(CLASSES.START_DATE, LocalDate.parse("2026-07-01"))
                .set(CLASSES.END_DATE, LocalDate.parse("2026-07-31"))
                .set(CLASSES.METADATA, JSONB.valueOf("""
                        {
                          "classType": "weekly",
                          "weekdays": ["MONDAY", "WEDNESDAY"],
                          "startTime": "09:00",
                          "endTime": "13:00"
                        }
                        """))
                .set(CLASSES.STATUS, "active")
                .execute();
        dsl.insertInto(CHILDREN)
                .set(CHILDREN.ID, childId)
                .set(CHILDREN.TENANT_ID, tenantId)
                .set(CHILDREN.PARENT_USER_ID, parentUserId)
                .set(CHILDREN.FIRST_NAME, "Avery")
                .set(CHILDREN.LAST_NAME, "Wang")
                .set(CHILDREN.STATUS, "active")
                .execute();
        dsl.insertInto(ENROLLMENTS)
                .set(ENROLLMENTS.ID, UUID.randomUUID())
                .set(ENROLLMENTS.TENANT_ID, tenantId)
                .set(ENROLLMENTS.CHILD_ID, childId)
                .set(ENROLLMENTS.CLASS_ID, classId)
                .set(ENROLLMENTS.ENROLLMENT_STATUS, "enrolled")
                .set(ENROLLMENTS.CREATED_BY_USER_ID, parentUserId)
                .execute();
    }

    @Test
    void parentCanCheckInAndReviewAttendanceHistory() {
        var response = attendanceService.parentCheckIn(
                parentUserId,
                new AttendanceCheckInRequest(tenantId, childId, classId, LocalDate.parse("2026-07-15"))
        );

        assertThat(response.childName()).isEqualTo("Avery Wang");
        assertThat(response.className()).isEqualTo("Morning Art");
        assertThat(response.status()).isEqualTo("checked_in");
        assertThat(response.checkedInByRole()).isEqualTo("PARENT");

        var history = attendanceService.listParentAttendance(tenantId, parentUserId);
        assertThat(history.attendance())
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.childId()).isEqualTo(childId);
                    assertThat(entry.classId()).isEqualTo(classId);
                    assertThat(entry.classDate()).isEqualTo(LocalDate.parse("2026-07-15"));
                });
    }

    @Test
    void parentCannotCheckInOnDateWithoutScheduledClass() {
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> attendanceService.parentCheckIn(
                        parentUserId,
                        new AttendanceCheckInRequest(tenantId, childId, classId, LocalDate.parse("2026-07-16"))
                )
        );

        assertThat(dsl.fetchCount(ATTENDANCE)).isZero();
    }
}
