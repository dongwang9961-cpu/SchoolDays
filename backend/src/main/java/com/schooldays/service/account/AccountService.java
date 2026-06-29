package com.schooldays.service.account;

import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.schooldays.dto.account.ChangePasswordRequest;
import com.schooldays.dto.account.ProfileResponse;
import com.schooldays.dto.account.UpdateProfileRequest;
import com.schooldays.jooq.generated.tables.records.UsersRecord;
import org.jooq.DSLContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final DSLContext dsl;
    private final PasswordEncoder passwordEncoder;

    public AccountService(DSLContext dsl, PasswordEncoder passwordEncoder) {
        this.dsl = dsl;
        this.passwordEncoder = passwordEncoder;
    }

    public ProfileResponse profile(UUID userId) {
        return ProfileResponse.from(requireUser(userId));
    }

    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UsersRecord updated = dsl.update(USERS)
                .set(USERS.FIRST_NAME, clean(request.firstName()))
                .set(USERS.LAST_NAME, clean(request.lastName()))
                .set(USERS.PHONE, clean(request.phone()))
                .set(USERS.UPDATED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(userId))
                .returning()
                .fetchOne();
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found");
        }
        return ProfileResponse.from(updated);
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        UsersRecord user = requireUser(userId);
        String currentHash = user.getPasswordHash();
        if (currentHash == null || currentHash.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This account does not have a password login");
        }
        if (!passwordEncoder.matches(request.currentPassword(), currentHash)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }
        dsl.update(USERS)
                .set(USERS.PASSWORD_HASH, passwordEncoder.encode(request.newPassword()))
                .set(USERS.UPDATED_AT, OffsetDateTime.now())
                .where(USERS.ID.eq(userId))
                .execute();
    }

    private UsersRecord requireUser(UUID userId) {
        UsersRecord user = dsl.selectFrom(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOne();
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User was not found");
        }
        return user;
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
