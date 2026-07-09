package com.schooldays.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.UserDao;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("tenantSecurity")
public class TenantSecurity {

    private final UserDao userDao;
    private final RoleDao roleDao;

    public TenantSecurity(UserDao userDao, RoleDao roleDao) {
        this.userDao = userDao;
        this.roleDao = roleDao;
    }

    public boolean hasTenantRole(Authentication authentication, UUID tenantId, String... roles) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt) || tenantId == null) {
            return false;
        }
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception exception) {
            return false;
        }
        if (userDao.findAuthUserById(userId).map(user -> !"active".equalsIgnoreCase(user.status())).orElse(true)) {
            return false;
        }
        return roleDao.findTenantRoles(userId).stream()
                .anyMatch(tenantRole -> tenantId.equals(tenantRole.tenantId()) && roleMatches(tenantRole.role(), roles));
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
}
