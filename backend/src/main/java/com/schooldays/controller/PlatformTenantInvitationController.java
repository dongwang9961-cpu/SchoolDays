package com.schooldays.controller;

import java.util.Map;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlatformTenantInvitationController extends ApiPlaceholderSupport {

    @PostMapping("/api/platform/tenant-invitations")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> createTenantInvitation(
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/platform/tenant-invitations");
    }

    @GetMapping("/api/platform/tenant-invitations")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> listTenantInvitations() {
        return notImplemented("GET /api/platform/tenant-invitations");
    }

    @PostMapping("/api/tenant-invitations/{token}/accept")
    public ResponseEntity<EndpointStatusResponse> acceptTenantInvitationByToken(
            @PathVariable("token") String token,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenant-invitations/{token}/accept");
    }
}
