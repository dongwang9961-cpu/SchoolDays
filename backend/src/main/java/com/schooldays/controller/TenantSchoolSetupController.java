package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;

import com.schooldays.dto.api.EndpointStatusResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInRequest;
import com.schooldays.dto.externalcheckin.ExternalCheckInListResponse;
import com.schooldays.dto.externalcheckin.ExternalCheckInResponse;
import com.schooldays.dto.externalstudent.ExternalStudentListResponse;
import com.schooldays.dto.externalstudent.ExternalStudentImportResponse;
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
import com.schooldays.service.externalcheckin.ExternalCheckInService;
import com.schooldays.service.externalstudent.ExternalStudentImportService;
import com.schooldays.service.classroom.ClassService;
import com.schooldays.service.pricing.ClassPricingService;
import com.schooldays.service.program.ProgramService;
import com.schooldays.service.site.SiteService;
import com.schooldays.service.student.StudentRosterService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
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
    private final ExternalStudentImportService externalStudentImportService;
    private final ExternalCheckInService externalCheckInService;
    private final AuthService authService;

    public TenantSchoolSetupController(
            SiteService siteService,
            ProgramService programService,
            ClassService classService,
            ClassPricingService classPricingService,
            StudentRosterService studentRosterService,
            ExternalStudentImportService externalStudentImportService,
            ExternalCheckInService externalCheckInService,
            AuthService authService
    ) {
        this.siteService = siteService;
        this.programService = programService;
        this.classService = classService;
        this.classPricingService = classPricingService;
        this.studentRosterService = studentRosterService;
        this.externalStudentImportService = externalStudentImportService;
        this.externalCheckInService = externalCheckInService;
        this.authService = authService;
    }

    @GetMapping("/sites")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<SiteListResponse> listSites(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(siteService.listSites(tenantId));
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
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ProgramListResponse> listPrograms(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(programService.listPrograms(tenantId, siteId));
    }

    @PostMapping("/programs")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ProgramResponse> createProgram(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateProgramRequest request
    ) {
        return ResponseEntity.ok(programService.createProgram(tenantId, request));
    }

    @PatchMapping("/programs/{programId}")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ProgramResponse> updateProgram(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("programId") UUID programId,
            @Valid @RequestBody UpdateProgramRequest request
    ) {
        return ResponseEntity.ok(programService.updateProgram(tenantId, programId, request));
    }

    @GetMapping("/classes")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassListResponse> listClasses(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(classService.listClasses(tenantId, siteId));
    }

    @GetMapping("/students")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<StudentRosterResponse> listStudents(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam(value = "classId", required = false) UUID classId
    ) {
        return ResponseEntity.ok(studentRosterService.listActiveClassStudents(tenantId, classId));
    }

    @PostMapping("/user-invitations")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<InviteUserResponse> inviteUsers(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody InviteUserRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(authService.inviteUsers(tenantId, userId(authentication), request));
    }

    @PostMapping(value = "/external-students/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ExternalStudentImportResponse> importExternalStudents(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.ok(externalStudentImportService.importStudents(tenantId, file));
    }

    @GetMapping("/external-students")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ExternalStudentListResponse> listExternalStudents(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize
    ) {
        if (page == null || pageSize == null) {
            return ResponseEntity.ok(externalStudentImportService.listStudents(tenantId));
        }
        return ResponseEntity.ok(externalStudentImportService.listStudents(tenantId, page, pageSize));
    }

    @PostMapping("/external-check-ins")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ExternalCheckInResponse> recordExternalCheckIn(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody ExternalCheckInRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                externalCheckInService.recordCheckIn(tenantId, userId(authentication), "SCHOOL_ADMIN", request)
        );
    }

    @GetMapping("/external-check-ins")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ExternalCheckInListResponse> listExternalCheckIns(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("classId") UUID classId,
            @RequestParam(value = "checkDate", required = false) LocalDate checkDate
    ) {
        return ResponseEntity.ok(externalCheckInService.listCheckIns(tenantId, classId, checkDate));
    }

    @PostMapping("/classes")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassResponse> createClass(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateClassRequest request
    ) {
        return ResponseEntity.ok(classService.createClass(tenantId, request));
    }

    @PatchMapping("/classes/{classId}")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassResponse> updateClass(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @Valid @RequestBody UpdateClassRequest request
    ) {
        return ResponseEntity.ok(classService.updateClass(tenantId, classId, request));
    }

    @PostMapping("/classes/{classId}/close-enrollment")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassResponse> closeClassEnrollment(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classService.closeEnrollment(tenantId, classId));
    }

    @PostMapping("/classes/{classId}/stop")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassResponse> stopClass(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classService.stopClass(tenantId, classId));
    }

    @GetMapping("/classes/{classId}/pricing")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassPricingResponse> getClassPricing(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classPricingService.getPricing(tenantId, classId));
    }

    @PostMapping("/classes/{classId}/pricing")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<ClassPricingResponse> saveClassPricing(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @Valid @RequestBody SaveClassPricingRequest request
    ) {
        return ResponseEntity.ok(classPricingService.savePricing(tenantId, classId, request));
    }

    @PostMapping("/classes/{classId}/schedules")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'SCHOOL_ADMIN')")
    public ResponseEntity<EndpointStatusResponse> createClassSchedule(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/classes/{classId}/schedules");
    }
}
