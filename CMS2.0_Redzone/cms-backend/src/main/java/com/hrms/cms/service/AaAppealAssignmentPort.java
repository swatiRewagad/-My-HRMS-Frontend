package com.hrms.cms.service;

import java.util.Optional;

/**
 * Outbound port for assigning a newly registered appeal to an AA Dealing Officer (stories 17, 19).
 *
 * WHY AN INTERFACE AND NOT AN IMPLEMENTATION: this session does not own round-robin assignment and
 * must not write one. The state of the world today:
 *
 *   - cms-backend's AppealWorkflowService.assignByRole (:561-574) round-robins over Keycloak realm
 *     role membership using an in-memory ConcurrentHashMap. It resets on restart and diverges per pod,
 *     so two instances hand the same officer consecutive appeals.
 *   - cms-backend's RoundRobinPointer entity — the durable counter that would fix that — is dead code
 *     with zero references anywhere in the repository.
 *   - cms-workflow-service has the real engine (RoundRobinAssignmentService over WF_OFFICER_POOL +
 *     WF_ASSIGNMENT_COUNTER, workload-aware, pessimistically locked counter), but assignNext() is
 *     reachable only in-process: AssignmentController exposes pool CRUD and release, deliberately NOT
 *     assign. There is no HTTP endpoint that returns "the next AA DO" today. WF_OFFICER_POOL also
 *     contains no AA_* role group, so that engine would return null for AA even if it were reachable.
 *   - cms-assignment-service (8085) is a stub whose reassign() is an empty log statement.
 *
 * So the integration is defined here and called by the register flow, and the transport is stubbed
 * until S2C exposes the engine. Assignment failure must NOT fail registration: an appeal that exists
 * unassigned can be picked up, whereas a citizen's appeal rejected because an assignment service was
 * down has lost a statutory filing. Hence Optional rather than an exception.
 */
public interface AaAppealAssignmentPort {

    /**
     * The next AA Dealing Officer for a new appeal, or empty when none can be determined.
     *
     * @param appealNumber the appeal being assigned, for the engine's audit trail
     * @return the assigned officer's user id, or empty when no officer pool is available
     */
    Optional<String> assignDealingOfficer(String appealNumber);
}
