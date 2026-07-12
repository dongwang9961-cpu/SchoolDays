package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.UserRegistrationLinks.USER_REGISTRATION_LINKS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.entities.auth.RegistrationLinkRow;
import com.schooldays.jooq.generated.tables.records.UserRegistrationLinksRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class RegistrationLinkDao {

    private final DSLContext dsl;
    private final RegistrationLinkRepository registrationLinkRepository;

    public RegistrationLinkDao(DSLContext dsl, RegistrationLinkRepository registrationLinkRepository) {
        this.dsl = dsl;
        this.registrationLinkRepository = registrationLinkRepository;
    }

    public Optional<RegistrationLinkRow> findPendingByTokenHash(String tokenHash, OffsetDateTime now) {
                return dsl.select(
                        USER_REGISTRATION_LINKS.ID,
                        USER_REGISTRATION_LINKS.TENANT_ID,
                        USER_REGISTRATION_LINKS.EMAIL,
                        USER_REGISTRATION_LINKS.INTENDED_ROLE,
                        USER_REGISTRATION_LINKS.INVITATION_TYPE,
                        USER_REGISTRATION_LINKS.RELATED_INVITATION_ID
                )
                .from(USER_REGISTRATION_LINKS)
                .where(USER_REGISTRATION_LINKS.TOKEN_HASH.eq(tokenHash))
                .and(USER_REGISTRATION_LINKS.STATUS.eq("pending"))
                .and(USER_REGISTRATION_LINKS.EXPIRES_AT.gt(now))
                .fetchOptional(record -> new RegistrationLinkRow(
                        record.get(USER_REGISTRATION_LINKS.ID),
                        record.get(USER_REGISTRATION_LINKS.TENANT_ID),
                        record.get(USER_REGISTRATION_LINKS.EMAIL),
                        record.get(USER_REGISTRATION_LINKS.INTENDED_ROLE),
                        record.get(USER_REGISTRATION_LINKS.INVITATION_TYPE),
                        record.get(USER_REGISTRATION_LINKS.RELATED_INVITATION_ID)
                ));
    }

    public RegistrationLinkRow create(
            UUID tenantId,
            String email,
            String intendedRole,
            String invitationType,
            UUID relatedInvitationId,
            String tokenHash,
            OffsetDateTime expiresAt,
            OffsetDateTime now
    ) {
        var saved = registrationLinkRepository.save(new UserRegistrationLinksRecord()
                .setTenantId(tenantId)
                .setEmail(email)
                .setIntendedRole(intendedRole)
                .setInvitationType(invitationType)
                .setRelatedInvitationId(relatedInvitationId)
                .setTokenHash(tokenHash)
                .setStatus("pending")
                .setExpiresAt(expiresAt)
                .setCreatedAt(now)
                .setUpdatedAt(now));
        return new RegistrationLinkRow(
                saved.getId(),
                tenantId,
                email,
                intendedRole,
                invitationType,
                relatedInvitationId
        );
    }

    public void markUsed(UUID linkId, OffsetDateTime now) {
        registrationLinkRepository.findById(linkId)
                .ifPresent(link -> {
                    link.setStatus("used");
                    link.setUsedAt(now);
                    link.setUpdatedAt(now);
                    link.changed(true);
                    registrationLinkRepository.save(link);
                });
    }
}
