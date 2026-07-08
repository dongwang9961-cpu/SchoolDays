package com.schooldays.dto.externalstudent;

import java.util.List;

public record ExternalStudentListResponse(
        List<ExternalStudentRowResponse> students,
        int page,
        int pageSize,
        long totalRows,
        int totalPages
) {
}
