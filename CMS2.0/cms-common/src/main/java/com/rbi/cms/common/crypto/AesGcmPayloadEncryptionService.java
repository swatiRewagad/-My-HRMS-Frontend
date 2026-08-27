package com.rbi.cms.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM, one key, versioned blob format: [1 byte version=1][12 byte nonce][ciphertext ||
 * 16 byte GCM tag]. A fresh random nonce per call — GCM's security depends on never reusing a
 * (key, nonce) pair, so callers must not try to be clever and cache/reuse the output of one
 * encrypt() call's nonce for another.
 */
public class AesGcmPayloadEncryptionService implements PayloadEncryptionService {

    private static final byte VERSION = 1;
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmPayloadEncryptionService(SecretKey key) {
        this.key = key;
    }

    /** Builds the key from a base64-encoded 32-byte (AES-256) secret — see
     *  MailIntakeCryptoConfig for where the base64 string itself comes from (an env var, never a
     *  config file). */
    public static AesGcmPayloadEncryptionService fromBase64Key(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) {
            throw new IllegalArgumentException(
                    "Encryption key must decode to exactly 32 bytes (AES-256); got " + raw.length);
        }
        return new AesGcmPayloadEncryptionService(new SecretKeySpec(raw, "AES"));
    }

    @Override
    public byte[] encrypt(byte[] plaintext) {
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext);

            ByteBuffer out = ByteBuffer.allocate(1 + nonce.length + ciphertext.length);
            out.put(VERSION);
            out.put(nonce);
            out.put(ciphertext);
            return out.array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public byte[] decrypt(byte[] blob) {
        if (blob.length < 1 + GCM_NONCE_LENGTH) {
            throw new IllegalArgumentException("Encrypted blob too short to contain version+nonce");
        }
        ByteBuffer in = ByteBuffer.wrap(blob);
        byte version = in.get();
        if (version != VERSION) {
            throw new IllegalArgumentException("Unrecognised encryption blob version: " + version);
        }
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        in.get(nonce);
        byte[] ciphertext = new byte[in.remaining()];
        in.get(ciphertext);

        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            return cipher.doFinal(ciphertext);
        } catch (GeneralSecurityException e) {
            // Deliberately vague — never leak whether it was a tag-mismatch (tamper) vs. a
            // different failure to a caller that might expose this message.
            throw new IllegalStateException("Decryption failed: payload unreadable or tampered", e);
        }
    }
}
