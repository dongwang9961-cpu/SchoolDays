package com.schooldays.controller;

import jakarta.validation.Valid;
import java.util.UUID;

import com.schooldays.dto.auth.AuthResponse;
import com.schooldays.dto.auth.AuthConfigResponse;
import com.schooldays.dto.auth.AuthenticatedUserResponse;
import com.schooldays.dto.auth.CompleteRegistrationRequest;
import com.schooldays.dto.auth.GoogleCallbackResult;
import com.schooldays.dto.auth.GoogleStartResponse;
import com.schooldays.dto.auth.LoginRequest;
import com.schooldays.dto.auth.RegistrationLinkRequest;
import com.schooldays.dto.auth.RegistrationLinkResponse;
import com.schooldays.dto.auth.SelfServiceRegistrationLinkRequest;
import com.schooldays.service.auth.AuthService;
import com.schooldays.service.auth.InvalidAuthRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @GetMapping("/config")
    public AuthConfigResponse config() {
        return authService.config();
    }

    @PostMapping("/request-parent-registration-link")
    public RegistrationLinkResponse requestParentRegistrationLink(
            @Valid @RequestBody RegistrationLinkRequest request
    ) {
        return authService.requestParentRegistrationLink(request);
    }

    @PostMapping("/request-self-service-registration-link")
    public RegistrationLinkResponse requestSelfServiceRegistrationLink(
            @Valid @RequestBody SelfServiceRegistrationLinkRequest request
    ) {
        return authService.requestSelfServiceRegistrationLink(request);
    }

    @PostMapping("/complete-registration")
    public AuthResponse completeRegistration(
            @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        return authService.completeRegistration(request);
    }

    @PostMapping("/register/complete")
    public AuthResponse completeRegistrationLegacy(
            @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        return authService.completeRegistration(request);
    }

    @GetMapping("/google/start")
    public GoogleStartResponse googleStart(
            @RequestParam UUID tenantId,
            @RequestHeader(value = "Origin", required = false) String origin
    ) {
        return authService.googleStart(tenantId, origin);
    }

    @GetMapping(value = "/google/callback", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> googleCallback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        try {
            return ResponseEntity.ok(googleCallbackHtml(authService.googleCallback(code, state)));
        } catch (InvalidAuthRequestException exception) {
            return ResponseEntity.badRequest().body(googleCallbackErrorHtml(exception.getMessage()));
        }
    }

    @PostMapping("/login")
    public AuthResponse login(
            @Valid @RequestBody LoginRequest request
    ) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        // Stateless JWT logout is handled client-side by discarding the bearer token.
    }

    @PostMapping("/accept-tenant-invitation")
    public AuthResponse acceptTenantInvitation(
            @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        return authService.acceptTenantInvitation(request);
    }

    @PostMapping("/accept-teacher-invitation")
    public AuthResponse acceptTeacherInvitation(
            @Valid @RequestBody CompleteRegistrationRequest request
    ) {
        return authService.acceptTeacherInvitation(request);
    }

    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        return authService.me(authentication);
    }

    private String googleCallbackHtml(GoogleCallbackResult result) {
        String accessToken = result.authResponse().accessToken();
        String returnUrl = appendAccessTokenFragment(result.returnUrl(), accessToken);
        return """
                <!doctype html>
                <html lang="en">
                  <head><meta charset="utf-8"><title>Google sign-in complete</title></head>
                  <body>
                    <p>Google sign-in complete. Returning to SchoolDays...</p>
                    <p><a href="%s">Return to SchoolDays</a></p>
                    <script>
                      window.location.replace("%s");
                    </script>
                  </body>
                </html>
                """.formatted(escapeHtml(returnUrl), escapeJavaScript(returnUrl));
    }

    private String googleCallbackErrorHtml(String message) {
        return """
                <!doctype html>
                <html lang="en">
                  <head><meta charset="utf-8"><title>Google sign-in failed</title></head>
                  <body>
                    <h1>Google sign-in failed</h1>
                    <p>%s</p>
                  </body>
                </html>
                """.formatted(escapeHtml(message));
    }

    private String appendAccessTokenFragment(String returnUrl, String accessToken) {
        return returnUrl + "#accessToken=" + urlEncode(accessToken);
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String escapeJavaScript(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "");
    }
}
