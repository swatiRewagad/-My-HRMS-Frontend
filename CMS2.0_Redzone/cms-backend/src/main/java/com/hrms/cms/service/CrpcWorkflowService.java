package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CrpcWorkflowService {

    private final EmailDraftRepository draftRepository;
    private final ComplaintRepository complaintRepository;
    private final NotificationService notificationService;
    private final CommunicationTemplateService communicationTemplateService;

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.of(
            "ASSIGNED", Set.of("SENT_FOR_APPROVAL", "NOT_A_COMPLAINT"),
            "SENT_FOR_APPROVAL", Set.of("APPROVED", "SENT_BACK"),
            "SENT_BACK", Set.of("SENT_FOR_APPROVAL", "NOT_A_COMPLAINT"),
            "APPROVED", Set.of("NEW_COMPLAINT"),
            "NOT_A_COMPLAINT", Set.of()
    );

    private static final List<String> NOT_A_COMPLAINT_REASONS = List.of(
            "SPAM",
            "PROMOTIONAL",
            "INTERNAL_COMMUNICATION",
            "DUPLICATE",
            "INCOMPLETE_DETAILS",
            "OUT_OF_JURISDICTION",
            "NOT_RELATED_TO_BANKING",
            "AUTO_REPLY",
            "FOLLOW_UP_ON_CLOSED"
    );

    @Transactional
    public EmailDraft sendForApproval(String draftId, String deoUserId, String deoRemarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "SENT_FOR_APPROVAL");
        draft.setStatus("SENT_FOR_APPROVAL");
        draft.setProcessedBy(deoUserId);
        draft.setDeoDecision("FORWARD");
        draft.setDeoRemarks(deoRemarks);
        draft = draftRepository.save(draft);

        if (draft.getReviewerAssignedTo() != null) {
            notificationService.send(
                    draft.getReviewerAssignedTo(),
                    "ASSIGNMENT",
                    "New draft pending review",
                    "Draft " + draft.getDraftId() + " has been sent for your approval.",
                    draft.getDraftId(),
                    "DRAFT",
                    "/crpc/reviewer/draft/" + draft.getDraftId()
            );
        }
        return draft;
    }

    @Transactional
    public EmailDraft markNotAComplaint(String draftId, String userId, String reason, String remarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "NOT_A_COMPLAINT");

        if (!NOT_A_COMPLAINT_REASONS.contains(reason)) {
            throw new IllegalArgumentException("Invalid not-a-complaint reason: " + reason);
        }
        if (remarks != null && remarks.length() > 150) {
            throw new IllegalArgumentException("Remarks must not exceed 150 characters");
        }

        draft.setStatus("NOT_A_COMPLAINT");
        draft.setProcessedBy(userId);
        draft.setDeoDecision("NOT_A_COMPLAINT");
        draft.setNonMaintainableReason(reason);
        draft.setDeoRemarks(remarks);
        return draftRepository.save(draft);
    }

    @Transactional
    public EmailDraft approve(String draftId, String reviewerUserId, String remarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "APPROVED");
        draft.setStatus("APPROVED");
        draft.setReviewerDecision("APPROVED");
        draft.setReviewerRemarks(remarks);
        draft = draftRepository.save(draft);

        notificationService.send(
                draft.getAssignedTo(),
                "ASSIGNMENT",
                "Draft approved",
                "Your draft " + draft.getDraftId() + " has been approved by reviewer.",
                draft.getDraftId(),
                "DRAFT",
                "/crpc/deo/draft/" + draft.getDraftId()
        );
        return draft;
    }

    @Transactional
    public EmailDraft sendBack(String draftId, String reviewerUserId, String remarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "SENT_BACK");
        draft.setStatus("SENT_BACK");
        draft.setReviewerDecision("SENT_BACK");
        draft.setReviewerRemarks(remarks);
        draft = draftRepository.save(draft);

        notificationService.send(
                draft.getAssignedTo(),
                "SENT_BACK",
                "Draft sent back for correction",
                "Reviewer sent back draft " + draft.getDraftId() + ": " + remarks,
                draft.getDraftId(),
                "DRAFT",
                "/crpc/deo/draft/" + draft.getDraftId()
        );
        return draft;
    }

    @Transactional
    public Complaint convertToComplaint(String draftId, String userId) {
        EmailDraft draft = getDraft(draftId);
        if (!"APPROVED".equals(draft.getStatus())) {
            throw new IllegalStateException("Only approved drafts can be converted to complaints");
        }

        Complaint complaint = Complaint.builder()
                .subject(draft.getSubject())
                .description(draft.getBody())
                .complainantName(draft.getComplainantName())
                .complainantEmail(draft.getSenderEmail())
                .complainantPhone(draft.getComplainantPhone())
                .complainantAddress(draft.getComplainantAddress())
                .status("new")
                .priority("medium")
                .build();
        complaint = complaintRepository.save(complaint);

        draft.setStatus("NEW_COMPLAINT");
        draft.setConvertedComplaintId(complaint.getComplaintNumber());
        draftRepository.save(draft);

        log.info("Converted draft {} to complaint {}", draftId, complaint.getComplaintNumber());
        return complaint;
    }

    @Transactional
    public List<EmailDraft> bulkMarkNotAComplaint(List<String> draftIds, String userId, String reason, String remarks) {
        return draftIds.stream()
                .map(id -> markNotAComplaint(id, userId, reason, remarks))
                .toList();
    }

    public List<String> getNotAComplaintReasons() {
        return NOT_A_COMPLAINT_REASONS;
    }

    private EmailDraft getDraft(String draftId) {
        return draftRepository.findByDraftId(draftId)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found: " + draftId));
    }

    private void validateTransition(String currentStatus, String targetStatus) {
        Set<String> allowed = VALID_TRANSITIONS.get(currentStatus);
        if (allowed == null || !allowed.contains(targetStatus)) {
            throw new IllegalStateException(
                    "Invalid status transition from '" + currentStatus + "' to '" + targetStatus + "'");
        }
    }
}
