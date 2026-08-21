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
    private final ComplaintRoutingService complaintRoutingService;

    private static final Map<String, Set<String>> VALID_TRANSITIONS = Map.ofEntries(
            Map.entry("ASSIGNED", Set.of("SENT_FOR_APPROVAL", "NOT_A_COMPLAINT", "SENT_TO_OTHER_DEPT_FOR_APPROVAL", "VERNACULAR_FOR_APPROVAL")),
            Map.entry("SENT_FOR_APPROVAL", Set.of("APPROVED", "SENT_BACK", "CLOSED_NOT_A_COMPLAINT")),
            Map.entry("SENT_TO_OTHER_DEPT_FOR_APPROVAL", Set.of("APPROVED_SENT_TO_OTHER_DEPT", "SENT_BACK")),
            Map.entry("VERNACULAR_FOR_APPROVAL", Set.of("APPROVED_VERNACULAR", "SENT_BACK")),
            Map.entry("SENT_BACK", Set.of("SENT_FOR_APPROVAL", "NOT_A_COMPLAINT", "SENT_TO_OTHER_DEPT_FOR_APPROVAL", "VERNACULAR_FOR_APPROVAL")),
            Map.entry("APPROVED", Set.of("NEW_COMPLAINT")),
            Map.entry("APPROVED_SENT_TO_OTHER_DEPT", Set.of()),
            Map.entry("APPROVED_VERNACULAR", Set.of()),
            Map.entry("CLOSED_NOT_A_COMPLAINT", Set.of()),
            Map.entry("NOT_A_COMPLAINT", Set.of())
    );

    private static final Map<String, String> VERNACULAR_LANGUAGE_OFFICE_MAP = Map.ofEntries(
            Map.entry("HINDI", "CRPC-DEL"),
            Map.entry("MARATHI", "CRPC-MUM"),
            Map.entry("TAMIL", "CRPC-CHE"),
            Map.entry("TELUGU", "CRPC-HYD"),
            Map.entry("KANNADA", "CRPC-BLR"),
            Map.entry("BENGALI", "CRPC-KOL"),
            Map.entry("GUJARATI", "CRPC-AHM"),
            Map.entry("MALAYALAM", "CRPC-CHE"),
            Map.entry("PUNJABI", "CRPC-CHD"),
            Map.entry("ODIA", "CRPC-BHU"),
            Map.entry("URDU", "CRPC-DEL"),
            Map.entry("ASSAMESE", "CRPC-GUW"),
            Map.entry("KONKANI", "CRPC-MUM"),
            Map.entry("SANSKRIT", "CRPC-DEL")
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
    public EmailDraft sendToOtherDeptForApproval(String draftId, String deoUserId, String targetEntity, String deoRemarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "SENT_TO_OTHER_DEPT_FOR_APPROVAL");
        draft.setStatus("SENT_TO_OTHER_DEPT_FOR_APPROVAL");
        draft.setProcessedBy(deoUserId);
        draft.setDeoDecision("SENT_TO_OTHER_DEPT");
        draft.setDeoRemarks(deoRemarks);
        draft.setTargetEntity(targetEntity);
        draft = draftRepository.save(draft);

        if (draft.getReviewerAssignedTo() != null) {
            notificationService.send(
                    draft.getReviewerAssignedTo(),
                    "ASSIGNMENT",
                    "Draft pending review - Sent to Other Department",
                    "Draft " + draft.getDraftId() + " marked for transfer to " + targetEntity + ". Awaiting approval.",
                    draft.getDraftId(),
                    "DRAFT",
                    "/crpc/reviewer/draft/" + draft.getDraftId()
            );
        }
        return draft;
    }

    @Transactional
    public EmailDraft sendVernacularForApproval(String draftId, String deoUserId, String language, String deoRemarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "VERNACULAR_FOR_APPROVAL");
        draft.setStatus("VERNACULAR_FOR_APPROVAL");
        draft.setProcessedBy(deoUserId);
        draft.setDeoDecision("VERNACULAR");
        draft.setDeoRemarks(deoRemarks);
        draft.setDetectedLanguage(language);
        draft = draftRepository.save(draft);

        if (draft.getReviewerAssignedTo() != null) {
            notificationService.send(
                    draft.getReviewerAssignedTo(),
                    "ASSIGNMENT",
                    "Vernacular complaint pending review",
                    "Draft " + draft.getDraftId() + " is a vernacular complaint (" + language + "). Awaiting approval.",
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
    public EmailDraft approveNotAComplaint(String draftId, String reviewerUserId, String remarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "CLOSED_NOT_A_COMPLAINT");
        draft.setStatus("CLOSED_NOT_A_COMPLAINT");
        draft.setReviewerDecision("CLOSED_NOT_A_COMPLAINT");
        draft.setReviewerRemarks(remarks);
        draft = draftRepository.save(draft);

        log.info("Draft {} closed as Not a Complaint by reviewer {}", draftId, reviewerUserId);
        return draft;
    }

    @Transactional
    public EmailDraft approveSentToOtherDept(String draftId, String reviewerUserId, String targetEntity, String remarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "APPROVED_SENT_TO_OTHER_DEPT");
        draft.setStatus("APPROVED_SENT_TO_OTHER_DEPT");
        draft.setReviewerDecision("APPROVED_SENT_TO_OTHER_DEPT");
        draft.setReviewerRemarks(remarks);
        draft.setTargetEntity(targetEntity);
        draft = draftRepository.save(draft);

        log.info("Draft {} approved and sent to other department/entity: {} by reviewer {}",
                draftId, targetEntity, reviewerUserId);
        return draft;
    }

    @Transactional
    public EmailDraft approveVernacular(String draftId, String reviewerUserId, String remarks) {
        EmailDraft draft = getDraft(draftId);
        validateTransition(draft.getStatus(), "APPROVED_VERNACULAR");

        String language = draft.getDetectedLanguage();
        String targetOffice = resolveVernacularOffice(language);

        draft.setStatus("APPROVED_VERNACULAR");
        draft.setReviewerDecision("APPROVED_VERNACULAR");
        draft.setReviewerRemarks(remarks);
        draft.setTargetOffice(targetOffice);
        draft = draftRepository.save(draft);

        notificationService.send(
                targetOffice,
                "ASSIGNMENT",
                "Vernacular complaint routed",
                "Draft " + draft.getDraftId() + " (" + language + ") has been routed to " + targetOffice + " for processing.",
                draft.getDraftId(),
                "DRAFT",
                "/crpc/deo/draft/" + draft.getDraftId()
        );

        log.info("Draft {} approved as vernacular ({}), routed to office {} by reviewer {}",
                draftId, language, targetOffice, reviewerUserId);
        return draft;
    }

    public String resolveVernacularOffice(String language) {
        if (language == null || language.isBlank()) return "CRPC-DEL";
        return VERNACULAR_LANGUAGE_OFFICE_MAP.getOrDefault(language.toUpperCase(), "CRPC-DEL");
    }

    public Map<String, String> getVernacularLanguageOfficeMap() {
        return VERNACULAR_LANGUAGE_OFFICE_MAP;
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
