package com.rbi.cms.common.crypto;

/**
 * Field/blob-level encryption for PII at rest. Deliberately independent of cms-backend's
 * EncryptionKeyService (com.hrms.cms.service) — that class lives in a separate, non-reactor Maven
 * project on a different package namespace and isn't reachable from modules in this reactor
 * without either pulling cms-backend into the build or calling it over HTTP from a hot path,
 * neither of which is justified for this. Tracked as tech debt: unify the two if/when cms-backend
 * joins the reactor.
 */
public interface PayloadEncryptionService {

    /** Returns an opaque, self-describing blob (algorithm version + nonce + ciphertext+tag) safe
     *  to write directly to the raw message / attachment store. */
    byte[] encrypt(byte[] plaintext);

    /** Inverse of {@link #encrypt(byte[])}. Throws if the blob is truncated, the tag doesn't
     *  verify, or the version byte is unrecognised. */
    byte[] decrypt(byte[] blob);
}
