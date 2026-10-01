package com.hrms.cms.controller;

import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.event.ComplaintEventPublisher;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.CategoryMasterRepository;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.CepcSlaService;
import com.hrms.cms.service.CepcWorkflowService;
import com.hrms.cms.service.ClosureClauseAccessService;
import com.hrms.cms.service.ClosureLetterService;
import com.hrms.cms.service.CommunicationTemplateService;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.KeycloakUserService;
import com.hrms.cms.service.NotificationService;
import com.hrms.cms.service.RbioCompensationService;
import com.hrms.cms.service.RbioSlaService;
import com.hrms.cms.service.RbioStatusVocabulary;
import com.hrms.cms.service.RbioWorkflowService;
import jakarta.servlet.http.HttpServletRequest;
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
    private final CategoryMasterRepository categoryMasterRepository;
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
    private final RequestIdentityResolver requestIdentityResolver;

    private final Map<String, Integer> roundRobinCounters = new ConcurrentHashMap<>();

    /**
     * The closed-status vocabulary, from RBIO_STATUS_MASTER rather than a literal.
     *
     * <p>This was one of the hardcoded copies. Two genuinely disagreed: this one held six values while
     * {@code NotificationScheduledTasks} held four, omitting {@code adjudicated} and {@code conciliated}
     * — so a complaint closed by an award or a successful conciliation was treated as OPEN by the
     * reminder scheduler and kept generating nudges about a case that was already decided.
     * RBIO_STATUS_MASTER.IS_CLOSED is now the authority; see the contract report for the mapping.
     *
     * <p>Resolved per call rather than cached in a static: the table is operator-editable, and a static
     * would freeze the vocabulary at class-load and need a redeploy to correct.
     */
    private List<String> closedStatuses() {
        return rbioStatusVocabulary != null
                ? rbioStatusVocabulary.closedStatuses()
                : RbioStatusVocabulary.legacyClosedStatuses();
    }

    /**
     * Setter-injected and OPTIONAL, matching the precedent set by {@code GlobalExceptionHandler}.
     *
     * <p>This controller is covered by {@code @ControllerSliceTest} slices, which load the web layer but
     * NOT {@code @Service} beans. A mandatory constructor dependency therefore made every slice context
     * fail to start, erroring every test method in those classes for a reason unrelated to the controller
     * under test. Falling back to the legacy vocabulary keeps the slice tests meaningful and is
     * behaviour-identical to the literal this replaced.
     */
    private RbioStatusVocabulary rbioStatusVocabulary;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setRbioStatusVocabulary(RbioStatusVocabulary vocabulary) {
        this.rbioStatusVocabulary = vocabulary;
    }

    /**
     * Setter-injected and OPTIONAL, for the same reason as {@link #rbioStatusVocabulary} above.
     *
     * <p>A mandatory CONSTRUCTOR dependency here broke every {@code @WebMvcTest} slice covering this
     * controller — 76 errors — because a slice loads the web layer but not {@code @Service} beans, so the
     * context failed to start and errored every test method for a reason unrelated to the controller. The
     * clause endpoint degrades to an empty list when absent, which is honest: with no access service there is
     * no authority to decide which clauses a role may cite, and serving a guessed set would be worse than
     * serving none. Nothing conditions the bean away in production.
     */
    private ClosureClauseAccessService closureClauseAccessService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setClosureClauseAccessService(ClosureClauseAccessService service) {
        this.closureClauseAccessService = service;
    }

    @GetMapping("/rbio/tasks")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> getRbioTasks(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String officer) {
        return getTasksByDepartment("RBIO", role, officer);
    }

    @GetMapping("/rbio/all-tasks")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
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
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
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
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> rbioAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {
        return performAction(complaintNumber, "RBIO", request);
    }

    @PostMapping("/cepc/action/{complaintNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN", "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> cepcAction(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        return performAction(complaintNumber, "CEPC", withResolvedRole(request, httpRequest));
    }

    // The acting role arrives as an identity header, not a body field, so the timeline recorded a
    // blank role. A userRole the caller passed deliberately is left alone (RBIO and admin callers do
    // pass it), but it is never taken from the body when absent, since that would let any caller
    // attribute an action to authority they do not hold.
    private Map<String, String> withResolvedRole(Map<String, String> request, HttpServletRequest httpRequest) {
        String supplied = request.get("userRole");
        if (supplied != null && !supplied.isBlank()) {
            return request;
        }
        RequestIdentity identity = requestIdentityResolver.resolve(httpRequest);
        if (identity == null || identity.getPrimaryRole() == null) {
            return request;
        }
        Map<String, String> enriched = new LinkedHashMap<>(request);
        enriched.put("userRole", identity.getPrimaryRole());
        return enriched;
    }

    @GetMapping("/rbio/completed")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> getRbioCompleted(
            @RequestParam(required = false) String officer) {
        return getCompletedByDepartment("RBIO", officer);
    }

    @GetMapping("/rbio/available-actions/{complaintNumber}")
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
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
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_CONCILIATOR", "RBIO_ADJUDICATOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
    public ResponseEntity<Map<String, Object>> getRbioSlaStats() {
        Map<String, Long> stats = rbioSlaService.getComplianceStats();
        return buildResponse(true, "RBIO SLA compliance stats", stats);
    }

    @PostMapping("/rbio/validate-award")
    @RbioRoleGuard(roles = {"RBIO_ADJUDICATOR", "RBIO_ADMIN", "RBIO_OMBUDSMAN"})
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
                    "CEPC", "CEPC_CONTACT_PERSON", officer, closedStatuses());
        } else {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    "CEPC", "CEPC_CONTACT_PERSON", closedStatuses());
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
    @RbioRoleGuard(roles = {"RBIO_OFFICER", "RBIO_SUPERVISOR", "RBIO_ADMIN",
            "RBIO_DEALING_OFFICIAL", "RBIO_REVIEWER", "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN"})
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

    /**
     * The closure clauses a role may cite (UST581-584, 769, 774).
     *
     * <p>Now served from CLOSURE_CLAUSE_MASTER via {@link ClosureClauseAccessService}. It previously built a
     * hardcoded {@code List<Map>} and never read that table, even though the table already models
     * {@code restricted_to_roles}, appealability per party, scheme version and effective dates. The Deputy
     * branch was an empty block and the Reviewer branch two comment lines, so both received the Ombudsman's
     * full list — UST582/583 unimplemented, UST584 violated.
     *
     * <p>Two invented clauses, 16(5) and 16(6), were served here unconditionally and flagged
     * {@code newIn2026}. They exist in no migration and no seeder, the seeder explicitly refuses to invent
     * 2026 codes pending RBI notification, and a complaint closed under an unconfigured clause can never be
     * appealed. They are REMOVED. The date-gated mechanism that will serve a 2026 set is in place; the clause
     * text must come from the gazette.
     *
     * <p>{@code role} is still accepted for backward compatibility but is no longer trusted on its own: when
     * a complaint number is supplied the clause set is scoped to that complaint's own scheme version, and the
     * closure path independently refuses a clause the role may not cite. A filtered picker is a courtesy, not
     * a control.
     */
    @GetMapping("/closure-clauses")
    public ResponseEntity<Map<String, Object>> getClosureClauses(
            @RequestParam String role,
            @RequestParam(required = false) String complaintNumber,
            @RequestParam(required = false) String schemeVersion) {

        if (closureClauseAccessService == null) {
            return buildResponse(true, "Closure clauses for role: " + role, List.of());
        }

        List<ClosureClauseMaster> clauses;
        if (complaintNumber != null && !complaintNumber.isBlank()) {
            Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber).orElse(null);
            clauses = closureClauseAccessService.clausesFor(complaint, role);
        } else {
            clauses = closureClauseAccessService.clausesForScheme(schemeVersion, role);
        }

        // Both the legacy RBIO shape (code/label/category/appellable) and the AA masters shape
        // (clauseCode/appealableBy*) are emitted. Two callers already read the first and a contract test
        // asserts the second, so serving both retires the hand-built endpoint without breaking either.
        List<Map<String, Object>> payload = clauses.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", c.getClauseCode());
            row.put("clauseCode", c.getClauseCode());
            row.put("label", c.getLabel());
            row.put("labelKey", c.getLabelKey());
            row.put("category", c.getCategory());
            row.put("appellable", c.isAppealableByComplainant() || c.isAppealableByEntity());
            row.put("appealableByComplainant", c.isAppealableByComplainant());
            row.put("appealableByEntity", c.isAppealableByEntity());
            row.put("schemeVersion", c.getSchemeVersion());
            return row;
        }).collect(Collectors.toList());

        return buildResponse(true, "Closure clauses for role: " + role, payload);
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
            for (String status : closedStatuses()) {
                completed.addAll(complaintRepository.findByDepartmentAndAssignedOfficerAndStatusOrderByCreatedAtDesc(dept, officer, status));
            }
        } else {
            for (String status : closedStatuses()) {
                completed.addAll(complaintRepository.findByDepartmentAndStatusOrderByCreatedAtDesc(dept, status));
            }
        }
        return buildResponse(true, "Completed tasks", buildTaskList(completed));
    }

    private ResponseEntity<Map<String, Object>> getTasksByDepartment(String dept, String role, String officer) {
        List<Complaint> tasks;

        if (officer != null && !officer.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedOfficerAndStatusNotInOrderByCreatedAtDesc(
                    dept, officer, closedStatuses());
        } else if (role != null && !role.isBlank()) {
            tasks = complaintRepository.findByDepartmentAndAssignedRoleAndStatusNotInOrderByCreatedAtDesc(
                    dept, role, closedStatuses());
        } else {
            tasks = complaintRepository.findByDepartmentAndStatusNotInOrderByCreatedAtDesc(dept, closedStatuses());
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

    /**
     * The field set every task grid renders.
     *
     * <p>{@code category}, {@code modeOfReceipt} and {@code createdAt} are published here because the
     * grids ASK for them. The CEPC dashboard declared all three as columns and they rendered blank,
     * because the row the server sent carried no such keys — a column that is always empty is worse
     * than an absent one, since an officer reads it as "this complaint has no category" rather than
     * "this screen was never wired". {@code RbioComplaintListService.toListItem} already publishes
     * modeOfReceipt and createdAt, so this closes a gap between two views of the same complaint.
     *
     * <p>Category names are resolved through ONE query for the whole page rather than per row: a
     * 598-row DO queue would otherwise issue 598 extra selects to render one column.
     */
    private List<Map<String, Object>> buildTaskList(List<Complaint> complaints) {
        Map<Long, String> categoryNames = resolveCategoryNames(complaints);
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
            // Mode of receipt IS the filing type — the same mapping RbioComplaintListService uses, so
            // the column reads identically on the RBIO and CEPC grids.
            task.put("modeOfReceipt", c.getFilingType() != null ? c.getFilingType() : "");
            task.put("category", c.getCategoryId() != null
                    ? categoryNames.getOrDefault(c.getCategoryId(), "") : "");
            task.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "");
            // The STAGE, which is not the status. A meeting being scheduled does not move the complaint
            // off in_progress — CepcWorkflowService's SCHEDULE_MEETING sets the stage and leaves the
            // status untouched — so a "Meeting Scheduled" queue bucket cannot be built from status at
            // all, and the CEPC dashboard had no way to offer one. RBIO's list publishes its milestone
            // for exactly this reason.
            task.put("workflowStage", c.getWorkflowStage() != null ? c.getWorkflowStage() : "");
            // Reopened-complaint bucket. A reopened complaint re-enters in_progress and is otherwise
            // indistinguishable from one that was never closed; the count is the only thing separating
            // them.
            task.put("reopenCount", c.getReopenCount() != null ? c.getReopenCount() : 0);
            return task;
        }).collect(Collectors.toList());
    }

    /**
     * Category id → display name for one page of complaints, in a single query.
     *
     * <p>Returns an empty map when no complaint on the page carries a category, which is the common
     * case on this data (34 of 2,316 CEPC rows have one): the grid then renders an empty cell, and an
     * empty cell is the honest rendering of an unclassified complaint.
     */
    private Map<Long, String> resolveCategoryNames(List<Complaint> complaints) {
        Set<Long> ids = complaints.stream()
                .map(Complaint::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        categoryMasterRepository.findAllById(ids).forEach(cat -> {
            if (cat.getCategoryName() != null) {
                names.put(cat.getId(), cat.getCategoryName());
            }
        });
        return names;
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
