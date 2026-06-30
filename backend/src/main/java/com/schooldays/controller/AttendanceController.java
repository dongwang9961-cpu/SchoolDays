package com.schooldays.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.schooldays.dto.attendance.AttendanceCheckInRequest;
import com.schooldays.dto.attendance.AttendanceListResponse;
import com.schooldays.dto.attendance.AttendanceResponse;
import com.schooldays.service.attendance.AttendanceService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AttendanceController extends ApiPlaceholderSupport {

    private final AttendanceService attendanceService;

    public AttendanceController(AttendanceService attendanceService) {
        this.attendanceService = attendanceService;
    }

    @PostMapping("/api/attendance/check-in")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #request.tenantId(), 'PARENT')")
    public ResponseEntity<AttendanceResponse> checkIn(
            @Valid @RequestBody AttendanceCheckInRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(attendanceService.parentCheckIn(userId(authentication), request));
    }

    @GetMapping("/api/classes/{classId}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN')")
    public ResponseEntity<AttendanceListResponse> getClassAttendance(
            @PathVariable("classId") UUID classId,
            @RequestParam LocalDate date
    ) {
        return ResponseEntity.ok(attendanceService.listClassAttendance(classId, date));
    }
}
