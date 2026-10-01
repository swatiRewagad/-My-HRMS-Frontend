package com.hrms.cms.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * What the caller asks the AA assignment engine to place.
 *
 * Deliberately does NOT carry an actor or a role: those are resolved server-side from the JWT by
 * AaIdentityResolver. Accepting them here would reintroduce the spoofing bug S1 closed.
 *
 * It also does not carry a target officer for ordinary assignment. Only the explicit manual-override
 * entry point takes a target, so a normal caller cannot steer work at a chosen officer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaAssignmentRequest {

    /** Appeal (or draft) number being assigned. Required. */
    private String appealNumber;

    /**
     * Pool to draw from, e.g. "AA_DO". Required. Maps to WF_OFFICER_POOL.ROLE_GROUP, which is also
     * the key the persisted round-robin pointer is held against.
     */
    private String roleGroup;

    /** Optional regional narrowing. Null means draw nationally. */
    private String regionalOffice;

    /**
     * Language required to handle this record, as an ISO code (e.g. "bn").
     *
     * Set by the caller that already knows the record's language — S2B stamps this on email/OCR
     * drafts. The engine does NOT re-detect language. When present and a skilled officer exists, the
     * engine bypasses round robin and routes directly to that officer, and the placement does not
     * count against the officer's threshold (story 8).
     */
    private String requiredLanguage;
}
