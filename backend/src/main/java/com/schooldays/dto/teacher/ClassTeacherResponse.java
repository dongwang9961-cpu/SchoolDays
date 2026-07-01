package com.schooldays.dto.teacher;

import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;
import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.jooq.Record;

public record ClassTeacherResponse(
        UUID assignmentId,
        UUID classId,
        UUID teacherUserId,
        String firstName,
        String lastName,
        String email,
        String phone,
        String status,
        OffsetDateTime assignedAt
) {

    public static ClassTeacherResponse from(Record record) {
        return new ClassTeacherResponse(
                record.get(TEACHER_ASSIGNMENTS.ID),
                record.get(TEACHER_ASSIGNMENTS.CLASS_ID),
                record.get(TEACHER_ASSIGNMENTS.TEACHER_USER_ID),
                record.get(USERS.FIRST_NAME),
                record.get(USERS.LAST_NAME),
                record.get(USERS.EMAIL),
                record.get(USERS.PHONE),
                record.get(USERS.STATUS),
                record.get(TEACHER_ASSIGNMENTS.CREATED_AT)
        );
    }
}
