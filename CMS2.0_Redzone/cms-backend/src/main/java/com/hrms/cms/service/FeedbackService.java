package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintFeedback;
import com.hrms.cms.repository.ComplaintFeedbackRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FeedbackService {

    private final ComplaintFeedbackRepository feedbackRepository;
    private final ComplaintRepository complaintRepository;

    /**
     * The statuses that count as "closed" for feedback purposes (UST109).
     *
     * <p>Previously only closed/resolved/rejected. That excluded the statuses a complaint reaches when
     * it is actually disposed of on its merits — a complaint settled by award (adjudicated) or by
     * conciliation, or withdrawn, or rejected at the portal — so the citizens with the most to say about
     * the process were the ones refused the feedback form. Kept in step with the closed set the RBIO
     * status vocabulary already treats as terminal.
     */
    private static final Set<String> TERMINAL_STATUSES = Set.of(
            "closed", "resolved", "rejected",
            "adjudicated", "conciliated", "withdrawn", "portal_rejection"
    );

    /**
     * Submit feedback for a closed/resolved/rejected complaint.
     *
     * @param request map containing feedback fields
     * @return the saved ComplaintFeedback entity
     * @throws IllegalArgumentException  for validation failures (400)
     * @throws IllegalStateException     for duplicate feedback (409)
     */
    public ComplaintFeedback submitFeedback(Map<String, Object> request) {

        // ── 1. Extract and validate complaintNumber ──────────────────────
        String complaintNumber = getString(request, "complaintNumber");
        if (complaintNumber == null || complaintNumber.isBlank()) {
            throw new IllegalArgumentException("Complaint number is required.");
        }

        // ── 2. Complaint must exist ──────────────────────────────────────
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Complaint not found: " + complaintNumber));

        // ── 3. Complaint must be in a terminal status ────────────────────
        if (!TERMINAL_STATUSES.contains(complaint.getStatus())) {
            throw new IllegalArgumentException(
                    "Feedback can only be submitted for closed, resolved, or rejected complaints. Current status: "
                            + complaint.getStatus());
        }

        // ── 4. No duplicate feedback ─────────────────────────────────────
        if (feedbackRepository.existsByComplaintNumber(complaintNumber)) {
            throw new IllegalStateException(
                    "Feedback already submitted for this complaint.");
        }

        // ── 5. Validate mandatory rating fields ──────────────────────────
        int overallRating = getIntInRange(request, "overallRating", 1, 5,
                "Overall rating is required and must be between 1 and 5.");
        int easeOfFiling = getIntInRange(request, "easeOfFiling", 1, 5,
                "Ease of filing rating is required and must be between 1 and 5.");
        int grievanceRedressTime = getIntInRange(request, "grievanceRedressTime", 1, 5,
                "Grievance redress time rating is required and must be between 1 and 5.");

        String sourceOfInformation = getString(request, "sourceOfInformation");
        if (sourceOfInformation == null || sourceOfInformation.isBlank()) {
            throw new IllegalArgumentException("Source of information is required.");
        }

        // UST109 Q6 is OPTIONAL. Requiring it rejected an otherwise complete questionnaire over the one
        // question the citizen is entitled to skip, and the portal's own form marks it optional — so a
        // valid submission from the UI was refused by the API.
        String cmsPortalAwareness = getString(request, "cmsPortalAwareness");

        // ── 6. Validate optional text-length fields ──────────────────────
        String feedbackText = getString(request, "feedbackText");
        if (feedbackText != null && feedbackText.length() > 500) {
            throw new IllegalArgumentException("Feedback text must not exceed 500 characters.");
        }

        String suggestions = getString(request, "suggestions");
        if (suggestions != null && suggestions.length() > 500) {
            throw new IllegalArgumentException("Suggestions must not exceed 500 characters.");
        }

        String sourceOtherText = getString(request, "sourceOtherText");
        if (sourceOtherText != null && sourceOtherText.length() > 500) {
            throw new IllegalArgumentException("Source other text must not exceed 500 characters.");
        }

        // ── 7. Determine officeCode from the complaint ───────────────────
        String officeCode = complaint.getDepartment();
        if (officeCode == null || officeCode.isBlank()) {
            officeCode = complaint.getEntityCode();
        }

        // ── 8. Optional fields ───────────────────────────────────────────
        Integer timelinessRating = getOptionalInt(request, "timelinessRating");
        Integer communicationRating = getOptionalInt(request, "communicationRating");
        Integer satisfactionRating = getOptionalInt(request, "satisfactionRating");
        String complainantPhone = getString(request, "complainantPhone");

        // ── 9. Build and save ────────────────────────────────────────────
        ComplaintFeedback feedback = ComplaintFeedback.builder()
                .complaintNumber(complaintNumber)
                .overallRating(overallRating)
                .easeOfFiling(easeOfFiling)
                .timelinessRating(timelinessRating)
                .communicationRating(communicationRating)
                .satisfactionRating(satisfactionRating)
                .grievanceRedressTime(grievanceRedressTime)
                .sourceOfInformation(sourceOfInformation)
                .sourceOtherText(sourceOtherText)
                .cmsPortalAwareness(cmsPortalAwareness)
                .feedbackText(feedbackText)
                .suggestions(suggestions)
                .complainantPhone(complainantPhone)
                .officeCode(officeCode)
                .build();

        return feedbackRepository.save(feedback);
    }

    /**
     * Retrieve feedback for a specific complaint.
     */
    public Optional<ComplaintFeedback> getFeedbackForComplaint(String complaintNumber) {
        return feedbackRepository.findByComplaintNumber(complaintNumber);
    }

    /**
     * List all feedback entries for a given office code (staff view).
     */
    public List<ComplaintFeedback> getFeedbackByOffice(String officeCode) {
        return feedbackRepository.findByOfficeCodeOrderBySubmittedAtDesc(officeCode);
    }

    /**
     * List all feedback entries (CEPD admin view).
     */
    public List<ComplaintFeedback> getAllFeedback() {
        return feedbackRepository.findAllByOrderBySubmittedAtDesc();
    }

    // ═════════════════════════════════════════════════════════════════════
    // Private helpers
    // ═════════════════════════════════════════════════════════════════════

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : null;
    }

    private int getIntInRange(Map<String, Object> map, String key, int min, int max, String errorMsg) {
        Object val = map.get(key);
        if (val == null) {
            throw new IllegalArgumentException(errorMsg);
        }
        try {
            int num = val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString().trim());
            if (num < min || num > max) {
                throw new IllegalArgumentException(errorMsg);
            }
            return num;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(errorMsg);
        }
    }

    private Integer getOptionalInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) {
            return null;
        }
        try {
            return val instanceof Number ? ((Number) val).intValue() : Integer.parseInt(val.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
