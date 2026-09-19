package com.schooldays.dto.enrollment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectEnrollmentRequest(
        @NotBlank(message = "A reason is required when rejecting an enrollment request")
        @Size(max = 2000, message = "The rejection reason cannot exceed 2000 characters")
        String message
) {
}
