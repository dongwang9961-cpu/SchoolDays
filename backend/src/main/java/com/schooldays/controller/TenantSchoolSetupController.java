package com.schooldays.controller;

import java.util.Map;
import java.util.UUID;

import com.schooldays.dto.api.EndpointStatusResponse;
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
import com.schooldays.service.classroom.ClassService;
import com.schooldays.service.pricing.ClassPricingService;
import com.schooldays.service.program.ProgramService;
import com.schooldays.service.site.SiteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    public TenantSchoolSetupController(
            SiteService siteService,
            ProgramService programService,
            ClassService classService,
            ClassPricingService classPricingService
    ) {
        this.siteService = siteService;
        this.programService = programService;
        this.classService = classService;
        this.classPricingService = classPricingService;
    }

    @GetMapping("/sites")
    public ResponseEntity<SiteListResponse> listSites(@PathVariable("tenantId") UUID tenantId) {
        return ResponseEntity.ok(siteService.listSites(tenantId));
    }

    @PostMapping("/sites")
    public ResponseEntity<SiteResponse> createSite(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateSiteRequest request
    ) {
        return ResponseEntity.ok(siteService.createSite(tenantId, request));
    }

    @PatchMapping("/sites/{siteId}")
    public ResponseEntity<SiteResponse> updateSite(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("siteId") UUID siteId,
            @Valid @RequestBody UpdateSiteRequest request
    ) {
        return ResponseEntity.ok(siteService.updateSite(tenantId, siteId, request));
    }

    @GetMapping("/programs")
    public ResponseEntity<ProgramListResponse> listPrograms(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(programService.listPrograms(tenantId, siteId));
    }

    @PostMapping("/programs")
    public ResponseEntity<ProgramResponse> createProgram(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateProgramRequest request
    ) {
        return ResponseEntity.ok(programService.createProgram(tenantId, request));
    }

    @PatchMapping("/programs/{programId}")
    public ResponseEntity<ProgramResponse> updateProgram(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("programId") UUID programId,
            @Valid @RequestBody UpdateProgramRequest request
    ) {
        return ResponseEntity.ok(programService.updateProgram(tenantId, programId, request));
    }

    @GetMapping("/classes")
    public ResponseEntity<ClassListResponse> listClasses(
            @PathVariable("tenantId") UUID tenantId,
            @RequestParam("siteId") UUID siteId
    ) {
        return ResponseEntity.ok(classService.listClasses(tenantId, siteId));
    }

    @PostMapping("/classes")
    public ResponseEntity<ClassResponse> createClass(
            @PathVariable("tenantId") UUID tenantId,
            @Valid @RequestBody CreateClassRequest request
    ) {
        return ResponseEntity.ok(classService.createClass(tenantId, request));
    }

    @PatchMapping("/classes/{classId}")
    public ResponseEntity<ClassResponse> updateClass(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @Valid @RequestBody UpdateClassRequest request
    ) {
        return ResponseEntity.ok(classService.updateClass(tenantId, classId, request));
    }

    @GetMapping("/classes/{classId}/pricing")
    public ResponseEntity<ClassPricingResponse> getClassPricing(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId
    ) {
        return ResponseEntity.ok(classPricingService.getPricing(tenantId, classId));
    }

    @PostMapping("/classes/{classId}/pricing")
    public ResponseEntity<ClassPricingResponse> saveClassPricing(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @Valid @RequestBody SaveClassPricingRequest request
    ) {
        return ResponseEntity.ok(classPricingService.savePricing(tenantId, classId, request));
    }

    @PostMapping("/classes/{classId}/schedules")
    public ResponseEntity<EndpointStatusResponse> createClassSchedule(
            @PathVariable("tenantId") UUID tenantId,
            @PathVariable("classId") UUID classId,
            @RequestBody(required = false) Map<String, Object> request
    ) {
        return notImplemented("POST /api/tenants/{tenantId}/classes/{classId}/schedules");
    }
}
