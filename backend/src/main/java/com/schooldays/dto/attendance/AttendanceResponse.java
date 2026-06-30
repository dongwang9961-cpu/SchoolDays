package com.schooldays.dto.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.Record;

import static com.schooldays.jooq.generated.tables.Attendance.ATTENDANCE;
import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;

public record AttendanceResponse(
        UUID id,
        Long seqId,
        UUID tenantId,
        UUID childId,
        String childName,
        UUID classId,
        String className,
        LocalDate classDate,
        OffsetDateTime checkedInAt,
        UUID checkedInByUserId,
        String checkedInByRole,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {

    public static AttendanceResponse from(Record record) {
        String firstName = record.get(CHILDREN.FIRST_NAME);
        String lastName = record.get(CHILDREN.LAST_NAME);
        String childName = String.join(" ",
                firstName == null ? "" : firstName,
                lastName == null ? "" : lastName
        ).trim();
        return new AttendanceResponse(
                record.get(ATTENDANCE.ID),
                record.get(ATTENDANCE.SEQ_ID),
                record.get(ATTENDANCE.TENANT_ID),
                record.get(ATTENDANCE.CHILD_ID),
                childName,
                record.get(ATTENDANCE.CLASS_ID),
                record.get(CLASSES.NAME),
                record.get(ATTENDANCE.CLASS_DATE),
                record.get(ATTENDANCE.CHECKED_IN_AT),
                record.get(ATTENDANCE.CHECKED_IN_BY_USER_ID),
                record.get(ATTENDANCE.CHECKED_IN_BY_ROLE),
                record.get(ATTENDANCE.STATUS),
                record.get(ATTENDANCE.CREATED_AT),
                record.get(ATTENDANCE.UPDATED_AT)
        );
    }
}
