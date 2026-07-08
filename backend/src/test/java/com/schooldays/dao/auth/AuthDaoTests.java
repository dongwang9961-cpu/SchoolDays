package com.schooldays.dao.auth;

import static com.schooldays.jooq.generated.tables.Roles.ROLES;
import static com.schooldays.jooq.generated.tables.TeacherInvitations.TEACHER_INVITATIONS;
import static com.schooldays.jooq.generated.tables.Tenants.TENANTS;
import static com.schooldays.jooq.generated.tables.TenantInvitations.TENANT_INVITATIONS;
import static com.schooldays.jooq.generated.tables.UserIdentities.USER_IDENTITIES;
import static com.schooldays.jooq.generated.tables.UserRegistrationLinks.USER_REGISTRATION_LINKS;
import static com.schooldays.jooq.generated.tables.UserRoles.USER_ROLES;
import static com.schooldays.jooq.generated.tables.Users.USERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.schooldays.entities.auth.RegistrationLinkRow;
import com.schooldays.entities.auth.TeacherInvitationRow;
import com.schooldays.entities.auth.TenantInvitationRow;
import com.schooldays.entities.auth.TenantRole;
import com.schooldays.entities.auth.UserAuthRow;
import com.schooldays.service.auth.InvalidAuthRequestException;
import com.schooldays.service.auth.TokenHasher;
import com.schooldays.jooq.JooqRepositoryFactory;
import com.schooldays.jooq.generated.tables.Roles;
import com.schooldays.jooq.generated.tables.TeacherInvitations;
import com.schooldays.jooq.generated.tables.Tenants;
import com.schooldays.jooq.generated.tables.TenantInvitations;
import com.schooldays.jooq.generated.tables.UserIdentities;
import com.schooldays.jooq.generated.tables.UserRegistrationLinks;
import com.schooldays.jooq.generated.tables.Users;
import com.schooldays.jooq.generated.tables.records.UsersRecord;
import com.schooldays.tenants.TenantRepository;

class AuthDaoTests {

    private static EmbeddedPostgres postgres;
    private static DSLContext dsl;
    private static UserRepository userRepository;
    private static UserDao userDao;
    private static RoleDao roleDao;
    private static RegistrationLinkDao registrationLinkDao;
    private static UserIdentityDao userIdentityDao;
    private static TenantDao tenantDao;
    private static TenantInvitationDao tenantInvitationDao;
    private static TeacherInvitationDao teacherInvitationDao;

    private UUID tenantId;

    @BeforeAll
    static void startDatabase() throws Exception {
        postgres = EmbeddedPostgres.builder()
                .setLocaleConfig("locale", "C")
                .start();
        Flyway.configure()
                .dataSource(postgres.getPostgresDatabase())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dsl = DSL.using(postgres.getPostgresDatabase(), SQLDialect.POSTGRES);

        JooqRepositoryFactory repositoryFactory = new JooqRepositoryFactory(dsl);
        userRepository = repositoryFactory.createRepository(
                UserRepository.class,
                Users.USERS,
                Users.USERS.ID
        );
        RoleRepository roleRepository = repositoryFactory.createRepository(
                RoleRepository.class,
                Roles.ROLES,
                Roles.ROLES.ID
        );

        RegistrationLinkRepository registrationLinkRepository = repositoryFactory.createRepository(
                RegistrationLinkRepository.class,
                UserRegistrationLinks.USER_REGISTRATION_LINKS,
                UserRegistrationLinks.USER_REGISTRATION_LINKS.ID
        );
        UserIdentityRepository userIdentityRepository = repositoryFactory.createRepository(
                UserIdentityRepository.class,
                UserIdentities.USER_IDENTITIES,
                UserIdentities.USER_IDENTITIES.ID
        );
        TenantRepository tenantRepository = repositoryFactory.createRepository(
                TenantRepository.class,
                Tenants.TENANTS,
                Tenants.TENANTS.ID
        );
        TenantInvitationRepository tenantInvitationRepository = repositoryFactory.createRepository(
                TenantInvitationRepository.class,
                TenantInvitations.TENANT_INVITATIONS,
                TenantInvitations.TENANT_INVITATIONS.ID
        );
        TeacherInvitationRepository teacherInvitationRepository = repositoryFactory.createRepository(
                TeacherInvitationRepository.class,
                TeacherInvitations.TEACHER_INVITATIONS,
                TeacherInvitations.TEACHER_INVITATIONS.ID
        );

        userDao = new UserDao(userRepository);
        roleDao = new RoleDao(dsl, roleRepository);
        registrationLinkDao = new RegistrationLinkDao(dsl, registrationLinkRepository);
        userIdentityDao = new UserIdentityDao(userIdentityRepository);
        tenantDao = new TenantDao(dsl, tenantRepository);
        tenantInvitationDao = new TenantInvitationDao(dsl, tenantInvitationRepository);
        teacherInvitationDao = new TeacherInvitationDao(dsl, teacherInvitationRepository);
    }

    @AfterAll
    static void stopDatabase() throws Exception {
        if (postgres != null) {
            postgres.close();
        }
    }

    @BeforeEach
    void resetData() {
        dsl.deleteFrom(USER_ROLES).execute();
        dsl.deleteFrom(TEACHER_INVITATIONS).execute();
        dsl.deleteFrom(TENANT_INVITATIONS).execute();
        dsl.deleteFrom(USER_REGISTRATION_LINKS).execute();
        dsl.deleteFrom(USER_IDENTITIES).execute();
        dsl.deleteFrom(USERS).execute();
        dsl.deleteFrom(TENANTS).execute();

        tenantId = UUID.randomUUID();
        dsl.insertInto(TENANTS)
                .set(TENANTS.ID, tenantId)
                .set(TENANTS.NAME, "Test School")
                .set(TENANTS.STATUS, "active")
                .execute();
    }

    @Test
    void repositorySaveInsertsRecordAndReturnsGeneratedValues() {
        UsersRecord saved = userRepository.save(new UsersRecord()
                .setEmail("saved@example.com")
                .setPhone("555-0101")
                .setPasswordHash("hash")
                .setFirstName("Sam")
                .setLastName("Saved")
                .setStatus("active"));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getSeqId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("saved@example.com");
        assertThat(userRepository.findById(saved.getId())).contains(saved);
    }

    @Test
    void repositorySaveUpsertsRecordByPrimaryKey() {
        UsersRecord saved = userRepository.save(new UsersRecord()
                .setEmail("upsert@example.com")
                .setPhone("555-0102")
                .setPasswordHash("hash-one")
                .setFirstName("Uma")
                .setLastName("Upsert")
                .setStatus("active"));

        UsersRecord updated = userRepository.save(new UsersRecord()
                .setId(saved.getId())
                .setEmail("upsert@example.com")
                .setPhone("555-0103")
                .setPasswordHash("hash-two")
                .setFirstName("Uma")
                .setLastName("Updated")
                .setStatus("active"));

        assertThat(updated.getId()).isEqualTo(saved.getId());
        assertThat(updated.getPasswordHash()).isEqualTo("hash-two");
        assertThat(updated.getLastName()).isEqualTo("Updated");
        assertThat(dsl.fetchCount(USERS, USERS.EMAIL.eq("upsert@example.com"))).isEqualTo(1);
    }

    @Test
    void userDaoCreatesFindsAndUpdatesPasswordUser() {
        OffsetDateTime now = OffsetDateTime.now();

        UUID userId = userDao.createOrUpdatePasswordUser(
                "parent@example.com",
                "hash-one",
                "Pat",
                "Parent",
                "555-0100",
                now
        );

        Optional<UserAuthRow> byEmail = userDao.findAuthUserByEmail("parent@example.com");
        Optional<UserAuthRow> byId = userDao.findAuthUserById(userId);

        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().id()).isEqualTo(userId);
        assertThat(byEmail.get().passwordHash()).isEqualTo("hash-one");
        assertThat(byEmail.get().status()).isEqualTo("active");
        assertThat(byId).contains(byEmail.get());

        UUID updatedUserId = userDao.createOrUpdatePasswordUser(
                "parent@example.com",
                "hash-two",
                "Patricia",
                "Parent",
                "555-0200",
                now.plusMinutes(1)
        );

        assertThat(updatedUserId).isEqualTo(userId);
        assertThat(userDao.findAuthUserByEmail("parent@example.com"))
                .isPresent()
                .get()
                .extracting(UserAuthRow::passwordHash)
                .isEqualTo("hash-two");
        assertThat(dsl.fetchCount(USERS)).isEqualTo(1);
    }

    @Test
    void userDaoCreatesOrUpdatesExternalUsersWithoutPassword() {
        OffsetDateTime now = OffsetDateTime.now();

        UUID userId = userDao.createOrUpdateExternalUser(
                "google-parent@example.com",
                "Gina",
                "Google",
                "555-0300",
                now
        );
        UUID updatedUserId = userDao.createOrUpdateExternalUser(
                "google-parent@example.com",
                "Gia",
                "Google",
                "555-0301",
                now.plusMinutes(1)
        );

        UsersRecord record = dsl.selectFrom(USERS)
                .where(USERS.ID.eq(userId))
                .fetchOne();

        assertThat(updatedUserId).isEqualTo(userId);
        assertThat(record).isNotNull();
        assertThat(record.getPasswordHash()).isNull();
        assertThat(record.getFirstName()).isEqualTo("Gia");
        assertThat(dsl.fetchCount(USERS)).isEqualTo(1);
    }

    @Test
    void userIdentityDaoLinksAndFindsProviderSubject() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID userId = userDao.createOrUpdateExternalUser(
                "google-parent@example.com",
                "Gina",
                "Google",
                "555-0300",
                now
        );

        userIdentityDao.linkIdentity(
                userId,
                "google",
                "google-subject",
                "google-parent@example.com",
                true,
                now
        );

        assertThat(userIdentityDao.findUserIdByProviderSubject("google", "google-subject"))
                .contains(userId);
        assertThat(userIdentityDao.findUserIdByProviderSubject("google", "missing"))
                .isEmpty();
    }

    @Test
    void roleDaoAssignsRoleIdempotentlyAndFindsTenantRoles() {
        UUID userId = userDao.createOrUpdatePasswordUser(
                "teacher@example.com",
                "hash",
                "Terry",
                "Teacher",
                "555-0400",
                OffsetDateTime.now()
        );

        roleDao.assignRole(userId, tenantId, "TEACHER");
        roleDao.assignRole(userId, tenantId, "TEACHER");

        List<TenantRole> tenantRoles = roleDao.findTenantRoles(userId);

        assertThat(tenantRoles).containsExactly(new TenantRole(tenantId, "TEACHER"));
        assertThat(dsl.fetchCount(USER_ROLES)).isEqualTo(1);
    }

    @Test
    void roleDaoRejectsUnknownRole() {
        UUID userId = userDao.createOrUpdatePasswordUser(
                "admin@example.com",
                "hash",
                "Ada",
                "Admin",
                "555-0401",
                OffsetDateTime.now()
        );

        assertThatThrownBy(() -> roleDao.assignRole(userId, tenantId, "NOT_A_ROLE"))
                .isInstanceOf(InvalidAuthRequestException.class)
                .hasMessageContaining("Unknown role");
    }

    @Test
    void registrationLinkDaoFindsOnlyPendingUnexpiredLinksAndMarksUsed() {
        OffsetDateTime now = OffsetDateTime.now();
        String token = "registration-token";
        String tokenHash = TokenHasher.sha256(token);
        UUID linkId = UUID.randomUUID();

        dsl.insertInto(USER_REGISTRATION_LINKS)
                .set(USER_REGISTRATION_LINKS.ID, linkId)
                .set(USER_REGISTRATION_LINKS.TENANT_ID, tenantId)
                .set(USER_REGISTRATION_LINKS.EMAIL, "parent@example.com")
                .set(USER_REGISTRATION_LINKS.INTENDED_ROLE, "PARENT")
                .set(USER_REGISTRATION_LINKS.INVITATION_TYPE, "self_assisted")
                .set(USER_REGISTRATION_LINKS.TOKEN_HASH, tokenHash)
                .set(USER_REGISTRATION_LINKS.STATUS, "pending")
                .set(USER_REGISTRATION_LINKS.EXPIRES_AT, now.plusHours(1))
                .execute();

        Optional<RegistrationLinkRow> found = registrationLinkDao.findPendingByTokenHash(tokenHash, now);

        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(new RegistrationLinkRow(
                linkId,
                tenantId,
                "parent@example.com",
                "PARENT",
                null
        ));

        registrationLinkDao.markUsed(linkId, now.plusMinutes(1));

        assertThat(registrationLinkDao.findPendingByTokenHash(tokenHash, now.plusMinutes(2))).isEmpty();
        var updated = dsl.selectFrom(USER_REGISTRATION_LINKS)
                .where(USER_REGISTRATION_LINKS.ID.eq(linkId))
                .fetchOne();
        assertThat(updated).isNotNull();
        assertThat(updated.getStatus()).isEqualTo("used");
        assertThat(updated.getUsedAt()).isNotNull();
    }

    @Test
    void registrationLinkDaoCreatesPendingLinks() {
        OffsetDateTime now = OffsetDateTime.now();
        String tokenHash = TokenHasher.sha256("new-link-token");

        RegistrationLinkRow created = registrationLinkDao.create(
                tenantId,
                "parent@example.com",
                "PARENT",
                "self_assisted",
                null,
                tokenHash,
                now.plusDays(2),
                now
        );

        assertThat(created.tenantId()).isEqualTo(tenantId);
        assertThat(created.email()).isEqualTo("parent@example.com");
        assertThat(created.intendedRole()).isEqualTo("PARENT");
        assertThat(registrationLinkDao.findPendingByTokenHash(tokenHash, now)).contains(created);
    }

    @Test
    void registrationLinkDaoIgnoresExpiredLinks() {
        OffsetDateTime now = OffsetDateTime.now();
        String tokenHash = TokenHasher.sha256("expired-token");

        dsl.insertInto(USER_REGISTRATION_LINKS)
                .set(USER_REGISTRATION_LINKS.ID, UUID.randomUUID())
                .set(USER_REGISTRATION_LINKS.TENANT_ID, tenantId)
                .set(USER_REGISTRATION_LINKS.EMAIL, "parent@example.com")
                .set(USER_REGISTRATION_LINKS.INTENDED_ROLE, "PARENT")
                .set(USER_REGISTRATION_LINKS.INVITATION_TYPE, "self_assisted")
                .set(USER_REGISTRATION_LINKS.TOKEN_HASH, tokenHash)
                .set(USER_REGISTRATION_LINKS.STATUS, "pending")
                .set(USER_REGISTRATION_LINKS.EXPIRES_AT, now.minusMinutes(1))
                .execute();

        assertThat(registrationLinkDao.findPendingByTokenHash(tokenHash, now)).isEmpty();
    }

    @Test
    void tenantDaoCreatesTenant() {
        UUID createdTenantId = tenantDao.createTenant("Created School", OffsetDateTime.now());

        var record = dsl.selectFrom(TENANTS)
                .where(TENANTS.ID.eq(createdTenantId))
                .fetchOne();

        assertThat(record).isNotNull();
        assertThat(record.getName()).isEqualTo("Created School");
        assertThat(record.getStatus()).isEqualTo("active");
    }

    @Test
    void tenantInvitationDaoFindsPendingInvitationAndMarksAccepted() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID invitationId = UUID.randomUUID();
        String tokenHash = TokenHasher.sha256("tenant-invite-token");

        dsl.insertInto(TENANT_INVITATIONS)
                .set(TENANT_INVITATIONS.ID, invitationId)
                .set(TENANT_INVITATIONS.SCHOOL_NAME, "Invited School")
                .set(TENANT_INVITATIONS.ADMIN_EMAIL, "admin@example.com")
                .set(TENANT_INVITATIONS.TOKEN_HASH, tokenHash)
                .set(TENANT_INVITATIONS.STATUS, "pending")
                .set(TENANT_INVITATIONS.EXPIRES_AT, now.plusHours(1))
                .execute();

        Optional<TenantInvitationRow> found = tenantInvitationDao.findPendingByTokenHash(tokenHash, now);

        assertThat(found).contains(new TenantInvitationRow(
                invitationId,
                "Invited School",
                "admin@example.com",
                null
        ));

        tenantInvitationDao.markAccepted(invitationId, tenantId, now.plusMinutes(1));

        var updated = dsl.selectFrom(TENANT_INVITATIONS)
                .where(TENANT_INVITATIONS.ID.eq(invitationId))
                .fetchOne();
        assertThat(updated).isNotNull();
        assertThat(updated.getTenantId()).isEqualTo(tenantId);
        assertThat(updated.getStatus()).isEqualTo("accepted");
        assertThat(updated.getAcceptedAt()).isNotNull();
    }

    @Test
    void teacherInvitationDaoFindsPendingInvitationAndMarksAccepted() {
        OffsetDateTime now = OffsetDateTime.now();
        UUID invitationId = UUID.randomUUID();
        UUID teacherUserId = userDao.createOrUpdatePasswordUser(
                "teacher@example.com",
                "hash",
                "Terry",
                "Teacher",
                "555-0402",
                now
        );
        String tokenHash = TokenHasher.sha256("teacher-invite-token");

        dsl.insertInto(TEACHER_INVITATIONS)
                .set(TEACHER_INVITATIONS.ID, invitationId)
                .set(TEACHER_INVITATIONS.TENANT_ID, tenantId)
                .set(TEACHER_INVITATIONS.EMAIL, "teacher@example.com")
                .set(TEACHER_INVITATIONS.TOKEN_HASH, tokenHash)
                .set(TEACHER_INVITATIONS.STATUS, "pending")
                .set(TEACHER_INVITATIONS.EXPIRES_AT, now.plusHours(1))
                .execute();

        Optional<TeacherInvitationRow> found = teacherInvitationDao.findPendingByTokenHash(tokenHash, now);

        assertThat(found).contains(new TeacherInvitationRow(
                invitationId,
                tenantId,
                "teacher@example.com"
        ));

        teacherInvitationDao.markAccepted(invitationId, teacherUserId, now.plusMinutes(1));

        var updated = dsl.selectFrom(TEACHER_INVITATIONS)
                .where(TEACHER_INVITATIONS.ID.eq(invitationId))
                .fetchOne();
        assertThat(updated).isNotNull();
        assertThat(updated.getTeacherUserId()).isEqualTo(teacherUserId);
        assertThat(updated.getStatus()).isEqualTo("accepted");
        assertThat(updated.getAcceptedAt()).isNotNull();
    }
}
