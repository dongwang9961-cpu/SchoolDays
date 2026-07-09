package com.schooldays.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schooldays.dao.auth.RoleDao;
import com.schooldays.dao.auth.UserDao;
import com.schooldays.entities.auth.TenantRole;
import com.schooldays.entities.auth.UserAuthRow;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class TenantSecurityTests {

    @Test
    void grantsOnlyMatchingTenantRole() {
        UUID tenantId = UUID.randomUUID();
        UUID otherTenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantSecurity tenantSecurity = new TenantSecurity(
                new StubUserDao(userId, "active"),
                new StubRoleDao(List.of(
                        new TenantRole(tenantId, "SCHOOL_ADMIN"),
                        new TenantRole(otherTenantId, "PARENT")
                ))
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithSubject(userId));

        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "SCHOOL_ADMIN")).isTrue();
        assertThat(tenantSecurity.hasTenantRole(authentication, otherTenantId, "SCHOOL_ADMIN")).isFalse();
        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "TEACHER")).isFalse();
    }

    @Test
    void rejectsParentRoleForNotificationSenderPermissions() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantSecurity tenantSecurity = new TenantSecurity(
                new StubUserDao(userId, "active"),
                new StubRoleDao(List.of(new TenantRole(tenantId, "PARENT")))
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithSubject(userId));

        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "SCHOOL_ADMIN", "TEACHER")).isFalse();
    }

    @Test
    void rejectsInactiveUsers() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        TenantSecurity tenantSecurity = new TenantSecurity(
                new StubUserDao(userId, "deleted"),
                new StubRoleDao(List.of(new TenantRole(tenantId, "SCHOOL_ADMIN")))
        );
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwtWithSubject(userId));

        assertThat(tenantSecurity.hasTenantRole(authentication, tenantId, "SCHOOL_ADMIN")).isFalse();
    }

    private Jwt jwtWithSubject(UUID userId) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("test-token")
                .header("alg", "HS256")
                .subject(userId.toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300))
                .build();
    }

    private static final class StubUserDao extends UserDao {
        private final UUID userId;
        private final String status;

        private StubUserDao(UUID userId, String status) {
            super(null);
            this.userId = userId;
            this.status = status;
        }

        @Override
        public java.util.Optional<UserAuthRow> findAuthUserById(UUID requestedUserId) {
            if (!userId.equals(requestedUserId)) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(new UserAuthRow(userId, "teacher@example.com", null, null, status));
        }
    }

    private static final class StubRoleDao extends RoleDao {
        private final List<TenantRole> tenantRoles;

        private StubRoleDao(List<TenantRole> tenantRoles) {
            super(null, null);
            this.tenantRoles = tenantRoles;
        }

        @Override
        public List<TenantRole> findTenantRoles(UUID userId) {
            return tenantRoles;
        }
    }
}
