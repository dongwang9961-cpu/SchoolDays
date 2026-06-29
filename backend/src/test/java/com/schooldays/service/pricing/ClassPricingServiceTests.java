package com.schooldays.service.pricing;

import static com.schooldays.jooq.generated.tables.ClassFeeItems.CLASS_FEE_ITEMS;
import static com.schooldays.jooq.generated.tables.ClassPricing.CLASS_PRICING;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.schooldays.dto.pricing.ClassFeeItemRequest;
import com.schooldays.dto.pricing.SaveClassPricingRequest;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClassPricingServiceTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static ClassPricingService classPricingService;

    private UUID tenantId;
    private UUID siteId;
    private UUID programId;
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
        classPricingService = new ClassPricingService(dsl);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(CLASS_FEE_ITEMS).execute();
        dsl.deleteFrom(CLASS_PRICING).execute();
        dsl.deleteFrom(CLASSES).execute();
        dsl.deleteFrom(PROGRAMS).execute();
        dsl.deleteFrom(SCHOOL_SITES).execute();
        dsl.deleteFrom(TENANTS).execute();

        tenantId = UUID.randomUUID();
        siteId = UUID.randomUUID();
        programId = UUID.randomUUID();
        classId = UUID.randomUUID();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Pricing Test School")
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
        dsl.insertInto(CLASSES)
                .set(CLASSES.ID, classId)
                .set(CLASSES.TENANT_ID, tenantId)
                .set(CLASSES.PROGRAM_ID, programId)
                .set(CLASSES.NAME, "Painting 101")
                .set(CLASSES.CAPACITY, 12)
                .set(CLASSES.START_DATE, LocalDate.parse("2026-07-01"))
                .set(CLASSES.END_DATE, LocalDate.parse("2026-07-31"))
                .set(CLASSES.STATUS, "active")
                .execute();
    }

    @Test
    void savesReturnsAndReplacesClassPricing() {
        var saved = classPricingService.savePricing(
                tenantId,
                classId,
                new SaveClassPricingRequest(
                        "paid",
                        "usd",
                        List.of(
                                new ClassFeeItemRequest("required_fees", "Tuition", "USD", 10000, "Monthly tuition"),
                                new ClassFeeItemRequest("optional_fees", "Snack", "USD", 1200, "Optional snack"),
                                new ClassFeeItemRequest("required_fees", "Lunch", "USD", 2000, "Required lunch")
                        )
                )
        );

        assertThat(saved.id()).isNotNull();
        assertThat(saved.classId()).isEqualTo(classId);
        assertThat(saved.currency()).isEqualTo("USD");
        assertThat(saved.totalAmount()).isEqualTo(12000);
        assertThat(saved.feeItems())
                .extracting("category")
                .containsExactly("required_fees", "required_fees", "optional_fees");
        assertThat(saved.feeItems())
                .extracting("currency")
                .containsOnly("USD");
        assertThat(saved.feeItems())
                .extracting("note")
                .contains("Monthly tuition", "Optional snack", "Required lunch");

        var publicPricing = classPricingService.getPublicPricing(classId);
        assertThat(publicPricing.totalAmount()).isEqualTo(12000);
        assertThat(publicPricing.feeItems()).hasSize(3);

        var updated = classPricingService.savePricing(
                tenantId,
                classId,
                new SaveClassPricingRequest(
                        "paid",
                        "USD",
                        List.of(new ClassFeeItemRequest("required_fees", "Tuition", "USD", 15000, "Updated tuition"))
                )
        );

        assertThat(updated.id()).isEqualTo(saved.id());
        assertThat(updated.totalAmount()).isEqualTo(15000);
        assertThat(updated.feeItems())
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.category()).isEqualTo("required_fees");
                    assertThat(item.fee()).isEqualTo(15000);
                    assertThat(item.required()).isTrue();
                    assertThat(item.note()).isEqualTo("Updated tuition");
                });
    }
}
