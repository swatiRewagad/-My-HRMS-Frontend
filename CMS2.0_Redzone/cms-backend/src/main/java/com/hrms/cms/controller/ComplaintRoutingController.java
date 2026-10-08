package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EntityOfficeNodalOfficer;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.EntityOfficeNodalOfficerRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintRoutingService.RoutingDecision;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
public class ComplaintRoutingController {

    private final ComplaintRoutingService routingService;
    private final ComplaintService complaintService;
    private final RegulatedEntityRepository regulatedEntityRepo;
    private final EntityOfficeNodalOfficerRepository nodalOfficerRepo;

    @GetMapping("/entity-mapping")
    public Map<String, Object> getEntityDepartmentMapping() {
        return wrapResponse(routingService.getEntityDepartmentMapping());
    }

    @GetMapping("/rules")
    public Map<String, Object> getRoutingRules() {
        return wrapResponse(routingService.getRoutingRulesSummary());
    }

    @GetMapping("/resolve-department")
    public Map<String, Object> resolveDepartment(@RequestParam String entityCode) {
        String department = routingService.resolveDepartment(entityCode);
        return wrapResponse(Map.of(
                "entityCode", entityCode,
                "department", department,
                "assignedRole", "CEPC".equals(department) ? "CEPC_OFFICER" : "RBIO_OFFICER",
                "note", "Entity-based routing applies only to EMAIL/PHYSICAL_LETTER channel. WEB_PORTAL always goes to RBIO."
        ));
    }

    @GetMapping("/resolve-by-name")
    public Map<String, Object> resolveByEntityName(@RequestParam String entityName) {
        var result = routingService.resolveEntityRouting(entityName);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entityName", entityName);
        data.put("department", result.getDepartment());
        data.put("matchedEntityName", result.getMatchedEntityName());
        data.put("entityType", result.getEntityType());
        data.put("matchType", result.getMatchType());
        data.put("matchCount", result.getMatchCount());
        data.put("reason", result.getReason());
        data.put("assignedRole", "CEPC".equals(result.getDepartment()) ? "CEPC_OFFICER" : "RBIO_OFFICER");
        return wrapResponse(data);
    }

    @PostMapping("/route")
    public Map<String, Object> routeComplaint(@RequestBody Map<String, Object> request) {
        String complaintNumber = (String) request.getOrDefault("complaintNumber", "");
        String entityCode = (String) request.getOrDefault("entityCode", "");
        String filingType = (String) request.getOrDefault("filingType", "WEB_PORTAL");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);
        complaint.setFilingType(filingType);

        RoutingDecision decision = routingService.routeComplaint(complaint, entityCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complaintNumber", complaintNumber);
        result.put("department", decision.getDepartment());
        result.put("assignedRole", decision.getAssignedRole());
        result.put("stage", decision.getStage());
        result.put("targetDepartment", decision.getTargetDepartment());
        result.put("reason", decision.getReason());
        result.put("routedAt", LocalDateTime.now().toString());

        return wrapResponse(result);
    }

    @PostMapping("/forward-to-approval")
    public Map<String, Object> forwardToApproval(@RequestBody Map<String, Object> request) {
        String complaintNumber = (String) request.getOrDefault("complaintNumber", "");
        String entityCode = (String) request.getOrDefault("entityCode", "");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);

        RoutingDecision decision = routingService.routeFromCrpcToApproval(complaint, entityCode);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complaintNumber", complaintNumber);
        result.put("department", decision.getDepartment());
        result.put("assignedRole", decision.getAssignedRole());
        result.put("stage", decision.getStage());
        result.put("reason", decision.getReason());
        result.put("forwardedAt", LocalDateTime.now().toString());

        return wrapResponse(result);
    }

    @PostMapping("/transfer")
    public Map<String, Object> transferComplaint(@RequestBody Map<String, Object> request) {
        String complaintNumber = (String) request.getOrDefault("complaintNumber", "");
        String fromDepartment = (String) request.getOrDefault("fromDepartment", "");
        String toDepartment = (String) request.getOrDefault("toDepartment", "");
        String reason = (String) request.getOrDefault("reason", "");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);

        RoutingDecision decision = routingService.transferBetweenDepartments(
                complaint, fromDepartment, toDepartment, reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("complaintNumber", complaintNumber);
        result.put("fromDepartment", fromDepartment);
        result.put("toDepartment", decision.getDepartment());
        result.put("assignedRole", decision.getAssignedRole());
        result.put("stage", decision.getStage());
        result.put("reason", decision.getReason());
        result.put("transferredAt", LocalDateTime.now().toString());

        return wrapResponse(result);
    }

    @PostMapping("/entities/bulk-import")
    public Map<String, Object> bulkImportEntities(@RequestBody List<Map<String, String>> entities) {
        if (entities == null || entities.size() > 1000) {
            return wrapResponse(Map.of("error", "Bulk import limited to 1000 entries"));
        }
        int imported = 0;
        for (Map<String, String> entry : entities) {
            String name = entry.getOrDefault("name", "").trim();
            String department = entry.getOrDefault("department", "").trim().toUpperCase();
            String entityType = entry.getOrDefault("entityType", "");

            if (name.isEmpty() || (!department.equals("CEPC") && !department.equals("RBIO"))) continue;

            String normalized = RegulatedEntity.normalize(name);
            if (regulatedEntityRepo.findByNameNormalized(normalized).isEmpty()) {
                regulatedEntityRepo.save(RegulatedEntity.builder()
                        .name(name)
                        .nameNormalized(normalized)
                        .department(department)
                        .entityType(entityType)
                        .build());
                imported++;
            }
        }

        return wrapResponse(Map.of(
                "imported", imported,
                "total", entities.size(),
                "skipped", entities.size() - imported
        ));
    }

    @GetMapping("/entities/stats")
    public Map<String, Object> getEntityStats() {
        long cepc = regulatedEntityRepo.countByDepartment("CEPC");
        long rbio = regulatedEntityRepo.countByDepartment("RBIO");
        return wrapResponse(Map.of("CEPC", cepc, "RBIO", rbio, "total", cepc + rbio));
    }

    @GetMapping("/entities/list")
    public Map<String, Object> listEntities(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String entityType) {

        List<RegulatedEntity> entities;
        if (search != null && !search.isBlank()) {
            String normalized = RegulatedEntity.normalize(search);
            entities = regulatedEntityRepo.searchByNormalizedName(normalized);
            if (department != null && !department.isBlank()) {
                String dept = department.toUpperCase();
                entities = entities.stream().filter(e -> dept.equals(e.getDepartment())).collect(java.util.stream.Collectors.toList());
            }
        } else if (department != null && !department.isBlank()) {
            entities = regulatedEntityRepo.findByDepartment(department.toUpperCase());
        } else {
            entities = regulatedEntityRepo.findAll();
        }

        if (entityType != null && !entityType.isBlank()) {
            entities = entities.stream().filter(e -> entityType.equalsIgnoreCase(e.getEntityType())).collect(java.util.stream.Collectors.toList());
        }

        List<Map<String, Object>> result = entities.stream()
                .map(ComplaintRoutingController::toEntityPayload)
                .collect(java.util.stream.Collectors.toList());

        return wrapResponse(result);
    }

    /**
     * One entity's own contact details, for the read-only Entity Contact panel on the Summary tab.
     *
     * <p>Answers a 404 for an unknown id rather than an empty object. The panel is what tells the officer
     * whether forwarding to this entity has a recipient at all, and an empty object is indistinguishable
     * from an entity with no nodal officer — two states that call for different actions.
     *
     * <p>The roster row is consulted FIRST where it has a value. {@code RegulatedEntity} holds one contact
     * per entity while {@code ENTITY_OFFICE_NODAL_OFFICER} holds the desk an operator has actually
     * maintained, and that is who a notice must reach when the two disagree. Field by field rather than row
     * by row, so a roster row that fills only the PNO does not blank out the nodal officer the entity master
     * does have.
     *
     * <p>The ENTITY-WIDE row is the one read, not an office-specific one: this endpoint is called from the
     * Summary tab's entity picker, which names an entity and no processing office. Picking an arbitrary
     * office's row would show contacts that belong to a different office's file.
     */
    @GetMapping("/entities/{id}")
    public ResponseEntity<Map<String, Object>> getEntity(@PathVariable Long id) {
        RegulatedEntity entity = regulatedEntityRepo.findById(id).orElse(null);
        if (entity == null) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("message", "cepc.entity.error.not_found");
            body.put("data", null);
            body.put("timestamp", LocalDateTime.now().toString());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
        }

        Map<String, Object> data = toEntityPayload(entity);
        EntityOfficeNodalOfficer roster = nodalOfficerRepo
                .findFirstByEntityNameNormalizedAndProcessingOfficeIsNullAndActiveTrue(
                        RegulatedEntity.normalize(entity.getName()))
                .orElse(null);

        data.put("nodalOfficerName", firstNonBlank(
                roster == null ? null : roster.getNodalOfficerName(), entity.getNodalOfficerName()));
        data.put("nodalOfficerDesignation", firstNonBlank(
                roster == null ? null : roster.getNodalOfficerDesignation(),
                entity.getNodalOfficerDesignation()));
        data.put("nodalOfficerEmail", firstNonBlank(
                roster == null ? null : roster.getNodalOfficerEmail(), entity.getNodalOfficerEmail()));
        data.put("nodalOfficerPhone", firstNonBlank(
                roster == null ? null : roster.getNodalOfficerPhone(), entity.getNodalOfficerPhone()));
        data.put("pnoName", firstNonBlank(
                roster == null ? null : roster.getPnoName(), entity.getPnoName()));
        data.put("pnoEmail", firstNonBlank(
                roster == null ? null : roster.getPnoEmail(), entity.getPnoEmail()));
        data.put("pnoPhone", firstNonBlank(
                roster == null ? null : roster.getPnoPhone(), entity.getPnoPhone()));
        data.put("portalEnabled", Boolean.TRUE.equals(entity.getPortalEnabled()));
        data.put("processingOffice", roster == null ? null : roster.getProcessingOffice());
        data.put("contactSource", roster == null ? "ENTITY_MASTER" : "OFFICE_ROSTER");

        return ResponseEntity.ok(wrapResponse(data));
    }

    /**
     * The shape the CEPC entity typeahead reads.
     *
     * <p>{@code entityCategory}, {@code entityTypeDetail} and {@code entityTypeDisplay} were absent, so the
     * four read-only Entity Details fields the officer cannot type went blank on every pick — the tab looked
     * like it had lost the data rather than like it had never been sent.
     *
     * <p>{@code entityCategory} IS {@code entityType}: the master stores the category there ("Public Sector
     * Bank"), and the two frontend field names describe the same column. {@code entityTypeDetail} is RBI's
     * sub-classification below the category and has no column on this master, so it is null and
     * {@code entityTypeDisplay} falls back to the category — which is what the screen shows anyway when
     * there is no sub-classification. Inventing a value for it would put a guess in a read-only field the
     * officer has no way to correct.
     */
    private static Map<String, Object> toEntityPayload(RegulatedEntity e) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", e.getId());
        item.put("name", e.getName());
        item.put("department", e.getDepartment());
        item.put("entityType", e.getEntityType());
        item.put("entityCategory", e.getEntityType());
        item.put("entityTypeDetail", null);
        item.put("entityTypeDisplay", e.getEntityType());
        item.put("city", e.getCity());
        item.put("state", e.getState());
        item.put("status", e.getStatus());
        return item;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback == null || fallback.isBlank() ? null : fallback;
    }

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
