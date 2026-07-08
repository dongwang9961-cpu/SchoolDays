package com.schooldays.dto.externalstudent;

import java.time.LocalDate;

public record ExternalStudentRowResponse(
        String externalId,
        String studentName,
        String lastName,
        String firstName,
        LocalDate birthDate,
        String gradeLevelCode,
        String genderCode
) {
}
