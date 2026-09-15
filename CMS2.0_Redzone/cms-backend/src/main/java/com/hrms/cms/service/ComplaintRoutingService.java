package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintRoutingService {

    private final RegulatedEntityRepository regulatedEntityRepo;
    private final KeycloakUserService keycloakUserService;

    private final ConcurrentHashMap<String, AtomicInteger> roundRobinCounters = new ConcurrentHashMap<>();

    public String assignOfficerByRole(String role) {
        List<Map<String, Object>> officers = keycloakUserService.getUsersByRole(role);
        if (officers.isEmpty()) {
            log.warn("No officers found in Keycloak for role: {}, using default team name", role);
            return role.replace("_", " ") + " Team";
        }

        AtomicInteger counter = roundRobinCounters.computeIfAbsent(role, k -> new AtomicInteger(0));
        int index = Math.abs(counter.getAndIncrement()) % officers.size();
        String assignedOfficer = (String) officers.get(index).get("userId");

        log.info("Round-robin assigned role={} to officer={} (index {} of {})", role, assignedOfficer, index, officers.size());
        return assignedOfficer;
    }

    public RoutingDecision routeComplaint(Complaint complaint, String entityCode) {
        String filingType = complaint.getFilingType();

        if ("EMAIL".equals(filingType) || "PHYSICAL_LETTER".equals(filingType)) {
            return routeFromCrpc(complaint, entityCode);
        }

        return routeFromPublicPortal(complaint);
    }

    private RoutingDecision routeFromPublicPortal(Complaint complaint) {
        String assignedRole = "RBIO_OFFICER";
        String assignedOfficer = assignOfficerByRole(assignedRole);

        log.info("Public portal complaint {} routed to RBIO, assigned to {}", complaint.getComplaintNumber(), assignedOfficer);

        return RoutingDecision.builder()
                .department("RBIO")
                .assignedRole(assignedRole)
                .assignedOfficer(assignedOfficer)
                .stage("INITIAL_REVIEW")
                .reason("Public portal filing - round-robin assigned to " + assignedOfficer)
                .build();
    }

    private RoutingDecision routeFromCrpc(Complaint complaint, String entityCode) {
        String assignedRole = "DEO";
        String assignedOfficer = assignOfficerByRole(assignedRole);

        log.info("Email/Physical complaint {} routed to CRPC, assigned to DEO {}",
                complaint.getComplaintNumber(), assignedOfficer);

        return RoutingDecision.builder()
                .department("CRPC")
                .assignedRole(assignedRole)
                .assignedOfficer(assignedOfficer)
                .stage("DATA_ENTRY")
                .targetDepartment(resolveDepartment(entityCode))
                .reason("Email/Physical letter - round-robin assigned to DEO " + assignedOfficer)
                .build();
    }

    public RoutingDecision routeFromCrpcToApproval(Complaint complaint, String entityCode) {
        String targetDept = resolveDepartment(entityCode);
        String targetRole = "RBIO".equals(targetDept) ? "RBIO_OFFICER" : "CEPC_OFFICER";

        log.info("CRPC forwarding complaint {} to {} for approval (entity: {})",
                complaint.getComplaintNumber(), targetDept, entityCode);

        return RoutingDecision.builder()
                .department(targetDept)
                .assignedRole(targetRole)
                .stage("APPROVAL_REVIEW")
                .reason("CRPC completed processing - forwarded to " + targetDept + " based on entity " + entityCode)
                .build();
    }

    public RoutingDecision transferBetweenDepartments(Complaint complaint, String fromDepartment,
                                                       String toDepartment, String reason) {
        String targetRole = "RBIO".equals(toDepartment) ? "RBIO_OFFICER" : "CEPC_OFFICER";

        log.info("Inter-department transfer: complaint {} from {} to {} (reason: {})",
                complaint.getComplaintNumber(), fromDepartment, toDepartment, reason);

        return RoutingDecision.builder()
                .department(toDepartment)
                .assignedRole(targetRole)
                .stage("TRANSFERRED")
                .previousDepartment(fromDepartment)
                .reason("Transferred from " + fromDepartment + ": " + reason)
                .build();
    }

    /**
     * Resolves department by entity name using the official RBI regulated entity lists.
     * Performs exact match first, then fuzzy (contains) match.
     * Falls back to RBIO if entity not found in either list.
     */
    public String resolveDepartment(String entityName) {
        if (entityName == null || entityName.isBlank()) return "RBIO";

        String normalized = RegulatedEntity.normalize(entityName);

        // Exact match on normalized name
        Optional<RegulatedEntity> exact = regulatedEntityRepo.findByNameNormalized(normalized);
        if (exact.isPresent()) {
            log.debug("Entity '{}' exact match → {}", entityName, exact.get().getDepartment());
            return exact.get().getDepartment();
        }

        // Fuzzy match (contains)
        List<RegulatedEntity> matches = regulatedEntityRepo.searchByNormalizedName(normalized);
        if (!matches.isEmpty()) {
            String dept = matches.get(0).getDepartment();
            log.debug("Entity '{}' fuzzy match ({} results) → {}", entityName, matches.size(), dept);
            return dept;
        }

        log.info("Entity '{}' not found in regulated entity lists, defaulting to RBIO", entityName);
        return "RBIO";
    }

    /**
     * Resolves department by entity name and returns full details including match info.
     */
    public EntityRoutingResult resolveEntityRouting(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return EntityRoutingResult.builder()
                    .department("RBIO")
                    .matchType("DEFAULT")
                    .reason("No entity name provided — default routing to RBIO")
                    .build();
        }

        String normalized = RegulatedEntity.normalize(entityName);

        Optional<RegulatedEntity> exact = regulatedEntityRepo.findByNameNormalized(normalized);
        if (exact.isPresent()) {
            RegulatedEntity e = exact.get();
            return EntityRoutingResult.builder()
                    .department(e.getDepartment())
                    .matchedEntityName(e.getName())
                    .entityType(e.getEntityType())
                    .matchType("EXACT")
                    .reason("Exact match found in " + e.getDepartment() + " entity list")
                    .build();
        }

        List<RegulatedEntity> fuzzy = regulatedEntityRepo.searchByNormalizedName(normalized);
        if (!fuzzy.isEmpty()) {
            RegulatedEntity e = fuzzy.get(0);
            return EntityRoutingResult.builder()
                    .department(e.getDepartment())
                    .matchedEntityName(e.getName())
                    .entityType(e.getEntityType())
                    .matchType("FUZZY")
                    .matchCount(fuzzy.size())
                    .reason("Partial match found in " + e.getDepartment() + " entity list (" + fuzzy.size() + " matches)")
                    .build();
        }

        return EntityRoutingResult.builder()
                .department("RBIO")
                .matchType("NOT_FOUND")
                .reason("Entity not found in CEPC or RBIO lists — default routing to RBIO")
                .build();
    }

    public Map<String, Object> getEntityDepartmentMapping() {
        long cepcCount = regulatedEntityRepo.countByDepartment("CEPC");
        long rbioCount = regulatedEntityRepo.countByDepartment("RBIO");

        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("CEPC_count", cepcCount);
        mapping.put("RBIO_count", rbioCount);
        mapping.put("total", cepcCount + rbioCount);
        mapping.put("source", "RBI Official Entity Lists (CEPC_English_Portal + RBIO_English_Portal)");
        return mapping;
    }

    public List<Map<String, String>> getRoutingRulesSummary() {
        return List.of(
                Map.of("source", "Public Portal (WEB_PORTAL)", "initialRoute", "RBIO", "flow", "Direct to RBIO officer"),
                Map.of("source", "Email (EMAIL)", "initialRoute", "CRPC", "flow", "DEO → Reviewer → RBIO or CEPC (based on entity list match)"),
                Map.of("source", "Physical Letter (PHYSICAL_LETTER)", "initialRoute", "CRPC", "flow", "DEO → Reviewer → RBIO or CEPC (based on entity list match)"),
                Map.of("source", "CEPC ↔ RBIO Transfer", "initialRoute", "Target Department", "flow", "Officer can transfer between departments")
        );
    }

    @lombok.Builder
    @lombok.Getter
    public static class RoutingDecision {
        private String department;
        private String assignedRole;
        private String assignedOfficer;
        private String stage;
        private String targetDepartment;
        private String previousDepartment;
        private String reason;
    }

    @lombok.Builder
    @lombok.Getter
    public static class EntityRoutingResult {
        private String department;
        private String matchedEntityName;
        private String entityType;
        private String matchType;
        private int matchCount;
        private String reason;
    }
}
