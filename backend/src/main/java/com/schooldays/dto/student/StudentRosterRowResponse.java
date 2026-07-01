package com.schooldays.dto.student;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Children.CHILDREN;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;
import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.Record;

public record StudentRosterRowResponse(
        UUID enrollmentId,
        UUID childId,
        String childName,
        String firstName,
        String lastName,
        LocalDate dateOfBirth,
        UUID classId,
        String className,
        String classStatus,
        String enrollmentStatus,
        UUID parentUserId,
        String parentEmail,
        String parentPhone,
        OffsetDateTime enrolledAt,
        int classCount
) {

    public static StudentRosterRowResponse from(Record record) {
        String firstName = record.get(CHILDREN.FIRST_NAME);
        String lastName = record.get(CHILDREN.LAST_NAME);
        String childName = String.join(" ",
                firstName == null ? "" : firstName,
                lastName == null ? "" : lastName
        ).trim();
        return new StudentRosterRowResponse(
                record.get(ENROLLMENTS.ID),
                record.get(CHILDREN.ID),
                childName,
                firstName,
                lastName,
                record.get(CHILDREN.DATE_OF_BIRTH),
                record.get(CLASSES.ID),
                record.get(CLASSES.NAME),
                record.get(CLASSES.STATUS),
                record.get(ENROLLMENTS.ENROLLMENT_STATUS),
                record.get(CHILDREN.PARENT_USER_ID),
                record.get(USERS.EMAIL),
                record.get(USERS.PHONE),
                record.get(ENROLLMENTS.CREATED_AT),
                1
        );
    }
}
