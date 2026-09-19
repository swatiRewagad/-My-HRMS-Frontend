package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintRoutingService.RoutingDecision;
import com.hrms.cms.service.ComplaintService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        String officeCode = (String) request.get("officeCode");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);
        complaint.setFilingType(filingType);

        RoutingDecision decision = routingService.routeComplaint(complaint, entityCode, officeCode);

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

        List<Map<String, Object>> result = entities.stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", e.getId());
            item.put("name", e.getName());
            item.put("department", e.getDepartment());
            item.put("entityType", e.getEntityType());
            // The three fields the complaint screens fill from a picked row. Carried here rather than
            // left to the detail call because they are what the officer checks before committing to the
            // row, so they have to arrive with the dropdown.
            item.put("moduleName", RegulatedEntity.moduleNameFor(e.getEntityType()));
            item.put("entityCategory", RegulatedEntity.entityCategoryFor(e.getEntityType()));
            item.put("entityTypeDetail", e.getEntityTypeDetail());
            item.put("city", e.getCity());
            item.put("state", e.getState());
            return item;
        }).collect(java.util.stream.Collectors.toList());

        return wrapResponse(result);
    }

    /**
     * One entity in full, for when the officer changes the regulated entity on a complaint and the
     * dependent fields have to be reloaded.
     *
     * <p>Separate from {@code /entities/list} rather than an extension of it: the list is a typeahead
     * that can return thousands of rows, so widening it to carry the nodal officer block would make
     * every keystroke pay for data only the one selected row needs. The nodal officer details are the
     * reason this exists — they are what NodalOfficerRecordService snapshots onto the record, so an
     * officer changing the entity is changing who the complaint gets forwarded to.
     */
    @GetMapping("/entities/{id}")
    public Map<String, Object> getEntity(@PathVariable Long id) {
        RegulatedEntity entity = regulatedEntityRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No regulated entity with id " + id));

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", entity.getId());
        item.put("name", entity.getName());
        item.put("department", entity.getDepartment());
        item.put("entityType", entity.getEntityType());
        item.put("moduleName", RegulatedEntity.moduleNameFor(entity.getEntityType()));
        item.put("entityCategory", RegulatedEntity.entityCategoryFor(entity.getEntityType()));
        item.put("entityTypeDetail", entity.getEntityTypeDetail());
        item.put("city", entity.getCity());
        item.put("state", entity.getState());
        item.put("status", entity.getStatus());
        item.put("portalEnabled", entity.getPortalEnabled());
        item.put("nodalOfficerName", entity.getNodalOfficerName());
        item.put("nodalOfficerEmail", entity.getNodalOfficerEmail());
        item.put("nodalOfficerPhone", entity.getNodalOfficerPhone());
        item.put("nodalOfficerDesignation", entity.getNodalOfficerDesignation());
        item.put("pnoName", entity.getPnoName());
        item.put("pnoEmail", entity.getPnoEmail());
        item.put("pnoPhone", entity.getPnoPhone());

        return wrapResponse(item);
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
