package com.hrms.cms.controller;

import com.hrms.cms.dto.FileComplaintRequest;
import com.hrms.cms.dto.NodalAssessmentRequest;
import com.hrms.cms.dto.complaint.ComplaintCommentResponse;
import com.hrms.cms.dto.complaint.ComplaintDetailResponse;
import com.hrms.cms.dto.complaint.ComplaintEmailItem;
import com.hrms.cms.dto.complaint.ComplaintEmailRequest;
import com.hrms.cms.dto.complaint.ComplaintEmailThreadResponse;
import com.hrms.cms.dto.complaint.ComplaintPhoneLookupItem;
import com.hrms.cms.dto.complaint.ComplaintRegistrationAck;
import com.hrms.cms.dto.complaint.ComplaintTimelineItem;
import com.hrms.cms.dto.complaint.EmailAttachmentRef;
import com.hrms.cms.dto.complaint.ForwardComplaintResponse;
import com.hrms.cms.dto.complaint.NodalRecordCommentResponse;
import com.hrms.cms.dto.complaint.NodalRecordRow;
import com.hrms.cms.dto.complaint.OfficeHeadDecisionResponse;
import com.hrms.cms.dto.complaint.RbioReassignResponse;
import com.hrms.cms.dto.complaint.RecentComplaintItem;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintComment;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.entity.RegulatedEntity;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintCommentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RegulatedEntityRepository;
import com.hrms.cms.repository.SimulatedEmailRepository;
import com.hrms.cms.config.EmailDispatchMode;
import com.hrms.cms.security.CallerIdentity;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.ComplaintService;
import com.hrms.cms.service.RbioHierarchyService;
import com.hrms.cms.service.triage.IntakeTriageService;
import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.DeliveryStatus;
import com.rbi.cms.common.enums.RoleConstants;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.domain.PageRequest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class ComplaintApiV1Controller {

    private final ComplaintService complaintService;
    private final BankRepository bankRepository;
    private final ComplaintCategoryRepository categoryRepository;
    private final RegulatedEntityRepository regulatedEntityRepository;
    private final SimulatedEmailRepository simulatedEmailRepository;
    private final ComplaintRepository complaintRepository;
    private final com.hrms.cms.event.NotificationEventPublisher notificationEventPublisher;
    private final ComplaintCommentRepository complaintCommentRepository;
    private final IntakeTriageService triageService;
    private final Validator validator;
    private final com.hrms.cms.service.ComplaintRoutingService complaintRoutingService;
    private final com.hrms.cms.service.KeycloakUserService keycloakUserService;
    private final com.hrms.cms.repository.OfficerAvailabilityRepository officerAvailabilityRepository;
    private final com.hrms.cms.repository.OfficeCodeMasterRepository officeCodeMasterRepository;
    private final RbioHierarchyService rbioHierarchyService;
    private final com.hrms.cms.service.OfficerDirectoryService officerDirectoryService;
    private final com.hrms.cms.event.ComplaintEventPublisher complaintEventPublisher;
    private final com.hrms.cms.service.NodalOfficerRecordService nodalOfficerRecordService;
    private final com.hrms.cms.service.NotificationService notificationService;
    private final com.hrms.cms.service.CepcAuditService auditService;
    private final CallerIdentity callerIdentity;

    @PostMapping
    public ResponseEntity<ApiResponse<ComplaintRegistrationAck>> registerComplaint(
            @RequestBody Map<String, Object> request) {
        FileComplaintRequest req = new FileComplaintRequest();
        req.setComplainantName((String) request.getOrDefault("complainantName", ""));
        req.setComplainantEmail((String) request.getOrDefault("complainantEmail", ""));
        req.setComplainantPhone((String) request.getOrDefault("complainantPhone", ""));
        req.setComplainantAddress((String) request.get("complainantAddress"));
        req.setComplainantState((String) request.get("complainantState"));
        req.setComplainantDistrict((String) request.get("complainantDistrict"));
        req.setComplainantPincode((String) request.get("complainantPincode"));
        req.setEntityState((String) request.get("entityState"));
        req.setEntityDistrict((String) request.get("entityDistrict"));
        // Blank must become null: the public wizard omits this field, and "" fails the 6-digit pattern.
        String entityPincode = (String) request.get("entityPincode");
        req.setEntityPincode(entityPincode == null || entityPincode.isBlank() ? null : entityPincode.trim());
        req.setEntityBranchName((String) request.get("entityBranchName"));
        req.setSubject((String) request.getOrDefault("subject", ""));
        req.setDescription((String) request.getOrDefault("description", ""));
        req.setReliefSought((String) request.get("reliefSought"));
        req.setPriority((String) request.getOrDefault("priority", "medium"));
        req.setFilingType((String) request.getOrDefault("filingType", "ONLINE"));
        req.setDraftId((String) request.get("draftId"));

        // The wizard's full state, so it survives the draft being deleted right after this call.
        if (request.get("formData") instanceof Map<?, ?> rawFormData) {
            Map<String, Object> formData = new LinkedHashMap<>();
            rawFormData.forEach((k, v) -> {
                if (k != null) formData.put(k.toString(), v);
            });
            req.setFormData(formData);
        }
        if (request.get("eligibilityAnswers") instanceof Map<?, ?> rawAnswers) {
            Map<String, String> answers = new LinkedHashMap<>();
            rawAnswers.forEach((k, v) -> {
                if (k != null && v != null) answers.put(k.toString(), v.toString());
            });
            req.setEligibilityAnswers(answers);
        }

        if (request.get("regulatedEntityId") != null) {
            req.setRegulatedEntityId(Long.valueOf(request.get("regulatedEntityId").toString()));
        }
        if (request.get("entityName") != null) {
            req.setEntityName(request.get("entityName").toString());
        }
        if (request.get("entityType") != null) {
            req.setEntityType(request.get("entityType").toString());
        }
        if (request.get("amountInvolved") != null) {
            try {
                req.setAmountInvolved(new java.math.BigDecimal(request.get("amountInvolved").toString()));
            } catch (NumberFormatException e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Validation failed: amountInvolved must be a number"));
            }
        }
        if (request.get("priorReComplaint") != null) {
            req.setPriorReComplaint(Boolean.valueOf(request.get("priorReComplaint").toString()));
        }
        if (request.get("reComplaintDate") != null) {
            req.setReComplaintDate(java.time.LocalDate.parse(request.get("reComplaintDate").toString()));
        }
        if (request.get("reComplaintReference") != null) {
            req.setReComplaintReference(request.get("reComplaintReference").toString());
        }
        if (request.get("reRepliedAndDissatisfied") != null) {
            req.setReRepliedAndDissatisfied(Boolean.valueOf(request.get("reRepliedAndDissatisfied").toString()));
        }

        // Resolve category name to ID
        if (request.get("category") != null) {
            String categoryName = request.get("category").toString();
            req.setCategoryName(categoryName);
            categoryRepository.findFirstByNameIgnoreCase(categoryName)
                    .ifPresent(cat -> req.setCategoryId(cat.getId()));
        }
        if (req.getCategoryId() == null && request.get("categoryId") != null) {
            req.setCategoryId(Long.valueOf(request.get("categoryId").toString()));
        }

        Set<ConstraintViolation<FileComplaintRequest>> violations = validator.validate(req);
        if (!violations.isEmpty()) {
            String errors = violations.stream()
                    .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                    .collect(Collectors.joining("; "));
            return ResponseEntity.badRequest().body(ApiResponse.error("Validation failed: " + errors));
        }

        Complaint c = complaintService.fileComplaint(req);

        try {
            triageService.triageOnRegistration(c);
        } catch (Exception e) {
            // Triage failure must not block complaint registration
        }

        ComplaintRegistrationAck ack = ComplaintRegistrationAck.builder()
                .complaintId(c.getComplaintNumber())
                .status("REGISTERED")
                .registeredAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : LocalDateTime.now().toString())
                .slaDueDate(c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString()
                        : LocalDateTime.now().plusDays(30).toString())
                .acknowledgementMessage(
                        "Your complaint has been registered successfully. Use the reference number to track status.")
                .build();

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ack, "Complaint registered successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ComplaintPhoneLookupItem>>> getComplaintsByPhone(
            @RequestParam String phone) {
        List<Complaint> complaints = complaintService.getByComplainantPhone(phone);

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy");

        List<ComplaintPhoneLookupItem> items = complaints.stream().map(c -> {
            String bankName = "";
            if (c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            return ComplaintPhoneLookupItem.builder()
                    .complaintId(c.getComplaintNumber())
                    .entityName(bankName.isEmpty() ? c.getSubject() : bankName)
                    .complaintDate(c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "")
                    .status(c.getStatus() != null ? c.getStatus().toUpperCase() : "PENDING")
                    .comments(c.getDescription() != null
                            ? c.getDescription().substring(0, Math.min(c.getDescription().length(), 50)) : "")
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items, "Complaints retrieved"));
    }

    @GetMapping("/{complaintNumber}")
    public ResponseEntity<ApiResponse<ComplaintDetailResponse>> getComplaintDetail(
            @PathVariable String complaintNumber) {
        Complaint c;
        try {
            c = complaintService.getByComplaintNumber(complaintNumber);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error("Complaint not found"));
        }
        List<ComplaintTimeline> timeline = complaintService.getTimeline(c.getId());

        String bankName = c.getEntityName() != null ? c.getEntityName() : "";
        if (bankName.isEmpty() && c.getBankId() != null) {
            bankName = bankRepository.findById(c.getBankId())
                    .map(b -> b.getName())
                    .orElse("");
        }

        RegulatedEntity regulatedEntity = c.getRegulatedEntityId() != null
                ? regulatedEntityRepository.findById(c.getRegulatedEntityId()).orElse(null)
                : null;
        if (bankName.isEmpty() && regulatedEntity != null) {
            bankName = regulatedEntity.getName();
        }

        String categoryName = c.getCategoryName() != null ? c.getCategoryName() : "General";
        if ("General".equals(categoryName) && c.getCategoryId() != null) {
            categoryName = categoryRepository.findById(c.getCategoryId())
                    .map(cat -> cat.getName()).orElse("General");
        }

        String registeredAt = c.getCreatedAt() != null ? c.getCreatedAt().toString() : "";
        String slaDueDate = c.getCreatedAt() != null ? c.getCreatedAt().plusDays(30).toString() : "";

        ComplaintDetailResponse detail = ComplaintDetailResponse.builder()
                .id(c.getId())
                .complaintId(c.getComplaintNumber())
                .complaintNumber(c.getComplaintNumber())
                .category(categoryName)
                .priority(c.getPriority() != null ? c.getPriority().toUpperCase() : "MEDIUM")
                .status(c.getStatus() != null ? c.getStatus().toUpperCase() : "NEW")
                .subject(c.getSubject())
                .description(c.getDescription())
                .complainantName(c.getComplainantName())
                .complainantEmail(c.getComplainantEmail())
                .complainantPhone(c.getComplainantPhone())
                .complainantAddress(c.getComplainantAddress())
                .complainantState(c.getComplainantState())
                .complainantDistrict(c.getComplainantDistrict())
                .complainantPincode(c.getComplainantPincode())
                .entityName(bankName)
                .regulatedEntityId(c.getRegulatedEntityId())
                .entityType(regulatedEntity != null ? regulatedEntity.getEntityType() : null)
                .entityCategory(c.getEntityCategory())
                .bsrCode(c.getEntityBsrCode())
                .entityPincode(c.getEntityPincode())
                .entityState(c.getEntityState())
                .entityDistrict(c.getEntityDistrict())
                .entityCity(c.getEntityCity())
                .entityBranchName(c.getEntityBranchName())
                .entityBranchCategory(c.getEntityBranchCategory())
                .entityAddress(c.getEntityAddress())
                .cosmosCode(c.getCosmosCode())
                .schemeVersion(c.getSchemeVersion())
                .amountInvolved(c.getAmountInvolved())
                .transactionDate(c.getBankComplaintDate() != null ? c.getBankComplaintDate().toString() : null)
                .assignedTeam(c.getAssignedOfficerName() != null ? c.getAssignedOfficerName()
                        : c.getAssignedOfficer() != null ? c.getAssignedOfficer() : "Unassigned")
                .assignedTo(c.getAssignedOfficer())
                .assignedToName(c.getAssignedOfficerName())
                .registeredAt(registeredAt)
                .createdAt(registeredAt)
                .slaDueDate(slaDueDate)
                .resolvedAt(c.getResolvedAt() != null ? c.getResolvedAt().toString() : null)
                .timeline(timeline.stream().map(t -> ComplaintTimelineItem.builder()
                                .fromStatus(t.getFromStatus())
                                .toStatus(t.getToStatus())
                                .action(t.getAction())
                                .timestamp(t.getPerformedAt() != null ? t.getPerformedAt().toString() : "")
                                .remarks(t.getRemarks())
                                .build())
                        .collect(Collectors.toList()))
                .triageSignal(c.getTriageSignal())
                .triageFlags(c.getTriageFlags())
                .eligibilityTimeline(c.getEligibilityTimeline())
                .closureClause(c.getClosureClause())
                .proposedAction(c.getProposedAction())
                .proposedClause(c.getProposedClause())
                .forwardedOfficeCode(c.getForwardedOfficeCode())
                .forwardedOfficeName(c.getForwardedOfficeCode() != null
                        ? officeCodeMasterRepository.findByOfficeCodeAndIsActiveTrue(c.getForwardedOfficeCode())
                                .map(o -> o.getOfficeName()).orElse(c.getForwardedOfficeCode())
                        : null)
                .preForwardOfficer(c.getPreForwardOfficer())
                .preForwardRole(c.getPreForwardRole())
                .closureClauseDescription(c.getClosureClauseDescription())
                .complaintStatusOnPortal(c.getComplaintStatusOnPortal())
                .speakingOrderGenerated(c.getSpeakingOrderGenerated())
                .gistOfCase(c.getGistOfCase())
                .gistOfCaseRegional(c.getGistOfCaseRegional())
                .build();

        return ResponseEntity.ok(ApiResponse.success(detail, "OK"));
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<RecentComplaintItem>>> getRecentComplaints(
            @RequestParam(defaultValue = "10") int limit) {
        limit = Math.min(limit, 50);
        List<Complaint> complaints = complaintService.getAllComplaintsPaged(PageRequest.of(0, limit)).getContent();

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM yyyy");

        List<RecentComplaintItem> items = complaints.stream().map(c -> {
            String bankName = "";
            if (c.getBankId() != null) {
                bankName = bankRepository.findById(c.getBankId())
                        .map(b -> b.getName()).orElse("");
            }

            return RecentComplaintItem.builder()
                    .complaintNumber(c.getComplaintNumber())
                    .subject(c.getSubject())
                    .entityName(bankName)
                    .complainantName(c.getComplainantName())
                    .status(c.getStatus())
                    .date(c.getCreatedAt() != null ? c.getCreatedAt().format(fmt) : "")
                    .build();
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items, "Recent complaints retrieved"));
    }

    private static final DateTimeFormatter EMAIL_DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final String EMAIL_DIRECTION_OUTBOUND = "OUTBOUND";

    @org.springframework.beans.factory.annotation.Value("${cms.email.dispatch.mode:EVENT}")
    private EmailDispatchMode emailDispatchMode;

    /**
     * Stores the lifecycle state a send should land in, and requests dispatch when one is needed.
     * Both columns are written: delivery_status is authoritative, and status is kept in step so the legacy
     * read surfaces over SIMULATED_EMAILS do not report a stale value.
     */
    private void applySendOutcome(SimulatedEmail email, DeliveryStatus resolved) {
        if (resolved == DeliveryStatus.PENDING && emailDispatchMode == EmailDispatchMode.INLINE) {
            email.setDeliveryStatus(DeliveryStatus.SENT);
            email.setStatus(DeliveryStatus.SENT.name());
            email.setProcessedAt(LocalDateTime.now());
            return;
        }
        email.setDeliveryStatus(resolved);
        email.setStatus(resolved.name());
    }

    /** No-op unless the row is genuinely queued, so INLINE mode and plain draft saves publish nothing. */
    private void requestDispatchIfQueued(SimulatedEmail saved) {
        if (saved.getDeliveryStatus() == DeliveryStatus.PENDING) {
            notificationEventPublisher.publishEmailDispatchRequested(saved);
        }
    }

    private static String sendOutcomeMessage(SimulatedEmail saved) {
        DeliveryStatus delivery = saved.getDeliveryStatus();
        if (delivery == DeliveryStatus.DRAFT) {
            return "Draft saved";
        }
        return delivery == DeliveryStatus.PENDING ? "Email queued for sending" : "Email sent";
    }

    @GetMapping("/{complaintNumber}/emails")
    public ResponseEntity<ApiResponse<List<ComplaintEmailItem>>> getComplaintEmails(
            @PathVariable String complaintNumber) {

        if (complaintRepository.findByComplaintNumber(complaintNumber).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Complaint " + complaintNumber + " was not found"));
        }

        List<ComplaintEmailItem> items = simulatedEmailRepository
                .findByComplaintNumberOrderBySentAtDesc(complaintNumber).stream()
                .map(ComplaintApiV1Controller::toEmailItem)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    /**
     * The whole conversation behind one activity row. The list endpoint already carries each mail's own
     * fields, so this exists for the reading pane, which shows every message sharing the thread.
     */
    @GetMapping("/{complaintNumber}/emails/{emailId}")
    public ResponseEntity<ApiResponse<ComplaintEmailThreadResponse>> getComplaintEmailThread(
            @PathVariable String complaintNumber,
            @PathVariable Long emailId) {

        Optional<SimulatedEmail> found = findComplaintEmail(complaintNumber, emailId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Email " + emailId + " was not found on complaint " + complaintNumber));
        }
        SimulatedEmail email = found.get();

        List<ComplaintEmailItem> messages = simulatedEmailRepository
                .findByThreadIdOrderBySentAtAsc(email.getThreadId()).stream()
                .map(ComplaintApiV1Controller::toEmailItem)
                .collect(Collectors.toList());

        DeliveryStatus threadDelivery = effectiveDeliveryStatus(email);
        ComplaintEmailThreadResponse thread = ComplaintEmailThreadResponse.builder()
                .threadId(email.getThreadId())
                .complaintNumber(email.getComplaintNumber())
                .subject(email.getSubject())
                .status(threadDelivery != null ? threadDelivery.name() : email.getStatus())
                .messageCount(messages.size())
                .email(toEmailItem(email))
                .messages(messages)
                .build();

        return ResponseEntity.ok(ApiResponse.success(thread));
    }

    @PostMapping("/{complaintNumber}/emails")
    public ResponseEntity<ApiResponse<ComplaintEmailItem>> createComplaintEmail(
            @PathVariable String complaintNumber,
            @Valid @RequestBody ComplaintEmailRequest request) {

        Optional<Complaint> complaint = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaint.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Complaint " + complaintNumber + " was not found"));
        }

        // A reply joins the thread it answers; anything else starts its own, so a draft and the mails
        // already sent on this complaint stay separate rows in the activity list.
        String threadId = UUID.randomUUID().toString();
        if (request.getInReplyToId() != null) {
            Optional<SimulatedEmail> parent = findComplaintEmail(complaintNumber, request.getInReplyToId());
            if (parent.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(
                        "The email being replied to was not found on complaint " + complaintNumber));
            }
            // Only a mail that actually went out can be replied to - a draft has no recipient who could
            // have received it, and one still queued or failed has not reached anyone either.
            if (effectiveDeliveryStatus(parent.get()) != DeliveryStatus.SENT) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.error(
                        "This email has not been sent yet, so it cannot be replied to. Send it first."));
            }
            threadId = parent.get().getThreadId();
        }

        SimulatedEmail email = SimulatedEmail.builder()
                .messageId(UUID.randomUUID().toString())
                .threadId(threadId)
                .fromEmail(request.getFrom().trim())
                .toEmail(request.getTo().trim())
                .ccEmail(blankToNull(request.getCc()))
                .bccEmail(blankToNull(request.getBcc()))
                .subject(request.getSubject().trim())
                .body(request.getBody())
                .direction(EMAIL_DIRECTION_OUTBOUND)
                .complaintId(complaint.get().getId())
                .complaintNumber(complaintNumber)
                .createdBy(callerIdentity.username())
                .build();
        applySendOutcome(email, request.resolvedStatus());

        SimulatedEmail saved = simulatedEmailRepository.save(email);
        requestDispatchIfQueued(saved);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toEmailItem(saved), sendOutcomeMessage(saved)));
    }

    /**
     * Saves further edits to a draft, and sends it when the request carries status SENT. Only a draft is
     * editable: a mail that has gone out must not change afterwards.
     */
    @PutMapping("/{complaintNumber}/emails/{emailId}")
    public ResponseEntity<ApiResponse<ComplaintEmailItem>> updateComplaintEmail(
            @PathVariable String complaintNumber,
            @PathVariable Long emailId,
            @Valid @RequestBody ComplaintEmailRequest request) {

        Optional<SimulatedEmail> found = findComplaintEmail(complaintNumber, emailId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Email " + emailId + " was not found on complaint " + complaintNumber));
        }

        SimulatedEmail email = found.get();
        // Only a draft is editable. A queued mail must not mutate underneath the dispatcher, and a sent
        // one is a record of what went out.
        if (effectiveDeliveryStatus(email) != DeliveryStatus.DRAFT) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("This email is no longer a draft and can no longer be edited."));
        }

        email.setFromEmail(request.getFrom().trim());
        email.setToEmail(request.getTo().trim());
        email.setCcEmail(blankToNull(request.getCc()));
        email.setBccEmail(blankToNull(request.getBcc()));
        email.setSubject(request.getSubject().trim());
        email.setBody(request.getBody());
        // sentAt is deliberately left alone - it is the row's creation time and the activity list's sort key.
        applySendOutcome(email, request.resolvedStatus());

        SimulatedEmail saved = simulatedEmailRepository.save(email);
        requestDispatchIfQueued(saved);

        return ResponseEntity.ok(ApiResponse.success(toEmailItem(saved), sendOutcomeMessage(saved)));
    }

    /**
     * Queues a failed mail for another dispatch attempt.
     *
     * <p>Without this a failed mail is a dead end: it is not a draft so it cannot be edited, and it never
     * went out so it cannot be replied to. It is also the manual recovery path when the broker was
     * unreachable at send time and the row was left behind.</p>
     */
    @PostMapping("/{complaintNumber}/emails/{emailId}/retry")
    public ResponseEntity<ApiResponse<ComplaintEmailItem>> retryComplaintEmail(
            @PathVariable String complaintNumber,
            @PathVariable Long emailId) {

        Optional<SimulatedEmail> found = findComplaintEmail(complaintNumber, emailId);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Email " + emailId + " was not found on complaint " + complaintNumber));
        }

        SimulatedEmail email = found.get();
        if (effectiveDeliveryStatus(email) != DeliveryStatus.FAILED) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Only an email whose sending failed can be retried."));
        }

        email.setLastError(null);
        applySendOutcome(email, DeliveryStatus.PENDING);

        SimulatedEmail saved = simulatedEmailRepository.save(email);
        requestDispatchIfQueued(saved);

        return ResponseEntity.ok(ApiResponse.success(toEmailItem(saved), sendOutcomeMessage(saved)));
    }

    /** Scoped by complaint so an email id from another complaint cannot be read or edited through this path. */
    private Optional<SimulatedEmail> findComplaintEmail(String complaintNumber, Long emailId) {
        return simulatedEmailRepository.findById(emailId)
                .filter(e -> complaintNumber.equals(e.getComplaintNumber()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * The row's delivery lifecycle, falling back to the legacy status column for rows written before
     * DELIVERY_STATUS existed. Null means the row has no lifecycle at all - an inbound mail, or a status
     * that was never a lifecycle value. Every read path goes through this so an environment that has not
     * run the backfill renders exactly as it did before.
     */
    private static DeliveryStatus effectiveDeliveryStatus(SimulatedEmail e) {
        if (e.getDeliveryStatus() != null) {
            return e.getDeliveryStatus();
        }
        return DeliveryStatus.parse(e.getStatus()).orElse(null);
    }

    private static ComplaintEmailItem toEmailItem(SimulatedEmail e) {
        DeliveryStatus delivery = effectiveDeliveryStatus(e);

        return ComplaintEmailItem.builder()
                .id(e.getId())
                .messageId(e.getMessageId())
                .threadId(e.getThreadId())
                .complaintNumber(e.getComplaintNumber())
                .subject(e.getSubject())
                .from(e.getFromEmail())
                .to(e.getToEmail())
                .cc(e.getCcEmail())
                .bcc(e.getBccEmail())
                .body(e.getBody())
                .date(e.getSentAt() != null ? e.getSentAt().format(EMAIL_DATE_FMT) : "")
                // The lifecycle is reported through the existing status field rather than a parallel one,
                // so PENDING and FAILED reach the UI without it having to choose between two sources.
                .status(delivery != null ? delivery.name() : e.getStatus())
                .direction(e.getDirection())
                .assignedTo(e.getCreatedBy())
                .sentAt(e.getSentAt() != null ? e.getSentAt().toString() : null)
                .updatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null)
                .lastError(e.getLastError())
                .canReply(delivery == DeliveryStatus.SENT)
                .editable(delivery == DeliveryStatus.DRAFT)
                .canRetry(delivery == DeliveryStatus.FAILED)
                .attachments(e.getAttachmentUrl() != null
                        ? List.of(EmailAttachmentRef.builder().name(e.getAttachmentUrl()).size("").build())
                        : List.of())
                .build();
    }

    @GetMapping("/{complaintNumber}/comments")
    public ResponseEntity<ApiResponse<List<ComplaintCommentResponse>>> getComments(
            @PathVariable String complaintNumber) {
        List<ComplaintComment> comments = complaintCommentRepository.findByComplaintNumberOrderByCreatedAtDesc(complaintNumber);

        List<ComplaintCommentResponse> items = comments.stream()
                .map(ComplaintApiV1Controller::toCommentResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    private static ComplaintCommentResponse toCommentResponse(ComplaintComment c) {
        return ComplaintCommentResponse.builder()
                .id(c.getId())
                .author(c.getAuthor())
                .initials(c.getInitials())
                .text(c.getText())
                .role(c.getRole())
                .color(c.getColor())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : "")
                .build();
    }

    private static NodalRecordCommentResponse toNodalCommentResponse(ComplaintComment c) {
        return NodalRecordCommentResponse.builder()
                .id(c.getId())
                .author(c.getAuthor())
                .initials(c.getInitials())
                .text(c.getText())
                .target(c.getTarget())
                .color(c.getColor())
                .createdAt(c.getCreatedAt() != null ? c.getCreatedAt().toString() : "")
                .build();
    }

    @PostMapping("/{complaintNumber}/comments")
    public ResponseEntity<ApiResponse<ComplaintCommentResponse>> addComment(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, String> request) {

        ComplaintComment comment = ComplaintComment.builder()
                .complaintNumber(complaintNumber)
                .author(request.getOrDefault("author", "Unknown"))
                .initials(request.getOrDefault("initials", ""))
                .text(request.getOrDefault("text", ""))
                .role(request.getOrDefault("role", ""))
                .color(request.getOrDefault("color", null))
                .build();

        complaintCommentRepository.save(comment);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toCommentResponse(comment), "Comment added"));
    }

    @GetMapping("/nodal-records")
    public ResponseEntity<ApiResponse<List<NodalRecordRow>>> getNodalRecords() {
        return ResponseEntity.ok(ApiResponse.success(nodalOfficerRecordService.listWorklist()));
    }

    @PostMapping("/nodal-records/{recordNumber}/forward-to-re")
    public ResponseEntity<ApiResponse<NodalRecordRow>> forwardNodalRecordToRe(
            @PathVariable String recordNumber,
            @Valid @RequestBody NodalAssessmentRequest request) {

        NodalRecordRow record = nodalOfficerRecordService.forwardToRegulatedEntity(
                recordNumber, request, callerIdentity.username());

        return ResponseEntity.ok(ApiResponse.success(record, "Record forwarded to the regulated entity"));
    }

    @GetMapping("/nodal-records/{recordNumber}/comments")
    public ResponseEntity<ApiResponse<List<NodalRecordCommentResponse>>> getNodalRecordComments(
            @PathVariable String recordNumber) {
        List<ComplaintComment> comments = complaintCommentRepository.findByNoRecordNumberOrderByCreatedAtDesc(recordNumber);

        List<NodalRecordCommentResponse> items = comments.stream()
                .map(ComplaintApiV1Controller::toNodalCommentResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(items));
    }

    @PostMapping("/nodal-records/{recordNumber}/comments")
    public ResponseEntity<ApiResponse<NodalRecordCommentResponse>> addNodalRecordComment(
            @PathVariable String recordNumber,
            @RequestBody Map<String, String> request) {

        String target = request.getOrDefault("target", "NO");
        ComplaintComment comment = ComplaintComment.builder()
                .noRecordNumber(recordNumber)
                .complaintNumber(request.getOrDefault("complaintNumber", ""))
                .author(request.getOrDefault("author", "Unknown"))
                .initials(request.getOrDefault("initials", ""))
                .text(request.getOrDefault("text", ""))
                .target(target)
                .color(request.getOrDefault("color", null))
                .build();

        complaintCommentRepository.save(comment);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toNodalCommentResponse(comment), "Comment added"));
    }

    @PostMapping("/{complaintNumber}/send-for-approval")
    public ResponseEntity<ApiResponse<ForwardComplaintResponse>> sendForApproval(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        String target = (String) request.getOrDefault("target", "");
        String assignedTo = (String) request.getOrDefault("assignedTo", "");
        String assignedToName = (String) request.getOrDefault("assignedToName", "");
        String assignmentMode = (String) request.getOrDefault("assignmentMode", "MANUAL");
        String performedBy = (String) request.getOrDefault("performedBy", assignedTo);
        String performedByRole = (String) request.get("performedByRole");
        String proposedAction = (String) request.get("proposedAction");
        String proposedClause = (String) request.get("proposedClause");

        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Complaint not found: " + complaintNumber));
        }

        String oldStatus = complaint.getStatus();
        String newStatus;
        boolean isOfficeForward = "OTHER_OFFICE".equals(target);
        boolean closesImmediately = "OTHER_REGULATORY_BODIES".equals(target) || "OTHER_RBI_DEPARTMENT".equals(target);
        switch (target) {
            case "REVIEWER": newStatus = ComplaintStatus.SENT_TO_REVIEWER.name(); break;
            case "DEPUTY_OMBUDSMAN": newStatus = ComplaintStatus.SENT_TO_DEPUTY_OMBUDSMAN.name(); break;
            case "OMBUDSMAN": newStatus = ComplaintStatus.SENT_TO_OMBUDSMAN.name(); break;
            case "DEALING_OFFICER": newStatus = ComplaintStatus.SENT_BACK_TO_DO.name(); break;
            case "CLOSE": newStatus = ComplaintStatus.CLOSED.name(); break;
            case "OTHER_OFFICE": newStatus = ComplaintStatus.PENDING_OFFICE_HEAD_APPROVAL.name(); break;
            default:
                if (closesImmediately) {
                    newStatus = ComplaintStatus.CLOSED.name();
                    break;
                }
                // Concatenating the target used to invent statuses that no ComplaintStatus constant
                // matches, which the grid then could not colour and the filters could not select.
                Optional<ComplaintStatus> derived = ComplaintStatus.parse("SENT_TO_" + target);
                if (derived.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("Unsupported forward target: " + target));
                }
                newStatus = derived.get().name();
                break;
        }

        Optional<RbioHierarchyService.Denial> denial =
                rbioHierarchyService.validateForward(complaint, target, performedByRole, performedBy, newStatus);
        if (denial.isPresent()) {
            return ResponseEntity.status(denial.get().status())
                    .body(ApiResponse.error(denial.get().message()));
        }

        complaint.setStatus(newStatus);
        if ("CLOSED".equals(newStatus)) {
            complaint.setResolvedAt(LocalDateTime.now());
            if (closesImmediately) {
                complaint.setClosureClauseDescription(
                        "Forwarded to " + assignedToName + " — complaint closed on this end."
                                + (request.get("remarks") != null ? " " + request.get("remarks") : ""));
            }
            if (request.get("closureClause") != null)
                complaint.setClosureClause(request.get("closureClause").toString());
            if (!closesImmediately && request.get("remarks") != null)
                complaint.setClosureClauseDescription(request.get("remarks").toString());
            if (request.get("complaintStatusOnPortal") != null)
                complaint.setComplaintStatusOnPortal(request.get("complaintStatusOnPortal").toString());
            if (request.get("speakingOrderGenerated") != null)
                complaint.setSpeakingOrderGenerated(request.get("speakingOrderGenerated").toString());
            if (request.get("gistOfCase") != null)
                complaint.setGistOfCase(request.get("gistOfCase").toString());
            if (request.get("gistOfCaseRegional") != null)
                complaint.setGistOfCaseRegional(request.get("gistOfCaseRegional").toString());
        }

        if (isOfficeForward) {
            String officeCode = (String) request.get("officeCode");
            if (officeCode == null || officeCode.isBlank()) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("officeCode is required when forwarding to Other Office"));
            }
            String headOfficer = complaintRoutingService.assignOfficerByRole("CRPC_HEAD");
            complaint.setForwardedOfficeCode(officeCode);
            complaint.setPreForwardOfficer(performedBy);
            complaint.setPreForwardRole(performedByRole != null && !performedByRole.isBlank() ? performedByRole : complaint.getAssignedRole());
            complaint.setAssignedRole("CRPC_HEAD");
            complaint.setAssignedOfficer(headOfficer);
            officerDirectoryService.applyAssigneeDetails(complaint, headOfficer, null);
        } else {
            complaint.setAssignedOfficer(assignedTo);
            officerDirectoryService.applyAssigneeDetails(complaint, assignedTo, assignedToName);
            // Without this the complaint keeps the forwarding officer's role, so the next hop's
            // hierarchy check would read the wrong rung.
            String forwardedToRole = rbioHierarchyService.roleForTarget(target);
            if (forwardedToRole != null && rbioHierarchyService.isRbio(complaint)) {
                complaint.setAssignedRole(forwardedToRole);
            }
        }
        if (proposedAction != null && !proposedAction.isBlank()) {
            complaint.setProposedAction(proposedAction);
        }
        if (proposedClause != null && !proposedClause.isBlank()) {
            complaint.setProposedClause(proposedClause);
        }
        complaintService.updateComplaintDirectly(complaint);

        String action = "CLOSED".equals(newStatus) ? "CLOSED" : "FORWARDED";
        String remarks;
        if (closesImmediately) {
            remarks = "Forwarded to " + assignedToName + " (" + target + ") — complaint closed.";
        } else if (isOfficeForward) {
            remarks = "Forwarded to office " + request.get("officeCode") + " — pending CRPC Head approval ("
                    + complaint.getAssignedOfficer() + ")";
        } else if ("CLOSED".equals(newStatus)) {
            remarks = "Complaint closed. " + (request.get("remarks") != null ? request.get("remarks").toString() : "");
        } else {
            remarks = "Forwarded to " + assignedToName + " (" + target + ") via " + assignmentMode;
        }
        complaintService.addTimeline(complaint.getId(), action, performedBy, remarks, oldStatus, newStatus);

        auditService.logActionAsync(complaintNumber, action, performedBy, performedByRole, remarks,
                Map.of("target", target, "assignmentMode", assignmentMode,
                        "assignedRole", String.valueOf(complaint.getAssignedRole())),
                oldStatus, newStatus);

        if (ComplaintStatus.CLOSED.name().equals(newStatus)) {
            complaintEventPublisher.publishComplaintClosed(complaint, performedBy, oldStatus);
        } else {
            complaintEventPublisher.publishComplaintAssigned(complaint, performedBy);
            notifyAssignee(complaint, complaintNumber, remarks);
        }

        ForwardComplaintResponse data = ForwardComplaintResponse.builder()
                .complaintNumber(complaintNumber)
                .status(newStatus)
                .assignedTo(assignedTo)
                .assignedToName(assignedToName)
                .assignedRole(complaint.getAssignedRole())
                .target(target)
                .build();

        return ResponseEntity.ok(ApiResponse.success(data, "Complaint forwarded to " + assignedToName));
    }

    /**
     * RBIO_ADMIN-only reassignment: hand the complaint to a different person in the same role when the
     * current holder is absent, or to a higher official to escalate past a rung. Ordinary officers use
     * {@code /send-for-approval}, which only climbs one rung at a time.
     */
    @PostMapping("/{complaintNumber}/rbio/reassign")
    @RbioRoleGuard(roles = {RoleConstants.RBIO_ADMIN})
    public ResponseEntity<ApiResponse<RbioReassignResponse>> rbioReassign(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        String assignedTo = (String) request.getOrDefault("assignedTo", "");
        String assignedToName = (String) request.getOrDefault("assignedToName", assignedTo);
        String performedBy = (String) request.getOrDefault("performedBy", RoleConstants.RBIO_ADMIN);
        String reason = (String) request.getOrDefault("reason", "");

        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Complaint not found: " + complaintNumber));
        }

        String previousRole = rbioHierarchyService.normalizeRole(complaint.getAssignedRole());
        String targetRole = rbioHierarchyService.normalizeRole((String) request.get("targetRole"));
        if (targetRole == null) {
            targetRole = previousRole;
        }

        Optional<RbioHierarchyService.Denial> denial =
                rbioHierarchyService.validateAdminReassign(complaint, targetRole, assignedTo);
        if (denial.isPresent()) {
            return ResponseEntity.status(denial.get().status())
                    .body(ApiResponse.error(denial.get().message()));
        }

        String previousOfficer = complaint.getAssignedOfficer();
        String oldStatus = complaint.getStatus();
        boolean isEscalation = !targetRole.equals(previousRole);

        String newStatus = oldStatus;
        if (isEscalation) {
            String escalatedStatus = rbioHierarchyService.statusForRole(targetRole);
            if (escalatedStatus != null && !escalatedStatus.equalsIgnoreCase(oldStatus)) {
                newStatus = escalatedStatus;
            }
            complaint.setEscalatedAt(LocalDateTime.now());
        }

        complaint.setStatus(newStatus);
        complaint.setAssignedRole(targetRole);
        complaint.setAssignedOfficer(assignedTo);
        officerDirectoryService.applyAssigneeDetails(complaint, assignedTo, assignedToName);
        complaintService.updateComplaintDirectly(complaint);

        String action = isEscalation ? "ESCALATE" : "REASSIGNED";
        String remarks = isEscalation
                ? "Escalated by RBIO_ADMIN from " + previousRole + " to " + targetRole + " (" + assignedToName + ")."
                : "Reassigned by RBIO_ADMIN from " + previousOfficer + " to " + assignedToName
                        + " within " + targetRole + ".";
        if (reason != null && !reason.isBlank()) {
            remarks = remarks + " Reason: " + reason;
        }
        complaintService.addTimeline(complaint.getId(), action, performedBy, remarks, oldStatus, newStatus);

        auditService.logActionAsync(complaintNumber, action, performedBy, RoleConstants.RBIO_ADMIN, remarks,
                Map.of("previousRole", String.valueOf(previousRole),
                        "previousOfficer", String.valueOf(previousOfficer),
                        "targetRole", targetRole),
                oldStatus, newStatus);

        if (isEscalation) {
            complaintEventPublisher.publishComplaintEscalated(complaint, performedBy, oldStatus);
        } else {
            complaintEventPublisher.publishComplaintAssigned(complaint, performedBy);
        }
        notifyAssignee(complaint, complaintNumber, remarks);

        RbioReassignResponse data = RbioReassignResponse.builder()
                .complaintNumber(complaintNumber)
                .status(newStatus)
                .assignedTo(assignedTo)
                .assignedToName(assignedToName)
                .assignedRole(targetRole)
                .previousRole(previousRole)
                .previousOfficer(previousOfficer)
                .escalation(isEscalation)
                .build();

        return ResponseEntity.ok(ApiResponse.success(data, remarks));
    }

    private void notifyAssignee(Complaint complaint, String complaintNumber, String remarks) {
        String assignee = complaint.getAssignedOfficer();
        if (assignee == null || assignee.isBlank()) {
            return;
        }
        String dept = complaint.getDepartment() == null ? "rbio" : complaint.getDepartment().toLowerCase();
        notificationService.send(
                assignee,
                "NEW_ASSIGNMENT",
                "Complaint " + complaintNumber + " assigned to you",
                remarks,
                complaintNumber,
                "COMPLAINT",
                "/workflow/" + dept + "/complaint/" + complaintNumber);
    }

    @PostMapping("/{complaintNumber}/office-head-decision")
    public ResponseEntity<ApiResponse<OfficeHeadDecisionResponse>> officeHeadDecision(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        String decision = (String) request.getOrDefault("decision", "");
        String comment = (String) request.get("comment");
        String performedBy = (String) request.getOrDefault("performedBy", "");

        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("decision must be APPROVE or REJECT"));
        }
        if ("REJECT".equals(decision) && (comment == null || comment.isBlank())) {
            return ResponseEntity.badRequest().body(ApiResponse.error("A rejection comment is mandatory"));
        }

        Complaint complaint;
        try {
            complaint = complaintService.getByComplaintNumber(complaintNumber);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Complaint not found: " + complaintNumber));
        }

        String oldStatus = complaint.getStatus();
        String newStatus;
        String remarks;

        if ("APPROVE".equals(decision)) {
            String overrideOfficeCode = (String) request.get("overrideOfficeCode");
            String officeCode = (overrideOfficeCode != null && !overrideOfficeCode.isBlank())
                    ? overrideOfficeCode : complaint.getForwardedOfficeCode();
            if (overrideOfficeCode != null && !overrideOfficeCode.isBlank()) {
                complaint.setForwardedOfficeCode(overrideOfficeCode);
            }
            String role = "CEPC".equals(complaint.getDepartment()) ? "CEPC_OFFICER" : "RBIO_OFFICER";
            String assignedOfficer = assignOfficerByRoleAndOffice(role, officeCode);
            newStatus = "assigned";
            complaint.setAssignedRole(role);
            complaint.setAssignedOfficer(assignedOfficer);
            officerDirectoryService.applyAssigneeDetails(complaint, assignedOfficer, null);
            remarks = "Approved by CRPC Head, assigned to " + assignedOfficer + " at office " + officeCode
                    + (comment != null && !comment.isBlank() ? " — " + comment : "");
        } else {
            newStatus = "SENT_BACK";
            String role = "CEPC".equals(complaint.getDepartment()) ? "CEPC_OFFICER" : "RBIO_OFFICER";
            complaint.setAssignedRole(role);
            complaint.setAssignedOfficer(complaint.getPreForwardOfficer());
            officerDirectoryService.applyAssigneeDetails(complaint, complaint.getPreForwardOfficer(), null);
            remarks = "Rejected by CRPC Head, returned to " + complaint.getPreForwardOfficer() + " — " + comment;
        }

        complaint.setStatus(newStatus);
        complaintService.updateComplaintDirectly(complaint);
        complaintService.addTimeline(complaint.getId(), "OFFICE_HEAD_" + decision, performedBy, remarks, oldStatus, newStatus);

        OfficeHeadDecisionResponse data = OfficeHeadDecisionResponse.builder()
                .complaintNumber(complaintNumber)
                .status(newStatus)
                .assignedOfficer(complaint.getAssignedOfficer())
                .decision(decision)
                .build();

        return ResponseEntity.ok(ApiResponse.success(data, remarks));
    }

    /**
     * Round-robin assignment scoped to a specific office. Falls back to the unscoped
     * role-wide round robin if no officer's OfficerAvailability record matches the office.
     */
    private String assignOfficerByRoleAndOffice(String role, String officeCode) {
        List<Map<String, Object>> officers = keycloakUserService.getUsersByRole(role);
        List<Map<String, Object>> officeMatched = new ArrayList<>();
        for (Map<String, Object> officer : officers) {
            String userId = (String) officer.get("userId");
            officerAvailabilityRepository.findByUserIdAndRole(userId, role).ifPresent(oa -> {
                if (officeCode != null && officeCode.equals(oa.getOfficeCode())) {
                    officeMatched.add(officer);
                }
            });
        }
        if (officeMatched.isEmpty()) {
            return complaintRoutingService.assignOfficerByRole(role);
        }
        String picked = complaintRoutingService.assignOfficerByRole(role);
        boolean pickedInOffice = officeMatched.stream().anyMatch(o -> picked.equals(o.get("userId")));
        return pickedInOffice ? picked : (String) officeMatched.get(0).get("userId");
    }
}
