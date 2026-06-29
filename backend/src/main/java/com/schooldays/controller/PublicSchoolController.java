package com.schooldays.controller;

import com.schooldays.dao.auth.TenantDao;
import com.schooldays.dto.school.PublicSchoolResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/public/schools")
public class PublicSchoolController {

    private final TenantDao tenantDao;

    public PublicSchoolController(TenantDao tenantDao) {
        this.tenantDao = tenantDao;
    }

    @GetMapping("/{slug}")
    public PublicSchoolResponse findBySlug(@PathVariable("slug") String slug) {
        String normalizedSlug = normalizeSlug(slug);
        return tenantDao.findActivePublicSchoolBySlug(normalizedSlug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "School was not found"));
    }

    private String normalizeSlug(String slug) {
        String normalized = slug == null ? "" : slug.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9](?:[a-z0-9-]{0,78}[a-z0-9])?")) {
            throw new InvalidSchoolSlugException();
        }
        return normalized;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    private static class InvalidSchoolSlugException extends RuntimeException {
    }
}
