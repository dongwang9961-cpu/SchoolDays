package com.schooldays.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.schooldays.dto.api.EndpointStatusResponse;
import com.schooldays.dto.enrollment.CreateEnrollmentRequest;
import com.schooldays.dto.enrollment.CreateEnrollmentResponse;
import com.schooldays.dto.enrollment.EnrollmentListResponse;
import com.schooldays.dto.enrollment.RejectEnrollmentRequest;
import com.schooldays.dto.enrollment.EnrollmentRequestResponse;
import com.schooldays.dto.enrollment.MessageListResponse;
import com.schooldays.dto.pricing.ClassPricingResponse;
import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.service.enrollment.EnrollmentService;
import com.schooldays.service.pricing.ClassPricingService;

import jakarta.validation.Valid;

@RestController
public class EnrollmentController extends ApiPlaceholderSupport {

    private final ClassPricingService classPricingService;
    private final EnrollmentService enrollmentService;

    public EnrollmentController(ClassPricingService classPricingService, EnrollmentService enrollmentService) {
        this.classPricingService = classPricingService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/api/parents/me/enrollments")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'PARENT')")
    public ResponseEntity<EnrollmentListResponse> listParentEnrollments(
            @RequestParam("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(enrollmentService.listParentEnrollments(tenantId, this.userId(authentication)));
    }

    @GetMapping("/api/parents/me/messages")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'PARENT')")
    public ResponseEntity<MessageListResponse> listParentMessages(
            @RequestParam("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(enrollmentService.listParentMessages(tenantId, this.userId(authentication)));
    }

    @PostMapping("/api/parents/me/messages/{messageId}/read")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'PARENT')")
    public ResponseEntity<EndpointStatusResponse> markParentMessageRead(
            @RequestParam("tenantId") UUID tenantId,
            @PathVariable UUID messageId,
            Authentication authentication
    ) {
        enrollmentService.markParentMessageRead(tenantId, this.userId(authentication), messageId);
        return ResponseEntity.ok(new EndpointStatusResponse("read", "POST /api/parents/me/messages/{messageId}/read", "Message marked as read."));
    }

    @PostMapping("/api/enrollments")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #request.tenantId(), 'PARENT')")
    public ResponseEntity<CreateEnrollmentResponse> createEnrollment(
            @Valid @RequestBody CreateEnrollmentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(enrollmentService.createParentEnrollment(this.userId(authentication), request));
    }

    @GetMapping("/api/tenants/{tenantId}/enrollment-requests")
    @PreAuthorize("@tenantSecurity.canManageSite(authentication, #tenantId, #siteId)")
    public ResponseEntity<java.util.List<EnrollmentRequestResponse>> listPendingEnrollmentRequests(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(enrollmentService.listPendingSiteEnrollments(tenantId, siteId));
    }

    @PostMapping("/api/tenants/{tenantId}/enrollment-requests/{enrollmentId}/approve")
    @PreAuthorize("@tenantSecurity.canManageEnrollment(authentication, #tenantId, #enrollmentId)")
    public ResponseEntity<EndpointStatusResponse> approveEnrollment(
            @PathVariable UUID tenantId,
            @PathVariable UUID enrollmentId
    ) {
        enrollmentService.approveEnrollment(tenantId, enrollmentId);
        return ResponseEntity.ok(new EndpointStatusResponse("approved", "POST /api/tenants/{tenantId}/enrollment-requests/{enrollmentId}/approve", "Enrollment request approved."));
    }

    @PostMapping("/api/tenants/{tenantId}/enrollment-requests/{enrollmentId}/reject")
    @PreAuthorize("@tenantSecurity.canManageEnrollment(authentication, #tenantId, #enrollmentId)")
    public ResponseEntity<EndpointStatusResponse> rejectEnrollment(
            @PathVariable UUID tenantId,
            @PathVariable UUID enrollmentId,
            @Valid @RequestBody RejectEnrollmentRequest request,
            Authentication authentication
    ) {
        enrollmentService.rejectEnrollment(tenantId, enrollmentId, this.userId(authentication), request.message());
        return ResponseEntity.ok(new EndpointStatusResponse("rejected", "POST /api/tenants/{tenantId}/enrollment-requests/{enrollmentId}/reject", "Enrollment request rejected."));
    }

    @GetMapping("/api/classes/{classId}/available-dates")
    public ResponseEntity<EndpointStatusResponse> getAvailableDates(@PathVariable("classId") UUID classId) {
        return notImplemented("GET /api/classes/{classId}/available-dates");
    }

    @GetMapping("/api/classes/{classId}/pricing")
    public ResponseEntity<ClassPricingResponse> getClassPricing(@PathVariable("classId") UUID classId) {
        return ResponseEntity.ok(classPricingService.getPublicPricing(classId));
    }
}
