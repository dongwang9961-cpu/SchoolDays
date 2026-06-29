package com.schooldays.service.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.schooldays.dao.auth.RegistrationLinkDao;
import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.TeacherInvitationDao;
import com.schooldays.dao.auth.TenantDao;
import com.schooldays.dao.auth.TenantInvitationDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.dao.auth.UserIdentityDao;
import com.schooldays.dto.auth.AuthResponse;
import com.schooldays.dto.auth.AuthenticatedUserResponse;
import com.schooldays.dto.auth.CompleteRegistrationRequest;
import com.schooldays.dto.auth.GoogleStartResponse;
import com.schooldays.dto.auth.LoginRequest;
import com.schooldays.dto.auth.RegistrationLinkRequest;
import com.schooldays.dto.auth.RegistrationLinkResponse;
import com.schooldays.dto.auth.SelfServiceRegistrationLinkRequest;
import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.entities.auth.GoogleOAuthState;
import com.schooldays.entities.auth.RegistrationLinkRow;
import com.schooldays.entities.auth.TeacherInvitationRow;
import com.schooldays.entities.auth.TenantInvitationRow;
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
    private final SecureTokenGenerator tokenGenerator;
    private final GoogleOAuthStateService googleOAuthStateService;
    private final RestClient restClient;
    private final String publicBaseUrl;
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
            SecureTokenGenerator tokenGenerator,
            GoogleOAuthStateService googleOAuthStateService,
            @Value("${schooldays.public-base-url:http://localhost:8080}") String publicBaseUrl,
            @Value("${schooldays.security.google.client-id:}") String googleClientId,
            @Value("${schooldays.security.google.client-secret:}") String googleClientSecret,
            @Value("${schooldays.security.google.redirect-uri:http://localhost:8080/api/auth/google/callback}") String googleRedirectUri,
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
        this.tokenGenerator = tokenGenerator;
        this.googleOAuthStateService = googleOAuthStateService;
        this.restClient = RestClient.create();
        this.publicBaseUrl = publicBaseUrl;
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
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

        UUID userId = userDao.createOrUpdatePasswordUser(
                email,
                passwordEncoder.encode(request.password()),
                request.firstName(),
                request.lastName(),
                request.phone(),
                now
        );
        roleDao.assignRole(userId, link.tenantId(), link.intendedRole());
        registrationLinkDao.markUsed(link.id(), now);

        AuthenticatedUser principal = userDetailsService.loadById(userId);
        return AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal);
    }

    public GoogleStartResponse googleStart(UUID tenantId) {
        if (tenantId == null) {
            throw new InvalidAuthRequestException("Tenant is required for Google login");
        }
        if (isBlank(googleClientId) || isBlank(googleClientSecret)) {
            throw new InvalidAuthRequestException("Google login is not configured");
        }
        String state = googleOAuthStateService.issue(tenantId);
        String authorizationUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?response_type=code"
                + "&client_id=" + urlEncode(googleClientId)
                + "&redirect_uri=" + urlEncode(googleRedirectUri)
                + "&scope=" + urlEncode("openid email profile")
                + "&state=" + urlEncode(state);
        return new GoogleStartResponse(authorizationUrl, state);
    }

    @Transactional
    public AuthResponse googleCallback(String code, String state) {
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
                .orElseGet(() -> userDao.findUserIdWithPhoneByEmail(email)
                        .orElseThrow(() -> new InvalidAuthRequestException(
                                "Phone number is required before Google login can create an account. Register with an email link first."
                        )));
        userIdentityDao.linkIdentity(userId, "google", providerSubject, email, true, now);
        roleDao.assignRole(userId, oauthState.tenantId(), "PARENT");

        AuthenticatedUser principal = userDetailsService.loadById(userId);
        return AuthResponse.bearer(jwtTokenService.issueAccessToken(principal), principal);
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
        return new RegistrationLinkResponse(
                link.tenantId(),
                link.email(),
                link.intendedRole(),
                token,
                registrationCompletionLink(link.tenantId(), token),
                expiresAt
        );
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
