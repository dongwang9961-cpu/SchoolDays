package com.schooldays.service.program;

import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.UUID;

import com.schooldays.dao.program.ProgramDao;
import com.schooldays.dao.program.ProgramRepository;
import com.schooldays.jooq.JooqRepositoryFactory;
import com.schooldays.dto.program.CreateProgramRequest;
import com.schooldays.dto.program.UpdateProgramRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProgramServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static ProgramService programService;

    private UUID tenantId;
    private UUID siteId;

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
        ProgramRepository repository = new JooqRepositoryFactory(dsl)
                .createRepository(ProgramRepository.class, PROGRAMS, PROGRAMS.ID);
        programService = new ProgramService(dsl, new ProgramDao(dsl, repository));
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(PROGRAMS).execute();
        dsl.deleteFrom(SCHOOL_SITES).execute();
        dsl.deleteFrom(TENANTS).execute();

        tenantId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Program Test School")
                .set(TENANTS.STATUS, "active")
                .execute();
        dsl.insertInto(SCHOOL_SITES)
                .set(SCHOOL_SITES.ID, siteId)
                .set(SCHOOL_SITES.TENANT_ID, tenantId)
                .set(SCHOOL_SITES.NAME, "Main Site")
                .set(SCHOOL_SITES.TIMEZONE, "America/Detroit")
                .set(SCHOOL_SITES.STATUS, "active")
                .execute();
    }

    @Test
    void createsListsAndUpdatesProgramsForSite() {
        var created = programService.createProgram(
                tenantId,
                new CreateProgramRequest(
                        siteId,
                        "Summer Art",
                        "Drawing, painting, and mixed-media projects.",
                        LocalDate.parse("2026-07-01"),
                        LocalDate.parse("2026-08-15")
                )
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.siteId()).isEqualTo(siteId);
        assertThat(created.status()).isEqualTo("active");
        assertThat(created.description()).isEqualTo("Drawing, painting, and mixed-media projects.");
        assertThat(created.startDate()).isEqualTo(LocalDate.parse("2026-07-01"));
        assertThat(created.endDate()).isEqualTo(LocalDate.parse("2026-08-15"));

        assertThat(programService.listPrograms(tenantId, siteId).programs())
                .singleElement()
                .extracting("name")
                .isEqualTo("Summer Art");

        var updated = programService.updateProgram(
                tenantId,
                created.id(),
                new UpdateProgramRequest(
                        "Summer Art Studio",
                        "Updated studio program description.",
                        LocalDate.parse("2026-07-08"),
                        LocalDate.parse("2026-08-22")
                )
        );

        assertThat(updated.name()).isEqualTo("Summer Art Studio");
        assertThat(updated.status()).isEqualTo("active");
        assertThat(updated.description()).isEqualTo("Updated studio program description.");
        assertThat(updated.startDate()).isEqualTo(LocalDate.parse("2026-07-08"));
        assertThat(updated.endDate()).isEqualTo(LocalDate.parse("2026-08-22"));
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }
}
