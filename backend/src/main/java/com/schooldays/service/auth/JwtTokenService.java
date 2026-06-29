package com.schooldays.service.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.schooldays.config.JwtKeyProperties;
import com.schooldays.dto.auth.AuthToken;
import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.entities.auth.TenantRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtKeyProperties jwtKeyProperties;
    private final String issuer;
    private final Duration accessTokenTtl;

    public JwtTokenService(
            JwtEncoder jwtEncoder,
            JwtKeyProperties jwtKeyProperties,
            @Value("${schooldays.security.jwt.issuer}") String issuer,
            @Value("${schooldays.security.jwt.access-token-ttl-minutes}") long accessTokenTtlMinutes
    ) {
        this.jwtEncoder = jwtEncoder;
        this.jwtKeyProperties = jwtKeyProperties;
        this.issuer = issuer;
        this.accessTokenTtl = Duration.ofMinutes(accessTokenTtlMinutes);
    }

    public AuthToken issueAccessToken(AuthenticatedUser user) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        List<String> roles = user.tenantRoles().stream()
                .map(TenantRole::role)
                .distinct()
                .toList();
        List<Map<String, String>> tenantRoles = user.tenantRoles().stream()
                .map(tenantRole -> Map.of(
                        "tenantId", tenantRole.tenantId() == null ? "" : tenantRole.tenantId().toString(),
                        "role", tenantRole.role()
                ))
                .toList();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.id().toString())
                .claim("email", user.email())
                .claim("phone", user.phone())
                .claim("roles", roles)
                .claim("tenant_roles", tenantRoles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .keyId(jwtKeyProperties.getCurrentKeyId())
                .build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AuthToken(token, accessTokenTtl.toSeconds());
    }
}
