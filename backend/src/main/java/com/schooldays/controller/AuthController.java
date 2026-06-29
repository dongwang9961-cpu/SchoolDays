package com.schooldays.controller;

import jakarta.validation.Valid;
import java.util.UUID;

import com.schooldays.dto.auth.AuthResponse;
import com.schooldays.dto.auth.AuthenticatedUserResponse;
import com.schooldays.dto.auth.CompleteRegistrationRequest;
import com.schooldays.dto.auth.GoogleStartResponse;
import com.schooldays.dto.auth.LoginRequest;
import com.schooldays.dto.auth.RegistrationLinkRequest;
import com.schooldays.dto.auth.RegistrationLinkResponse;
import com.schooldays.dto.auth.SelfServiceRegistrationLinkRequest;
import com.schooldays.service.auth.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
    public GoogleStartResponse googleStart(@RequestParam UUID tenantId) {
        return authService.googleStart(tenantId);
    }

    @GetMapping("/google/callback")
    public AuthResponse googleCallback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        return authService.googleCallback(code, state);
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
}
