package com.schooldays.controller;

import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import com.schooldays.dto.child.ChildListResponse;
import com.schooldays.dto.child.ChildRequest;
import com.schooldays.dto.child.ChildResponse;
import com.schooldays.service.child.ChildService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parents/me")
public class ParentChildController extends ApiPlaceholderSupport {

    private final ChildService childService;

    public ParentChildController(ChildService childService) {
        this.childService = childService;
    }

    @GetMapping("/children")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'PARENT')")
    public ChildListResponse listChildren(
            @RequestParam("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        return childService.listChildren(tenantId, userId(authentication));
    }

    @PostMapping("/children")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #request.tenantId(), 'PARENT')")
    public ChildResponse createChild(
            Authentication authentication,
            @Valid @RequestBody ChildRequest request
    ) {
        return childService.createChild(userId(authentication), request);
    }

    @PatchMapping("/children/{childId}")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #request.tenantId(), 'PARENT')")
    public ChildResponse updateChild(
            Authentication authentication,
            @PathVariable("childId") UUID childId,
            @Valid @RequestBody ChildRequest request
    ) {
        return childService.updateChild(userId(authentication), childId, request);
    }

    @GetMapping("/children/{childId}/attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> getChildAttendance(@PathVariable("childId") UUID childId) {
        return notImplemented("GET /api/parents/me/children/{childId}/attendance");
    }

}
