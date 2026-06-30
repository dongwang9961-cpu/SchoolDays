package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import com.schooldays.dto.pricing.ClassPricingResponse;
import com.schooldays.service.pricing.ClassPricingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EnrollmentController extends ApiPlaceholderSupport {

    private final ClassPricingService classPricingService;

    public EnrollmentController(ClassPricingService classPricingService) {
        this.classPricingService = classPricingService;
    }

    @GetMapping("/api/parents/me/enrollments")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> listParentEnrollments() {
        return notImplemented("GET /api/parents/me/enrollments");
    }

    @PostMapping("/api/enrollments")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> createEnrollment(
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/enrollments");
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
