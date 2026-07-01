package com.schooldays.dao.attendance;

import static com.schooldays.jooq.generated.tables.Attendance.ATTENDANCE;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;
import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.AttendanceRecord;
import com.schooldays.jooq.generated.tables.records.ClassesRecord;
import com.schooldays.jooq.generated.tables.records.ChildrenRecord;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Condition;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
public class AttendanceDao {

    private final DSLContext dsl;

    public AttendanceDao(DSLContext dsl) {
        this.dsl = dsl;
    }

    public Optional<ChildrenRecord> findParentChild(UUID tenantId, UUID parentUserId, UUID childId) {
        return dsl.selectFrom(CHILDREN)
                .where(CHILDREN.TENANT_ID.eq(tenantId))
                .and(CHILDREN.PARENT_USER_ID.eq(parentUserId))
                .and(CHILDREN.ID.eq(childId))
                .and(CHILDREN.STATUS.eq("active"))
                .fetchOptional();
    }

    public Optional<ClassesRecord> findActiveClass(UUID tenantId, UUID classId) {
        return dsl.selectFrom(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .and(CLASSES.STATUS.eq("active"))
                .fetchOptional();
    }

    public Optional<ClassesRecord> findClass(UUID tenantId, UUID classId) {
        return dsl.selectFrom(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .fetchOptional();
    }

    public Optional<ClassesRecord> findActiveClassById(UUID classId) {
        return dsl.selectFrom(CLASSES)
                .where(CLASSES.ID.eq(classId))
                .and(CLASSES.STATUS.eq("active"))
                .fetchOptional();
    }

    public boolean hasActiveEnrollment(UUID tenantId, UUID childId, UUID classId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(ENROLLMENTS)
                .where(ENROLLMENTS.TENANT_ID.eq(tenantId))
                .and(ENROLLMENTS.CHILD_ID.eq(childId))
                .and(ENROLLMENTS.CLASS_ID.eq(classId))
                .and(ENROLLMENTS.ENROLLMENT_STATUS.notIn("cancelled", "rejected")));
    }

    public AttendanceRecord checkIn(
            UUID tenantId,
            UUID childId,
            UUID classId,
            LocalDate classDate,
            UUID checkedInByUserId,
            String checkedInByRole,
            OffsetDateTime now
    ) {
        return dsl.insertInto(ATTENDANCE)
                .set(ATTENDANCE.ID, UUID.randomUUID())
                .set(ATTENDANCE.TENANT_ID, tenantId)
                .set(ATTENDANCE.CHILD_ID, childId)
                .set(ATTENDANCE.CLASS_ID, classId)
                .set(ATTENDANCE.CLASS_DATE, classDate)
                .set(ATTENDANCE.CHECKED_IN_AT, now)
                .set(ATTENDANCE.CHECKED_IN_BY_USER_ID, checkedInByUserId)
                .set(ATTENDANCE.CHECKED_IN_BY_ROLE, checkedInByRole)
                .set(ATTENDANCE.STATUS, "checked_in")
                .set(ATTENDANCE.METADATA, JSONB.valueOf("{}"))
                .set(ATTENDANCE.CREATED_AT, now)
                .set(ATTENDANCE.UPDATED_AT, now)
                .onConflict(ATTENDANCE.CHILD_ID, ATTENDANCE.CLASS_ID, ATTENDANCE.CLASS_DATE)
                .doUpdate()
                .set(ATTENDANCE.CHECKED_IN_AT, now)
                .set(ATTENDANCE.CHECKED_IN_BY_USER_ID, checkedInByUserId)
                .set(ATTENDANCE.CHECKED_IN_BY_ROLE, checkedInByRole)
                .set(ATTENDANCE.STATUS, "checked_in")
                .set(ATTENDANCE.UPDATED_AT, now)
                .returning()
                .fetchOne();
    }

    public List<Record> listParentAttendance(UUID tenantId, UUID parentUserId) {
        return listAttendanceWhere(
                ATTENDANCE.TENANT_ID.eq(tenantId),
                CHILDREN.PARENT_USER_ID.eq(parentUserId)
        );
    }

    public List<Record> listParentChildAttendance(UUID tenantId, UUID parentUserId, UUID childId) {
        return listAttendanceWhere(
                ATTENDANCE.TENANT_ID.eq(tenantId),
                ATTENDANCE.CHILD_ID.eq(childId),
                CHILDREN.PARENT_USER_ID.eq(parentUserId)
        );
    }

    public List<Record> listClassAttendance(UUID classId, LocalDate classDate) {
        return listAttendanceWhere(
                ATTENDANCE.CLASS_ID.eq(classId),
                ATTENDANCE.CLASS_DATE.eq(classDate)
        );
    }

    public List<Record> listClassRoster(UUID tenantId, UUID classId) {
        return dsl.select(
                        CHILDREN.ID,
                        CHILDREN.FIRST_NAME,
                        CHILDREN.LAST_NAME,
                        USERS.EMAIL,
                        USERS.PHONE
                )
                .from(ENROLLMENTS)
                .join(CHILDREN).on(CHILDREN.ID.eq(ENROLLMENTS.CHILD_ID))
                .join(USERS).on(USERS.ID.eq(CHILDREN.PARENT_USER_ID))
                .where(ENROLLMENTS.TENANT_ID.eq(tenantId))
                .and(ENROLLMENTS.CLASS_ID.eq(classId))
                .and(ENROLLMENTS.ENROLLMENT_STATUS.notIn("cancelled", "rejected"))
                .and(CHILDREN.STATUS.eq("active"))
                .orderBy(CHILDREN.LAST_NAME.asc(), CHILDREN.FIRST_NAME.asc(), CHILDREN.SEQ_ID.asc())
                .fetch()
                .stream()
                .map(record -> (Record) record)
                .toList();
    }

    public List<Record> listClassAttendance(UUID tenantId, UUID classId) {
        return listAttendanceWhere(
                ATTENDANCE.TENANT_ID.eq(tenantId),
                ATTENDANCE.CLASS_ID.eq(classId)
        );
    }

    private List<Record> listAttendanceWhere(Condition... conditions) {
        return dsl.select(
                        ATTENDANCE.ID,
                        ATTENDANCE.SEQ_ID,
                        ATTENDANCE.TENANT_ID,
                        ATTENDANCE.CHILD_ID,
                        CHILDREN.FIRST_NAME,
                        CHILDREN.LAST_NAME,
                        ATTENDANCE.CLASS_ID,
                        CLASSES.NAME,
                        ATTENDANCE.CLASS_DATE,
                        ATTENDANCE.CHECKED_IN_AT,
                        ATTENDANCE.CHECKED_IN_BY_USER_ID,
                        ATTENDANCE.CHECKED_IN_BY_ROLE,
                        ATTENDANCE.STATUS,
                        ATTENDANCE.CREATED_AT,
                        ATTENDANCE.UPDATED_AT
                )
                .from(ATTENDANCE)
                .join(CHILDREN).on(CHILDREN.ID.eq(ATTENDANCE.CHILD_ID))
                .join(CLASSES).on(CLASSES.ID.eq(ATTENDANCE.CLASS_ID))
                .where(conditions)
                .orderBy(ATTENDANCE.CLASS_DATE.desc(), ATTENDANCE.CHECKED_IN_AT.desc(), ATTENDANCE.SEQ_ID.desc())
                .fetch()
                .stream()
                .map(record -> (Record) record)
                .toList();
    }
}
