package com.schooldays.service.notification;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import com.schooldays.config.JwtKeyProperties;
import org.springframework.stereotype.Component;

@Component
public class TokenCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final JwtKeyProperties jwtKeyProperties;

    public TokenCipher(JwtKeyProperties jwtKeyProperties) {
        this.jwtKeyProperties = jwtKeyProperties;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer.array());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encrypt Gmail token", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encryptedValue);
            byte[] iv = Arrays.copyOfRange(bytes, 0, IV_BYTES);
            byte[] encrypted = Arrays.copyOfRange(bytes, IV_BYTES, bytes.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to decrypt Gmail token", exception);
        }
    }

    private SecretKeySpec key() throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(jwtKeyProperties.currentSecret().getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(digest, "AES");
    }
}
