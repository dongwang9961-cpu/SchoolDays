package com.schooldays.dto.student;

import java.util.List;

public record StudentRosterResponse(List<StudentRosterRowResponse> students) {
}
