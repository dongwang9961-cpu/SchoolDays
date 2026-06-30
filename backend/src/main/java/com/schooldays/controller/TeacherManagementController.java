package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TeacherManagementController extends ApiPlaceholderSupport {

    @PostMapping("/classes/{classId}/teachers/invite")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> inviteTeacher(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/classes/{classId}/teachers/invite");
    }

    @GetMapping("/teacher-invitations")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> listTeacherInvitations(@PathVariable("tenantId") UUID tenantId) {
        return notImplemented("GET /api/tenants/{tenantId}/teacher-invitations");
    }

    @PostMapping("/classes/{classId}/teachers")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> assignTeacher(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/classes/{classId}/teachers");
    }

    @DeleteMapping("/classes/{classId}/teachers/{teacherUserId}")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> removeTeacher(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @PathVariable("teacherUserId") UUID teacherUserId
    ) {
        return notImplemented("DELETE /api/tenants/{tenantId}/classes/{classId}/teachers/{teacherUserId}");
    }
}
