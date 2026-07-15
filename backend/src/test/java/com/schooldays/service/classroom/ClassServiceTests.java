package com.schooldays.service.classroom;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.schooldays.dao.classroom.ClassDao;
import com.schooldays.dao.classroom.ClassRepository;
import com.schooldays.dto.classroom.CreateClassRequest;
import com.schooldays.dto.classroom.UpdateClassRequest;
import com.schooldays.jooq.JooqRepositoryFactory;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static ClassService classService;

    private UUID tenantId;
    private UUID siteId;
    private UUID programId;

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
        ClassRepository repository = new JooqRepositoryFactory(dsl)
                .createRepository(ClassRepository.class, CLASSES, CLASSES.ID);
        classService = new ClassService(dsl, new ClassDao(dsl, repository));
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(CLASSES).execute();
        dsl.deleteFrom(PROGRAMS).execute();
        dsl.deleteFrom(SCHOOL_SITES).execute();
        dsl.deleteFrom(TENANTS).execute();

        tenantId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        programId = UUID.randomUUID();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Class Test School")
                .set(TENANTS.STATUS, "active")
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
    }

    @Test
    void createsListsAndUpdatesClassesForSitePrograms() {
        var created = classService.createClass(
                tenantId,
                new CreateClassRequest(
                        programId,
                        "Beginner Drawing",
                        "Foundational drawing class.",
                        "weekly",
                        List.of("MONDAY", "WEDNESDAY"),
                        12,
                        LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-07-31"),
                        LocalTime.parse("10:00"),
                        LocalTime.parse("11:30")
                )
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.programId()).isEqualTo(programId);
        assertThat(created.status()).isEqualTo("active");
        assertThat(created.description()).isEqualTo("Foundational drawing class.");
        assertThat(created.classType()).isEqualTo("weekly");
        assertThat(created.weekdays()).containsExactly("MONDAY", "WEDNESDAY");
        assertThat(created.capacity()).isEqualTo(12);
        assertThat(created.startTime()).isEqualTo(LocalTime.parse("10:00"));
        assertThat(created.endTime()).isEqualTo(LocalTime.parse("11:30"));

        assertThat(classService.listClasses(tenantId, siteId).classes())
                .singleElement()
                .extracting("name")
                .isEqualTo("Beginner Drawing");

        var updated = classService.updateClass(
                tenantId,
                created.id(),
                new UpdateClassRequest(
                        programId,
                        "Intermediate Drawing",
                        "Updated drawing class.",
                        "time_range",
                        List.of(),
                        14,
                        LocalDate.parse("2026-07-08"),
                        LocalDate.parse("2026-08-07"),
                        LocalTime.parse("13:00"),
                        LocalTime.parse("15:00")
                )
        );

        assertThat(updated.name()).isEqualTo("Intermediate Drawing");
        assertThat(updated.description()).isEqualTo("Updated drawing class.");
        assertThat(updated.classType()).isEqualTo("time_range");
        assertThat(updated.weekdays()).isEmpty();
        assertThat(updated.capacity()).isEqualTo(14);
        assertThat(updated.startDate()).isEqualTo(LocalDate.parse("2026-07-08"));
        assertThat(updated.endDate()).isEqualTo(LocalDate.parse("2026-08-07"));
        assertThat(updated.startTime()).isEqualTo(LocalTime.parse("13:00"));
        assertThat(updated.endTime()).isEqualTo(LocalTime.parse("15:00"));
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
        assertThat(classService.listClasses(tenantId, siteId).classes())
                .singleElement()
                .extracting("name")
                .isEqualTo("Intermediate Drawing");
    }
}
