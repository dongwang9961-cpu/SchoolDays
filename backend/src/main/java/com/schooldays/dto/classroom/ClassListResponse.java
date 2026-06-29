package com.schooldays.dto.classroom;

import java.util.List;

public record ClassListResponse(
        List<ClassResponse> classes
) {
}
