package com.schooldays.dao.enrollment;

import static com.schooldays.jooq.generated.tables.ClassFeeItems.CLASS_FEE_ITEMS;
import static com.schooldays.jooq.generated.tables.ClassPricing.CLASS_PRICING;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.EnrollmentPerks.ENROLLMENT_PERKS;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.ClassFeeItemsRecord;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import com.schooldays.jooq.generated.tables.records.EnrollmentsRecord;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class EnrollmentDao {

    private final DSLContext dsl;

    public EnrollmentDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<ClassesRecord> findActiveClass(UUID tenantId, UUID classId) {
        return dsl.selectFrom(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .and(CLASSES.STATUS.eq("active"))
                .fetchOptional();
    }

    public List<ChildrenRecord> findParentChildren(UUID tenantId, UUID parentUserId, List<UUID> childIds) {
        return dsl.selectFrom(CHILDREN)
                .where(CHILDREN.TENANT_ID.eq(tenantId))
                .and(CHILDREN.PARENT_USER_ID.eq(parentUserId))
                .and(CHILDREN.ID.in(childIds))
                .and(CHILDREN.STATUS.eq("active"))
                .fetch();
    }

    public int activeEnrollmentCount(UUID tenantId, UUID classId) {
        return dsl.fetchCount(
                ENROLLMENTS,
                ENROLLMENTS.TENANT_ID.eq(tenantId)
                        .and(ENROLLMENTS.CLASS_ID.eq(classId))
                        .and(ENROLLMENTS.ENROLLMENT_STATUS.notIn("cancelled", "rejected"))
        );
    }

    public boolean enrollmentExists(UUID tenantId, UUID childId, UUID classId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(ENROLLMENTS)
                .where(ENROLLMENTS.TENANT_ID.eq(tenantId))
                .and(ENROLLMENTS.CHILD_ID.eq(childId))
                .and(ENROLLMENTS.CLASS_ID.eq(classId))
                .and(ENROLLMENTS.ENROLLMENT_STATUS.notIn("cancelled", "rejected")));
    }

    public List<ClassFeeItemsRecord> activeFeeItems(UUID tenantId, UUID classId) {
        return dsl.select(CLASS_FEE_ITEMS.fields())
                .from(CLASS_FEE_ITEMS)
                .join(CLASS_PRICING).on(CLASS_PRICING.ID.eq(CLASS_FEE_ITEMS.CLASS_PRICING_ID))
                .where(CLASS_FEE_ITEMS.TENANT_ID.eq(tenantId))
                .and(CLASS_PRICING.CLASS_ID.eq(classId))
                .and(CLASS_PRICING.STATUS.eq("active"))
                .and(CLASS_FEE_ITEMS.STATUS.eq("active"))
                .fetchInto(CLASS_FEE_ITEMS);
    }

    public List<EnrollmentsRecord> listParentEnrollments(UUID tenantId, UUID parentUserId) {
        return dsl.select(ENROLLMENTS.fields())
                .from(ENROLLMENTS)
                .join(CHILDREN).on(CHILDREN.ID.eq(ENROLLMENTS.CHILD_ID))
                .where(ENROLLMENTS.TENANT_ID.eq(tenantId))
                .and(CHILDREN.PARENT_USER_ID.eq(parentUserId))
                .orderBy(ENROLLMENTS.CREATED_AT.desc(), ENROLLMENTS.SEQ_ID.desc())
                .fetchInto(ENROLLMENTS);
    }

    public List<UUID> selectedOptionalFeeItemIds(UUID enrollmentId) {
        return dsl.select(ENROLLMENT_PERKS.CLASS_FEE_ITEM_ID)
                .from(ENROLLMENT_PERKS)
                .where(ENROLLMENT_PERKS.ENROLLMENT_ID.eq(enrollmentId))
                .and(ENROLLMENT_PERKS.PERK_STATUS.notIn("cancelled", "rejected"))
                .fetch(ENROLLMENT_PERKS.CLASS_FEE_ITEM_ID);
    }

    public EnrollmentsRecord createEnrollment(
            UUID tenantId,
            UUID childId,
            UUID classId,
            String status,
            UUID createdByUserId,
            OffsetDateTime now
    ) {
        return dsl.insertInto(ENROLLMENTS)
                .set(ENROLLMENTS.ID, UUID.randomUUID())
                .set(ENROLLMENTS.TENANT_ID, tenantId)
                .set(ENROLLMENTS.CHILD_ID, childId)
                .set(ENROLLMENTS.CLASS_ID, classId)
                .set(ENROLLMENTS.ENROLLMENT_STATUS, status)
                .set(ENROLLMENTS.CREATED_BY_USER_ID, createdByUserId)
                .set(ENROLLMENTS.METADATA, JSONB.valueOf("{}"))
                .set(ENROLLMENTS.CREATED_AT, now)
                .set(ENROLLMENTS.UPDATED_AT, now)
                .returning()
                .fetchOne();
    }

    public void createPerk(UUID tenantId, UUID enrollmentId, UUID classFeeItemId, String status, OffsetDateTime now) {
        dsl.insertInto(ENROLLMENT_PERKS)
                .set(ENROLLMENT_PERKS.ID, UUID.randomUUID())
                .set(ENROLLMENT_PERKS.TENANT_ID, tenantId)
                .set(ENROLLMENT_PERKS.ENROLLMENT_ID, enrollmentId)
                .set(ENROLLMENT_PERKS.CLASS_FEE_ITEM_ID, classFeeItemId)
                .set(ENROLLMENT_PERKS.PERK_TYPE, "optional_fee")
                .set(ENROLLMENT_PERKS.PERK_STATUS, status)
                .set(ENROLLMENT_PERKS.METADATA, JSONB.valueOf("{}"))
                .set(ENROLLMENT_PERKS.CREATED_AT, now)
                .set(ENROLLMENT_PERKS.UPDATED_AT, now)
                .execute();
    }
}
