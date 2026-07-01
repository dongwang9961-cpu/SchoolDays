package com.schooldays.dto.teacher;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AssignTeacherRequest(
        @NotBlank @Email String email
) {
}
