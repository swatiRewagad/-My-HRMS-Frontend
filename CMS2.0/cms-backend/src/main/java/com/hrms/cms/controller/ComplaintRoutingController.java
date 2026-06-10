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

    private Map<String, Object> wrapResponse(Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("message", "OK");
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }
}
