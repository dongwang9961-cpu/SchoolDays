package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.TenantInvitations.TENANT_INVITATIONS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.entities.auth.TenantInvitationRow;
import com.schooldays.jooq.generated.tables.records.TenantInvitationsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class TenantInvitationDao {

    private final DSLContext dsl;
    private final TenantInvitationRepository tenantInvitationRepository;

    public TenantInvitationDao(DSLContext dsl, TenantInvitationRepository tenantInvitationRepository) {
        this.dsl = dsl;
        this.tenantInvitationRepository = tenantInvitationRepository;
    }

    public Optional<TenantInvitationRow> findPendingByTokenHash(String tokenHash, OffsetDateTime now) {
        return dsl.select(
                        TENANT_INVITATIONS.ID,
                        TENANT_INVITATIONS.SCHOOL_NAME,
                        TENANT_INVITATIONS.ADMIN_EMAIL,
                        TENANT_INVITATIONS.TENANT_ID
                )
                .from(TENANT_INVITATIONS)
                .where(TENANT_INVITATIONS.TOKEN_HASH.eq(tokenHash))
                .and(TENANT_INVITATIONS.STATUS.eq("pending"))
                .and(TENANT_INVITATIONS.EXPIRES_AT.gt(now))
                .fetchOptional(record -> new TenantInvitationRow(
                        record.get(TENANT_INVITATIONS.ID),
                        record.get(TENANT_INVITATIONS.SCHOOL_NAME),
                        record.get(TENANT_INVITATIONS.ADMIN_EMAIL),
                        record.get(TENANT_INVITATIONS.TENANT_ID)
                ));
    }

    public void markAccepted(UUID invitationId, UUID tenantId, OffsetDateTime now) {
        tenantInvitationRepository.findById(invitationId)
                .ifPresent(invitation -> tenantInvitationRepository.save(accepted(invitation, tenantId, now)));
    }

    private TenantInvitationsRecord accepted(TenantInvitationsRecord invitation, UUID tenantId, OffsetDateTime now) {
        invitation.setTenantId(tenantId);
        invitation.setStatus("accepted");
        invitation.setAcceptedAt(now);
        invitation.setUpdatedAt(now);
        invitation.changed(true);
        return invitation;
    }
}
