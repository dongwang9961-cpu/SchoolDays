package com.schooldays.service.auth;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;

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

@Service
public class AuthService {

    private static final Set<String> SELF_SERVICE_ROLES = Set.of("PARENT");
    private static final long REGISTRATION_LINK_TTL_HOURS = 48;
    private static final String GOOGLE_PENDING_PHONE = "__google_profile_pending__";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        return AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal);
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
                AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal),
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
        return AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal);
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
        return AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal);
    }

    @Transactional
    public InviteUserResponse inviteUsers(UUID tenantId, UUID invitedByUserId, InviteUserRequest request) {
        String role = normalizeRole(request.role());
        List<String> emails = request.emails() == null ? List.of() : request.emails();
        if (emails.isEmpty()) {
            throw new InvalidAuthRequestException("At least one email address is required");
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

    public AuthResponse login(LoginRequest request) {
        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        EmailNormalizer.normalize(request.email()),
                        request.password()
                )
        );
        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        return AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal);
    }

    public AuthenticatedUserResponse me(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw new InvalidAuthRequestException("Authentication is required");
        }
        AuthenticatedUser principal = userDetailsService.loadById(UUID.fromString(jwt.getSubject()));
        return AuthenticatedUserResponse.from(principal);
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
        String completionLink = registrationCompletionLink(link.tenantId(), token);
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
            sendTeacherGrantedEmail(school, tenantId, email, classId, false);
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
                            <p>This link expires at %s.</p>
                            %s
                            """.formatted(
                            escapeHtml(registrationLinkPurpose(response.intendedRole())),
                            escapeHtml(school.name()),
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

    private String registrationCompletionLink(UUID tenantId, String token) {
        String encodedToken = urlEncode(token);
        if (tenantId == null) {
            return publicBaseUrl + "?token=" + encodedToken;
        }
        return tenantDao.findActivePublicSchoolById(tenantId)
                .map(school -> publicBaseUrl + "/school/" + urlEncode(school.slug()) + "?token=" + encodedToken)
                .orElse(publicBaseUrl + "?token=" + encodedToken);
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
                .findFirst()
                .orElse(frontendBaseUrl);
    }

    private boolean isAllowedReturnOrigin(String origin) {
        if (isBlank(origin)) {
            return false;
        }
        return allowedReturnOrigins.stream()
                .map(this::normalizeOrigin)
                .anyMatch(origin::equals);
    }

    private String normalizeOrigin(String value) {
        if (isBlank(value)) {
            return "";
        }
        try {
            URI uri = URI.create(stripTrailingSlash(value.trim()));
            if (isBlank(uri.getScheme()) || isBlank(uri.getHost())) {
                return "";
            }
            int port = uri.getPort();
            return uri.getScheme() + "://" + uri.getHost() + (port >= 0 ? ":" + port : "");
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
