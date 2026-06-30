package com.schooldays.dto.enrollment;

import java.util.List;

public record CreateEnrollmentResponse(
        List<EnrollmentResponse> enrollments,
        boolean paymentRequired,
        Integer requiredFeeTotal,
        String currency
) {
}
