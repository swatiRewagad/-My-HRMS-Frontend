package com.rbi.cms.ingestion.email.service;

import com.rbi.cms.common.enums.Channel;
import com.rbi.cms.common.enums.ComplaintCategory;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.Priority;
import com.rbi.cms.common.event.ComplaintEvent;
import com.rbi.cms.common.util.ComplaintIdGenerator;
import com.rbi.cms.ingestion.email.dto.*;
import com.rbi.cms.ingestion.email.entity.*;
import com.rbi.cms.ingestion.email.repository.*;
import com.rbi.cms.ingestion.entity.ComplaintMaster;
import com.rbi.cms.ingestion.repository.ComplaintMasterRepository;
import com.rbi.cms.ingestion.service.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSyndicationService {

    private final EmailDraftRepository draftRepository;
    private final EmailAttachmentRepository attachmentRepository;
    private final EmailIgnoreListRepository ignoreListRepository;
    private final DeoUserRepository deoUserRepository;
    private final ComplaintMasterRepository complaintRepository;
    private final RoundRobinAssignmentService assignmentService;
    private final EventPublisher eventPublisher;

    @Transactional
    public EmailDraftResponse ingestEmail(EmailIngestRequest request) {
        log.info("Ingesting email from: {} subject: {}", request.getSenderEmail(), request.getSubject());

        if (isOnIgnoreList(request.getSenderEmail())) {
            log.info("Email from {} is on ignore list, skipping", request.getSenderEmail());
            return null;
        }

        if (request.getMessageId() != null) {
            Optional<EmailDraft> existing = draftRepository.findByMessageId(request.getMessageId());
            if (existing.isPresent()) {
                log.info("Duplicate messageId {}, skipping", request.getMessageId());
                return toResponse(existing.get());
            }
        }

        DuplicateCheckResult duplicateResult = checkDuplicate(request.getSenderEmail(), request.getSubject());

        if (duplicateResult.isDuplicate() && duplicateResult.getParentComplaintId() != null) {
            Optional<ComplaintMaster> parent = complaintRepository.findByComplaintId(duplicateResult.getParentComplaintId());
            if (parent.isPresent() && !isClosedStatus(parent.get().getStatus())) {
                log.info("Duplicate email detected, attaching to parent complaint: {}", duplicateResult.getParentComplaintId());
                EmailDraft draft = createDraftRecord(request, EmailDraftStatus.DUPLICATE, duplicateResult.getParentComplaintId());
                return toResponse(draft);
            }
        }

        EmailDraft draft = createDraftRecord(request, EmailDraftStatus.PENDING, null);

        String assignedDeo = assignmentService.assignNextDeo();
        if (assignedDeo != null) {
            draft.setAssignedTo(assignedDeo);
            draft.setStatus(EmailDraftStatus.ASSIGNED);
            draftRepository.save(draft);
            log.info("Draft {} assigned to DEO: {}", draft.getDraftId(), assignedDeo);
        }

        return toResponse(draft);
    }

    public List<EmailDraftResponse> getEmailQueue(String status) {
        List<EmailDraft> drafts;
        if (status != null && !status.isEmpty()) {
            drafts = draftRepository.findByStatusOrderByReceivedAtDesc(EmailDraftStatus.valueOf(status));
        } else {
            drafts = draftRepository.findByStatusInOrderByReceivedAtDesc(
                    List.of(EmailDraftStatus.PENDING, EmailDraftStatus.ASSIGNED, EmailDraftStatus.IN_PROGRESS));
        }
        return drafts.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public EmailDraftResponse getDraft(String draftId) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + draftId));

        EmailDraftResponse response = toResponse(draft);

        List<EmailDraft> related = draftRepository.findBySenderEmail(draft.getSenderEmail());
        response.setSuggestedRelated(related.stream()
                .filter(d -> !d.getDraftId().equals(draftId))
                .limit(5)
                .map(this::toResponse)
                .collect(Collectors.toList()));

        return response;
    }

    @Transactional
    public EmailDraftResponse updateDraft(String draftId, EmailDraftUpdateRequest request) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + draftId));

        if (request.getComplainantName() != null) draft.setComplainantName(request.getComplainantName());
        if (request.getComplainantPhone() != null) draft.setComplainantPhone(request.getComplainantPhone());
        if (request.getCpgramsNumber() != null) draft.setCpgramsNumber(request.getCpgramsNumber());
        if (request.getComplaintSummary() != null) draft.setComplaintSummary(request.getComplaintSummary());
        if (request.getCategory() != null) draft.setCategory(request.getCategory());
        if (request.getSubject() != null) draft.setSubject(request.getSubject());

        if (draft.getStatus() == EmailDraftStatus.ASSIGNED) {
            draft.setStatus(EmailDraftStatus.IN_PROGRESS);
        }

        draftRepository.save(draft);
        return toResponse(draft);
    }

    @Transactional
    public EmailDraftResponse convertToComplaint(String draftId, String processedBy) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + draftId));

        String complaintId = ComplaintIdGenerator.generate();

        ComplaintCategory category = ComplaintCategory.GENERAL;
        if (draft.getCategory() != null && !draft.getCategory().isBlank()) {
            try {
                category = ComplaintCategory.valueOf(draft.getCategory().toUpperCase());
            } catch (IllegalArgumentException ignored) {
            }
        }

        ComplaintMaster complaint = ComplaintMaster.builder()
                .complaintId(complaintId)
                .channel(Channel.EMAIL)
                .category(category)
                .status(ComplaintStatus.NEW)
                .priority(Priority.MEDIUM)
                .complainantName(draft.getComplainantName() != null ? draft.getComplainantName() : draft.getSenderEmail())
                .complainantEmail(draft.getSenderEmail())
                .complainantPhone(draft.getComplainantPhone())
                .entityName("RBI")
                .entityType("REGULATOR")
                .subject(draft.getSubject())
                .description(draft.getComplaintSummary() != null ? draft.getComplaintSummary() : draft.getBody())
                .createdBy(processedBy)
                .build();

        complaintRepository.save(complaint);

        draft.setStatus(EmailDraftStatus.CONVERTED);
        draft.setConvertedComplaintId(complaintId);
        draft.setProcessedBy(processedBy);
        draftRepository.save(draft);

        assignmentService.decrementCount(draft.getAssignedTo());

        ComplaintEvent event = ComplaintEvent.builder()
                .eventId(java.util.UUID.randomUUID().toString())
                .complaintId(complaintId)
                .currentStatus(ComplaintStatus.NEW)
                .occurredAt(java.time.Instant.now())
                .correlationId(draftId)
                .build();
        eventPublisher.publishComplaintEvent("complaint.ingested", complaintId, event);

        log.info("Draft {} converted to complaint {} and persisted", draftId, complaintId);
        return toResponse(draft);
    }

    @Transactional
    public void reassignDraft(String draftId, String targetDeoId, String requestedBy) {
        EmailDraft draft = draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new RuntimeException("Draft not found: " + draftId));

        String previousAssignee = draft.getAssignedTo();
        draft.setAssignedTo(targetDeoId);
        draft.setStatus(EmailDraftStatus.ASSIGNED);
        draftRepository.save(draft);

        if (previousAssignee != null) {
            assignmentService.decrementCount(previousAssignee);
        }
        assignmentService.incrementCount(targetDeoId);

        log.info("Draft {} reassigned from {} to {} by {}", draftId, previousAssignee, targetDeoId, requestedBy);
    }

    public EmailQueueStats getStats() {
        return EmailQueueStats.builder()
                .totalDrafts(draftRepository.count())
                .pendingCount(draftRepository.countByStatus(EmailDraftStatus.PENDING))
                .assignedCount(draftRepository.countByStatus(EmailDraftStatus.ASSIGNED))
                .inProgressCount(draftRepository.countByStatus(EmailDraftStatus.IN_PROGRESS))
                .convertedCount(draftRepository.countByStatus(EmailDraftStatus.CONVERTED))
                .duplicateCount(draftRepository.countByStatus(EmailDraftStatus.DUPLICATE))
                .ignoredCount(draftRepository.countByStatus(EmailDraftStatus.IGNORED))
                .activeDeoCount(deoUserRepository.countByIsActiveTrueAndIsOnLeaveFalse())
                .build();
    }

    private EmailDraft createDraftRecord(EmailIngestRequest request, EmailDraftStatus status, String parentComplaintId) {
        String draftId = "DRF-" + System.currentTimeMillis();

        EmailDraft draft = EmailDraft.builder()
                .draftId(draftId)
                .messageId(request.getMessageId())
                .senderEmail(request.getSenderEmail())
                .subject(request.getSubject())
                .body(request.getBody())
                .status(status)
                .parentComplaintId(parentComplaintId)
                .isDuplicate(parentComplaintId != null)
                .receivedAt(Instant.now())
                .build();

        return draftRepository.save(draft);
    }

    private boolean isOnIgnoreList(String email) {
        String domain = "*@" + email.substring(email.indexOf('@') + 1);
        List<EmailIgnoreList> matches = ignoreListRepository.findMatchingPatterns(email, domain);
        return !matches.isEmpty();
    }

    private DuplicateCheckResult checkDuplicate(String senderEmail, String subject) {
        List<EmailDraft> existing = draftRepository.findBySenderEmailAndSubject(senderEmail, subject);
        if (!existing.isEmpty()) {
            EmailDraft matched = existing.get(0);
            String parentId = matched.getConvertedComplaintId() != null
                    ? matched.getConvertedComplaintId()
                    : matched.getParentComplaintId();
            return new DuplicateCheckResult(true, parentId);
        }
        return new DuplicateCheckResult(false, null);
    }

    private boolean isClosedStatus(ComplaintStatus status) {
        return status == ComplaintStatus.CLOSED || status == ComplaintStatus.RESOLVED;
    }

    private EmailDraftResponse toResponse(EmailDraft draft) {
        List<EmailAttachment> attachments = attachmentRepository.findByDraftId(draft.getDraftId());

        return EmailDraftResponse.builder()
                .id(draft.getId())
                .draftId(draft.getDraftId())
                .messageId(draft.getMessageId())
                .senderEmail(draft.getSenderEmail())
                .subject(draft.getSubject())
                .body(draft.getBody())
                .complainantName(draft.getComplainantName())
                .complainantPhone(draft.getComplainantPhone())
                .cpgramsNumber(draft.getCpgramsNumber())
                .complaintSummary(draft.getComplaintSummary())
                .category(draft.getCategory())
                .modeOfReceipt(draft.getModeOfReceipt())
                .status(draft.getStatus())
                .assignedTo(draft.getAssignedTo())
                .parentComplaintId(draft.getParentComplaintId())
                .isDuplicate(draft.getIsDuplicate())
                .ocrProcessed(draft.getOcrProcessed())
                .ocrConfidence(draft.getOcrConfidence())
                .receivedAt(draft.getReceivedAt())
                .createdAt(draft.getCreatedAt())
                .processedBy(draft.getProcessedBy())
                .convertedComplaintId(draft.getConvertedComplaintId())
                .attachments(attachments.stream().map(a -> EmailAttachmentResponse.builder()
                        .id(a.getId())
                        .fileName(a.getFileName())
                        .fileType(a.getFileType())
                        .fileSize(a.getFileSize())
                        .ocrText(a.getOcrText())
                        .ocrConfidence(a.getOcrConfidence())
                        .createdAt(a.getCreatedAt())
                        .build()).collect(Collectors.toList()))
                .build();
    }

    private record DuplicateCheckResult(boolean isDuplicate, String parentComplaintId) {
        public boolean isDuplicate() { return isDuplicate; }
        public String getParentComplaintId() { return parentComplaintId; }
    }
}
