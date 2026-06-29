package com.schooldays.config;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schooldays.security.jwt")
public class JwtKeyProperties {

    private String currentKeyId;
    private Map<String, String> keys = new LinkedHashMap<>();

    public String getCurrentKeyId() {
        return currentKeyId;
    }

    public void setCurrentKeyId(String currentKeyId) {
        this.currentKeyId = currentKeyId;
    }

    public Map<String, String> getKeys() {
        return keys;
    }

    public void setKeys(Map<String, String> keys) {
        this.keys = keys;
    }

    public String currentSecret() {
        validate();
        return keys.get(currentKeyId);
    }

    public SecretKey currentSecretKey() {
        return secretKey(currentSecret());
    }

    public Map<String, SecretKey> secretKeys() {
        validate();
        Map<String, SecretKey> secretKeys = new LinkedHashMap<>();
        keys.forEach((keyId, secret) -> secretKeys.put(keyId, secretKey(secret)));
        return secretKeys;
    }

    public void validate() {
        if (currentKeyId == null || currentKeyId.isBlank()) {
            throw new IllegalStateException("schooldays.security.jwt.current-key-id must be configured");
        }
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("schooldays.security.jwt.keys must contain at least one key");
        }
        if (!keys.containsKey(currentKeyId)) {
            throw new IllegalStateException("schooldays.security.jwt.current-key-id must exist in schooldays.security.jwt.keys");
        }
        keys.forEach((keyId, secret) -> {
            if (keyId == null || keyId.isBlank()) {
                throw new IllegalStateException("JWT key ids must not be blank");
            }
            if (secret == null || secret.length() < 32) {
                throw new IllegalStateException("JWT secret for key '" + keyId + "' must be at least 32 characters");
            }
        });
    }

    private SecretKey secretKey(String secret) {
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
