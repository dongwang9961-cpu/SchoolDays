package com.schooldays.dto.attendance;

import java.util.List;

public record AttendanceListResponse(List<AttendanceResponse> attendance) {
}
