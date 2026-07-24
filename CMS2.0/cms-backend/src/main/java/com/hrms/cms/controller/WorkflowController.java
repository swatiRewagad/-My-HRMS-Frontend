package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.event.ComplaintEventPublisher;
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

        // UST606: TRANSFER_IN — notify destination user when transfer is approved
        if ("APPROVE_TRANSFER".equals(action) && c.getAssignedOfficer() != null && !c.getAssignedOfficer().isBlank()) {
            notificationService.send(c.getAssignedOfficer(), "TRANSFER_IN",
                    "Complaint transferred to you",
                    "Complaint " + c.getComplaintNumber() + " has arrived via inter-office transfer.",
                    c.getComplaintNumber(), "COMPLAINT",
                    "/workflow/crpc/complaint/" + c.getComplaintNumber());
        }

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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("department", department);
        data.put("assignedRole", role);
        data.put("assignedOfficer", officer);
        data.put("status", "assigned");

        return buildResponse(true, "Complaint routed successfully", data);
    }

    // ═══ UST581-584: Closure Clauses filtered by role ═══
    @GetMapping("/closure-clauses")
    public ResponseEntity<Map<String, Object>> getClosureClauses(@RequestParam String role) {
        List<Map<String, Object>> clauses = new ArrayList<>();

        // Base clauses available to all
        clauses.add(Map.of("code", "16(1)", "label", "Resolved to satisfaction", "category", "RESOLUTION"));
        clauses.add(Map.of("code", "16(2)(a)", "label", "Not maintainable - time barred", "category", "NON_MAINTAINABLE"));
        clauses.add(Map.of("code", "16(2)(b)", "label", "Not maintainable - frivolous/vexatious", "category", "NON_MAINTAINABLE"));
        clauses.add(Map.of("code", "16(2)(g)", "label", "Not maintainable - anonymous", "category", "NON_MAINTAINABLE"));
        clauses.add(Map.of("code", "16(2)(h)", "label", "Not maintainable - insufficient information", "category", "NON_MAINTAINABLE"));
        clauses.add(Map.of("code", "16(3)", "label", "Closed - complainant not responding", "category", "CLOSURE"));
        clauses.add(Map.of("code", "16(4)", "label", "Closed - matter settled", "category", "CLOSURE"));

        // Appellable clauses - only Ombudsman can use
        if ("OMBUDSMAN".equalsIgnoreCase(role) || "RBIO_ADMIN".equalsIgnoreCase(role)) {
            clauses.add(Map.of("code", "16(2)(c)", "label", "Not maintainable - sub-judice", "category", "NON_MAINTAINABLE", "appellable", true));
            clauses.add(Map.of("code", "16(2)(d)", "label", "Not maintainable - outside jurisdiction", "category", "NON_MAINTAINABLE", "appellable", true));
            clauses.add(Map.of("code", "16(2)(e)", "label", "Not maintainable - already settled by RBI", "category", "NON_MAINTAINABLE", "appellable", true));
            clauses.add(Map.of("code", "16(2)(f)", "label", "Not maintainable - covered by other dispute mechanism", "category", "NON_MAINTAINABLE", "appellable", true));
            clauses.add(Map.of("code", "15(1)(a)", "label", "Award - full relief", "category", "AWARD", "appellable", true));
            clauses.add(Map.of("code", "15(1)(b)", "label", "Award - partial relief with compensation", "category", "AWARD", "appellable", true));
        }

        // Deputy Ombudsman: non-appealable subset
        if ("DEPUTY_OMBUDSMAN".equalsIgnoreCase(role)) {
            // Excludes 16(2)(c)-(f) and 15(1)(a)/(b) - already not added for this role
        }

        // Reviewer: only delegated non-maintainable clauses (excludes 16(2)(c)-(f), 15(1)(a)/(b))
        // Already handled by not adding them for roles other than OMBUDSMAN

        // RBIOS 2026 new clauses
        clauses.add(Map.of("code", "16(5)", "label", "Closed - entity licence cancelled/surrendered", "category", "CLOSURE", "newIn2026", true));
        clauses.add(Map.of("code", "16(6)", "label", "Closed - complaint withdrawn by complainant", "category", "CLOSURE", "newIn2026", true));

        return buildResponse(true, "Closure clauses for role: " + role, clauses);
    }

    // ═══ UST656: Email Validation - RBI Domain Only ═══
    @PostMapping("/validate-email-recipients")
    public ResponseEntity<Map<String, Object>> validateEmailRecipients(
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

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", invalidEmails.isEmpty());
        data.put("validEmails", validEmails);
        data.put("invalidEmails", invalidEmails);
        if (!invalidEmails.isEmpty()) {
            data.put("error", "Only official RBI email addresses can be used for outbound complaint emails");
        }
        return buildResponse(invalidEmails.isEmpty(), invalidEmails.isEmpty() ? "All recipients valid" : "Invalid recipients detected", data);
    }

    // ═══ UST655: Get assignable users (exclude SECRETARY) ═══
    @GetMapping("/assignable-users")
    public ResponseEntity<Map<String, Object>> getAssignableUsers(@RequestParam String role) {
        List<Map<String, Object>> users = keycloakUserService.getUsersByRole(role);
        // Filter out users with SECRETARY role
        users = users.stream()
                .filter(u -> {
                    String userId = (String) u.getOrDefault("userId", "");
                    // Additional filter: exclude any user whose userId or role contains secretary
                    return !userId.toLowerCase().contains("secretary");
                })
                .collect(Collectors.toList());
        return buildResponse(true, "Assignable users for role: " + role, users);
    }

    // ═══ UST504-505: Check closure letter dispatch status ═══
    @GetMapping("/closure-status/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getClosureStatus(@PathVariable String complaintNumber) {
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(complaintNumber);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Complaint c = opt.get();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("hasEmail", c.getComplainantEmail() != null && !c.getComplainantEmail().isBlank());
        data.put("closureLetterSentAt", c.getClosureLetterSentAt() != null ? c.getClosureLetterSentAt().toString() : null);
        data.put("status", c.getStatus());
        data.put("closureCause", c.getClosureCause());
        data.put("closureClause", c.getClosureClause());
        data.put("customClosureText", c.getCustomClosureText());
        return buildResponse(true, "Closure status", data);
    }

    private ResponseEntity<Map<String, Object>> getAllTasksByDepartment(String dept, String officer) {
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
        } else {
            tasks = complaintRepository.findByDepartmentOrderByCreatedAtDesc(dept);
        }
        return buildResponse(true, "All tasks retrieved", buildTaskList(tasks));
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

        // ═══ Notification triggers based on action ═══
        triggerActionNotifications(c, action, actor, prevStatus);

        // ═══ Kafka event publishing ═══
        publishActionEvent(c, action, actor, prevStatus);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", c.getComplaintNumber());
        data.put("action", action);
        data.put("newStatus", c.getStatus());
        data.put("assignedRole", c.getAssignedRole());
        data.put("assignedOfficer", c.getAssignedOfficer());

        return buildResponse(true, "Action performed: " + action, data);
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
