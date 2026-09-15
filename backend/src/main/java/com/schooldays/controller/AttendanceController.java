package com.schooldays.controller;

import java.time.LocalDate;
import java.util.UUID;

import com.schooldays.dto.attendance.AttendanceCheckInRequest;
import com.schooldays.dto.attendance.AttendanceGridResponse;
import com.schooldays.dto.attendance.AttendanceListResponse;
import com.schooldays.dto.attendance.AttendanceResponse;
import com.schooldays.security.TenantSecurity;
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
    private final TenantSecurity tenantSecurity;

    public AttendanceController(AttendanceService attendanceService, TenantSecurity tenantSecurity) {
        this.attendanceService = attendanceService;
        this.tenantSecurity = tenantSecurity;
    }

    @PostMapping("/api/attendance/check-in")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #request.tenantId(), 'PARENT') or @tenantSecurity.canCheckInClass(authentication, #request.tenantId(), #request.classId())")
    public ResponseEntity<AttendanceResponse> checkIn(
            @Valid @RequestBody AttendanceCheckInRequest request,
            Authentication authentication
    ) {
        if (tenantSecurity.hasTenantRole(authentication, request.tenantId(), "PARENT")) {
            return ResponseEntity.ok(attendanceService.parentCheckIn(userId(authentication), request));
        }
        String checkedInByRole = tenantSecurity.hasTenantRole(authentication, request.tenantId(), "SCHOOL_ADMIN")
                ? "SCHOOL_ADMIN"
                : tenantSecurity.hasTenantRole(authentication, request.tenantId(), "SITE_MANAGER")
                    ? "SITE_MANAGER"
                    : "TEACHER";
        return ResponseEntity.ok(attendanceService.staffCheckIn(userId(authentication), checkedInByRole, request));
    }

    @GetMapping("/api/tenants/{tenantId}/classes/{classId}/attendance")
    @PreAuthorize("@tenantSecurity.canCheckInClass(authentication, #tenantId, #classId)")
    public ResponseEntity<AttendanceListResponse> getTenantClassAttendance(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @RequestParam LocalDate date
    ) {
        return ResponseEntity.ok(attendanceService.listClassAttendance(tenantId, classId, date));
    }

    @GetMapping("/api/classes/{classId}/attendance")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN')")
    public ResponseEntity<AttendanceListResponse> getClassAttendance(
            @PathVariable("classId") UUID classId,
            @RequestParam LocalDate date
    ) {
        return ResponseEntity.ok(attendanceService.listClassAttendance(classId, date));
    }

    @GetMapping("/api/tenants/{tenantId}/classes/{classId}/attendance-grid")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'TEACHER') or @tenantSecurity.canManageClass(authentication, #tenantId, #classId)")
    public ResponseEntity<AttendanceGridResponse> getClassAttendanceGrid(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(attendanceService.getClassAttendanceGrid(tenantId, classId));
    }
}
