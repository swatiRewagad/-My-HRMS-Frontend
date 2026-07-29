package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AppealEligibilityService {

    private final ComplaintRepository complaintRepository;
    private final AppealRepository appealRepository;

    private static final List<String> TERMINAL_STATUSES = List.of(
            "closed", "resolved", "rejected", "adjudicated", "conciliated", "withdrawn"
    );

    private static final List<String> ACTIVE_APPEAL_STATUSES = List.of(
            "filed", "under_review", "hearing_scheduled"
    );

    private static final int FILING_DEADLINE_DAYS = 30;

    /**
     * Check whether the given complaint is eligible for an appeal/representation.
     *
     * @return map with { eligible: boolean, reason: String, suggestedType: String }
     */
    public Map<String, Object> checkEligibility(String originalComplaintNumber) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. Complaint must exist
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(originalComplaintNumber);
        if (opt.isEmpty()) {
            result.put("eligible", false);
            result.put("reason", "Complaint not found: " + originalComplaintNumber);
            return result;
        }

        Complaint complaint = opt.get();

        // 2. Complaint must be in a terminal state
        if (!TERMINAL_STATUSES.contains(complaint.getStatus())) {
            result.put("eligible", false);
            result.put("reason", "Complaint is still active (status: " + complaint.getStatus() + "). Appeals can only be filed against closed/resolved complaints.");
            return result;
        }

        // 3. Must be within 30 days of closure
        LocalDateTime closureDate = complaint.getClosedAt() != null ? complaint.getClosedAt()
                : complaint.getResolvedAt() != null ? complaint.getResolvedAt()
                : complaint.getUpdatedAt();

        if (closureDate != null) {
            long daysSinceClosure = ChronoUnit.DAYS.between(closureDate, LocalDateTime.now());
            if (daysSinceClosure > FILING_DEADLINE_DAYS) {
                result.put("eligible", false);
                result.put("reason", "Filing deadline exceeded. Appeals must be filed within "
                        + FILING_DEADLINE_DAYS + " days of closure. Days elapsed: " + daysSinceClosure);
                return result;
            }
        }

        // 4. Must not already have an active appeal
        List<Appeal> existingAppeals = appealRepository.findByOriginalComplaintNumber(originalComplaintNumber);
        boolean hasActiveAppeal = existingAppeals.stream()
                .anyMatch(a -> ACTIVE_APPEAL_STATUSES.contains(a.getStatus()));
        if (hasActiveAppeal) {
            result.put("eligible", false);
            result.put("reason", "An active appeal already exists for this complaint.");
            return result;
        }

        // 5. Determine suggested type
        String suggestedType = "APPEAL";
        if (complaint.getAdvisoryText() != null && !complaint.getAdvisoryText().isBlank()) {
            // If complaint was resolved via Advisory, suggest Representation
            suggestedType = "REPRESENTATION";
        }

        result.put("eligible", true);
        result.put("reason", "Complaint is eligible for appeal/representation.");
        result.put("suggestedType", suggestedType);
        result.put("originalStatus", complaint.getStatus());
        result.put("closureDate", closureDate != null ? closureDate.toString() : null);

        return result;
    }
}
