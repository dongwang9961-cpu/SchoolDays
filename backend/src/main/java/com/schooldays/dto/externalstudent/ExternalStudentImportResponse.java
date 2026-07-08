package com.schooldays.dto.externalstudent;

import java.util.List;

public record ExternalStudentImportResponse(
        String status,
        int totalRows,
        int importedCount,
        int updatedCount,
        int skippedCount,
        List<String> errors
) {
}
