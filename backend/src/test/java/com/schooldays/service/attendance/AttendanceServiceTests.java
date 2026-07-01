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
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.web.server.ResponseStatusException;

class AttendanceServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static AttendanceService attendanceService;
    private static final Clock TEST_CLOCK = Clock.fixed(
            Instant.parse("2026-07-15T14:00:00Z"),
            ZoneId.of("America/Detroit")
    );

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
        attendanceService = new AttendanceService(new AttendanceDao(dsl), TEST_CLOCK);
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
    void classAttendanceGridIncludesRosterDatesAndCheckIns() {
        attendanceService.parentCheckIn(
                parentUserId,
                new AttendanceCheckInRequest(tenantId, childId, classId, LocalDate.parse("2026-07-15"))
        );

        var response = attendanceService.getClassAttendanceGrid(tenantId, classId);

        assertThat(response.classRecord().id()).isEqualTo(classId);
        assertThat(response.dates()).hasSize(31);
        assertThat(response.dates())
                .filteredOn(date -> date.classDate().equals(LocalDate.parse("2026-07-01")))
                .singleElement()
                .extracting("scheduled")
                .isEqualTo(true);
        assertThat(response.dates())
                .filteredOn(date -> date.classDate().equals(LocalDate.parse("2026-07-02")))
                .singleElement()
                .extracting("scheduled")
                .isEqualTo(false);
        assertThat(response.students())
                .singleElement()
                .satisfies(student -> {
                    assertThat(student.childId()).isEqualTo(childId);
                    assertThat(student.childName()).isEqualTo("Avery Wang");
                    assertThat(student.attendance())
                            .filteredOn(cell -> cell.classDate().equals(LocalDate.parse("2026-07-15")))
                            .singleElement()
                            .satisfies(cell -> {
                                assertThat(cell.scheduled()).isTrue();
                                assertThat(cell.checkedIn()).isTrue();
                                assertThat(cell.status()).isEqualTo("checked_in");
                            });
                });
    }

    @Test
    void parentCannotCheckInOnDateWithoutScheduledClass() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> attendanceService.parentCheckIn(
                        parentUserId,
                        new AttendanceCheckInRequest(tenantId, childId, classId, LocalDate.parse("2026-07-16"))
                )
        );

        assertThat(exception.getReason())
                .isEqualTo("Morning Art does not meet on Thursday, July 16, 2026. Scheduled days are Monday and Wednesday.");
        assertThat(dsl.fetchCount(ATTENDANCE)).isZero();
    }

    @Test
    void parentCannotCheckInOutsideYesterdayTodayTomorrowWindow() {
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> attendanceService.parentCheckIn(
                        parentUserId,
                        new AttendanceCheckInRequest(tenantId, childId, classId, LocalDate.parse("2026-07-22"))
                )
        );

        assertThat(exception.getReason())
                .isEqualTo("Parents can only check in for yesterday, today, or tomorrow (July 14, 2026 to July 16, 2026).");
        assertThat(dsl.fetchCount(ATTENDANCE)).isZero();
    }
}
