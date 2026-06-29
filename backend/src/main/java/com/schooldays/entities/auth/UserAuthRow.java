package com.schooldays.entities.auth;

import java.util.UUID;

public record UserAuthRow(
        UUID id,
        String email,
        String phone,
        String passwordHash,
        String status
) {
}
