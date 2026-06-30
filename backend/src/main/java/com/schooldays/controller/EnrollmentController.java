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

    @PostMapping("/api/enrollments")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #request.tenantId(), 'PARENT')")
    public ResponseEntity<CreateEnrollmentResponse> createEnrollment(
            @Valid @RequestBody CreateEnrollmentRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(enrollmentService.createParentEnrollment(this.userId(authentication), request));
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
