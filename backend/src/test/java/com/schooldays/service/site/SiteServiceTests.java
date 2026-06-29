package com.schooldays.service.site;

import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import com.schooldays.dto.site.CreateSiteRequest;
import com.schooldays.dto.site.UpdateSiteRequest;
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

class SiteServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static SiteService siteService;

    private UUID tenantId;

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
        siteService = new SiteService(dsl);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(SCHOOL_SITES).execute();
        dsl.deleteFrom(TENANTS).execute();

        tenantId = UUID.randomUUID();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Site Test School")
                .set(TENANTS.STATUS, "active")
                .execute();
    }

    @Test
    void createsListsAndUpdatesSites() {
        var created = siteService.createSite(
                tenantId,
                createSiteRequest("Main Studio")
        );

        assertThat(created.id()).isNotNull();
        assertThat(created.seqId()).isNotNull();
        assertThat(created.status()).isEqualTo("active");
        assertThat(siteService.listSites(tenantId).sites())
                .singleElement()
                .extracting("name")
                .isEqualTo("Main Studio");

        var updated = siteService.updateSite(
                tenantId,
                created.id(),
                new UpdateSiteRequest(
                        "Main Art Studio",
                        "America/Detroit",
                        "inactive",
                        "123 Main St",
                        "Suite 200",
                        "Ann Arbor",
                        "MI",
                        "48104",
                        "ChIJN1t_tDeuEmsRUsoyG83frY4",
                        "123 Main St, Ann Arbor, MI 48104, USA",
                        "42.2808",
                        "-83.7430",
                        "Longlong Admin",
                        "555-0100",
                        "school.admin@longlong-art-studio.test",
                        "K-5"
                )
        );

        assertThat(updated.name()).isEqualTo("Main Art Studio");
        assertThat(updated.timezone()).isEqualTo("America/Detroit");
        assertThat(updated.status()).isEqualTo("inactive");
        assertThat(updated.streetAddress()).isEqualTo("123 Main St");
        assertThat(updated.suite()).isEqualTo("Suite 200");
        assertThat(updated.city()).isEqualTo("Ann Arbor");
        assertThat(updated.state()).isEqualTo("MI");
        assertThat(updated.zipCode()).isEqualTo("48104");
        assertThat(updated.googlePlaceId()).isEqualTo("ChIJN1t_tDeuEmsRUsoyG83frY4");
        assertThat(updated.formattedAddress()).isEqualTo("123 Main St, Ann Arbor, MI 48104, USA");
        assertThat(updated.latitude()).isEqualTo("42.2808");
        assertThat(updated.longitude()).isEqualTo("-83.7430");
        assertThat(updated.ownerFullName()).isEqualTo("Longlong Admin");
        assertThat(updated.ownerPhone()).isEqualTo("555-0100");
        assertThat(updated.ownerEmail()).isEqualTo("school.admin@longlong-art-studio.test");
        assertThat(updated.gradeLevelsServed()).isEqualTo("K-5");
        assertThat(updated.updatedAt()).isAfterOrEqualTo(created.updatedAt());
    }

    @Test
    void rejectsCreateWhenTenantReachesDefaultOneSiteLimit() {
        siteService.createSite(tenantId, createSiteRequest("Only Site"));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> siteService.createSite(
                        tenantId,
                        createSiteRequest("Second Site")
                ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("site limit");
    }

    @Test
    void supportsConfiguredAndUnlimitedSiteLimits() {
        dsl.update(TENANTS)
                .set(TENANTS.METADATA, JSONB.valueOf("{\"settings\":{\"max_sites\":2}}"))
                .where(TENANTS.ID.eq(tenantId))
                .execute();

        siteService.createSite(tenantId, createSiteRequest("Site One"));
        var afterFirst = siteService.listSites(tenantId);

        assertThat(afterFirst.quota().unlimitedSites()).isFalse();
        assertThat(afterFirst.quota().maxSites()).isEqualTo(2);
        assertThat(afterFirst.quota().remainingSites()).isEqualTo(1);

        siteService.createSite(tenantId, createSiteRequest("Site Two"));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> siteService.createSite(
                        tenantId,
                        createSiteRequest("Site Three")
                ))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class)
                .hasMessageContaining("site limit");

        dsl.update(TENANTS)
                .set(TENANTS.METADATA, JSONB.valueOf("{\"settings\":{\"max_sites\":\"unlimited\"}}"))
                .where(TENANTS.ID.eq(tenantId))
                .execute();

        siteService.createSite(tenantId, createSiteRequest("Site Three"));
        var unlimited = siteService.listSites(tenantId);

        assertThat(unlimited.quota().unlimitedSites()).isTrue();
        assertThat(unlimited.quota().maxSites()).isNull();
        assertThat(unlimited.quota().remainingSites()).isNull();
        assertThat(unlimited.quota().currentSiteCount()).isEqualTo(3);
    }

    private CreateSiteRequest createSiteRequest(String name) {
        return new CreateSiteRequest(
                name,
                "America/New_York",
                null,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                ""
        );
    }
}
