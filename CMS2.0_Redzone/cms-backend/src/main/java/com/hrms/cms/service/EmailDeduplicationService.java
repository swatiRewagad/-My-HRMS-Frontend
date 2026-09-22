package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.EmailDraftRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Duplicate inbound-email detection.
 *
 * Rule: same sender email AND exactly equal subject, resolved against a parent complaint that is
 * CLOSED. A follow-up on a still-open complaint is not a duplicate — it is correspondence on live
 * business and must keep reaching the officer handling it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailDeduplicationService {

    private final EmailDraftRepository draftRepository;
    private final ComplaintRepository complaintRepository;

    private List<String> closedStatuses;

    @Value("${cms.complaint.closed-statuses:closed,resolved,rejected,disposed}")
    public void setClosedStatuses(String csv) {
        this.closedStatuses = Arrays.stream(csv.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(String::toLowerCase).toList();
    }

    public record DuplicateMatch(EmailDraft existingDraft, String parentComplaintNumber) {}

    /**
     * @return the closed parent this email duplicates, or empty when it is not a duplicate.
     */
    public Optional<DuplicateMatch> findDuplicate(String senderEmail, String subject) {
        if (senderEmail == null || senderEmail.isBlank() || subject == null || subject.isBlank()) {
            return Optional.empty();
        }

        List<EmailDraft> priorDrafts =
                draftRepository.findBySenderEmailIgnoreCaseAndSubjectOrderByCreatedAtDesc(senderEmail.trim(), subject);

        for (EmailDraft prior : priorDrafts) {
            String parentNumber = prior.getConvertedComplaintId() != null && !prior.getConvertedComplaintId().isBlank()
                    ? prior.getConvertedComplaintId()
                    : prior.getParentComplaintId();
            if (parentNumber == null || parentNumber.isBlank()) {
                continue;
            }
            Optional<Complaint> parent = complaintRepository.findByComplaintNumber(parentNumber);
            if (parent.isEmpty()) {
                continue;
            }
            if (isClosed(parent.get().getStatus())) {
                return Optional.of(new DuplicateMatch(prior, parentNumber));
            }
            log.debug("Prior draft {} matched sender+subject but parent {} is still open (status={}) — not a duplicate",
                    prior.getDraftId(), parentNumber, parent.get().getStatus());
        }
        return Optional.empty();
    }

    public boolean isClosed(String status) {
        return status != null && closedStatuses.contains(status.toLowerCase().trim());
    }

    public List<String> getClosedStatuses() {
        return closedStatuses;
    }
}
