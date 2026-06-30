package com.schooldays.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantSecurityTests {

    private final TenantSecurity tenantSecurity = new TenantSecurity();

    @Test
    void grantsOnlyMatchingTenantRole() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithRoles(
                Map.of("tenantId", tenantId.toString(), "role", "SCHOOL_ADMIN"),
                Map.of("tenantId", otherTenantId.toString(), "role", "PARENT")
        ));

        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "SCHOOL_ADMIN")).isTrue();
        assertThat(tenantSecurity.hasTenantRole(authentication, otherTenantId, "SCHOOL_ADMIN")).isFalse();
        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "TEACHER")).isFalse();
    }

    @Test
    void rejectsParentRoleForNotificationSenderPermissions() {
        UUID tenantId = UUID.randomUUID();
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithRoles(
                Map.of("tenantId", tenantId.toString(), "role", "PARENT")
        ));

        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "SCHOOL_ADMIN", "TEACHER")).isFalse();
    }

    @SafeVarargs
    private Jwt jwtWithRoles(Map<String, String>... tenantRoles) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .claim("tenant_roles", List.of(tenantRoles))
                .build();
    }
}
