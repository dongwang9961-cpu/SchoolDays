package com.schooldays.dto.attendance;

import java.util.List;
import java.util.UUID;

public record AttendanceGridStudentResponse(
        UUID childId,
        String childName,
        String parentEmail,
        String parentPhone,
        List<AttendanceGridCellResponse> attendance
) {
}
