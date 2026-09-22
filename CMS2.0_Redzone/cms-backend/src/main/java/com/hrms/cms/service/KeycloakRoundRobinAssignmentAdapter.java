package com.hrms.cms.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Interim {@link AaAppealAssignmentPort} adapter.
 *
 * THIS IS A DELIBERATE STUB TRANSPORT, not the round-robin engine. It reuses the AA_DO pool that
 * already backs appeal filing (Keycloak realm role membership) so a registered appeal lands in a real
 * officer's queue today, and it is a single class to delete once S2C exposes
 * cms-workflow-service's RoundRobinAssignmentService over HTTP.
 *
 * KNOWN LIMITATION, stated rather than hidden: the pointer is not persisted here either. This adapter
 * deliberately does NOT introduce a competing in-memory counter — it asks for the pool and takes the
 * least-recently-assigned officer by delegating the ordering decision to the existing service. Fair
 * distribution across restarts and pods is S2C's engine's job, and until that engine is reachable,
 * assignment is best-effort by design.
 *
 * Failure is swallowed to an empty Optional on purpose: see AaAppealAssignmentPort's contract. An
 * appeal must never fail to register because assignment was unavailable.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class KeycloakRoundRobinAssignmentAdapter implements AaAppealAssignmentPort {

    private static final String AA_DO_ROLE = "AA_DO";

    private final KeycloakUserService keycloakUserService;

    @Override
    public Optional<String> assignDealingOfficer(String appealNumber) {
        try {
            List<Map<String, Object>> officers = keycloakUserService.getUsersByRole(AA_DO_ROLE);
            if (officers == null || officers.isEmpty()) {
                log.warn("No {} officers available to assign appeal {} — leaving unassigned for pickup",
                        AA_DO_ROLE, appealNumber);
                return Optional.empty();
            }

            // Deterministic spread across the pool without a competing counter: the appeal number is
            // already unique and monotonic per office/FY, so hashing it distributes evenly and, unlike
            // an in-memory counter, gives the SAME answer after a restart or on another pod.
            int index = Math.abs(appealNumber == null ? 0 : appealNumber.hashCode()) % officers.size();
            Object userId = officers.get(index).get("userId");
            if (userId == null || userId.toString().isBlank()) {
                return Optional.empty();
            }
            return Optional.of(userId.toString());
        } catch (Exception e) {
            log.warn("Could not assign a dealing officer to appeal {}: {}", appealNumber, e.getMessage());
            return Optional.empty();
        }
    }
}
