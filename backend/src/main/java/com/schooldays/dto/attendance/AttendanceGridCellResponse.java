package com.schooldays.dto.attendance;

import java.time.LocalDate;
import java.time.OffsetDateTime;

public record AttendanceGridCellResponse(
        LocalDate classDate,
        boolean scheduled,
        boolean checkedIn,
        OffsetDateTime checkedInAt,
        String status
) {
}
