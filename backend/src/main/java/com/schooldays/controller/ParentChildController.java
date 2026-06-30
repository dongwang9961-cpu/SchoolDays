package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parents/me")
public class ParentChildController extends ApiPlaceholderSupport {

    @GetMapping("/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> listChildren() {
        return notImplemented("GET /api/parents/me/children");
    }

    @PostMapping("/children")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> createChild(
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/parents/me/children");
    }

    @PatchMapping("/children/{childId}")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> updateChild(
            @PathVariable("childId") UUID childId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("PATCH /api/parents/me/children/{childId}");
    }

    @GetMapping("/children/{childId}/attendance")
    @PreAuthorize("hasRole('PARENT')")
    public ResponseEntity<EndpointStatusResponse> getChildAttendance(@PathVariable("childId") UUID childId) {
        return notImplemented("GET /api/parents/me/children/{childId}/attendance");
    }
}
