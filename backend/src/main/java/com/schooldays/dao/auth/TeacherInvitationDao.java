package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.TeacherInvitations.TEACHER_INVITATIONS;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import com.schooldays.entities.auth.TeacherInvitationRow;
import com.schooldays.jooq.generated.tables.records.TeacherInvitationsRecord;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class TeacherInvitationDao {

    private final DSLContext dsl;
    private final TeacherInvitationRepository teacherInvitationRepository;

    public TeacherInvitationDao(DSLContext dsl, TeacherInvitationRepository teacherInvitationRepository) {
        this.dsl = dsl;
        this.teacherInvitationRepository = teacherInvitationRepository;
    }

    public Optional<TeacherInvitationRow> findPendingByTokenHash(String tokenHash, OffsetDateTime now) {
        return dsl.select(
                        TEACHER_INVITATIONS.ID,
                        TEACHER_INVITATIONS.TENANT_ID,
                        TEACHER_INVITATIONS.EMAIL
                )
                .from(TEACHER_INVITATIONS)
                .where(TEACHER_INVITATIONS.TOKEN_HASH.eq(tokenHash))
                .and(TEACHER_INVITATIONS.STATUS.eq("pending"))
                .and(TEACHER_INVITATIONS.EXPIRES_AT.gt(now))
                .fetchOptional(record -> new TeacherInvitationRow(
                        record.get(TEACHER_INVITATIONS.ID),
                        record.get(TEACHER_INVITATIONS.TENANT_ID),
                        record.get(TEACHER_INVITATIONS.EMAIL)
                ));
    }

    public void markAccepted(UUID invitationId, UUID teacherUserId, OffsetDateTime now) {
        teacherInvitationRepository.findById(invitationId)
                .ifPresent(invitation -> teacherInvitationRepository.save(accepted(invitation, teacherUserId, now)));
    }

    private TeacherInvitationsRecord accepted(
            TeacherInvitationsRecord invitation,
            UUID teacherUserId,
            OffsetDateTime now
    ) {
        invitation.setTeacherUserId(teacherUserId);
        invitation.setStatus("accepted");
        invitation.setAcceptedAt(now);
        invitation.setUpdatedAt(now);
        invitation.changed(true);
        return invitation;
    }
}
