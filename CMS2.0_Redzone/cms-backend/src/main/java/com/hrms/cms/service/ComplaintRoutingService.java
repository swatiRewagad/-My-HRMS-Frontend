package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.service.mre.MreEntityCoverageService;
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
    private final MreEntityCoverageService entityCoverageService;

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

        return routeFromPublicPortal(complaint, entityCode);
    }

    /**
     * Routes a public-portal filing to the department that may lawfully handle it.
     *
     * <p>The department comes from the Scheme-coverage determination (UST473). This method previously
     * hardcoded {@code "RBIO"} and ignored the entity entirely, which was the LAST of the four
     * default-to-RBIO paths and the only one on the citizen filing path: however carefully coverage was
     * determined upstream, the decision was discarded here and every portal complaint became an RBIO one.
     *
     * <p>A CEPC complaint also needs a CEPC officer rather than an RBIO one — assigning an RBIO officer to
     * a complaint the Ombudsman cannot hear would leave it sitting with somebody unable to act on it.
     */
    private RoutingDecision routeFromPublicPortal(Complaint complaint, String entityCode) {
        MreEntityCoverageService.Coverage coverage = resolveCoverage(entityCode);
        String department = coverage.department();
        boolean rbio = !MreEntityCoverageService.DEPARTMENT_CEPC.equals(department);
        String assignedRole = rbio ? "RBIO_OFFICER" : "CEPC_DO";

        // An RBIO complaint's officer is chosen by its OFFICE, not here (UST468-472), and that decision
        // needs the office — which is only known after office routing, later in filing. Picking an officer
        // here as well would advance the durable rotation pointer TWICE per complaint, so every filing
        // would consume two turns and half the rota would be skipped. Observed exactly that: four filings
        // used three officers and missed two entirely.
        //
        // CEPC has no per-office strategy, so its officer is still chosen here.
        String assignedOfficer = rbio ? null : assignOfficerByRole(assignedRole);

        log.info("Public portal complaint {} routed to {} ({}); officer {}",
                complaint.getComplaintNumber(), department, coverage.status(),
                rbio ? "to be chosen by the office's assignment strategy"
                     : (assignedOfficer != null ? assignedOfficer : "nobody (unassigned)"));

        return RoutingDecision.builder()
                .department(department)
                .assignedRole(assignedRole)
                .assignedOfficer(assignedOfficer)
                .stage("INITIAL_REVIEW")
                .reason("Public portal filing to " + department + "; " + coverage.reason())
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
        return resolveCoverage(entityName).department();
    }

    /**
     * The Scheme-coverage determination behind the department (UST473).
     *
     * <p>Delegates to {@link MreEntityCoverageService} so there is ONE coverage authority rather than two
     * implementations that can disagree. This method used to make its own decision, and it got two things
     * wrong that mattered:
     *
     * <ul>
     *   <li>It took {@code matches.get(0)} from an unordered partial match. Against the live data "HDFC"
     *       matches HDFC Credila (CEPC) and two HDFC Bank rows (RBIO), with the CEPC one returned first —
     *       so a complaint against a Scheme-covered bank was routed to CEPC by row order.</li>
     *   <li>It returned "RBIO" for a blank name and for an unknown entity. That is default-ALLOW on a
     *       maintainability determination: an entity nobody regulates was admitted to the Ombudsman
     *       Scheme, and UST473 requires the opposite direction.</li>
     * </ul>
     *
     * <p>An ambiguous or unknown entity now resolves to CEPC, which is the fail-closed direction: CEPC
     * handles complaints outside the Scheme and can escalate one in, whereas RBIO issuing a determination
     * on an entity it has no jurisdiction over cannot be undone from the citizen's side. The
     * {@code needsReview} flag on the result is what callers should surface so a human confirms the
     * entity rather than the routing being silently accepted.
     */
    public MreEntityCoverageService.Coverage resolveCoverage(String entityName) {
        MreEntityCoverageService.Coverage coverage = entityCoverageService.resolveCoverage(entityName);

        if (coverage.needsReview()) {
            log.info("Entity '{}' coverage is {} — routing to CEPC pending confirmation: {}",
                    entityName, coverage.status(), coverage.reason());
            return new MreEntityCoverageService.Coverage(
                    coverage.status(),
                    MreEntityCoverageService.DEPARTMENT_CEPC,
                    coverage.matchedName(),
                    coverage.reason(),
                    coverage.schemeVersion());
        }
        return coverage;
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
