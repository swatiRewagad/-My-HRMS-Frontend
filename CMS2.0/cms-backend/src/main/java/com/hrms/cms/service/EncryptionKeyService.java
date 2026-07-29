package com.hrms.cms.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Slf4j
@Service
public class EncryptionKeyService {

    @Value("${cms.encryption.server-secret:}")
    private String serverSecret;

    @PostConstruct
    void validateSecret() {
        if (serverSecret == null || serverSecret.isBlank()) {
            throw new IllegalStateException(
                    "CMS_ENCRYPTION_SECRET must be set. Cannot start without PII encryption key.");
        }
        if (serverSecret.length() < 16) {
            throw new IllegalStateException(
                    "CMS_ENCRYPTION_SECRET must be at least 16 characters.");
        }
    }

    private static final String HKDF_ALGORITHM = "HmacSHA256";
    private static final String INFO_CONTEXT = "cms-pii-encryption-v1";

    public String deriveSessionKey(String sessionId) {
        try {
            byte[] prk = hkdfExtract(serverSecret.getBytes(StandardCharsets.UTF_8),
                    sessionId.getBytes(StandardCharsets.UTF_8));
            byte[] okm = hkdfExpand(prk, INFO_CONTEXT.getBytes(StandardCharsets.UTF_8), 32);
            return Base64.getEncoder().encodeToString(okm);
        } catch (Exception e) {
            log.error("Failed to derive session encryption key", e);
            throw new RuntimeException("Key derivation failed", e);
        }
    }

    public boolean isValidSessionForKey(String sessionId, String providedKeyBase64) {
        String expectedKey = deriveSessionKey(sessionId);
        return expectedKey.equals(providedKeyBase64);
    }

    private byte[] hkdfExtract(byte[] salt, byte[] inputKeyMaterial)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HKDF_ALGORITHM);
        mac.init(new SecretKeySpec(salt, HKDF_ALGORITHM));
        return mac.doFinal(inputKeyMaterial);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int outputLength)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Mac mac = Mac.getInstance(HKDF_ALGORITHM);
        mac.init(new SecretKeySpec(prk, HKDF_ALGORITHM));

        byte[] output = new byte[outputLength];
        byte[] t = new byte[0];
        int offset = 0;
        byte counter = 1;

        while (offset < outputLength) {
            mac.reset();
            mac.update(t);
            mac.update(info);
            mac.update(counter);
            t = mac.doFinal();

            int toCopy = Math.min(t.length, outputLength - offset);
            System.arraycopy(t, 0, output, offset, toCopy);
            offset += toCopy;
            counter++;
        }

        return output;
    }
}
