package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.CepcSlaService;
import com.hrms.cms.service.CepcWorkflowService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.RbioCompensationService;
import com.hrms.cms.service.RbioSlaService;
import com.hrms.cms.service.RbioWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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

    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();

    private static final List<String> CLOSED_STATUSES = List.of("resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated");

    @GetMapping("/rbio/tasks")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> getRbioTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("RBIO", role, officer);
    }

    @GetMapping("/rbio/all-tasks")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> getRbioAllTasks(
            @RequestParam(required = false) String officer) {
        return getAllTasksByDepartment("RBIO", officer);
    }

    @GetMapping("/cepc/tasks")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> getCepcTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("CEPC", role, officer);
    }

    @GetMapping("/cepc/all-tasks")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> getCepcAllTasks(
            @RequestParam(required = false) String officer) {
        return getAllTasksByDepartment("CEPC", officer);
    }

    @PostMapping("/rbio/assign/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> assignToRbio(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return assignComplaint(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/assign/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_INCHARGE", "CEPC_ADMIN"})
    public ResponseEntity<Map<String, Object>> assignToCepc(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return assignComplaint(complaintNumber, "CEPC", request);
    }

    @PostMapping("/rbio/action/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> rbioAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/action/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> cepcAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "CEPC", request);
    }

    @GetMapping("/rbio/completed")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> getRbioCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("RBIO", officer);
    }

    @GetMapping("/rbio/available-actions/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> getRbioAvailableActions(
            @PathVariable String complaintNumber,
            @RequestParam String userRole) {
        List<String> actions = rbioWorkflowService.getAvailableActions(complaintNumber, userRole);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", complaintNumber);
        data.put("userRole", userRole);
        data.put("availableActions", actions);
        return buildResponse(true, "Available actions", data);
    }

    @GetMapping("/rbio/sla-stats")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> getRbioSlaStats() {
        Map<String, Long> stats = rbioSlaService.getComplianceStats();
        return buildResponse(true, "RBIO SLA compliance stats", stats);
    }

    @PostMapping("/rbio/validate-award")
    @RbioRoleGuard(roles = {"RBIO_ADJUDICATOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> validateRbioAward(
            @RequestBody Map<String, String> request) {
        String amountStr = request.getOrDefault("amount", "0");
        String compensationType = request.getOrDefault("compensationType", "COMBINED");

        try {
            BigDecimal amount = new BigDecimal(amountStr);
            rbioCompensationService.validateAward(amount, compensationType);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", amountStr);
            data.put("compensationType", compensationType);
            data.put("valid", true);
            data.put("band", rbioCompensationService.calculateCompensationBand(amount));
            data.put("maxAllowed", rbioCompensationService.getMaxAllowed(compensationType).toPlainString());
            return buildResponse(true, "Award amount is within permitted limits", data);
        } catch (IllegalArgumentException e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("amount", amountStr);
            data.put("compensationType", compensationType);
            data.put("valid", false);
            data.put("reason", e.getMessage());
            return buildResponse(false, e.getMessage(), data);
        }
    }

    @GetMapping("/cepc/completed")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> getCepcCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("CEPC", officer);
    }

    @GetMapping("/my-actions")
    public ResponseEntity<Map<String, Object>> getMyActions(@RequestParam String officer) {
        List<Long> complaintIds = complaintTimelineRepository.findDistinctComplaintIdsByPerformedBy(officer);
        if (complaintIds.isEmpty()) {
            return buildResponse(true, "My actions", List.of());
        }
        List<Complaint> complaints = complaintRepository.findAllById(complaintIds);
        complaints.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return buildResponse(true, "My actions", buildTaskList(complaints));
    }

    @GetMapping("/unassigned")
    public ResponseEntity<Map<String, Object>> getUnassigned() {
        List<Complaint> unassigned = complaintRepository.findByStatusAndDepartmentIsNullOrderByCreatedAtDesc("pending");
        return buildResponse(true, "Unassigned complaints", buildTaskList(unassigned));
    }

    @GetMapping("/cepc/contact-person/tasks")
    @CepcRoleGuard(roles = {"CEPC_CONTACT_PERSON", "CEPC_DO", "CEPC_ADMIN"})
    public ResponseEntity<Map<String, Object>> getContactPersonTasks(
            @RequestParam(required = false) String officer) {
        List<Complaint> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", officer, CLOSED_STATUSES);
        } else {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", CLOSED_STATUSES);
        }
        return buildResponse(true, "Contact Person tasks", buildTaskList(tasks));
    }

    @PostMapping("/cepc/create-complaint")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_ADMIN"})
    public ResponseEntity<Map<String, Object>> cepcCreateComplaint(
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", number);
        data.put("complaintId", c.getId());
        data.put("status", "assigned");
        data.put("department", "CEPC");

        return buildResponse(true, "Complaint created successfully", data);
    }

    @PostMapping("/rbio/create-complaint")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> rbioCreateComplaint(
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", number);
        data.put("complaintId", c.getId());
        data.put("status", "assigned");
        data.put("department", "RBIO");

        return buildResponse(true, "RBIO Complaint created successfully", data);
    }

    @GetMapping("/crpc/transfers")
    public ResponseEntity<Map<String, Object>> getCrpcTransfers() {
        List<Complaint> transfers = complaintRepository.findByDepartmentAndStatusInOrderByCreatedAtDesc(
                "CRPC", List.of("sent_to_other", "pending_approval", "sent_back", "forwarded_external"));
        List<Map<String, Object>> data = transfers.stream().map(c -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("complaintId", c.getId() != null ? c.getId().toString() : "");
            item.put("complaintNumber", c.getComplaintNumber());
            item.put("from", c.getComplainantEmail());
            item.put("pending", c.getCreatedAt() != null ?
                    Duration.between(c.getCreatedAt(), LocalDateTime.now()).toDays() : 0);
            item.put("fromOffice", c.getDepartment() != null ? c.getDepartment() : "CRPC");
            item.put("targetOffice", c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "");
            item.put("status", c.getStatus() != null ? c.getStatus() : "");
            item.put("entityName", c.getEntityCode() != null ? c.getEntityCode() : "");
            item.put("proposedCategory", c.getFilingType() != null ? c.getFilingType() : "");
            item.put("creationDate", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            item.put("language", "");
            item.put("territory", "");
            item.put("subject", c.getSubject());
            item.put("complainantName", c.getComplainantName());
            item.put("complainantEmail", c.getComplainantEmail());
            item.put("complainantPhone", c.getComplainantPhone());
            item.put("description", c.getDescription());
            item.put("timeline", List.of());
            return item;
        }).collect(Collectors.toList());
        return buildResponse(true, "Transfer complaints retrieved", data);
    }

    @PostMapping("/crpc/transfer-action/{complaintId}")
    public ResponseEntity<Map<String, Object>> crpcTransferAction(
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
            return buildResponse(false, "Unknown transfer action: " + action, null);
        }

        complaintRepository.save(c);
        complaintService.addTimeline(c.getId(), action, actor, remarks, prevStatus, c.getStatus());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("action", action);
        data.put("newStatus", c.getStatus());
        return buildResponse(true, "Transfer action performed", data);
    }

    @GetMapping("/cepc/sla-stats")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN"})
    public ResponseEntity<Map<String, Object>> getCepcSlaStats() {
        Map<String, Long> stats = cepcSlaService.getComplianceStats("CEPC");
        return buildResponse(true, "SLA compliance stats", stats);
    }

    @GetMapping("/cepc/available-actions/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> getCepcAvailableActions(
            @PathVariable String complaintNumber,
            @RequestParam String userRole) {
        List<String> actions = cepcWorkflowService.getAvailableActions(complaintNumber, userRole);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", complaintNumber);
        data.put("userRole", userRole);
        data.put("availableActions", actions);
        return buildResponse(true, "Available actions", data);
    }

    @GetMapping("/cepc/validate-action")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> validateCepcAction(
            @RequestParam String userRole,
            @RequestParam String action) {
        boolean authorized = cepcWorkflowService.validateRoleAuthorization(userRole, action);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userRole", userRole);
        data.put("action", action);
        data.put("authorized", authorized);
        return buildResponse(true, "Role authorization check", data);
    }

    @PostMapping("/route/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> routeComplaint(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Complaint c = opt.get();
        String department = request.getOrDefault("department", "RBIO");
        String role = request.getOrDefault("role", department + "_OFFICER");
        String officer = request.getOrDefault("officer", "");

        c.setDepartment(department);
        c.setAssignedRole(role);
        c.setAssignedOfficer(officer);
        c.setStatus("assigned");
        complaintRepository.save(c);

        complaintService.addTimeline(c.getId(), "ROUTED", "system",
                "Routed to " + department + " - " + role, "pending", "assigned");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("department", department);
        data.put("assignedRole", role);
        data.put("assignedOfficer", officer);
        data.put("status", "assigned");

        return buildResponse(true, "Complaint routed successfully", data);
    }

    private ResponseEntity<Map<String, Object>> getAllTasksByDepartment(String dept, String officer) {
        List<Complaint> tasks;
        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedOfficerOrderByCreatedAtDesc(dept, officer);
        } else {
            tasks = complaintRepository.findByDepartmentOrderByCreatedAtDesc(dept);
        }
        return buildResponse(true, "All tasks retrieved", buildTaskList(tasks));
    }

    private ResponseEntity<Map<String, Object>> getCompletedByDepartment(String dept, String officer) {
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
        return buildResponse(true, "Completed tasks", buildTaskList(completed));
    }

    private ResponseEntity<Map<String, Object>> getTasksByDepartment(String dept, String role, String officer) {
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

        return buildResponse(true, "Tasks retrieved", buildTaskList(tasks));
    }

    private ResponseEntity<Map<String, Object>> assignComplaint(String complaintNumber, String dept, Map<String, String> request) {
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("assignedTo", officer);
        data.put("role", role);

        return buildResponse(true, "Complaint assigned", data);
    }

    private ResponseEntity<Map<String, Object>> performAction(String complaintNumber, String dept, Map<String, String> request) {
        String action = request.getOrDefault("action", "").toUpperCase();

        // Delegate CEPC-specific actions to CepcWorkflowService
        if ("CEPC".equals(dept) && cepcWorkflowService.isCepcAction(action)) {
            try {
                Map<String, Object> data = cepcWorkflowService.performAction(complaintNumber, action, request);
                return buildResponse(true, "Action performed: " + action, data);
            } catch (IllegalArgumentException e) {
                return buildResponse(false, e.getMessage(), null);
            }
        }

        // Delegate RBIO-specific actions to RbioWorkflowService
        if ("RBIO".equals(dept) && rbioWorkflowService.isRbioAction(action)) {
            try {
                Map<String, Object> data = rbioWorkflowService.performAction(complaintNumber, action, request);
                return buildResponse(true, "Action performed: " + action, data);
            } catch (IllegalArgumentException e) {
                return buildResponse(false, e.getMessage(), null);
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
                return buildResponse(false, "Unknown action: " + action, null);
        }

        complaintRepository.save(c);
        complaintService.addTimeline(c.getId(), action, actor, remarks, prevStatus, c.getStatus());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("action", action);
        data.put("newStatus", c.getStatus());
        data.put("assignedRole", c.getAssignedRole());
        data.put("assignedOfficer", c.getAssignedOfficer());

        return buildResponse(true, "Action performed: " + action, data);
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

    private List<Map<String, Object>> buildTaskList(List<Complaint> complaints) {
        return complaints.stream().map(c -> {
            Map<String, Object> task = new LinkedHashMap<>();
            task.put("complaintId", c.getId());
            task.put("complaintNumber", c.getComplaintNumber());
            task.put("subject", c.getSubject());
            task.put("complainantName", c.getComplainantName());
            task.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM");
            task.put("status", c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING");
            task.put("assignedAt", c.getUpdatedAt() != null ? c.getUpdatedAt().toString() : "");
            task.put("slaDueDate", c.getSlaDeadline() != null ? c.getSlaDeadline().toString()
                    : (c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : ""));
            String entityName = "";
            if (c.getEntityCode() != null && !c.getEntityCode().isBlank()) {
                entityName = c.getEntityCode();
            } else if (c.getBankId() != null) {
                entityName = bankRepository.findById(c.getBankId()).map(b -> b.getName()).orElse("");
            }
            task.put("entityName", entityName);
            task.put("department", c.getDepartment());
            task.put("assignedRole", c.getAssignedRole());
            task.put("assignedOfficer", c.getAssignedOfficer());
            task.put("triageSignal", c.getTriageSignal());
            task.put("hasAttachments", complaintAttachmentRepository.existsByComplaintId(c.getId()));
            return task;
        }).collect(Collectors.toList());
    }

    private ResponseEntity<Map<String, Object>> buildResponse(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return ResponseEntity.ok(response);
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
