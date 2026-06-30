package com.schooldays.dao.auth;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.entities.auth.UserAuthRow;
import com.schooldays.jooq.generated.tables.records.UsersRecord;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class UserDao {

    private final UserRepository userRepository;

    public UserDao(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Optional<UserAuthRow> findAuthUserByEmail(String email) {
        return userRepository.findByEmail(email).map(this::toAuthRow);
    }

    public Optional<UserAuthRow> findAuthUserById(UUID userId) {
        return userRepository.findById(userId).map(this::toAuthRow);
    }

    public Optional<UUID> findUserIdWithPhoneByEmail(String email) {
        return userRepository.findByEmail(email)
                .filter(record -> record.getPhone() != null && !record.getPhone().isBlank())
                .map(UsersRecord::getId);
    }

    public UUID createOrUpdatePasswordUser(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            OffsetDateTime now
    ) {
        return createOrUpdatePasswordUser(email, passwordHash, firstName, lastName, phone, null, now);
    }

    public UUID createOrUpdatePasswordUser(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            String phone,
            String metadataJson,
            OffsetDateTime now
    ) {
        Optional<UUID> existingUserId = userRepository.findByEmail(email)
                .map(UsersRecord::getId);

        UsersRecord record = new UsersRecord()
                .setEmail(email)
                .setPasswordHash(passwordHash)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setPhone(phone)
                .setStatus("active")
                .setUpdatedAt(now);
        if (metadataJson != null) {
            record.setMetadata(JSONB.valueOf(metadataJson));
        }
        existingUserId.ifPresent(record::setId);
        return userRepository.save(record).getId();
    }

    public UUID createOrUpdateExternalUser(
            String email,
            String firstName,
            String lastName,
            String phone,
            OffsetDateTime now
    ) {
        Optional<UUID> existingUserId = userRepository.findByEmail(email)
                .map(UsersRecord::getId);

        UsersRecord record = new UsersRecord()
                .setEmail(email)
                .setFirstName(firstName)
                .setLastName(lastName)
                .setPhone(phone)
                .setStatus("active")
                .setUpdatedAt(now);
        if (existingUserId.isEmpty()) {
            record.setCreatedAt(now);
        }
        existingUserId.ifPresent(record::setId);
        return userRepository.save(record).getId();
    }

    private UserAuthRow toAuthRow(UsersRecord record) {
        return new UserAuthRow(
                record.getId(),
                record.getEmail(),
                record.getPhone(),
                record.getPasswordHash(),
                record.getStatus()
        );
    }
}
