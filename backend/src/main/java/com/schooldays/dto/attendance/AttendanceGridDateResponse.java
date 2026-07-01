package com.schooldays.dto.attendance;

import java.time.LocalDate;

public record AttendanceGridDateResponse(
        LocalDate classDate,
        boolean scheduled
) {
}
