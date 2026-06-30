package com.schooldays.dao.notification;

import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.EmailNotificationHistory.EMAIL_NOTIFICATION_HISTORY;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;
import static com.schooldays.jooq.generated.tables.NotificationProviders.NOTIFICATION_PROVIDERS;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static com.schooldays.jooq.generated.tables.UserRoles.USER_ROLES;
import static com.schooldays.jooq.generated.tables.Users.USERS;
import static com.schooldays.jooq.generated.tables.Roles.ROLES;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.EmailNotificationHistoryRecord;
import com.schooldays.jooq.generated.tables.records.NotificationProvidersRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationDao {

    private final DSLContext dsl;

    public NotificationDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public boolean tenantExists(UUID tenantId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(TENANTS)
                .where(TENANTS.ID.eq(tenantId)));
    }

    public boolean userCanSendForTenant(UUID tenantId, UUID userId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(USER_ROLES)
                .join(ROLES).on(ROLES.ID.eq(USER_ROLES.ROLE_ID))
                .where(USER_ROLES.TENANT_ID.eq(tenantId))
                .and(USER_ROLES.USER_ID.eq(userId))
                .and(ROLES.NAME.in("SCHOOL_ADMIN", "TEACHER")));
    }

    public boolean classBelongsToTenant(UUID tenantId, UUID classId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId)));
    }

    public List<String> parentEmailsForClass(UUID tenantId, UUID classId) {
        return dsl.selectDistinct(USERS.EMAIL)
                .from(ENROLLMENTS)
                .join(CHILDREN).on(CHILDREN.ID.eq(ENROLLMENTS.CHILD_ID))
                .join(USERS).on(USERS.ID.eq(CHILDREN.PARENT_USER_ID))
                .where(ENROLLMENTS.TENANT_ID.eq(tenantId))
                .and(ENROLLMENTS.CLASS_ID.eq(classId))
                .and(ENROLLMENTS.ENROLLMENT_STATUS.in("pending", "approved", "active"))
                .orderBy(USERS.EMAIL.asc())
                .fetch(USERS.EMAIL);
    }

    public Optional<NotificationProvidersRecord> findGmailProvider(UUID tenantId, UUID userId) {
        return dsl.selectFrom(NOTIFICATION_PROVIDERS)
                .where(NOTIFICATION_PROVIDERS.TENANT_ID.eq(tenantId))
                .and(NOTIFICATION_PROVIDERS.CREATED_BY_USER_ID.eq(userId))
                .and(NOTIFICATION_PROVIDERS.PROVIDER_TYPE.eq("gmail_oauth"))
                .orderBy(NOTIFICATION_PROVIDERS.SEQ_ID.desc())
                .limit(1)
                .fetchOptional();
    }

    public List<NotificationProvidersRecord> listProviders(UUID tenantId, UUID userId) {
        return dsl.selectFrom(NOTIFICATION_PROVIDERS)
                .where(NOTIFICATION_PROVIDERS.TENANT_ID.eq(tenantId))
                .and(NOTIFICATION_PROVIDERS.CREATED_BY_USER_ID.eq(userId))
                .orderBy(NOTIFICATION_PROVIDERS.SEQ_ID.desc())
                .fetch();
    }

    public NotificationProvidersRecord saveProvider(NotificationProvidersRecord record) {
        if (record.getId() == null) {
            return dsl.insertInto(NOTIFICATION_PROVIDERS)
                    .set(record)
                    .returning()
                    .fetchOne();
        }
        record.store();
        return record;
    }

    public EmailNotificationHistoryRecord saveHistory(EmailNotificationHistoryRecord record) {
        return dsl.insertInto(EMAIL_NOTIFICATION_HISTORY)
                .set(record)
                .returning()
                .fetchOne();
    }

    public List<EmailNotificationHistoryRecord> listHistory(UUID tenantId, UUID userId, int limit) {
        return dsl.selectFrom(EMAIL_NOTIFICATION_HISTORY)
                .where(EMAIL_NOTIFICATION_HISTORY.TENANT_ID.eq(tenantId))
                .and(EMAIL_NOTIFICATION_HISTORY.SENDER_USER_ID.eq(userId))
                .orderBy(EMAIL_NOTIFICATION_HISTORY.SEQ_ID.desc())
                .limit(limit)
                .fetch();
    }

    public void markProviderActive(UUID providerId, String fromEmail, String fromName, String metadata, OffsetDateTime now) {
        dsl.update(NOTIFICATION_PROVIDERS)
                .set(NOTIFICATION_PROVIDERS.STATUS, "active")
                .set(NOTIFICATION_PROVIDERS.FROM_EMAIL, fromEmail)
                .set(NOTIFICATION_PROVIDERS.FROM_NAME, fromName)
                .set(NOTIFICATION_PROVIDERS.METADATA, org.jooq.JSONB.valueOf(metadata))
                .set(NOTIFICATION_PROVIDERS.UPDATED_AT, now)
                .where(NOTIFICATION_PROVIDERS.ID.eq(providerId))
                .execute();
    }
}
