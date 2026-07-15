package com.schooldays.service.auth;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;
import static com.schooldays.jooq.generated.tables.TeacherInvitations.TEACHER_INVITATIONS;
import static com.schooldays.jooq.generated.tables.TenantInvitations.TENANT_INVITATIONS;
import static com.schooldays.jooq.generated.tables.UserRegistrationLinks.USER_REGISTRATION_LINKS;
import static com.schooldays.jooq.generated.tables.UserRoles.USER_ROLES;
import static com.schooldays.jooq.generated.tables.Users.USERS;

import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.schooldays.dto.api.EndpointStatusResponse;
import com.schooldays.dao.auth.RegistrationLinkDao;
import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.TeacherInvitationDao;
import com.schooldays.dao.auth.TenantDao;
import com.schooldays.dao.auth.TenantInvitationDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.dao.auth.UserIdentityDao;
import com.schooldays.dto.auth.AuthConfigResponse;
import com.schooldays.dto.auth.AuthResponse;
import com.schooldays.dto.auth.AuthenticatedUserResponse;
import com.schooldays.dto.auth.CompletePasswordResetRequest;
import com.schooldays.dto.auth.CompleteRegistrationRequest;
import com.schooldays.dto.auth.GoogleCallbackResult;
import com.schooldays.dto.auth.GoogleStartResponse;
import com.schooldays.dto.auth.InviteUserRequest;
import com.schooldays.dto.auth.InviteUserResponse;
import com.schooldays.dto.auth.InviteUserResultResponse;
import com.schooldays.dto.auth.LoginRequest;
import com.schooldays.dto.auth.RegistrationLinkRequest;
import com.schooldays.dto.auth.RegistrationLinkResponse;
import com.schooldays.dto.auth.SelfServiceRegistrationLinkRequest;
import com.schooldays.dto.school.PublicSchoolResponse;
import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.entities.auth.GoogleOAuthState;
import com.schooldays.entities.auth.RegistrationLinkRow;
import com.schooldays.entities.auth.TeacherInvitationRow;
import com.schooldays.entities.auth.TenantInvitationRow;
import com.schooldays.entities.auth.UserAuthRow;
import com.schooldays.service.email.SystemEmailMessage;
import com.schooldays.service.email.SystemEmailService;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AuthService {

    private static final Set<String> SELF_SERVICE_ROLES = Set.of("PARENT");
    private static final long REGISTRATION_LINK_TTL_HOURS = 48;
    private static final String GOOGLE_PENDING_PHONE = "__google_profile_pending__";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);

    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final SchoolDaysUserDetailsService userDetailsService;
    private final JwtTokenService jwtTokenService;
    private final RegistrationLinkDao registrationLinkDao;
    private final TenantInvitationDao tenantInvitationDao;
    private final TeacherInvitationDao teacherInvitationDao;
    private final TenantDao tenantDao;
    private final UserDao userDao;
    private final UserIdentityDao userIdentityDao;
    private final RoleDao roleDao;
    private final DSLContext dsl;
    private final SecureTokenGenerator tokenGenerator;
    private final GoogleOAuthStateService googleOAuthStateService;
    private final SystemEmailService systemEmailService;
    private final RestClient restClient;
    private final String publicBaseUrl;
    private final String frontendBaseUrl;
    private final List<String> allowedReturnOrigins;
    private final String systemEmailFromEmail;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private final String googleTokenUri;
    private final String googleUserInfoUri;

    public AuthService(
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            SchoolDaysUserDetailsService userDetailsService,
            JwtTokenService jwtTokenService,
            RegistrationLinkDao registrationLinkDao,
            TenantInvitationDao tenantInvitationDao,
            TeacherInvitationDao teacherInvitationDao,
            TenantDao tenantDao,
            UserDao userDao,
            UserIdentityDao userIdentityDao,
            RoleDao roleDao,
            DSLContext dsl,
            SecureTokenGenerator tokenGenerator,
            GoogleOAuthStateService googleOAuthStateService,
            SystemEmailService systemEmailService,
            @Value("${schooldays.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${schooldays.frontend-base-url:http://localhost:5173}") String frontendBaseUrl,
            @Value("${schooldays.oauth.allowed-return-origins:${schooldays.cors.allowed-origins}}") List<String> allowedReturnOrigins,
            @Value("${schooldays.system-email.from-email:noreply@schooldays.cc}") String systemEmailFromEmail,
            @Value("${schooldays.security.google.client-id:}") String googleClientId,
            @Value("${schooldays.security.google.client-secret:}") String googleClientSecret,
            @Value("${schooldays.security.google.redirect-uri:}") String googleRedirectUri,
            @Value("${schooldays.security.google.token-uri:https://oauth2.googleapis.com/token}") String googleTokenUri,
            @Value("${schooldays.security.google.user-info-uri:https://openidconnect.googleapis.com/v1/userinfo}") String googleUserInfoUri
    ) {
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtTokenService = jwtTokenService;
        this.registrationLinkDao = registrationLinkDao;
        this.tenantInvitationDao = tenantInvitationDao;
        this.teacherInvitationDao = teacherInvitationDao;
        this.tenantDao = tenantDao;
        this.userDao = userDao;
        this.userIdentityDao = userIdentityDao;
        this.roleDao = roleDao;
        this.dsl = dsl;
        this.tokenGenerator = tokenGenerator;
        this.googleOAuthStateService = googleOAuthStateService;
        this.systemEmailService = systemEmailService;
        this.restClient = RestClient.create();
        this.publicBaseUrl = stripTrailingSlash(publicBaseUrl);
        this.frontendBaseUrl = stripTrailingSlash(frontendBaseUrl);
        this.allowedReturnOrigins = allowedReturnOrigins == null ? List.of() : allowedReturnOrigins;
        this.systemEmailFromEmail = systemEmailFromEmail;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = isBlank(googleRedirectUri)
                ? this.publicBaseUrl + "/api/auth/google/callback"
                : googleRedirectUri;
        this.googleTokenUri = googleTokenUri;
        this.googleUserInfoUri = googleUserInfoUri;
    }

    @Transactional
    public RegistrationLinkResponse requestParentRegistrationLink(RegistrationLinkRequest request) {
        return createRegistrationLink(
                request.tenantId(),
                EmailNormalizer.normalize(request.email()),
                "PARENT",
                "parent_self_service",
                null
        );
    }

    public AuthConfigResponse config() {
        return new AuthConfigResponse(googleLoginEnabled());
    }

    @Transactional
    public RegistrationLinkResponse requestSelfServiceRegistrationLink(SelfServiceRegistrationLinkRequest request) {
        String intendedRole = normalizeRole(request.intendedRole() == null || request.intendedRole().isBlank()
                ? "PARENT"
                : request.intendedRole());
        if (!SELF_SERVICE_ROLES.contains(intendedRole)) {
            throw new InvalidAuthRequestException("Self-service registration is only available for parent users");
        }
        return createRegistrationLink(
                request.tenantId(),
                EmailNormalizer.normalize(request.email()),
                intendedRole,
                "self_assisted",
                null
        );
    }

    @Transactional
    public AuthResponse completeRegistration(CompleteRegistrationRequest request) {
        String tokenHash = TokenHasher.sha256(request.token());
        OffsetDateTime now = OffsetDateTime.now();

        RegistrationLinkRow link = registrationLinkDao.findPendingByTokenHash(tokenHash, now)
                .orElseThrow(() -> new InvalidAuthRequestException("Registration link is invalid or expired"));
        if ("password_reset".equals(link.invitationType())) {
            throw new InvalidAuthRequestException("Password reset links must be used from the password reset form");
        }

        String email = EmailNormalizer.normalize(link.email());
        if (request.email() != null && !request.email().isBlank()
                && !email.equals(EmailNormalizer.normalize(request.email()))) {
            throw new InvalidAuthRequestException("Registration link does not match the submitted email");
        }
        String metadataJson = null;
        if ("PARENT".equals(link.intendedRole())) {
            metadataJson = parentMetadataJson(request.address());
        }

        UUID userId = userDao.createOrUpdatePasswordUser(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                request.phone(),
                metadataJson,
                now
        );
        roleDao.assignRole(userId, link.tenantId(), link.intendedRole());
        if ("TEACHER".equals(link.intendedRole()) && link.relatedInvitationId() != null) {
            assignTeacherToClass(link.tenantId(), link.relatedInvitationId(), userId, userId);
        }
        registrationLinkDao.markUsed(link.id(), now);

        AuthenticatedUser principal = userDetailsService.loadById(userId);
        return authResponse(principal);
    }

    @Transactional
    public AuthResponse completePasswordReset(CompletePasswordResetRequest request) {
        String tokenHash = TokenHasher.sha256(request.token());
        OffsetDateTime now = OffsetDateTime.now();

        RegistrationLinkRow link = registrationLinkDao.findPendingByTokenHash(tokenHash, now)
                .orElseThrow(() -> new InvalidAuthRequestException("Password reset link is invalid or expired"));
        if (!"password_reset".equals(link.invitationType())) {
            throw new InvalidAuthRequestException("This link is not a password reset link");
        }

        String email = EmailNormalizer.normalize(link.email());
        UserAuthRow user = userDao.findAuthUserByEmail(email)
                .orElseThrow(() -> new InvalidAuthRequestException("User was not found"));
        if (!roleDao.hasTenantRole(user.id(), link.tenantId(), link.intendedRole())) {
            throw new InvalidAuthRequestException("Password reset link does not match this user's school role");
        }

        dsl.update(USERS)
                .set(USERS.PASSWORD_HASH, passwordEncoder.encode(request.password()))
                .set(USERS.STATUS, "active")
                .set(USERS.UPDATED_AT, now)
                .where(USERS.ID.eq(user.id()))
                .execute();
        registrationLinkDao.markUsed(link.id(), now);

        AuthenticatedUser principal = userDetailsService.loadById(user.id());
        return authResponse(principal);
    }

    public GoogleStartResponse googleStart(UUID tenantId, String requestOrigin) {
        if (tenantId == null) {
            throw new InvalidAuthRequestException("Tenant is required for Google login");
        }
        if (!googleLoginEnabled()) {
            throw new InvalidAuthRequestException("Google login is not configured");
        }
        String state = googleOAuthStateService.issue(tenantId, googleReturnUrl(tenantId, requestOrigin));
        String authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + urlEncode(googleClientId)
                + "&redirect_uri=" + urlEncode(googleRedirectUri)
                + "&scope=" + urlEncode("openid email profile")
                + "&state=" + urlEncode(state);
        return new GoogleStartResponse(authorizationUrl, state);
    }

    private boolean googleLoginEnabled() {
        return !isBlank(googleClientId) && !isBlank(googleClientSecret);
    }

    @Transactional
    public GoogleCallbackResult googleCallback(String code, String state) {
        if (code == null || code.isBlank() || state == null || state.isBlank()) {
            throw new InvalidAuthRequestException("Google authorization code and state are required");
        }
        GoogleOAuthState oauthState = googleOAuthStateService.verify(state);
        Map<String, Object> tokenResponse = exchangeGoogleAuthorizationCode(code);
        String accessToken = stringClaim(tokenResponse, "access_token");
        if (isBlank(accessToken)) {
            throw new InvalidAuthRequestException("Google did not return an access token");
        }

        Map<String, Object> userInfo = fetchGoogleUserInfo(accessToken);
        String providerSubject = stringClaim(userInfo, "sub");
        String email = EmailNormalizer.normalize(stringClaim(userInfo, "email"));
        if (isBlank(providerSubject) || isBlank(email) || !booleanClaim(userInfo, "email_verified")) {
            throw new InvalidAuthRequestException("Google account email must be verified");
        }

        OffsetDateTime now = OffsetDateTime.now();
        UUID userId = userIdentityDao.findUserIdByProviderSubject("google", providerSubject)
                .orElseGet(() -> userDao.findUserIdByEmail(email)
                        .orElseGet(() -> userDao.createOrUpdateExternalUser(
                                email,
                                googleFirstName(userInfo),
                                googleLastName(userInfo),
                                GOOGLE_PENDING_PHONE,
                                now
                        )));
        userIdentityDao.linkIdentity(userId, "google", providerSubject, email, true, now);
        roleDao.assignRole(userId, oauthState.tenantId(), "PARENT");

        AuthenticatedUser principal = userDetailsService.loadById(userId);
        return new GoogleCallbackResult(
                authResponse(principal),
                oauthState.returnUrl()
        );
    }

    @Transactional
    public AuthResponse acceptTenantInvitation(CompleteRegistrationRequest request) {
        String tokenHash = TokenHasher.sha256(request.token());
        OffsetDateTime now = OffsetDateTime.now();

        TenantInvitationRow invitation = tenantInvitationDao.findPendingByTokenHash(tokenHash, now)
                .orElseThrow(() -> new InvalidAuthRequestException("Tenant invitation is invalid or expired"));
        String email = EmailNormalizer.normalize(invitation.adminEmail());
        assertSubmittedEmailMatches(request.email(), email, "Tenant invitation does not match the submitted email");

        UUID tenantId = invitation.tenantId();
        if (tenantId == null) {
            tenantId = tenantDao.createTenant(invitation.schoolName(), now);
        }
        UUID userId = createOrUpdateInvitedUser(request, email, now);
        roleDao.assignRole(userId, tenantId, "SCHOOL_ADMIN");
        tenantInvitationDao.markAccepted(invitation.id(), tenantId, now);

        AuthenticatedUser principal = userDetailsService.loadById(userId);
        return authResponse(principal);
    }

    @Transactional
    public AuthResponse acceptTeacherInvitation(CompleteRegistrationRequest request) {
        String tokenHash = TokenHasher.sha256(request.token());
        OffsetDateTime now = OffsetDateTime.now();

        TeacherInvitationRow invitation = teacherInvitationDao.findPendingByTokenHash(tokenHash, now)
                .orElseThrow(() -> new InvalidAuthRequestException("Teacher invitation is invalid or expired"));
        String email = EmailNormalizer.normalize(invitation.email());
        assertSubmittedEmailMatches(request.email(), email, "Teacher invitation does not match the submitted email");

        UUID userId = createOrUpdateInvitedUser(request, email, now);
        roleDao.assignRole(userId, invitation.tenantId(), "TEACHER");
        teacherInvitationDao.markAccepted(invitation.id(), userId, now);

        AuthenticatedUser principal = userDetailsService.loadById(userId);
        return authResponse(principal);
    }

    @Transactional
    public InviteUserResponse inviteUsers(UUID tenantId, UUID invitedByUserId, InviteUserRequest request) {
        String role = normalizeRole(request.role());
        List<String> emails = request.emails() == null ? List.of() : request.emails();
        if (emails.isEmpty()) {
            throw new InvalidAuthRequestException("At least one email address is required");
        }
        if (Set.of("SCHOOL_ADMIN", "TEACHER").contains(role) && !roleDao.hasTenantRole(invitedByUserId, tenantId, "SCHOOL_ADMIN")) {
            throw new InvalidAuthRequestException("Only school administrators can send invitations for school administrator or teacher roles");
        }
        if ("TEACHER".equals(role)) {
            if (request.classId() == null) {
                throw new InvalidAuthRequestException("A class must be selected for teacher invitations");
            }
            if (emails.size() > 5) {
                throw new InvalidAuthRequestException("Teacher invitations are limited to 5 email addresses per request");
            }
        }
        if ("SCHOOL_ADMIN".equals(role) && emails.size() > 1) {
            throw new InvalidAuthRequestException("School administrator invitations accept one email address per request");
        }
        if (!Set.of("SCHOOL_ADMIN", "TEACHER", "PARENT").contains(role)) {
            throw new InvalidAuthRequestException("Unknown invitation role: " + request.role());
        }

        PublicSchoolResponse school = tenantDao.findActivePublicSchoolById(tenantId)
                .orElseThrow(() -> new InvalidAuthRequestException("School not found"));

        List<InviteUserResultResponse> results = emails.stream()
                .map(email -> inviteUser(tenantId, invitedByUserId, school, role, request.classId(), email))
                .toList();
        return new InviteUserResponse("success", results);
    }

    @Transactional
    public InviteUserResponse sendPasswordResetLinks(UUID tenantId, UUID requestedByUserId, InviteUserRequest request) {
        String role = normalizeRole(request.role());
        List<String> emails = request.emails() == null ? List.of() : request.emails();
        if (!roleDao.hasTenantRole(requestedByUserId, tenantId, "SCHOOL_ADMIN")) {
            throw new InvalidAuthRequestException("Only school administrators can send password reset links");
        }
        if (!Set.of("SCHOOL_ADMIN", "TEACHER").contains(role)) {
            throw new InvalidAuthRequestException("Password reset links can only be sent for school administrators or teachers");
        }
        if (emails.isEmpty()) {
            throw new InvalidAuthRequestException("At least one email address is required");
        }
        if (emails.size() > 5) {
            throw new InvalidAuthRequestException("Password reset links are limited to 5 email addresses per request");
        }

        PublicSchoolResponse school = tenantDao.findActivePublicSchoolById(tenantId)
                .orElseThrow(() -> new InvalidAuthRequestException("School not found"));

        List<InviteUserResultResponse> results = emails.stream()
                .map(email -> sendPasswordResetLink(tenantId, school, role, email))
                .toList();
        return new InviteUserResponse("success", results);
    }

    @Transactional
    public EndpointStatusResponse deleteUser(UUID tenantId, UUID deletedByUserId, String rawEmail) {
        String email = EmailNormalizer.normalize(rawEmail);
        UserAuthRow user = userDao.findAuthUserByEmail(email)
                .orElseThrow(() -> new InvalidAuthRequestException("User was not found"));
        if (user.id().equals(deletedByUserId)) {
            throw new InvalidAuthRequestException("School administrators cannot delete themselves");
        }

        UUID userId = user.id();
        dsl.deleteFrom(TEACHER_ASSIGNMENTS)
                .where(TEACHER_ASSIGNMENTS.TEACHER_USER_ID.eq(userId))
                .and(TEACHER_ASSIGNMENTS.CLASS_ID.in(
                        dsl.select(CLASSES.ID)
                                .from(CLASSES)
                                .where(CLASSES.TENANT_ID.eq(tenantId))
                ))
                .execute();
        dsl.deleteFrom(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .and(USER_ROLES.TENANT_ID.eq(tenantId))
                .execute();
        dsl.deleteFrom(TEACHER_INVITATIONS)
                .where(TEACHER_INVITATIONS.TENANT_ID.eq(tenantId))
                .and(TEACHER_INVITATIONS.TEACHER_USER_ID.eq(userId)
                        .or(TEACHER_INVITATIONS.INVITED_BY_USER_ID.eq(userId)))
                .execute();
        dsl.deleteFrom(TENANT_INVITATIONS)
                .where(TENANT_INVITATIONS.TENANT_ID.eq(tenantId))
                .and(TENANT_INVITATIONS.INVITED_BY_USER_ID.eq(userId))
                .execute();
        dsl.deleteFrom(USER_REGISTRATION_LINKS)
                .where(USER_REGISTRATION_LINKS.TENANT_ID.eq(tenantId))
                .and(USER_REGISTRATION_LINKS.EMAIL.eq(email))
                .execute();

        if (roleDao.findTenantRoles(userId).isEmpty()) {
            archiveDeletedUser(tenantId, deletedByUserId, userId, email);
            deleteUserOwnedData(userId, email);
            dsl.deleteFrom(USERS)
                    .where(USERS.ID.eq(userId))
                    .execute();
            return new EndpointStatusResponse("success", "DELETE /api/tenants/{tenantId}/users", "User archived and removed from the system.");
        }

        return new EndpointStatusResponse("success", "DELETE /api/tenants/{tenantId}/users", "User removed from this school.");
    }

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailNormalizer.normalize(request.email()),
                        request.password()
                )
        );
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return authResponse(principal);
    }

    public AuthenticatedUserResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidAuthRequestException("Authentication is required");
        }
        AuthenticatedUser principal = userDetailsService.loadById(UUID.fromString(jwt.getSubject()));
        return authenticatedUserResponse(principal);
    }

    private AuthResponse authResponse(AuthenticatedUser principal) {
        return AuthResponse.bearer(
                jwtTokenService.issueAccessToken(principal),
                principal,
                authProviders(principal.id())
        );
    }

    private AuthenticatedUserResponse authenticatedUserResponse(AuthenticatedUser principal) {
        return AuthenticatedUserResponse.from(principal, authProviders(principal.id()));
    }

    private List<String> authProviders(UUID userId) {
        return userIdentityDao.findProvidersByUserId(userId);
    }

    private RegistrationLinkResponse createRegistrationLink(
            UUID tenantId,
            String email,
            String intendedRole,
            String invitationType,
            UUID relatedInvitationId
    ) {
        String token = tokenGenerator.newToken();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(REGISTRATION_LINK_TTL_HOURS);
        RegistrationLinkRow link = registrationLinkDao.create(
                tenantId,
                email,
                intendedRole,
                invitationType,
                relatedInvitationId,
                TokenHasher.sha256(token),
                expiresAt,
                now
        );
        String completionLink = registrationCompletionLink(link.tenantId(), link.intendedRole(), token);
        RegistrationLinkResponse response = new RegistrationLinkResponse(
                link.tenantId(),
                link.email(),
                link.intendedRole(),
                expiresAt
        );
        sendRegistrationLinkEmail(link, response, completionLink);
        return response;
    }

    private InviteUserResultResponse inviteUser(
            UUID tenantId,
            UUID invitedByUserId,
            PublicSchoolResponse school,
            String role,
            UUID classId,
            String rawEmail
    ) {
        String email = EmailNormalizer.normalize(rawEmail);
        UUID userId = userDao.findUserIdByEmail(email).orElse(null);

        if ("SCHOOL_ADMIN".equals(role)) {
            return inviteSchoolAdmin(tenantId, school, email, userId);
        }
        if ("TEACHER".equals(role)) {
            return inviteTeacher(tenantId, invitedByUserId, school, classId, email, userId);
        }
        return inviteParent(tenantId, school, email, userId);
    }

    private InviteUserResultResponse inviteSchoolAdmin(
            UUID tenantId,
            PublicSchoolResponse school,
            String email,
            UUID userId
    ) {
        if (userId != null && roleDao.hasTenantRole(userId, tenantId, "SCHOOL_ADMIN")) {
            return new InviteUserResultResponse(
                    email,
                    "SCHOOL_ADMIN",
                    "already_admin",
                    "The user is already a school administrator."
            );
        }

        if (userId != null) {
            roleDao.assignRole(userId, tenantId, "SCHOOL_ADMIN");
            sendSchoolAdminGrantedEmail(school, email);
            return new InviteUserResultResponse(
                    email,
                    "SCHOOL_ADMIN",
                    "granted",
                    "School administrator access granted and notification email sent."
            );
        }

        createRegistrationLink(
                tenantId,
                email,
                "SCHOOL_ADMIN",
                "tenant_admin_invitation",
                null
        );
        return new InviteUserResultResponse(
                email,
                "SCHOOL_ADMIN",
                "invited",
                "Invitation email sent with a registration link."
        );
    }

    private InviteUserResultResponse inviteTeacher(
            UUID tenantId,
            UUID invitedByUserId,
            PublicSchoolResponse school,
            UUID classId,
            String email,
            UUID userId
    ) {
        requireClass(tenantId, classId);

        if (userId != null && roleDao.hasTenantRole(userId, tenantId, "TEACHER") && isTeacherAssignedToClass(classId, userId)) {
            return new InviteUserResultResponse(
                    email,
                    "TEACHER",
                    "already_assigned",
                    "The user is already a teacher for the selected class."
            );
        }

        if (userId != null) {
            roleDao.assignRole(userId, tenantId, "TEACHER");
            assignTeacherToClass(tenantId, classId, userId, invitedByUserId);
            try {
                sendTeacherGrantedEmail(school, tenantId, email, classId, false);
            } catch (RuntimeException exception) {
                LOGGER.warn("Teacher invitation email could not be sent for {} in tenant {}", email, tenantId, exception);
            }
            return new InviteUserResultResponse(
                    email,
                    "TEACHER",
                    "granted",
                    "Teacher access granted and the selected class assigned."
            );
        }

        createRegistrationLink(
                tenantId,
                email,
                "TEACHER",
                "teacher_class_invitation",
                classId
        );
        return new InviteUserResultResponse(
                email,
                "TEACHER",
                "invited",
                "Invitation email sent with a registration link."
        );
    }

    private InviteUserResultResponse inviteParent(
            UUID tenantId,
            PublicSchoolResponse school,
            String email,
            UUID userId
    ) {
        if (userId != null && roleDao.hasTenantRole(userId, tenantId, "PARENT")) {
            return new InviteUserResultResponse(
                    email,
                    "PARENT",
                    "already_parent",
                    "The user is already a school parent."
            );
        }

        if (userId != null) {
            roleDao.assignRole(userId, tenantId, "PARENT");
            sendParentGrantedEmail(school, email);
            return new InviteUserResultResponse(
                    email,
                    "PARENT",
                    "granted",
                    "Parent access granted and notification email sent."
            );
        }

        createRegistrationLink(
                tenantId,
                email,
                "PARENT",
                "parent_invitation",
                null
        );
        return new InviteUserResultResponse(
                email,
                "PARENT",
                "invited",
                "Invitation email sent with a registration link."
        );
    }

    private InviteUserResultResponse sendPasswordResetLink(
            UUID tenantId,
            PublicSchoolResponse school,
            String role,
            String rawEmail
    ) {
        String email = EmailNormalizer.normalize(rawEmail);
        UserAuthRow user = userDao.findAuthUserByEmail(email).orElse(null);
        if (user == null) {
            return new InviteUserResultResponse(
                    email,
                    role,
                    "not_found",
                    "No active account was found for this email address."
            );
        }
        if (!"active".equals(user.status())) {
            return new InviteUserResultResponse(
                    email,
                    role,
                    "inactive",
                    "This account is not active."
            );
        }
        if (!roleDao.hasTenantRole(user.id(), tenantId, role)) {
            return new InviteUserResultResponse(
                    email,
                    role,
                    "role_mismatch",
                    "This user does not have the selected role at this school."
            );
        }

        String token = tokenGenerator.newToken();
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusHours(REGISTRATION_LINK_TTL_HOURS);
        RegistrationLinkRow link = registrationLinkDao.create(
                tenantId,
                email,
                role,
                "password_reset",
                null,
                TokenHasher.sha256(token),
                expiresAt,
                now
        );
        String resetLink = passwordResetCompletionLink(link.tenantId(), link.intendedRole(), token);
        sendPasswordResetEmail(school, link, expiresAt, resetLink);
        return new InviteUserResultResponse(
                email,
                role,
                "sent",
                "Password reset email sent."
        );
    }

    private void archiveDeletedUser(UUID tenantId, UUID deletedByUserId, UUID userId, String email) {
        dsl.execute(
                "insert into user_history (" +
                        "original_user_id, deleted_from_tenant_id, deleted_by_user_id, email, phone, password_hash, first_name, last_name, status, metadata, deleted_reason, deleted_at, created_at, updated_at" +
                        ") select id, ?, ?, email, phone, password_hash, first_name, last_name, 'deleted', metadata, ?, now(), created_at, updated_at from users where id = ?",
                tenantId,
                deletedByUserId,
                "deleted from school management",
                userId
        );
    }

    private void deleteUserOwnedData(UUID userId, String email) {
        dsl.execute("delete from user_registration_links where email = ?", email);
        dsl.execute("delete from teacher_invitations where invited_by_user_id = ? or teacher_user_id = ? or email = ?", userId, userId, email);
        dsl.execute("delete from tenant_invitations where invited_by_user_id = ? or admin_email = ?", userId, email);
        dsl.execute("delete from teacher_assignments where teacher_user_id = ? or assigned_by_user_id = ?", userId, userId);
        dsl.execute("delete from user_roles where user_id = ?", userId);
        dsl.execute("delete from external_check_ins where checked_in_by_user_id = ?", userId);
        dsl.execute("delete from email_notification_history where sender_user_id = ?", userId);
        dsl.execute("delete from notification_providers where created_by_user_id = ?", userId);
        dsl.execute("delete from payment_receipts where uploaded_by_user_id = ? or reviewed_by_user_id = ?", userId, userId);
        dsl.execute("delete from payment_transactions where payer_user_id = ? or recorded_by_user_id = ?", userId, userId);
        dsl.execute("delete from attendance where checked_in_by_user_id = ?", userId);
        dsl.execute("delete from enrollment_perks where enrollment_id in (select e.id from enrollments e join children c on c.id = e.child_id where c.parent_user_id = ?)", userId);
        dsl.execute("delete from enrollment_dates where enrollment_id in (select e.id from enrollments e join children c on c.id = e.child_id where c.parent_user_id = ?)", userId);
        dsl.execute("delete from payment_receipts where enrollment_id in (select e.id from enrollments e join children c on c.id = e.child_id where c.parent_user_id = ?)", userId);
        dsl.execute("delete from payment_transactions where enrollment_id in (select e.id from enrollments e join children c on c.id = e.child_id where c.parent_user_id = ?)", userId);
        dsl.execute("delete from attendance where child_id in (select id from children where parent_user_id = ?)", userId);
        dsl.execute("delete from enrollments where child_id in (select id from children where parent_user_id = ?) or created_by_user_id = ?", userId, userId);
        dsl.execute("delete from children where parent_user_id = ?", userId);
        dsl.execute("delete from user_identities where user_id = ?", userId);
    }

    private void sendRegistrationLinkEmail(RegistrationLinkRow link, RegistrationLinkResponse response, String completionLink) {
        tenantDao.findActivePublicSchoolById(response.tenantId())
                .ifPresent(school -> {
                    String subject = "Complete your " + school.name() + " " + registrationLinkPurpose(response.intendedRole());
                    String classNote = link.relatedInvitationId() != null && "TEACHER".equals(response.intendedRole())
                            ? " After registration, you will be assigned to the selected class."
                            : "";
                    String classNoteText = classNote.isBlank() ? "" : "\n\n" + classNote.trim();
                    String classNoteHtml = classNote.isBlank() ? "" : "<p>" + escapeHtml(classNote.trim()) + "</p>";
                    String textBody = """
                            Hello,

                            Use this secure link to complete your SchoolDays %s for %s.

                            If the link is not clickable, copy and paste this URL into your browser:
                            %s

                            This link expires at %s.%s""".formatted(
                            registrationLinkPurpose(response.intendedRole()),
                            school.name(),
                            completionLink,
                            response.expiresAt(),
                            classNoteText
                    );
                    String htmlBody = """
                            <p>Hello,</p>
                            <p>Use this secure link to complete your SchoolDays %s for %s.</p>
                            <p><a href="%s">Complete registration</a></p>
                            <p>If the link is not clickable, copy and paste this URL into your browser:<br><span>%s</span></p>
                            <p>This link expires at %s.</p>
                            %s
                            """.formatted(
                            escapeHtml(registrationLinkPurpose(response.intendedRole())),
                            escapeHtml(school.name()),
                            escapeHtml(completionLink),
                            escapeHtml(completionLink),
                            response.expiresAt(),
                            classNoteHtml
                    );

                    systemEmailService.send(new SystemEmailMessage(
                            response.email(),
                            systemEmailFromEmail,
                            school.name(),
                            subject,
                            textBody,
                            htmlBody
                    ));
                });
    }

    private void sendPasswordResetEmail(PublicSchoolResponse school, RegistrationLinkRow link, OffsetDateTime expiresAt, String resetLink) {
        String subject = "Reset your " + school.name() + " SchoolDays password";
        String textBody = """
                Hello,

                Use this secure link to reset your SchoolDays password for %s.

                If the link is not clickable, copy and paste this URL into your browser:
                %s

                This link expires at %s.
                """.formatted(
                school.name(),
                resetLink,
                expiresAt
        );
        String htmlBody = """
                <p>Hello,</p>
                <p>Use this secure link to reset your SchoolDays password for %s.</p>
                <p><a href="%s">Reset password</a></p>
                <p>If the link is not clickable, copy and paste this URL into your browser:<br><span>%s</span></p>
                <p>This link expires at %s.</p>
                """.formatted(
                escapeHtml(school.name()),
                escapeHtml(resetLink),
                escapeHtml(resetLink),
                expiresAt
        );

        systemEmailService.send(new SystemEmailMessage(
                link.email(),
                systemEmailFromEmail,
                school.name(),
                subject,
                textBody,
                htmlBody
        ));
    }

    private void sendSchoolAdminGrantedEmail(PublicSchoolResponse school, String email) {
        String subject = "SchoolDays school administrator access for " + school.name();
        String textBody = """
                Hello,

                You have been granted school administrator access for %s.

                You can sign in to SchoolDays with your existing account.
                """.formatted(school.name());
        String htmlBody = """
                <p>Hello,</p>
                <p>You have been granted school administrator access for %s.</p>
                <p>You can sign in to SchoolDays with your existing account.</p>
                """.formatted(escapeHtml(school.name()));

        systemEmailService.send(new SystemEmailMessage(
                email,
                systemEmailFromEmail,
                school.name(),
                subject,
                textBody,
                htmlBody
        ));
    }

    private void sendParentGrantedEmail(PublicSchoolResponse school, String email) {
        String subject = "SchoolDays parent access for " + school.name();
        String textBody = """
                Hello,

                You have been granted parent access for %s.

                You can sign in to SchoolDays with your existing account.
                """.formatted(school.name());
        String htmlBody = """
                <p>Hello,</p>
                <p>You have been granted parent access for %s.</p>
                <p>You can sign in to SchoolDays with your existing account.</p>
                """.formatted(escapeHtml(school.name()));

        systemEmailService.send(new SystemEmailMessage(
                email,
                systemEmailFromEmail,
                school.name(),
                subject,
                textBody,
                htmlBody
        ));
    }

    private void sendTeacherGrantedEmail(PublicSchoolResponse school, UUID tenantId, String email, UUID classId, boolean isAssignmentOnly) {
        String className = dsl.select(CLASSES.NAME)
                .from(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .fetchOne(CLASSES.NAME);
        if (className == null) {
            className = "selected class";
        }

        String subject = "SchoolDays teacher access for " + school.name();
        String textBody = isAssignmentOnly
                ? """
                        Hello,

                        You have been assigned to the %s class at %s.

                        You can sign in to SchoolDays with your existing account.
                        """.formatted(className, school.name())
                : """
                        Hello,

                        You have been granted teacher access for %s and assigned to the %s class.

                        You can sign in to SchoolDays with your existing account.
                        """.formatted(school.name(), className);
        String htmlBody = isAssignmentOnly
                ? """
                        <p>Hello,</p>
                        <p>You have been assigned to the %s class at %s.</p>
                        <p>You can sign in to SchoolDays with your existing account.</p>
                        """.formatted(escapeHtml(className), escapeHtml(school.name()))
                : """
                        <p>Hello,</p>
                        <p>You have been granted teacher access for %s and assigned to the %s class.</p>
                        <p>You can sign in to SchoolDays with your existing account.</p>
                        """.formatted(escapeHtml(school.name()), escapeHtml(className));

        systemEmailService.send(new SystemEmailMessage(
                email,
                systemEmailFromEmail,
                school.name(),
                subject,
                textBody,
                htmlBody
        ));
    }

    private void requireClass(UUID tenantId, UUID classId) {
        boolean classExists = dsl.fetchExists(dsl.selectOne()
                .from(CLASSES)
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId)));
        if (!classExists) {
            throw new InvalidAuthRequestException("Class was not found");
        }
    }

    private boolean isTeacherAssignedToClass(UUID classId, UUID teacherUserId) {
        return dsl.fetchExists(dsl.selectOne()
                .from(TEACHER_ASSIGNMENTS)
                .where(TEACHER_ASSIGNMENTS.CLASS_ID.eq(classId))
                .and(TEACHER_ASSIGNMENTS.TEACHER_USER_ID.eq(teacherUserId)));
    }

    private void assignTeacherToClass(UUID tenantId, UUID classId, UUID teacherUserId, UUID assignedByUserId) {
        requireClass(tenantId, classId);
        dsl.insertInto(TEACHER_ASSIGNMENTS)
                .set(TEACHER_ASSIGNMENTS.CLASS_ID, classId)
                .set(TEACHER_ASSIGNMENTS.TEACHER_USER_ID, teacherUserId)
                .set(TEACHER_ASSIGNMENTS.ASSIGNED_BY_USER_ID, assignedByUserId)
                .onConflict(TEACHER_ASSIGNMENTS.CLASS_ID, TEACHER_ASSIGNMENTS.TEACHER_USER_ID)
                .doNothing()
                .execute();
    }

    private String registrationLinkPurpose(String intendedRole) {
        String normalizedRole = normalizeRole(intendedRole);
        return switch (normalizedRole) {
            case "SCHOOL_ADMIN" -> "school administrator registration";
            case "TEACHER" -> "teacher registration";
            case "PARENT" -> "registration";
            default -> "registration";
        };
    }

    private String registrationCompletionLink(UUID tenantId, String intendedRole, String token) {
        return schoolAuthLink(tenantId, intendedRole, token, false);
    }

    private String passwordResetCompletionLink(UUID tenantId, String intendedRole, String token) {
        return schoolAuthLink(tenantId, intendedRole, token, true);
    }

    private String schoolAuthLink(UUID tenantId, String intendedRole, String token, boolean passwordReset) {
        String encodedToken = urlEncode(token);
        String query = "?token=" + encodedToken + (passwordReset ? "&reset=1" : "");
        if (tenantId == null) {
            return publicBaseUrl + query;
        }
        String portalPath = switch (normalizeRole(intendedRole)) {
            case "TEACHER" -> "/t";
            case "SCHOOL_ADMIN" -> "/admin";
            default -> "";
        };
        return tenantDao.findActivePublicSchoolById(tenantId)
                .map(school -> publicBaseUrl + "/school/" + urlEncode(school.slug()) + portalPath + query)
                .orElse(publicBaseUrl + query);
    }

    private String googleReturnUrl(UUID tenantId, String requestOrigin) {
        String origin = allowedReturnOrigin(requestOrigin);
        return tenantDao.findActivePublicSchoolById(tenantId)
                .map(school -> origin + "/school/" + urlEncode(school.slug()))
                .orElse(origin);
    }

    private String allowedReturnOrigin(String requestOrigin) {
        String origin = normalizeOrigin(requestOrigin);
        if (isAllowedReturnOrigin(origin)) {
            return origin;
        }
        if (isAllowedReturnOrigin(frontendBaseUrl)) {
            return frontendBaseUrl;
        }
        return allowedReturnOrigins.stream()
                .map(this::normalizeOrigin)
                .filter(value -> !isBlank(value))
                .filter(value -> !value.contains("*"))
                .findFirst()
                .orElse(frontendBaseUrl);
    }

    private boolean isAllowedReturnOrigin(String origin) {
        if (isBlank(origin)) {
            return false;
        }
        return allowedReturnOrigins.stream()
                .anyMatch(allowedOrigin -> originMatches(allowedOrigin, origin));
    }

    private boolean originMatches(String allowedOrigin, String origin) {
        String normalizedAllowedOrigin = normalizeOrigin(allowedOrigin);
        if (isBlank(normalizedAllowedOrigin)) {
            return false;
        }
        if (normalizedAllowedOrigin.contains("*")) {
            String regex = "\\Q" + normalizedAllowedOrigin.replace("*", "\\E[^.]+\\Q") + "\\E";
            return origin.matches(regex);
        }
        return origin.equals(normalizedAllowedOrigin);
    }

    private String normalizeOrigin(String value) {
        if (isBlank(value)) {
            return "";
        }
        try {
            URI uri = URI.create(stripTrailingSlash(value.trim()));
            String host = uri.getHost();
            if (isBlank(host) && uri.getAuthority() != null && uri.getAuthority().contains("*")) {
                host = uri.getAuthority();
            }
            if (isBlank(uri.getScheme()) || isBlank(host)) {
                return "";
            }
            int port = uri.getPort();
            return uri.getScheme() + "://" + host + (port >= 0 && !host.contains(":") ? ":" + port : "");
        } catch (IllegalArgumentException exception) {
            return "";
        }
    }

    private String stripTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private UUID createOrUpdateInvitedUser(CompleteRegistrationRequest request, String email, OffsetDateTime now) {
        return userDao.createOrUpdatePasswordUser(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                request.phone(),
                now
        );
    }

    private String parentMetadataJson(CompleteRegistrationRequest.ParentAddress address) {
        if (address == null) {
            throw new InvalidAuthRequestException("Address is required for parent registration");
        }
        ObjectNode root = OBJECT_MAPPER.createObjectNode();
        ObjectNode addressNode = root.putObject("address");
        put(addressNode, "streetAddress", address.streetAddress());
        put(addressNode, "suite", address.suite());
        put(addressNode, "city", address.city());
        put(addressNode, "state", address.state());
        put(addressNode, "zipCode", address.zipCode());
        try {
            return OBJECT_MAPPER.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize parent address", exception);
        }
    }

    private String googleFirstName(Map<String, Object> userInfo) {
        String givenName = stringClaim(userInfo, "given_name");
        if (!isBlank(givenName)) {
            return givenName;
        }
        String name = stringClaim(userInfo, "name");
        if (isBlank(name)) {
            return "";
        }
        String[] parts = name.trim().split("\\s+", 2);
        return parts[0];
    }

    private String googleLastName(Map<String, Object> userInfo) {
        String familyName = stringClaim(userInfo, "family_name");
        if (!isBlank(familyName)) {
            return familyName;
        }
        String name = stringClaim(userInfo, "name");
        if (isBlank(name)) {
            return "";
        }
        String[] parts = name.trim().split("\\s+", 2);
        return parts.length > 1 ? parts[1] : "";
    }

    private void put(ObjectNode node, String field, String value) {
        if (value != null && !value.isBlank()) {
            node.put(field, value.trim());
        }
    }

    private void assertSubmittedEmailMatches(String submittedEmail, String expectedEmail, String message) {
        if (submittedEmail != null && !submittedEmail.isBlank()
                && !expectedEmail.equals(EmailNormalizer.normalize(submittedEmail))) {
            throw new InvalidAuthRequestException(message);
        }
    }

    private String normalizeRole(String role) {
        return role.trim().toUpperCase();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String escapeHtml(String value) {
        return value == null
                ? ""
                : value
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;");
    }

    private Map<String, Object> exchangeGoogleAuthorizationCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", googleClientId);
        form.add("client_secret", googleClientSecret);
        form.add("redirect_uri", googleRedirectUri);
        form.add("grant_type", "authorization_code");

        try {
            return restClient.post()
                    .uri(googleTokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw new InvalidAuthRequestException("Google authorization code could not be exchanged");
        }
    }

    private Map<String, Object> fetchGoogleUserInfo(String accessToken) {
        try {
            return restClient.get()
                    .uri(googleUserInfoUri)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException exception) {
            throw new InvalidAuthRequestException("Google profile could not be loaded");
        }
    }

    private String stringClaim(Map<String, Object> claims, String name) {
        if (claims == null) {
            return null;
        }
        Object value = claims.get(name);
        return value == null ? null : value.toString();
    }

    private boolean booleanClaim(Map<String, Object> claims, String name) {
        Object value = claims == null ? null : claims.get(name);
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
