package com.schooldays.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceController extends ApiPlaceholderSupport {

    @PostMapping("/api/attendance/check-in")
    @PreAuthorize("hasAnyRole('PARENT', 'TEACHER', 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> checkIn(
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/attendance/check-in");
    }

    @GetMapping("/api/classes/{classId}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> getClassAttendance(
            @PathVariable("classId") UUID classId,
            @RequestParam LocalDate date
    ) {
        return notImplemented("GET /api/classes/{classId}/attendance");
    }
}
