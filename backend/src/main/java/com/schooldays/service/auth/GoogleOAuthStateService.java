package com.schooldays.service.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Base64;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.schooldays.entities.auth.GoogleOAuthState;
import com.schooldays.config.JwtKeyProperties;
import org.springframework.stereotype.Service;

@Service
public class GoogleOAuthStateService {

    private static final long STATE_TTL_SECONDS = 600;

    private final JwtKeyProperties jwtKeyProperties;
    private final SecureTokenGenerator tokenGenerator;
    private final Clock clock;

    public GoogleOAuthStateService(
            JwtKeyProperties jwtKeyProperties,
            SecureTokenGenerator tokenGenerator
    ) {
        this.jwtKeyProperties = jwtKeyProperties;
        this.tokenGenerator = tokenGenerator;
        this.clock = Clock.systemUTC();
    }

    public String issue(UUID tenantId) {
        long expiresAtEpochSecond = clock.instant().plusSeconds(STATE_TTL_SECONDS).getEpochSecond();
        String payload = tenantId + ":" + expiresAtEpochSecond + ":" + tokenGenerator.newToken();
        String encodedPayload = base64Url(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public GoogleOAuthState verify(String state) {
        String[] parts = state == null ? new String[0] : state.split("\\.", -1);
        if (parts.length != 2 || !sign(parts[0]).equals(parts[1])) {
            throw new InvalidAuthRequestException("Google login state is invalid");
        }

        String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String[] values = payload.split(":", -1);
        if (values.length != 3) {
            throw new InvalidAuthRequestException("Google login state is invalid");
        }

        try {
            long expiresAtEpochSecond = Long.parseLong(values[1]);
            if (clock.instant().getEpochSecond() > expiresAtEpochSecond) {
                throw new InvalidAuthRequestException("Google login state is expired");
            }
            return new GoogleOAuthState(UUID.fromString(values[0]));
        } catch (IllegalArgumentException exception) {
            throw new InvalidAuthRequestException("Google login state is invalid");
        }
    }

    private String sign(String encodedPayload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtKeyProperties.currentSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return base64Url(mac.doFinal(encodedPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign Google OAuth state", exception);
        }
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
