package com.schooldays.security;

import static com.schooldays.jooq.generated.tables.Classes.CLASSES;
import static com.schooldays.jooq.generated.tables.Programs.PROGRAMS;
import static com.schooldays.jooq.generated.tables.SchoolSites.SCHOOL_SITES;
import static com.schooldays.jooq.generated.tables.TeacherAssignments.TEACHER_ASSIGNMENTS;
import static com.schooldays.jooq.generated.tables.Enrollments.ENROLLMENTS;

import java.util.List;
import java.util.UUID;

import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.entities.auth.TenantRole;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("tenantSecurity")
public class TenantSecurity {

    private final UserDao userDao;
    private final RoleDao roleDao;
    private final DSLContext dsl;

    @Autowired
    public TenantSecurity(UserDao userDao, RoleDao roleDao, DSLContext dsl) {
        this.userDao = userDao;
        this.roleDao = roleDao;
        this.dsl = dsl;
    }

    TenantSecurity(UserDao userDao, RoleDao roleDao) {
        this(userDao, roleDao, null);
    }

    public boolean hasTenantRole(Authentication authentication, UUID tenantId, String... roles) {
        if (tenantId == null) {
            return false;
        }
        return tenantRolesFor(authentication).stream()
                .anyMatch(tenantRole -> tenantId.equals(tenantRole.tenantId()) && roleMatches(tenantRole.role(), roles));
    }

    public boolean canManageSite(Authentication authentication, UUID tenantId, UUID siteId) {
        if (tenantId == null || siteId == null || !siteBelongsToTenant(tenantId, siteId)) {
            return false;
        }
        List<TenantRole> tenantRoles = tenantRolesFor(authentication);
        if (tenantRoles.stream().anyMatch(tenantRole ->
                tenantId.equals(tenantRole.tenantId()) && "SCHOOL_ADMIN".equals(tenantRole.role()))) {
            return true;
        }
        return tenantRoles.stream()
                .anyMatch(tenantRole -> tenantId.equals(tenantRole.tenantId())
                        && "SITE_MANAGER".equals(tenantRole.role())
                        && tenantRole.appliesToSite(siteId));
    }

    public boolean canManageProgram(Authentication authentication, UUID tenantId, UUID programId) {
        if (tenantId == null || programId == null || dsl == null) {
            return false;
        }
        UUID siteId = dsl.select(PROGRAMS.SITE_ID)
                .from(PROGRAMS)
                .where(PROGRAMS.TENANT_ID.eq(tenantId))
                .and(PROGRAMS.ID.eq(programId))
                .fetchOne(PROGRAMS.SITE_ID);
        return canManageSite(authentication, tenantId, siteId);
    }

    public boolean canManageClass(Authentication authentication, UUID tenantId, UUID classId) {
        if (tenantId == null || classId == null || dsl == null) {
            return false;
        }
        UUID siteId = dsl.select(PROGRAMS.SITE_ID)
                .from(CLASSES)
                .join(PROGRAMS).on(PROGRAMS.ID.eq(CLASSES.PROGRAM_ID))
                .where(CLASSES.TENANT_ID.eq(tenantId))
                .and(CLASSES.ID.eq(classId))
                .fetchOne(PROGRAMS.SITE_ID);
        return canManageSite(authentication, tenantId, siteId);
    }

    public boolean canCheckInClass(Authentication authentication, UUID tenantId, UUID classId) {
        if (canManageClass(authentication, tenantId, classId)) {
            return true;
        }
        if (tenantId == null || classId == null || dsl == null
                || !hasTenantRole(authentication, tenantId, "TEACHER")) {
            return false;
        }
        UUID userId = userId(authentication);
        return userId != null && dsl.fetchExists(dsl.selectOne()
                .from(TEACHER_ASSIGNMENTS)
                .where(TEACHER_ASSIGNMENTS.CLASS_ID.eq(classId))
                .and(TEACHER_ASSIGNMENTS.TEACHER_USER_ID.eq(userId)));
    }

    public boolean canManageEnrollment(Authentication authentication, UUID tenantId, UUID enrollmentId) {
        if (tenantId == null || enrollmentId == null || dsl == null) {
            return false;
        }
        UUID classId = dsl.select(ENROLLMENTS.CLASS_ID)
                .from(ENROLLMENTS)
                .where(ENROLLMENTS.TENANT_ID.eq(tenantId))
                .and(ENROLLMENTS.ID.eq(enrollmentId))
                .fetchOne(ENROLLMENTS.CLASS_ID);
        return canManageClass(authentication, tenantId, classId);
    }

    public List<UUID> siteManagerSiteIds(Authentication authentication, UUID tenantId) {
        if (tenantId == null) {
            return List.of();
        }
        return tenantRolesFor(authentication).stream()
                .filter(tenantRole -> tenantId.equals(tenantRole.tenantId()))
                .filter(tenantRole -> "SITE_MANAGER".equals(tenantRole.role()))
                .flatMap(tenantRole -> tenantRole.siteIds().stream())
                .distinct()
                .filter(siteId -> siteBelongsToTenant(tenantId, siteId))
                .toList();
    }

    private List<TenantRole> tenantRolesFor(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return List.of();
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception exception) {
            return List.of();
        }
        if (userDao.findAuthUserById(userId).map(user -> !"active".equalsIgnoreCase(user.status())).orElse(true)) {
            return List.of();
        }
        return roleDao.findTenantRoles(userId);
    }

    private UUID userId(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt)) {
            return null;
        }
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (Exception exception) {
            return null;
        }
    }

    private boolean roleMatches(String actualRole, String[] allowedRoles) {
        if (actualRole == null) {
            return false;
        }
        for (String allowedRole : allowedRoles) {
            if (actualRole.equals(allowedRole)) {
                return true;
            }
        }
        return false;
    }

    private boolean siteBelongsToTenant(UUID tenantId, UUID siteId) {
        if (dsl == null || tenantId == null || siteId == null) {
            return false;
        }
        return dsl.fetchExists(dsl.selectOne()
                .from(SCHOOL_SITES)
                .where(SCHOOL_SITES.TENANT_ID.eq(tenantId))
                .and(SCHOOL_SITES.ID.eq(siteId)));
    }
}
