package com.schooldays.dao.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.dto.school.PublicSchoolResponse;
import com.schooldays.jooq.generated.tables.records.TenantsRecord;
import com.schooldays.tenants.TenantRepository;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
public class TenantDao {

    private final DSLContext dsl;
    private final TenantRepository tenantRepository;

    public TenantDao(DSLContext dsl, TenantRepository tenantRepository) {
        this.dsl = dsl;
        this.tenantRepository = tenantRepository;
    }

    public UUID createTenant(String name, OffsetDateTime now) {
        TenantsRecord saved = tenantRepository.save(new TenantsRecord()
                .setName(name)
                .setStatus("active")
                .setCreatedAt(now)
                .setUpdatedAt(now));
        return saved.getId();
    }

    public Optional<PublicSchoolResponse> findActivePublicSchoolBySlug(String slug) {
        return selectActivePublicSchool()
                .and(DSL.lower(DSL.field("slug", String.class)).eq(slug.toLowerCase()))
                .fetchOptional(record -> new PublicSchoolResponse(
                        record.get(DSL.field("id", UUID.class)),
                        record.get(DSL.field("slug", String.class)),
                        record.get(DSL.field("name", String.class))
                ));
    }

    public Optional<PublicSchoolResponse> findActivePublicSchoolById(UUID tenantId) {
        return selectActivePublicSchool()
                .and(DSL.field("id", UUID.class).eq(tenantId))
                .fetchOptional(record -> new PublicSchoolResponse(
                        record.get(DSL.field("id", UUID.class)),
                        record.get(DSL.field("slug", String.class)),
                        record.get(DSL.field("name", String.class))
                ));
    }

    private org.jooq.SelectConditionStep<org.jooq.Record3<UUID, String, String>> selectActivePublicSchool() {
        var tenants = DSL.table("tenants");
        var id = DSL.field("id", UUID.class);
        var slugField = DSL.field("slug", String.class);
        var name = DSL.field("name", String.class);
        var status = DSL.field("status", String.class);

        return dsl.select(id, slugField, name)
                .from(tenants)
                .where(status.eq("active"))
                .and(slugField.isNotNull());
    }
}
