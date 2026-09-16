package com.schooldays.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

import com.schooldays.dto.api.EndpointStatusResponse;
import com.schooldays.dto.auth.InviteUserRequest;
import com.schooldays.dto.auth.InviteUserResponse;
import com.schooldays.dto.classroom.ClassListResponse;
import com.schooldays.dto.classroom.ClassResponse;
import com.schooldays.dto.classroom.CreateClassRequest;
import com.schooldays.dto.classroom.UpdateClassRequest;
import com.schooldays.dto.program.CreateProgramRequest;
import com.schooldays.dto.program.ProgramListResponse;
import com.schooldays.dto.program.ProgramResponse;
import com.schooldays.dto.program.UpdateProgramRequest;
import com.schooldays.dto.pricing.ClassPricingResponse;
import com.schooldays.dto.pricing.SaveClassPricingRequest;
import com.schooldays.dto.site.CreateSiteRequest;
import com.schooldays.dto.site.SiteListResponse;
import com.schooldays.dto.site.SiteResponse;
import com.schooldays.dto.site.UpdateSiteRequest;
import com.schooldays.dto.student.StudentRosterResponse;
import com.schooldays.service.auth.AuthService;
import com.schooldays.service.classroom.ClassService;
import com.schooldays.service.pricing.ClassPricingService;
import com.schooldays.service.program.ProgramService;
import com.schooldays.service.site.SiteService;
import com.schooldays.service.student.StudentRosterService;
import com.schooldays.security.TenantSecurity;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenants/{tenantId}")
public class TenantSchoolSetupController extends ApiPlaceholderSupport {

    private final SiteService siteService;
    private final ProgramService programService;
    private final ClassService classService;
    private final ClassPricingService classPricingService;
    private final StudentRosterService studentRosterService;
    private final AuthService authService;
    private final TenantSecurity tenantSecurity;

    public TenantSchoolSetupController(
            SiteService siteService,
            ProgramService programService,
            ClassService classService,
            ClassPricingService classPricingService,
            StudentRosterService studentRosterService,
            AuthService authService,
            TenantSecurity tenantSecurity
    ) {
        this.siteService = siteService;
        this.programService = programService;
        this.classService = classService;
        this.classPricingService = classPricingService;
        this.studentRosterService = studentRosterService;
        this.authService = authService;
        this.tenantSecurity = tenantSecurity;
    }

    @GetMapping("/sites")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN', 'SITE_MANAGER')")
    public ResponseEntity<SiteListResponse> listSites(
            @PathVariable("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        if (tenantSecurity.hasTenantRole(authentication, tenantId, "SCHOOL_ADMIN")) {
            return ResponseEntity.ok(siteService.listSites(tenantId));
        }
        return ResponseEntity.ok(siteService.listSites(
                tenantId,
                tenantSecurity.siteManagerSiteIds(authentication, tenantId)
        ));
    }

    @PostMapping("/sites")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<SiteResponse> createSite(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateSiteRequest request
    ) {
        return ResponseEntity.ok(siteService.createSite(tenantId, request));
    }

    @PatchMapping("/sites/{siteId}")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<SiteResponse> updateSite(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("siteId") UUID siteId,
            @Valid @RequestBody UpdateSiteRequest request
    ) {
        return ResponseEntity.ok(siteService.updateSite(tenantId, siteId, request));
    }

    @GetMapping("/programs")
    @PreAuthorize("@tenantSecurity.canManageSite(authentication, #tenantId, #siteId)")
    public ResponseEntity<ProgramListResponse> listPrograms(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(programService.listPrograms(tenantId, siteId));
    }

    @PostMapping("/programs")
    @PreAuthorize("#request != null and @tenantSecurity.canManageSite(authentication, #tenantId, #request.siteId())")
    public ResponseEntity<ProgramResponse> createProgram(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateProgramRequest request
    ) {
        return ResponseEntity.ok(programService.createProgram(tenantId, request));
    }

    @PatchMapping("/programs/{programId}")
    @PreAuthorize("@tenantSecurity.canManageProgram(authentication, #tenantId, #programId)")
    public ResponseEntity<ProgramResponse> updateProgram(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("programId") UUID programId,
            @Valid @RequestBody UpdateProgramRequest request
    ) {
        return ResponseEntity.ok(programService.updateProgram(tenantId, programId, request));
    }

    @GetMapping("/classes")
    @PreAuthorize("@tenantSecurity.canManageSite(authentication, #tenantId, #siteId)")
    public ResponseEntity<ClassListResponse> listClasses(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(classService.listClasses(tenantId, siteId));
    }

    @GetMapping("/teacher-classes")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'TEACHER')")
    public ResponseEntity<ClassListResponse> listTeacherClasses(
            @PathVariable("tenantId") UUID tenantId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(classService.listTeacherClasses(tenantId, userId(authentication)));
    }

    @GetMapping("/students")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN') or (#classId != null and @tenantSecurity.canCheckInClass(authentication, #tenantId, #classId)) or (#siteId != null and @tenantSecurity.canManageSite(authentication, #tenantId, #siteId))")
    public ResponseEntity<StudentRosterResponse> listStudents(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam(value = "classId", required = false) UUID classId,
            @RequestParam(value = "siteId", required = false) UUID siteId,
            @RequestParam(value = "group", defaultValue = "active") String group,
            @RequestParam(value = "year", required = false) Integer year
    ) {
        return ResponseEntity.ok(studentRosterService.listClassStudents(tenantId, classId, siteId, group, year));
    }

    @PostMapping("/user-invitations")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN') or (#request != null and #request.role() != null and #request.role().equalsIgnoreCase('TEACHER') and @tenantSecurity.canManageClass(authentication, #tenantId, #request.classId()))")
    public ResponseEntity<InviteUserResponse> inviteUsers(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody InviteUserRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(authService.inviteUsers(tenantId, userId(authentication), request));
    }

    @PostMapping("/password-reset-links")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<InviteUserResponse> sendPasswordResetLinks(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody InviteUserRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(authService.sendPasswordResetLinks(tenantId, userId(authentication), request));
    }

    @DeleteMapping("/users")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> deleteUser(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("email") String email,
            Authentication authentication
    ) {
        return ResponseEntity.ok(authService.deleteUser(tenantId, userId(authentication), email));
    }

    @PostMapping("/classes")
    @PreAuthorize("#request != null and @tenantSecurity.canManageProgram(authentication, #tenantId, #request.programId())")
    public ResponseEntity<ClassResponse> createClass(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateClassRequest request
    ) {
        return ResponseEntity.ok(classService.createClass(tenantId, request));
    }

    @PatchMapping("/classes/{classId}")
    @PreAuthorize("@tenantSecurity.canManageClass(authentication, #tenantId, #classId) and (#request == null or #request.programId() == null or @tenantSecurity.canManageProgram(authentication, #tenantId, #request.programId()))")
    public ResponseEntity<ClassResponse> updateClass(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @Valid @RequestBody UpdateClassRequest request
    ) {
        return ResponseEntity.ok(classService.updateClass(tenantId, classId, request));
    }

    @PostMapping("/classes/{classId}/close-enrollment")
    @PreAuthorize("@tenantSecurity.canManageClass(authentication, #tenantId, #classId)")
    public ResponseEntity<ClassResponse> closeClassEnrollment(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classService.closeEnrollment(tenantId, classId));
    }

    @PostMapping("/classes/{classId}/stop")
    @PreAuthorize("@tenantSecurity.canManageClass(authentication, #tenantId, #classId)")
    public ResponseEntity<ClassResponse> stopClass(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classService.stopClass(tenantId, classId));
    }

    @GetMapping("/classes/{classId}/pricing")
    @PreAuthorize("@tenantSecurity.canManageClass(authentication, #tenantId, #classId)")
    public ResponseEntity<ClassPricingResponse> getClassPricing(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classPricingService.getPricing(tenantId, classId));
    }

    @PostMapping("/classes/{classId}/pricing")
    @PreAuthorize("@tenantSecurity.canManageClass(authentication, #tenantId, #classId)")
    public ResponseEntity<ClassPricingResponse> saveClassPricing(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @Valid @RequestBody SaveClassPricingRequest request
    ) {
        return ResponseEntity.ok(classPricingService.savePricing(tenantId, classId, request));
    }

    @PostMapping("/classes/{classId}/schedules")
    @PreAuthorize("@tenantSecurity.canManageClass(authentication, #tenantId, #classId)")
    public ResponseEntity<EndpointStatusResponse> createClassSchedule(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/classes/{classId}/schedules");
    }
}
