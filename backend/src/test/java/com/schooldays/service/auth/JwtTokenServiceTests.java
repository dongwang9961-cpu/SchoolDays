package com.schooldays.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.schooldays.config.JwtKeyProperties;
import com.schooldays.config.SecurityConfig;
import com.schooldays.entities.auth.AuthenticatedUser;
import com.schooldays.entities.auth.TenantRole;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

class JwtTokenServiceTests {

    private static final String CURRENT_KEY_ID = "2026-07";
    private static final String OLD_KEY_ID = "2026-06";
    private static final String CURRENT_SECRET = "current-secret-value-with-at-least-32-characters";
    private static final String OLD_SECRET = "previous-secret-value-with-at-least-32-characters";

    @Test
    void decoderAcceptsTokensSignedWithCurrentAndPreviousKeys() {
        String currentToken = tokenSignedWith(CURRENT_KEY_ID);
        String oldToken = tokenSignedWith(OLD_KEY_ID);
        JwtDecoder decoder = decoderForKeyRing(CURRENT_KEY_ID);

        Jwt decodedCurrentToken = decoder.decode(currentToken);
        Jwt decodedOldToken = decoder.decode(oldToken);

        assertThat(decodedCurrentToken.getHeaders()).containsEntry("kid", CURRENT_KEY_ID);
        assertThat(decodedOldToken.getHeaders()).containsEntry("kid", OLD_KEY_ID);
        assertThat(decodedCurrentToken.getClaimAsString("email")).isEqualTo("parent@example.com");
        assertThat(decodedOldToken.getClaimAsString("email")).isEqualTo("parent@example.com");
    }

    private String tokenSignedWith(String currentKeyId) {
        JwtEncoder encoder = encoderForKeyRing(currentKeyId);
        JwtTokenService tokenService = new JwtTokenService(encoder, jwtKeyProperties(currentKeyId), "schooldays-test", 60);
        return tokenService.issueAccessToken(authenticatedUser()).accessToken();
    }

    private JwtEncoder encoderForKeyRing(String currentKeyId) {
        SecurityConfig securityConfig = new SecurityConfig();
        return securityConfig.jwtEncoder(securityConfig.jwtJwkSource(jwtKeyProperties(currentKeyId)));
    }

    private JwtDecoder decoderForKeyRing(String currentKeyId) {
        SecurityConfig securityConfig = new SecurityConfig();
        return securityConfig.jwtDecoder(securityConfig.jwtJwkSource(jwtKeyProperties(currentKeyId)));
    }

    private JwtKeyProperties jwtKeyProperties(String currentKeyId) {
        JwtKeyProperties properties = new JwtKeyProperties();
        properties.setCurrentKeyId(currentKeyId);
        properties.setKeys(Map.of(
                CURRENT_KEY_ID, CURRENT_SECRET,
                OLD_KEY_ID, OLD_SECRET
        ));
        return properties;
    }

    private AuthenticatedUser authenticatedUser() {
        UUID tenantId = UUID.randomUUID();
        return new AuthenticatedUser(
                UUID.randomUUID(),
                "parent@example.com",
                "555-0100",
                "hash",
                "active",
                List.of(new TenantRole(tenantId, "PARENT")),
                List.of(new SimpleGrantedAuthority("ROLE_PARENT"))
        );
    }
}
