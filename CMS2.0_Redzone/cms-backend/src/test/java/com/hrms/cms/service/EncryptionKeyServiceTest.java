package com.hrms.cms.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class EncryptionKeyServiceTest {

    private EncryptionKeyService service;

    @BeforeEach
    void setup() {
        service = new EncryptionKeyService();
        ReflectionTestUtils.setField(service, "serverSecret", "test-secret-for-unit-tests");
    }

    @Test
    @DisplayName("should derive a 256-bit key (32 bytes, base64 encoded)")
    void shouldDeriveCorrectLength() {
        String key = service.deriveSessionKey("session-123");
        byte[] keyBytes = Base64.getDecoder().decode(key);
        assertThat(keyBytes).hasSize(32);
    }

    @Test
    @DisplayName("same session ID should always produce the same key")
    void shouldBeDeterministic() {
        String key1 = service.deriveSessionKey("session-abc");
        String key2 = service.deriveSessionKey("session-abc");
        assertThat(key1).isEqualTo(key2);
    }

    @Test
    @DisplayName("different session IDs should produce different keys")
    void shouldProduceDifferentKeysForDifferentSessions() {
        String key1 = service.deriveSessionKey("session-1");
        String key2 = service.deriveSessionKey("session-2");
        assertThat(key1).isNotEqualTo(key2);
    }

    @Test
    @DisplayName("derived key should work with AES-256-GCM encryption/decryption")
    void shouldWorkForAesGcm() throws Exception {
        String keyBase64 = service.deriveSessionKey("session-test");
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);

        String plaintext = "Sensitive PII: John Doe";
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);

        Cipher encCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] ciphertext = encCipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        Cipher decCipher = Cipher.getInstance("AES/GCM/NoPadding");
        decCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] decrypted = decCipher.doFinal(ciphertext);

        assertThat(new String(decrypted, StandardCharsets.UTF_8)).isEqualTo(plaintext);
    }

    @Test
    @DisplayName("cross-session isolation: key from session A cannot decrypt session B data")
    void shouldIsolateSessionKeys() throws Exception {
        String keyA = service.deriveSessionKey("session-A");
        String keyB = service.deriveSessionKey("session-B");
        byte[] keyABytes = Base64.getDecoder().decode(keyA);
        byte[] keyBBytes = Base64.getDecoder().decode(keyB);

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        String plaintext = "Secret data";

        Cipher encCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyABytes, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] ciphertextFromA = encCipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        try {
            Cipher decCipher = Cipher.getInstance("AES/GCM/NoPadding");
            decCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBBytes, "AES"),
                    new GCMParameterSpec(128, iv));
            decCipher.doFinal(ciphertextFromA);
            assertThat(true).as("Should have thrown exception").isFalse();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(javax.crypto.AEADBadTagException.class);
        }
    }

    @Test
    @DisplayName("tampered ciphertext should fail decryption")
    void shouldRejectTamperedData() throws Exception {
        String keyBase64 = service.deriveSessionKey("session-tamper");
        byte[] keyBytes = Base64.getDecoder().decode(keyBase64);

        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        String plaintext = "Original data";

        Cipher encCipher = Cipher.getInstance("AES/GCM/NoPadding");
        encCipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                new GCMParameterSpec(128, iv));
        byte[] ciphertext = encCipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

        ciphertext[5] ^= 0xFF;

        try {
            Cipher decCipher = Cipher.getInstance("AES/GCM/NoPadding");
            decCipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new GCMParameterSpec(128, iv));
            decCipher.doFinal(ciphertext);
            assertThat(true).as("Should have thrown exception").isFalse();
        } catch (Exception e) {
            assertThat(e).isInstanceOf(javax.crypto.AEADBadTagException.class);
        }
    }
}
