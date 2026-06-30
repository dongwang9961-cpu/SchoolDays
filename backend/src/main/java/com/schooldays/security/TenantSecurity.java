package com.schooldays.security;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component("tenantSecurity")
public class TenantSecurity {

    public boolean hasTenantRole(Authentication authentication, UUID tenantId, String... roles) {
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt jwt) || tenantId == null) {
            return false;
        }
        List<Map<String, String>> tenantRoles = jwt.getClaim("tenant_roles");
        if (tenantRoles == null) {
            return false;
        }
        String tenant = tenantId.toString();
        for (Map<String, String> tenantRole : tenantRoles) {
            if (tenant.equals(tenantRole.get("tenantId")) && roleMatches(tenantRole.get("role"), roles)) {
                return true;
            }
        }
        return false;
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
