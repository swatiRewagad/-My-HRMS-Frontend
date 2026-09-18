package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ComplaintRoutingService {

    private final RegulatedEntityRepository regulatedEntityRepo;
    private final DurableRoundRobinAssigner roundRobinAssigner;

    /**
     * Picks the next officer for a role, or returns null when nobody is available.
     *
     * <p>Delegates to {@link DurableRoundRobinAssigner} (UST471/UST450). The previous implementation
     * kept a {@code ConcurrentHashMap<String,AtomicInteger>} on this bean, which meant the rotation
     * position was per-JVM: every pod started at officer one, and a restart reset it. It also applied
     * no active/on-leave filter, so UST450 was unimplemented on this path.
     *
     * <p><b>Returns null rather than a placeholder.</b> This used to synthesise
     * {@code role.replace("_"," ") + " Team"} — literally {@code "RBIO OFFICER Team"} — and that string
     * was written into {@code Complaint.assignedOfficer}. A complaint then looked assigned to a person
     * who does not exist, no dashboard could distinguish it from a real assignment, and the officer who
     * should have picked it up never saw it. A null officer is honest and is already tolerated by the
     * persistence path: {@code routeFromCrpcToApproval} and {@code transferBetweenDepartments} have
     * always returned a decision with no officer.
     */
    public String assignOfficerByRole(String role) {
        DurableRoundRobinAssigner.Assignment assignment = roundRobinAssigner.assignNext(role);
        if (!assignment.isAssigned()) {
            log.warn("No officer assigned for role {}: {}", role, assignment.reason());
            return null;
        }
        return assignment.officerId();
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

        log.info("Public portal complaint {} routed to RBIO, assigned to {}",
                complaint.getComplaintNumber(), assignedOfficer != null ? assignedOfficer : "nobody (unassigned)");

        return RoutingDecision.builder()
                .department("RBIO")
                .assignedRole(assignedRole)
                .assignedOfficer(assignedOfficer)
                .stage("INITIAL_REVIEW")
                .reason(assignmentReason("Public portal filing", assignedRole, assignedOfficer))
                .build();
    }

    private RoutingDecision routeFromCrpc(Complaint complaint, String entityCode) {
        String assignedRole = "DEO";
        String assignedOfficer = assignOfficerByRole(assignedRole);

        log.info("Email/Physical complaint {} routed to CRPC, assigned to DEO {}",
                complaint.getComplaintNumber(), assignedOfficer != null ? assignedOfficer : "nobody (unassigned)");

        return RoutingDecision.builder()
                .department("CRPC")
                .assignedRole(assignedRole)
                .assignedOfficer(assignedOfficer)
                .stage("DATA_ENTRY")
                .targetDepartment(resolveDepartment(entityCode))
                .reason(assignmentReason("Email/Physical letter", assignedRole, assignedOfficer))
                .build();
    }

    /**
     * The human-readable reason recorded on the complaint timeline.
     *
     * <p>Spells out an unassigned outcome instead of interpolating "null" into the narrative, so the
     * timeline says why nobody holds the complaint and the role that still owes it work.
     */
    private String assignmentReason(String source, String assignedRole, String assignedOfficer) {
        if (assignedOfficer == null) {
            return source + " - awaiting assignment: no active " + assignedRole + " available";
        }
        return source + " - round-robin assigned to " + assignedOfficer;
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
