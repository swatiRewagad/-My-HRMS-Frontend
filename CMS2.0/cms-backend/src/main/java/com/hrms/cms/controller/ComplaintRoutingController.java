package com.hrms.cms.controller;

import com.hrms.cms.dto.routing.ApprovalForwardResponse;
import com.hrms.cms.dto.routing.BulkImportResponse;
import com.hrms.cms.dto.routing.DepartmentResolutionResponse;
import com.hrms.cms.dto.routing.DepartmentTransferResponse;
import com.hrms.cms.dto.routing.EntityMappingSummaryResponse;
import com.hrms.cms.dto.routing.EntityRoutingResponse;
import com.hrms.cms.dto.routing.EntityStatsResponse;
import com.hrms.cms.dto.routing.RegulatedEntityDetailResponse;
import com.hrms.cms.dto.routing.RegulatedEntityListItem;
import com.hrms.cms.dto.routing.RoutingResultResponse;
import com.hrms.cms.dto.routing.RoutingRuleResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.service.ComplaintRoutingService;
import com.hrms.cms.service.ComplaintRoutingService.RoutingDecision;
import com.hrms.cms.service.ComplaintService;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/routing")
@RequiredArgsConstructor
public class ComplaintRoutingController {

    private static final int BULK_IMPORT_LIMIT = 1000;

    private final ComplaintRoutingService routingService;
    private final ComplaintService complaintService;
    private final RegulatedEntityRepository regulatedEntityRepo;

    @GetMapping("/entity-mapping")
    public ResponseEntity<ApiResponse<EntityMappingSummaryResponse>> getEntityDepartmentMapping() {
        return wrapResponse(routingService.getEntityDepartmentMapping());
    }

    @GetMapping("/rules")
    public ResponseEntity<ApiResponse<List<RoutingRuleResponse>>> getRoutingRules() {
        return wrapResponse(routingService.getRoutingRulesSummary());
    }

    @GetMapping("/resolve-department")
    public ResponseEntity<ApiResponse<DepartmentResolutionResponse>> resolveDepartment(
            @RequestParam String entityCode) {
        String department = routingService.resolveDepartment(entityCode);
        return wrapResponse(DepartmentResolutionResponse.builder()
                .entityCode(entityCode)
                .department(department)
                .assignedRole(roleFor(department))
                .build());
    }

    @GetMapping("/resolve-by-name")
    public ResponseEntity<ApiResponse<EntityRoutingResponse>> resolveByEntityName(
            @RequestParam String entityName) {
        var result = routingService.resolveEntityRouting(entityName);
        return wrapResponse(EntityRoutingResponse.builder()
                .entityName(entityName)
                .department(result.getDepartment())
                .matchedEntityName(result.getMatchedEntityName())
                .entityType(result.getEntityType())
                .matchType(result.getMatchType())
                .matchCount(result.getMatchCount())
                .reason(result.getReason())
                .assignedRole(roleFor(result.getDepartment()))
                .build());
    }

    @PostMapping("/route")
    public ResponseEntity<ApiResponse<RoutingResultResponse>> routeComplaint(
            @RequestBody Map<String, Object> request) {
        String complaintNumber = (String) request.getOrDefault("complaintNumber", "");
        String entityCode = (String) request.getOrDefault("entityCode", "");
        String filingType = (String) request.getOrDefault("filingType", "WEB_PORTAL");
        String officeCode = (String) request.get("officeCode");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);
        complaint.setFilingType(filingType);

        RoutingDecision decision = routingService.routeComplaint(complaint, entityCode, officeCode);

        return wrapResponse(RoutingResultResponse.builder()
                .complaintNumber(complaintNumber)
                .department(decision.getDepartment())
                .assignedRole(decision.getAssignedRole())
                .stage(decision.getStage())
                .targetDepartment(decision.getTargetDepartment())
                .reason(decision.getReason())
                .routedAt(LocalDateTime.now().toString())
                .build());
    }

    @PostMapping("/forward-to-approval")
    public ResponseEntity<ApiResponse<ApprovalForwardResponse>> forwardToApproval(
            @RequestBody Map<String, Object> request) {
        String complaintNumber = (String) request.getOrDefault("complaintNumber", "");
        String entityCode = (String) request.getOrDefault("entityCode", "");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);

        RoutingDecision decision = routingService.routeFromCrpcToApproval(complaint, entityCode);

        return wrapResponse(ApprovalForwardResponse.builder()
                .complaintNumber(complaintNumber)
                .department(decision.getDepartment())
                .assignedRole(decision.getAssignedRole())
                .stage(decision.getStage())
                .reason(decision.getReason())
                .forwardedAt(LocalDateTime.now().toString())
                .build());
    }

    @PostMapping("/transfer")
    public ResponseEntity<ApiResponse<DepartmentTransferResponse>> transferComplaint(
            @RequestBody Map<String, Object> request) {
        String complaintNumber = (String) request.getOrDefault("complaintNumber", "");
        String fromDepartment = (String) request.getOrDefault("fromDepartment", "");
        String toDepartment = (String) request.getOrDefault("toDepartment", "");
        String reason = (String) request.getOrDefault("reason", "");

        Complaint complaint = new Complaint();
        complaint.setComplaintNumber(complaintNumber);

        RoutingDecision decision = routingService.transferBetweenDepartments(
                complaint, fromDepartment, toDepartment, reason);

        return wrapResponse(DepartmentTransferResponse.builder()
                .complaintNumber(complaintNumber)
                .fromDepartment(fromDepartment)
                .toDepartment(decision.getDepartment())
                .assignedRole(decision.getAssignedRole())
                .stage(decision.getStage())
                .reason(decision.getReason())
                .transferredAt(LocalDateTime.now().toString())
                .build());
    }

    @PostMapping("/entities/bulk-import")
    public ResponseEntity<ApiResponse<BulkImportResponse>> bulkImportEntities(
            @RequestBody List<Map<String, String>> entities) {
        if (entities == null || entities.size() > BULK_IMPORT_LIMIT) {
            // Was an HTTP 200 carrying success:true and an {error: ...} payload, so callers saw a
            // rejected import as a successful one with no imported count.
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Bulk import limited to " + BULK_IMPORT_LIMIT + " entries"));
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

        return wrapResponse(BulkImportResponse.builder()
                .imported(imported)
                .total(entities.size())
                .skipped(entities.size() - imported)
                .build());
    }

    @GetMapping("/entities/stats")
    public ResponseEntity<ApiResponse<EntityStatsResponse>> getEntityStats() {
        long cepc = regulatedEntityRepo.countByDepartment("CEPC");
        long rbio = regulatedEntityRepo.countByDepartment("RBIO");
        return wrapResponse(EntityStatsResponse.builder()
                .cepc(cepc)
                .rbio(rbio)
                .total(cepc + rbio)
                .build());
    }

    @GetMapping("/entities/list")
    public ResponseEntity<ApiResponse<List<RegulatedEntityListItem>>> listEntities(
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String entityType) {

        List<RegulatedEntity> entities;
        if (search != null && !search.isBlank()) {
            String normalized = RegulatedEntity.normalize(search);
            entities = regulatedEntityRepo.searchByNormalizedName(normalized);
            if (department != null && !department.isBlank()) {
                String dept = department.toUpperCase();
                entities = entities.stream().filter(e -> dept.equals(e.getDepartment())).toList();
            }
        } else if (department != null && !department.isBlank()) {
            entities = regulatedEntityRepo.findByDepartment(department.toUpperCase());
        } else {
            entities = regulatedEntityRepo.findAll();
        }

        if (entityType != null && !entityType.isBlank()) {
            entities = entities.stream().filter(e -> entityType.equalsIgnoreCase(e.getEntityType())).toList();
        }

        return wrapResponse(entities.stream().map(ComplaintRoutingController::toListItem).toList());
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
    public ResponseEntity<ApiResponse<RegulatedEntityDetailResponse>> getEntity(@PathVariable Long id) {
        RegulatedEntity entity = regulatedEntityRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No regulated entity with id " + id));

        return wrapResponse(RegulatedEntityDetailResponse.builder()
                .id(entity.getId())
                .name(entity.getName())
                .department(entity.getDepartment())
                .entityType(entity.getEntityType())
                .moduleName(RegulatedEntity.moduleNameFor(entity.getEntityType()))
                .entityCategory(RegulatedEntity.entityCategoryFor(entity.getEntityType()))
                .entityTypeDetail(entity.getEntityTypeDetail())
                .entityTypeDisplay(RegulatedEntity.entityTypeDisplayFor(
                        entity.getEntityType(), entity.getEntityTypeDetail()))
                .city(entity.getCity())
                .state(entity.getState())
                .status(entity.getStatus())
                .portalEnabled(entity.getPortalEnabled())
                .nodalOfficerName(entity.getNodalOfficerName())
                .nodalOfficerEmail(entity.getNodalOfficerEmail())
                .nodalOfficerPhone(entity.getNodalOfficerPhone())
                .nodalOfficerDesignation(entity.getNodalOfficerDesignation())
                .pnoName(entity.getPnoName())
                .pnoEmail(entity.getPnoEmail())
                .pnoPhone(entity.getPnoPhone())
                .build());
    }

    private static RegulatedEntityListItem toListItem(RegulatedEntity e) {
        return RegulatedEntityListItem.builder()
                .id(e.getId())
                .name(e.getName())
                .department(e.getDepartment())
                .entityType(e.getEntityType())
                .moduleName(RegulatedEntity.moduleNameFor(e.getEntityType()))
                .entityCategory(RegulatedEntity.entityCategoryFor(e.getEntityType()))
                .entityTypeDetail(e.getEntityTypeDetail())
                .entityTypeDisplay(RegulatedEntity.entityTypeDisplayFor(
                        e.getEntityType(), e.getEntityTypeDetail()))
                .city(e.getCity())
                .state(e.getState())
                .build();
    }

    private static String roleFor(String department) {
        return "CEPC".equals(department) ? "CEPC_OFFICER" : "RBIO_OFFICER";
    }

    /**
     * Every success from this controller carried the message "OK" back when the envelope was assembled
     * by hand here, so it is preserved rather than replaced with something per-endpoint.
     */
    private static <T> ResponseEntity<ApiResponse<T>> wrapResponse(T data) {
        return ResponseEntity.ok(ApiResponse.success(data, "OK"));
    }
}
