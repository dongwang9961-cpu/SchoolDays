package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.UserIdentities.USER_IDENTITIES;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.jooq.generated.tables.records.UserIdentitiesRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class UserIdentityDao {

    private final DSLContext dsl;
    private final UserIdentityRepository userIdentityRepository;

    public UserIdentityDao(DSLContext dsl, UserIdentityRepository userIdentityRepository) {
        this.dsl = dsl;
        this.userIdentityRepository = userIdentityRepository;
    }

    public Optional<UUID> findUserIdByProviderSubject(String provider, String providerSubject) {
        return userIdentityRepository.findByProviderAndProviderSubject(provider, providerSubject)
                .map(UserIdentitiesRecord::getUserId);
    }

    public List<String> findProvidersByUserId(UUID userId) {
        return dsl.select(USER_IDENTITIES.PROVIDER)
                .from(USER_IDENTITIES)
                .where(USER_IDENTITIES.USER_ID.eq(userId))
                .orderBy(USER_IDENTITIES.PROVIDER.asc())
                .fetch(USER_IDENTITIES.PROVIDER);
    }

    public void linkIdentity(
            UUID userId,
            String provider,
            String providerSubject,
            String email,
            boolean emailVerified,
            OffsetDateTime now
    ) {
        Optional<UserIdentitiesRecord> existingIdentity = userIdentityRepository
                .findByProviderAndProviderSubject(provider, providerSubject);

        if (existingIdentity.isPresent()) {
            UserIdentitiesRecord record = existingIdentity.get();
            record.setUserId(userId);
            record.setEmail(email);
            record.setEmailVerified(emailVerified);
            record.setUpdatedAt(now);
            record.changed(true);
            userIdentityRepository.save(record);
            return;
        }

        UserIdentitiesRecord record = new UserIdentitiesRecord()
                .setUserId(userId)
                .setProvider(provider)
                .setProviderSubject(providerSubject)
                .setEmail(email)
                .setEmailVerified(emailVerified)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        userIdentityRepository.save(record);
    }
}
