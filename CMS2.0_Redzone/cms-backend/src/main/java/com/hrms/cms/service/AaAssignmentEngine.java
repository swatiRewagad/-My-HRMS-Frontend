package com.hrms.cms.service;

import com.hrms.cms.dto.AaAssignmentRequest;
import com.hrms.cms.dto.AaAssignmentResult;

/**
 * The single entry point for placing AA work with an officer.
 *
 * Published as an interface ahead of its implementation so the parallel AA sessions can compile
 * against a stable seam: S2A calls it when registering an appeal, S2B calls it when an email or
 * scanned-letter draft is created.
 *
 * Callers must treat an unassigned result as a normal outcome, not an error. A pool can legitimately
 * be empty or exhausted, and the previous implementation's habit of silently assigning to the
 * least-loaded officer anyway meant a threshold breach looked identical to a healthy placement.
 */
public interface AaAssignmentEngine {

    /**
     * Places the record described by {@code request} with an eligible officer.
     *
     * Eligibility is evaluated in real time against the live pool: an officer who is inactive, on
     * leave, or already at their configured threshold is excluded. Selection is lowest current
     * workload first, with the persisted round-robin pointer as a deterministic tie-break that
     * survives a restart.
     *
     * Never returns null. Never throws for an exhausted or empty pool.
     */
    AaAssignmentResult assign(AaAssignmentRequest request);

    /**
     * Places {@code appealNumber} with {@code targetUserId} by AA Admin fiat, bypassing the
     * threshold. Requires a non-blank reason, which is written to the audit trail with the acting
     * admin and a timestamp.
     *
     * The target must still be an active, not-on-leave member of the pool: an admin may overload an
     * officer deliberately, but may not assign work to someone who cannot receive it.
     *
     * @throws IllegalArgumentException if the reason is blank or the target is not assignable
     */
    AaAssignmentResult assignManually(String appealNumber, String targetUserId, String reason);

    /**
     * Live count of records currently assigned to {@code userId} and still awaiting their action.
     *
     * Computed from the appeals table, not read from WF_OFFICER_POOL.CURRENT_WORKLOAD, because that
     * stored counter is maintained by a different service and demonstrably drifts.
     */
    int currentWorkload(String userId);
}
