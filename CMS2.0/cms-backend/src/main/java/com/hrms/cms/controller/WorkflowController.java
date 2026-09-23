package com.hrms.cms.controller;

import com.hrms.cms.dto.workflow.AvailableActionsResponse;
import com.hrms.cms.dto.workflow.AwardValidationResponse;
import com.hrms.cms.dto.workflow.ClosureClauseResponse;
import com.hrms.cms.dto.workflow.ClosureStatusResponse;
import com.hrms.cms.dto.workflow.ComplaintAssignmentResponse;
import com.hrms.cms.dto.workflow.ComplaintCreatedResponse;
import com.hrms.cms.dto.workflow.CrpcTransferResponse;
import com.hrms.cms.dto.workflow.EmailRecipientValidationResponse;
import com.hrms.cms.dto.workflow.RoleAuthorizationResponse;
import com.hrms.cms.dto.workflow.RouteComplaintResponse;
import com.hrms.cms.dto.workflow.TransferActionResponse;
import com.hrms.cms.dto.workflow.WorkflowActionResponse;
import com.hrms.cms.dto.workflow.WorkflowTaskResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.enums.DepartmentConstants;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.CepcSlaService;
import com.hrms.cms.service.CepcWorkflowService;
import com.hrms.cms.service.ClosureLetterService;
import com.hrms.cms.service.CommunicationTemplateService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.NotificationService;
import com.hrms.cms.service.RbioCompensationService;
import com.hrms.cms.service.RbioSlaService;
import com.hrms.cms.service.RbioWorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
public class WorkflowController {

    private final ComplaintRepository complaintRepository;
    private final ComplaintAttachmentRepository complaintAttachmentRepository;
    private final ComplaintService complaintService;
    private final BankRepository bankRepository;
    private final KeycloakUserService keycloakUserService;
    private final ComplaintTimelineRepository complaintTimelineRepository;
    private final CepcWorkflowService cepcWorkflowService;
    private final CepcSlaService cepcSlaService;
    private final RbioWorkflowService rbioWorkflowService;
    private final RbioSlaService rbioSlaService;
    private final RbioCompensationService rbioCompensationService;
    private final NotificationService notificationService;
    private final ComplaintEventPublisher complaintEventPublisher;
    private final ClosureLetterService closureLetterService;
    private final CommunicationTemplateService communicationTemplateService;

    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();

    private static final List<String> CLOSED_STATUSES = List.of("resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated");

    @GetMapping("/rbio/tasks")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getRbioTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("RBIO", role, officer);
    }

    @GetMapping("/rbio/all-tasks")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getRbioAllTasks(
            @RequestParam(required = false) String officer) {
        return getAllTasksByDepartment("RBIO", officer);
    }

    @GetMapping("/cepc/tasks")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getCepcTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("CEPC", role, officer);
    }

    @GetMapping("/cepc/all-tasks")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getCepcAllTasks(
            @RequestParam(required = false) String officer) {
        return getAllTasksByDepartment("CEPC", officer);
    }

    @PostMapping("/rbio/assign/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR"})
    public ResponseEntity<ApiResponse<ComplaintAssignmentResponse>> assignToRbio(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return assignComplaint(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/assign/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_INCHARGE", "CEPC_ADMIN"})
    public ResponseEntity<ApiResponse<ComplaintAssignmentResponse>> assignToCepc(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return assignComplaint(complaintNumber, "CEPC", request);
    }

    @PostMapping("/rbio/action/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<WorkflowActionResponse>> rbioAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/action/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<ApiResponse<WorkflowActionResponse>> cepcAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "CEPC", request);
    }

    @GetMapping("/rbio/completed")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getRbioCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("RBIO", officer);
    }

    @GetMapping("/rbio/available-actions/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<AvailableActionsResponse>> getRbioAvailableActions(
            @PathVariable String complaintNumber,
            @RequestParam String userRole) {
        List<String> actions = rbioWorkflowService.getAvailableActions(complaintNumber, userRole);
        return ResponseEntity.ok(ApiResponse.success(AvailableActionsResponse.builder()
                .complaintNumber(complaintNumber)
                .userRole(userRole)
                .availableActions(actions)
                .build(), "Available actions"));
    }

    // The stat keys are derived from whatever SLA buckets the service finds, so this payload stays a
    // Map rather than a DTO that would have to be edited every time a bucket is added.
    @GetMapping("/rbio/sla-stats")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "CRPC_HEAD"})
    public ResponseEntity<ApiResponse<Map<String, Long>>> getRbioSlaStats() {
        Map<String, Long> stats = rbioSlaService.getComplianceStats();
        return ResponseEntity.ok(ApiResponse.success(stats, "RBIO SLA compliance stats"));
    }

    @PostMapping("/rbio/validate-award")
    @RbioRoleGuard(roles = {"RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "RBIO_ADMIN", "RBIO_ADJUDICATOR"})
    public ResponseEntity<ApiResponse<AwardValidationResponse>> validateRbioAward(
            @RequestBody Map<String, String> request) {
        String amountStr = request.getOrDefault("amount", "0");
        String compensationType = request.getOrDefault("compensationType", "COMBINED");

        try {
            BigDecimal amount = new BigDecimal(amountStr);
            rbioCompensationService.validateAward(amount, compensationType);

            return ResponseEntity.ok(ApiResponse.success(AwardValidationResponse.builder()
                    .amount(amountStr)
                    .compensationType(compensationType)
                    .valid(true)
                    .band(rbioCompensationService.calculateCompensationBand(amount))
                    .maxAllowed(rbioCompensationService.getMaxAllowed(compensationType).toPlainString())
                    .build(), "Award amount is within permitted limits"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(ApiResponse.<AwardValidationResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .data(AwardValidationResponse.builder()
                            .amount(amountStr)
                            .compensationType(compensationType)
                            .valid(false)
                            .reason(e.getMessage())
                            .build())
                    .build());
        }
    }

    @GetMapping("/cepc/completed")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getCepcCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("CEPC", officer);
    }

    @GetMapping("/my-actions")
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getMyActions(@RequestParam String officer) {
        List<Long> complaintIds = complaintTimelineRepository.findDistinctComplaintIdsByPerformedBy(officer);
        if (complaintIds.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(List.of(), "My actions"));
        }
        List<Complaint> complaints = complaintRepository.findAllById(complaintIds);
        complaints.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return ResponseEntity.ok(ApiResponse.success(buildTaskList(complaints), "My actions"));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getUnassigned() {
        List<Complaint> unassigned = complaintRepository.findByStatusAndDepartmentIsNullOrderByCreatedAtDesc("pending");
        return ResponseEntity.ok(ApiResponse.success(buildTaskList(unassigned), "Unassigned complaints"));
    }

    @GetMapping("/cepc/contact-person/tasks")
    @CepcRoleGuard(roles = {"CEPC_CONTACT_PERSON", "CEPC_DO", "CEPC_ADMIN"})
    public ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getContactPersonTasks(
            @RequestParam(required = false) String officer) {
        List<Complaint> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", officer, CLOSED_STATUSES);
        } else {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", CLOSED_STATUSES);
        }
        return ResponseEntity.ok(ApiResponse.success(buildTaskList(tasks), "Contact Person tasks"));
    }

    @PostMapping("/cepc/create-complaint")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_ADMIN"})
    public ResponseEntity<ApiResponse<ComplaintCreatedResponse>> cepcCreateComplaint(
            @RequestBody Map<String, String> request) {
        String number = "CMP-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now())
                + "-" + (100000 + new Random().nextInt(900000));

        Complaint c = new Complaint();
        c.setComplaintNumber(number);
        c.setComplainantName(request.getOrDefault("complainantName", ""));
        c.setComplainantEmail(request.getOrDefault("complainantEmail", ""));
        c.setComplainantPhone(request.getOrDefault("complainantPhone", ""));
        c.setComplainantAddress(request.getOrDefault("complainantAddress", ""));
        c.setSubject(request.getOrDefault("subject", ""));
        c.setDescription(request.getOrDefault("description", ""));
        c.setEntityCode(request.getOrDefault("entityName", ""));
        c.setPriority(request.getOrDefault("priority", "MEDIUM"));
        c.setFilingType(request.getOrDefault("filingType", "CEPC_MANUAL"));
        c.setDepartment("CEPC");
        c.setAssignedRole("CEPC_DO");
        c.setAssignedOfficer(request.getOrDefault("createdBy", ""));
        c.setStatus("assigned");
        c.setWorkflowStage("CREATED");
        c.setReopenCount(0);

        // Apply SLA deadline based on priority
        cepcSlaService.applySlaDeadline(c);

        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "CREATED", request.getOrDefault("createdBy", "system"),
                "Complaint created by CEPC Dealing Official", "new", "assigned");

        return ResponseEntity.ok(ApiResponse.success(ComplaintCreatedResponse.builder()
                .complaintNumber(number)
                .complaintId(c.getId())
                .status("assigned")
                .department("CEPC")
                .build(), "Complaint created successfully"));
    }

    @PostMapping("/rbio/create-complaint")
    @RbioRoleGuard(roles = {"RBIO_DO", "RBIO_REVIEWER", "RBIO_ADMIN", "RBIO_OFFICER", "RBIO_SUPERVISOR"})
    public ResponseEntity<ApiResponse<ComplaintCreatedResponse>> rbioCreateComplaint(
            @RequestBody Map<String, String> request) {
        String number = "CMP-" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd").format(java.time.LocalDate.now())
                + "-" + (100000 + new Random().nextInt(900000));

        Complaint c = new Complaint();
        c.setComplaintNumber(number);
        c.setComplainantName(request.getOrDefault("complainantName", ""));
        c.setComplainantEmail(request.getOrDefault("complainantEmail", ""));
        c.setComplainantPhone(request.getOrDefault("complainantPhone", ""));
        c.setComplainantAddress(request.getOrDefault("complainantAddress", ""));
        c.setSubject(request.getOrDefault("subject", ""));
        c.setDescription(request.getOrDefault("description", ""));
        c.setEntityCode(request.getOrDefault("entityName", ""));
        c.setPriority(request.getOrDefault("priority", "MEDIUM"));
        c.setFilingType(request.getOrDefault("filingType", "ONLINE"));
        c.setDepartment("RBIO");
        c.setAssignedRole("RBIO_OFFICER");
        c.setAssignedOfficer(request.getOrDefault("createdBy", "rbio_officer_001"));
        c.setStatus("assigned");
        c.setWorkflowStage("CREATED");
        c.setReopenCount(0);

        rbioSlaService.applyStageSla(c, "OFFICER");

        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "CREATED", request.getOrDefault("createdBy", "system"),
                "Complaint created for RBIO processing", "new", "assigned");

        return ResponseEntity.ok(ApiResponse.success(ComplaintCreatedResponse.builder()
                .complaintNumber(number)
                .complaintId(c.getId())
                .status("assigned")
                .department("RBIO")
                .build(), "RBIO Complaint created successfully"));
    }

    @GetMapping("/crpc/transfers")
    public ResponseEntity<ApiResponse<List<CrpcTransferResponse>>> getCrpcTransfers() {
        List<Complaint> transfers = complaintRepository.findByDepartmentAndStatusInOrderByCreatedAtDesc(
                "CRPC", List.of("sent_to_other", "pending_approval", "sent_back", "forwarded_external"));
        List<CrpcTransferResponse> data = transfers.stream()
                .map(c -> CrpcTransferResponse.builder()
                        .complaintId(c.getId() != null ? c.getId().toString() : "")
                        .complaintNumber(c.getComplaintNumber())
                        .from(c.getComplainantEmail())
                        .pending(c.getCreatedAt() != null
                                ? Duration.between(c.getCreatedAt(), LocalDateTime.now()).toDays() : 0)
                        .fromOffice(c.getDepartment() != null ? c.getDepartment() : "CRPC")
                        .targetOffice(c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "")
                        .status(c.getStatus() != null ? c.getStatus() : "")
                        .entityName(c.getEntityCode() != null ? c.getEntityCode() : "")
                        .proposedCategory(c.getFilingType() != null ? c.getFilingType() : "")
                        .creationDate(c.getCreatedAt() != null ? c.getCreatedAt().toString() : "")
                        .subject(c.getSubject())
                        .complainantName(c.getComplainantName())
                        .complainantEmail(c.getComplainantEmail())
                        .complainantPhone(c.getComplainantPhone())
                        .description(c.getDescription())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(data, "Transfer complaints retrieved"));
    }

    @PostMapping("/crpc/transfer-action/{complaintId}")
    public ResponseEntity<ApiResponse<TransferActionResponse>> crpcTransferAction(
            @PathVariable String complaintId,
            @RequestBody Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintId);
        if (opt.isEmpty()) {
            opt = complaintRepository.findById(Long.valueOf(complaintId));
        }
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String action = request.getOrDefault("action", "").toUpperCase();
        String remarks = request.getOrDefault("remarks", "");
        String actor = request.getOrDefault("actor", "");
        String prevStatus = c.getStatus();

        if ("APPROVE_TRANSFER".equals(action)) {
            c.setStatus("assigned");
            c.setWorkflowStage("TRANSFER_APPROVED");
            c.setAssignedRole("CRPC_DO");
        } else if ("REJECT_TRANSFER".equals(action)) {
            c.setStatus("sent_back");
            c.setWorkflowStage("TRANSFER_REJECTED");
        } else {
            return ResponseEntity.ok(ApiResponse.error("Unknown transfer action: " + action));
        }

        complaintRepository.save(c);
        complaintService.addTimeline(c.getId(), action, actor, remarks, prevStatus, c.getStatus());

        // UST606: TRANSFER_IN — notify destination user when transfer is approved
        if ("APPROVE_TRANSFER".equals(action) && c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
            notificationService.send(c.getAssignedOfficer(), "TRANSFER_IN",
                    "Complaint transferred to you",
                    "Complaint " + c.getComplaintNumber() + " has arrived via inter-office transfer.",
                    c.getComplaintNumber(), "COMPLAINT",
                    "/workflow/crpc/complaint/" + c.getComplaintNumber());
        }

        return ResponseEntity.ok(ApiResponse.success(TransferActionResponse.builder()
                .complaintNumber(c.getComplaintNumber())
                .action(action)
                .newStatus(c.getStatus())
                .build(), "Transfer action performed"));
    }

    @GetMapping("/cepc/sla-stats")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN"})
    public ResponseEntity<ApiResponse<Map<String, Long>>> getCepcSlaStats() {
        Map<String, Long> stats = cepcSlaService.getComplianceStats("CEPC");
        return ResponseEntity.ok(ApiResponse.success(stats, "SLA compliance stats"));
    }

    @GetMapping("/cepc/available-actions/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<ApiResponse<AvailableActionsResponse>> getCepcAvailableActions(
            @PathVariable String complaintNumber,
            @RequestParam String userRole) {
        List<String> actions = cepcWorkflowService.getAvailableActions(complaintNumber, userRole);
        return ResponseEntity.ok(ApiResponse.success(AvailableActionsResponse.builder()
                .complaintNumber(complaintNumber)
                .userRole(userRole)
                .availableActions(actions)
                .build(), "Available actions"));
    }

    @GetMapping("/cepc/validate-action")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<ApiResponse<RoleAuthorizationResponse>> validateCepcAction(
            @RequestParam String userRole,
            @RequestParam String action) {
        boolean authorized = cepcWorkflowService.validateRoleAuthorization(userRole, action);
        return ResponseEntity.ok(ApiResponse.success(RoleAuthorizationResponse.builder()
                .userRole(userRole)
                .action(action)
                .authorized(authorized)
                .build(), "Role authorization check"));
    }

    @PostMapping("/route/{complaintNumber}")
    public ResponseEntity<ApiResponse<RouteComplaintResponse>> routeComplaint(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();

        // Department is the search service's tenancy boundary, so an arbitrary caller-supplied value
        // here would move a complaint into or out of another department's visibility. Reject anything
        // outside the known set rather than writing it through.
        String requestedDepartment = request.getOrDefault("department", DepartmentConstants.DEPT_RBIO);
        String department = DepartmentConstants.canonicalize(requestedDepartment);
        if (department == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "Unknown department '" + requestedDepartment + "'. Allowed: "
                            + DepartmentConstants.ALL_DEPARTMENTS));
        }

        String role = request.getOrDefault("role", department + "_OFFICER");
        String officer = request.getOrDefault("officer", "");

        c.setDepartment(department);
        c.setAssignedRole(role);
        c.setAssignedOfficer(officer);
        c.setStatus("assigned");
        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "ROUTED", "system",
                "Routed to " + department + " - " + role, "pending", "assigned");

        // UST605: NEW_ASSIGNMENT — notify routed officer
        if (officer != null && !officer.isBlank()) {
            notificationService.send(officer, "NEW_ASSIGNMENT",
                    "New complaint routed to you",
                    "Complaint " + c.getComplaintNumber() + " has been routed to " + department + " / " + role,
                    c.getComplaintNumber(), "COMPLAINT",
                    "/workflow/" + department.toLowerCase() + "/complaint/" + c.getComplaintNumber());
        }

        // Kafka: complaint.assigned
        complaintEventPublisher.publishComplaintAssigned(c, "system");

        return ResponseEntity.ok(ApiResponse.success(RouteComplaintResponse.builder()
                .complaintNumber(c.getComplaintNumber())
                .department(department)
                .assignedRole(role)
                .assignedOfficer(officer)
                .status("assigned")
                .build(), "Complaint routed successfully"));
    }

    // ═══ UST581-584: Closure Clauses filtered by role ═══
    @GetMapping("/closure-clauses")
    public ResponseEntity<ApiResponse<List<ClosureClauseResponse>>> getClosureClauses(@RequestParam String role) {
        List<ClosureClauseResponse> clauses = new ArrayList<>();

        // Base clauses available to all
        clauses.add(clause("16(1)", "Resolved to satisfaction", "RESOLUTION"));
        clauses.add(clause("16(2)(a)", "Not maintainable - time barred", "NON_MAINTAINABLE"));
        clauses.add(clause("16(2)(b)", "Not maintainable - frivolous/vexatious", "NON_MAINTAINABLE"));
        clauses.add(clause("16(2)(g)", "Not maintainable - anonymous", "NON_MAINTAINABLE"));
        clauses.add(clause("16(2)(h)", "Not maintainable - insufficient information", "NON_MAINTAINABLE"));
        clauses.add(clause("16(3)", "Closed - complainant not responding", "CLOSURE"));
        clauses.add(clause("16(4)", "Closed - matter settled", "CLOSURE"));

        // Appellable clauses - only Ombudsman can use
        if ("OMBUDSMAN".equalsIgnoreCase(role) || "RBIO_ADMIN".equalsIgnoreCase(role)) {
            clauses.add(appellableClause("16(2)(c)", "Not maintainable - sub-judice", "NON_MAINTAINABLE"));
            clauses.add(appellableClause("16(2)(d)", "Not maintainable - outside jurisdiction", "NON_MAINTAINABLE"));
            clauses.add(appellableClause("16(2)(e)", "Not maintainable - already settled by RBI", "NON_MAINTAINABLE"));
            clauses.add(appellableClause("16(2)(f)", "Not maintainable - covered by other dispute mechanism", "NON_MAINTAINABLE"));
            clauses.add(appellableClause("15(1)(a)", "Award - full relief", "AWARD"));
            clauses.add(appellableClause("15(1)(b)", "Award - partial relief with compensation", "AWARD"));
        }

        // Deputy Ombudsman: non-appealable subset
        if ("DEPUTY_OMBUDSMAN".equalsIgnoreCase(role)) {
            // Excludes 16(2)(c)-(f) and 15(1)(a)/(b) - already not added for this role
        }

        // Reviewer: only delegated non-maintainable clauses (excludes 16(2)(c)-(f), 15(1)(a)/(b))
        // Already handled by not adding them for roles other than OMBUDSMAN

        // RBIOS 2026 new clauses
        clauses.add(newClause("16(5)", "Closed - entity licence cancelled/surrendered"));
        clauses.add(newClause("16(6)", "Closed - complaint withdrawn by complainant"));

        return ResponseEntity.ok(ApiResponse.success(clauses, "Closure clauses for role: " + role));
    }

    private static ClosureClauseResponse clause(String code, String label, String category) {
        return ClosureClauseResponse.builder().code(code).label(label).category(category).build();
    }

    private static ClosureClauseResponse appellableClause(String code, String label, String category) {
        return ClosureClauseResponse.builder()
                .code(code).label(label).category(category).appellable(true).build();
    }

    private static ClosureClauseResponse newClause(String code, String label) {
        return ClosureClauseResponse.builder()
                .code(code).label(label).category("CLOSURE").newIn2026(true).build();
    }

    // ═══ UST656: Email Validation - RBI Domain Only ═══
    @PostMapping("/validate-email-recipients")
    public ResponseEntity<ApiResponse<EmailRecipientValidationResponse>> validateEmailRecipients(
            @RequestBody Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<String> recipients = (List<String>) request.getOrDefault("recipients", List.of());
        String actor = (String) request.getOrDefault("actor", "unknown");

        List<String> invalidEmails = new ArrayList<>();
        List<String> validEmails = new ArrayList<>();

        for (String email : recipients) {
            if (email != null && (email.toLowerCase().endsWith("@rbi.org.in") || email.toLowerCase().endsWith("@rbi.gov.in"))) {
                validEmails.add(email);
            } else {
                invalidEmails.add(email);
                log.warn("UST656: Rejected non-RBI email attempt by actor={}, email={}", actor, email);
            }
        }

        boolean allValid = invalidEmails.isEmpty();
        return ResponseEntity.ok(ApiResponse.<EmailRecipientValidationResponse>builder()
                .success(allValid)
                .message(allValid ? "All recipients valid" : "Invalid recipients detected")
                .data(EmailRecipientValidationResponse.builder()
                        .valid(allValid)
                        .validEmails(validEmails)
                        .invalidEmails(invalidEmails)
                        .error(allValid ? null
                                : "Only official RBI email addresses can be used for outbound complaint emails")
                        .build())
                .build());
    }

    // ═══ UST655: Get assignable users (exclude SECRETARY) ═══
    // The user payload stays a Map until KeycloakUserService is retyped — it is the shared producer
    // for KeycloakUserController's endpoints too, so both move together or neither does.
    @GetMapping("/assignable-users")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getAssignableUsers(@RequestParam String role) {
        List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);
        // Filter out users with SECRETARY role
        users = users.stream()
                .filter(u -> {
                    String userId = (String) u.getOrDefault("userId", "");
                    // Additional filter: exclude any user whose userId or role contains secretary
                    return !userId.toLowerCase().contains("secretary");
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(users, "Assignable users for role: " + role));
    }

    // ═══ UST504-505: Check closure letter dispatch status ═══
    @GetMapping("/closure-status/{complaintNumber}")
    public ResponseEntity<ApiResponse<ClosureStatusResponse>> getClosureStatus(@PathVariable String complaintNumber) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Complaint c = opt.get();
        return ResponseEntity.ok(ApiResponse.success(ClosureStatusResponse.builder()
                .complaintNumber(c.getComplaintNumber())
                .hasEmail(c.getComplainantEmail() != null && !c.getComplainantEmail().isBlank())
                .closureLetterSentAt(c.getClosureLetterSentAt() != null
                        ? c.getClosureLetterSentAt().toString() : null)
                .status(c.getStatus())
                .closureCause(c.getClosureCause())
                .closureClause(c.getClosureClause())
                .customClosureText(c.getCustomClosureText())
                .build(), "Closure status"));
    }

    private ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getAllTasksByDepartment(String dept, String officer) {
        List<Complaint> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc(dept, officer);
            // Also include complaints assigned by role (e.g., escalated to RBIO_SUPERVISOR)
            List<String> roles = resolveRolesForOfficer(officer);
            for (String role : roles) {
                List<Complaint> roleTasks = complaintRepository.findByDepartmentAndAssignedRoleOrderByCreatedAtDesc(dept, role);
                for (Complaint rt : roleTasks) {
                    if (tasks.stream().noneMatch(t -> t.getId().equals(rt.getId()))) {
                        tasks.add(rt);
                    }
                }
            }
            // Also include complaints this officer previously acted on, even if it has since
            // moved to someone else — shown read-only so the officer keeps visibility of what
            // they forwarded.
            List<Long> pastIds = complaintTimelineRepository.findDistinctComplaintIdsByPerformedBy(officer);
            if (!pastIds.isEmpty()) {
                List<Complaint> pastTasks = complaintRepository.findAllById(pastIds).stream()
                        .filter(c -> dept.equals(c.getDepartment()))
                        .collect(Collectors.toList());
                for (Complaint pt : pastTasks) {
                    if (tasks.stream().noneMatch(t -> t.getId().equals(pt.getId()))) {
                        tasks.add(pt);
                    }
                }
            }
        } else {
            tasks = complaintRepository.findByDepartmentOrderByCreatedAtDesc(dept);
        }
        return ResponseEntity.ok(ApiResponse.success(buildTaskList(tasks, officer), "All tasks retrieved"));
    }

    private List<String> resolveRolesForOfficer(String officer) {
        List<String> roles = new java.util.ArrayList<>();
        try {
            List<Map<String, Object>> supervisors = keycloakUserService.getUsersByRole("RBIO_SUPERVISOR", false);
            if (supervisors.stream().anyMatch(u -> officer.equals(u.get("userId")))) roles.add("RBIO_SUPERVISOR");
            List<Map<String, Object>> conciliators = keycloakUserService.getUsersByRole("RBIO_CONCILIATOR", false);
            if (conciliators.stream().anyMatch(u -> officer.equals(u.get("userId")))) roles.add("RBIO_CONCILIATOR");
            List<Map<String, Object>> adjudicators = keycloakUserService.getUsersByRole("RBIO_ADJUDICATOR", false);
            if (adjudicators.stream().anyMatch(u -> officer.equals(u.get("userId")))) roles.add("RBIO_ADJUDICATOR");
        } catch (Exception e) {
            log.warn("Failed to resolve roles for officer {}: {}", officer, e.getMessage());
        }
        return roles;
    }

    private ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getCompletedByDepartment(String dept, String officer) {
        List<Complaint> completed = new java.util.ArrayList<>();
        if (officer != null && !officer.isBlank()) {
            for (String status : CLOSED_STATUSES) {
                completed.addAll(complaintRepository.findByDepartmentAndAssignedOfficerAndStatusOrderByCreatedAtDesc(dept, officer, status));
            }
        } else {
            for (String status : CLOSED_STATUSES) {
                completed.addAll(complaintRepository.findByDepartmentAndStatusOrderByCreatedAtDesc(dept, status));
            }
        }
        return ResponseEntity.ok(ApiResponse.success(buildTaskList(completed), "Completed tasks"));
    }

    private ResponseEntity<ApiResponse<List<WorkflowTaskResponse>>> getTasksByDepartment(String dept, String role, String officer) {
        List<Complaint> tasks;

        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    dept, officer, CLOSED_STATUSES);
        } else if (role != null && !role.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    dept, role, CLOSED_STATUSES);
        } else {
            tasks = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(dept, CLOSED_STATUSES);
        }

        return ResponseEntity.ok(ApiResponse.success(buildTaskList(tasks), "Tasks retrieved"));
    }

    private ResponseEntity<ApiResponse<ComplaintAssignmentResponse>> assignComplaint(String complaintNumber, String dept, Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String role = request.getOrDefault("role", dept + "_OFFICER");
        String officer = request.getOrDefault("officer", "");

        String prevStatus = c.getStatus();
        c.setDepartment(dept);
        c.setAssignedRole(role);
        c.setAssignedOfficer(officer);
        c.setStatus("assigned");
        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "ASSIGNED", request.getOrDefault("actor", "system"),
                "Assigned to " + officer + " (" + role + ")", prevStatus, "assigned");

        // UST605: NEW_ASSIGNMENT — notify new owner
        if (officer != null && !officer.isBlank()) {
            notificationService.send(officer, "NEW_ASSIGNMENT",
                    "New complaint assigned to you",
                    "Complaint " + c.getComplaintNumber() + " has been assigned to you (" + role + ")",
                    c.getComplaintNumber(), "COMPLAINT",
                    "/workflow/" + dept.toLowerCase() + "/complaint/" + c.getComplaintNumber());
        }

        // Kafka: complaint.assigned
        complaintEventPublisher.publishComplaintAssigned(c, request.getOrDefault("actor", "system"));

        return ResponseEntity.ok(ApiResponse.success(ComplaintAssignmentResponse.builder()
                .complaintNumber(c.getComplaintNumber())
                .assignedTo(officer)
                .role(role)
                .build(), "Complaint assigned"));
    }

    private ResponseEntity<ApiResponse<WorkflowActionResponse>> performAction(String complaintNumber, String dept, Map<String, String> request) {
        String action = request.getOrDefault("action", "").toUpperCase();

        // Delegate CEPC-specific actions to CepcWorkflowService
        if ("CEPC".equals(dept) && cepcWorkflowService.isCepcAction(action)) {
            try {
                Map<String, Object> data = cepcWorkflowService.performAction(complaintNumber, action, request);
                return ResponseEntity.ok(ApiResponse.success(
                        WorkflowActionResponse.from(data), "Action performed: " + action));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
            }
        }

        // Delegate RBIO-specific actions to RbioWorkflowService
        if ("RBIO".equals(dept) && rbioWorkflowService.isRbioAction(action)) {
            try {
                Map<String, Object> data = rbioWorkflowService.performAction(complaintNumber, action, request);
                return ResponseEntity.ok(ApiResponse.success(
                        WorkflowActionResponse.from(data), "Action performed: " + action));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.ok(ApiResponse.error(e.getMessage()));
            }
        }

        // Generic/fallback actions for departments without dedicated service
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String remarks = request.getOrDefault("remarks", "");
        String actor = request.getOrDefault("actor", "");
        String prevStatus = c.getStatus();

        switch (action) {
            case "ACCEPT":
            case "TAKE_ACTION":
                c.setStatus("in_progress");
                c.setAssignedOfficer(actor);
                c.setWorkflowStage("EXAMINATION");
                break;
            case "APPROVE":
                c.setStatus("approved");
                c.setAssignedRole(getNextRole(dept, c.getAssignedRole()));
                break;
            case "REJECT":
                c.setStatus("rejected");
                c.setResolvedAt(LocalDateTime.now());
                break;
            case "ESCALATE":
                c.setStatus("escalated");
                c.setEscalatedAt(LocalDateTime.now());
                c.setAssignedRole(getNextRole(dept, c.getAssignedRole()));
                break;
            case "RETURN_TO_OFFICER":
                c.setStatus("returned");
                c.setAssignedRole(dept + "_OFFICER");
                break;
            case "RESOLVE":
                c.setStatus("resolved");
                c.setResolvedAt(LocalDateTime.now());
                break;
            case "CONCILIATION_SUCCESS":
                c.setStatus("conciliated");
                c.setResolvedAt(LocalDateTime.now());
                break;
            case "CONCILIATION_FAILED":
                c.setStatus("escalated");
                c.setAssignedRole(dept + "_ADJUDICATOR");
                break;
            case "ADJUDICATION_AWARD":
                c.setStatus("adjudicated");
                c.setResolvedAt(LocalDateTime.now());
                break;
            default:
                return ResponseEntity.ok(ApiResponse.error("Unknown action: " + action));
        }

        complaintRepository.save(c);
        complaintService.addTimeline(c.getId(), action, actor, remarks, prevStatus, c.getStatus());

        // ═══ Notification triggers based on action ═══
        triggerActionNotifications(c, action, actor, prevStatus);

        // ═══ Kafka event publishing ═══
        publishActionEvent(c, action, actor, prevStatus);

        return ResponseEntity.ok(ApiResponse.success(WorkflowActionResponse.builder()
                .complaintNumber(c.getComplaintNumber())
                .action(action)
                .newStatus(c.getStatus())
                .assignedRole(c.getAssignedRole())
                .assignedOfficer(c.getAssignedOfficer())
                .build(), "Action performed: " + action));
    }

    /**
     * Triggers in-app notifications based on workflow action performed.
     */
    private void triggerActionNotifications(Complaint c, String action, String actor, String prevStatus) {
        String complaintUrl = "/workflow/complaint/" + c.getComplaintNumber();

        switch (action) {
            case "ACCEPT":
            case "TAKE_ACTION":
                // UST605: NEW_ASSIGNMENT — if officer changes, notify new owner
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank() && !c.getAssignedOfficer().equals(actor)) {
                    notificationService.send(c.getAssignedOfficer(), "NEW_ASSIGNMENT",
                            "Complaint accepted and in progress",
                            "Complaint " + c.getComplaintNumber() + " is now in progress with you.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "ESCALATE":
                // UST605: Notify the new role owner after escalation
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
                    notificationService.send(c.getAssignedOfficer(), "NEW_ASSIGNMENT",
                            "Escalated complaint assigned",
                            "Complaint " + c.getComplaintNumber() + " has been escalated to your role (" + c.getAssignedRole() + ")",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                // Kafka: complaint.escalated
                break;

            case "RESOLVE":
            case "CONCILIATION_SUCCESS":
            case "ADJUDICATION_AWARD":
                // UST663: COMPLAINT_CLOSED — notify owner, NO, PNO
                String owner = c.getAssignedOfficer();
                if (owner != null && !owner.isBlank() && !owner.equals(actor)) {
                    notificationService.send(owner, "COMPLAINT_CLOSED",
                            "Complaint resolved",
                            "Complaint " + c.getComplaintNumber() + " has been resolved/closed via " + action,
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                // Notify the original actor (if different from assigned officer)
                if (actor != null && !actor.isBlank() && !actor.equals(owner)) {
                    notificationService.send(actor, "COMPLAINT_CLOSED",
                            "Complaint you worked on is closed",
                            "Complaint " + c.getComplaintNumber() + " has been resolved via " + action,
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "REJECT":
                // UST663: COMPLAINT_CLOSED variant — notify owner
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank() && !c.getAssignedOfficer().equals(actor)) {
                    notificationService.send(c.getAssignedOfficer(), "COMPLAINT_CLOSED",
                            "Complaint rejected",
                            "Complaint " + c.getComplaintNumber() + " has been rejected.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "RETURN_TO_OFFICER":
                // UST610: NO_REASSIGNED_TO_RBI — notify complaint owner (officer)
                String officerRole = c.getDepartment() + "_OFFICER";
                // The officer will be determined by the role; notify any assigned officer
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
                    notificationService.send(c.getAssignedOfficer(), "NO_REASSIGNED_TO_RBI",
                            "Complaint returned to officer",
                            "Complaint " + c.getComplaintNumber() + " has been returned to " + officerRole + " for further action.",
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "APPROVE":
                // UST605: Notify next role owner
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
                    notificationService.send(c.getAssignedOfficer(), "NEW_ASSIGNMENT",
                            "Complaint approved and forwarded",
                            "Complaint " + c.getComplaintNumber() + " has been approved and forwarded to " + c.getAssignedRole(),
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            case "CONCILIATION_FAILED":
                // Escalation to adjudicator — notify
                if (c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
                    notificationService.send(c.getAssignedOfficer(), "NEW_ASSIGNMENT",
                            "Conciliation failed — escalated to adjudication",
                            "Complaint " + c.getComplaintNumber() + " conciliation has failed. Escalated to " + c.getAssignedRole(),
                            c.getComplaintNumber(), "COMPLAINT", complaintUrl);
                }
                break;

            default:
                break;
        }
    }

    /**
     * Publishes Kafka events for workflow actions.
     */
    private void publishActionEvent(Complaint c, String action, String actor, String prevStatus) {
        switch (action) {
            case "ESCALATE":
            case "CONCILIATION_FAILED":
                complaintEventPublisher.publishComplaintEscalated(c, actor, prevStatus);
                break;
            case "RESOLVE":
            case "CONCILIATION_SUCCESS":
            case "ADJUDICATION_AWARD":
            case "REJECT":
                complaintEventPublisher.publishComplaintClosed(c, actor, prevStatus);
                break;
            default:
                break;
        }
    }

    private String getNextRole(String dept, String currentRole) {
        if (currentRole == null) return dept + "_OFFICER";
        Map<String, String> escalation = Map.of(
                dept + "_OFFICER", dept + "_SUPERVISOR",
                dept + "_SUPERVISOR", dept + "_CONCILIATOR",
                dept + "_CONCILIATOR", dept + "_ADJUDICATOR"
        );
        return escalation.getOrDefault(currentRole, currentRole);
    }

    private List<WorkflowTaskResponse> buildTaskList(List<Complaint> complaints) {
        return buildTaskList(complaints, null);
    }

    private List<WorkflowTaskResponse> buildTaskList(List<Complaint> complaints, String requestingOfficer) {
        return complaints.stream().map(c -> {
            String entityName = "";
            if (c.getEntityCode() != null && !c.getEntityCode().isBlank()) {
                entityName = c.getEntityCode();
            } else if (c.getBankId() != null) {
                entityName = bankRepository.findById(c.getBankId()).map(b -> b.getName()).orElse("");
            }
            // The 6-digit CRPC draft id if this complaint was converted from one, otherwise
            // fall back to this row's own internal id (complaints seeded directly into RBIO/CEPC
            // without ever going through a CRPC draft have no origin draft id to show).
            String complaintId = c.getOriginDraftId() != null ? c.getOriginDraftId()
                    : (c.getId() != null ? c.getId().toString() : null);
            return WorkflowTaskResponse.builder()
                    .complaintId(complaintId)
                    .complaintNumber(c.getComplaintNumber())
                    .subject(c.getSubject())
                    .complainantName(c.getComplainantName())
                    .priority(c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM")
                    .status(c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING")
                    .assignedAt(c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "")
                    .slaDueDate(c.getSlaDeadline() != null ? c.getSlaDeadline().toString()
                            : (c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : ""))
                    .entityName(entityName)
                    .department(c.getDepartment())
                    .assignedRole(c.getAssignedRole())
                    .assignedOfficer(c.getAssignedOfficer())
                    .triageSignal(c.getTriageSignal())
                    .hasAttachments(complaintAttachmentRepository.existsByComplaintId(c.getId()))
                    .viewOnly(requestingOfficer != null && !requestingOfficer.isBlank()
                            && !requestingOfficer.equals(c.getAssignedOfficer()))
                    .build();
        }).collect(Collectors.toList());
    }

    private String assignByRole(String role) {
        try {
            List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);
            if (users.isEmpty()) return null;
            int index = roundRobinCounters.getOrDefault(role, 0);
            if (index >= users.size()) index = 0;
            String userId = (String) users.get(index).get("userId");
            roundRobinCounters.put(role, index + 1);
            return userId;
        } catch (Exception e) {
            return null;
        }
    }
}
