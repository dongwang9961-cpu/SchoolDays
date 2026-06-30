package com.schooldays.controller;

import java.util.UUID;

import com.schooldays.dto.classroom.ClassListResponse;
import com.schooldays.service.classroom.ClassService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/parents/me")
public class ParentClassController {

    private final ClassService classService;

    public ParentClassController(ClassService classService) {
        this.classService = classService;
    }

    @GetMapping("/classes")
    @PreAuthorize("@tenantSecurity.hasTenantRole(authentication, #tenantId, 'PARENT')")
    public ClassListResponse listAvailableClasses(@RequestParam("tenantId") UUID tenantId) {
        return classService.listAvailableClasses(tenantId);
    }
}
