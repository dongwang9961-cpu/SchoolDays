package com.schooldays.dto.attendance;

import java.util.List;

import com.schooldays.dto.classroom.ClassResponse;

public record AttendanceGridResponse(
        ClassResponse classRecord,
        List<AttendanceGridDateResponse> dates,
        List<AttendanceGridStudentResponse> students
) {
}
