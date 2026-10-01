package com.hrms.cms.controller;

import com.hrms.cms.security.AaIdentityResolver;
import com.hrms.cms.security.AaRoleGuard;
import com.hrms.cms.service.AaEscalationSweepService;
import com.hrms.cms.service.AaReassignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Story 11: AA reassignment requests, and the claim/escalation endpoints that support story 12.
 *
 * Mounted under a distinct path from the RE reassignment API (/api/v1/re-portal/reassignment) because
 * this is a fork, not a shared surface -- the two have different scoping rules and different guards.
 */
@RestController
@RequestMapping("/api/v1/aa/reassignment")
@RequiredArgsConstructor
public class AaReassignmentController {

    private final AaReassignmentService reassignmentService;
    private final AaEscalationSweepService escalationSweepService;
    private final AaIdentityResolver identityResolver;

    /** Any AA officer holding a record may ask for it to be moved. */
    @PostMapping("/request")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> raise(@RequestBody Map<String, String> body) {
        return ResponseEntity.ok(reassignmentService.raise(
                body.get("appealNumber"), body.get("toUserId"), body.get("reason")));
    }

    /** The requester's own requests and their outcomes. */
    @GetMapping("/my-requests")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN", "ADMIN"})
    public ResponseEntity<List<Map<String, Object>>> myRequests() {
        return ResponseEntity.ok(reassignmentService.myRequests());
    }

    /** The approval queue. Only an admin decides, so only an admin may see it. */
    @GetMapping("/pending")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<List<Map<String, Object>>> pending() {
        return ResponseEntity.ok(reassignmentService.pendingQueue());
    }

    @PostMapping("/{requestId}/approve")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> approve(@PathVariable Long requestId,
                                                       @RequestBody(required = false)
                                                       Map<String, String> body) {
        return ResponseEntity.ok(reassignmentService.decide(requestId, true,
                body == null ? null : body.get("comment")));
    }

    @PostMapping("/{requestId}/reject")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> reject(@PathVariable Long requestId,
                                                      @RequestBody(required = false)
                                                      Map<String, String> body) {
        return ResponseEntity.ok(reassignmentService.decide(requestId, false,
                body == null ? null : body.get("comment")));
    }

    @PostMapping("/{requestId}/withdraw")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> withdraw(@PathVariable Long requestId) {
        return ResponseEntity.ok(reassignmentService.withdraw(requestId));
    }

    /**
     * Marks a draft as picked up, which stops the escalation clock.
     *
     * The claiming user is resolved from the JWT, never from the request body. Taking it from the body
     * let any AA officer silence another officer's escalation clock by naming them — the same
     * self-declared-identity flaw that was closed across the rest of the AA module.
     */
    @PostMapping("/claim")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> claim(@RequestBody Map<String, String> body) {
        String appealNumber = body.get("appealNumber");
        String actor = identityResolver.resolveActor();
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException("aa.reassign.error_identity_unresolved");
        }
        boolean claimed = escalationSweepService.markClaimed(appealNumber, actor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("appealNumber", appealNumber);
        result.put("claimed", claimed);
        result.put("messageKey", claimed ? "aa.escalation.claimed" : "aa.escalation.claim_not_holder");
        return ResponseEntity.ok(result);
    }

    /** Runs the sweep on demand, so an admin need not wait for the next tick. */
    @PostMapping("/escalation-sweep")
    @AaRoleGuard(roles = {"AA_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> sweep() {
        int escalated = escalationSweepService.sweepUnclaimed();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("escalated", escalated);
        result.put("messageKey", "aa.escalation.sweep_complete");
        return ResponseEntity.ok(result);
    }
}
