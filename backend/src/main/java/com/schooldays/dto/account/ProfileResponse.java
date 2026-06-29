package com.schooldays.dto.account;

import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.UsersRecord;

public record ProfileResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone
) {
    public static ProfileResponse from(UsersRecord record) {
        return new ProfileResponse(
                record.getId(),
                record.getEmail(),
                record.getFirstName(),
                record.getLastName(),
                record.getPhone()
        );
    }
}
