package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.ClosureClauseMaster;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppealEligibilityService {

    private final ComplaintRepository complaintRepository;
    private final AppealRepository appealRepository;
    private final SystemConfigRepository systemConfigRepository;
    private final BankRepository bankRepository;
    private final ComplaintCategoryRepository complaintCategoryRepository;
    private final AppealClassificationService classificationService;

    // "withdrawn" removed — withdrawn complaints are not appealable
    private static final List<String> TERMINAL_STATUSES = List.of(
            "closed", "resolved", "rejected", "adjudicated", "conciliated"
    );

    private static final List<String> ACTIVE_APPEAL_STATUSES = List.of(
            "filed", "under_review", "hearing_scheduled",
            "accepted", "assigned", "documents_requested",
            "hearing_completed", "order_reserved"
    );

    private static final int DEFAULT_FILING_WINDOW_DAYS = 30;
    private static final int DEFAULT_EXTENDED_WINDOW_DAYS = 60;

    /**
     * The one string a citizen may be shown when the filing window has closed.
     *
     * <p>Fixed wording, because it is the refusal a complainant is told at the point they lose a
     * statutory remedy. The diagnostic detail that used to be concatenated into it still travels, on
     * {@code reasonDetail} — an operator needs "days elapsed: 90", a citizen does not.
     */
    public static final String WINDOW_CLOSED_MESSAGE = "Not eligible for filing appeal.";

    /**
     * Check whether the given complaint is eligible for an appeal/representation.
     * Returns a 3-tier result: eligible (0-30 days), delayedEligible (31-60 days), ineligible (61+).
     *
     * @return map with { eligible, delayedFiling, daysSinceDecision, reason, suggestedType,
     *         originalStatus, closureDate, complaintSummary }
     */
    public Map<String, Object> checkEligibility(String originalComplaintNumber) {
        Map<String, Object> result = new LinkedHashMap<>();

        // Read configurable timelines from SYSTEM_CONFIG
        int filingWindowDays = getConfigInt("timeline.appeal.filing_window_days", DEFAULT_FILING_WINDOW_DAYS);
        int extendedWindowDays = getConfigInt("timeline.appeal.extended_window_days", DEFAULT_EXTENDED_WINDOW_DAYS);

        // 1. Complaint must exist
        Optional<Complaint> opt = complaintRepository.findByComplaintNumber(originalComplaintNumber);
        if (opt.isEmpty()) {
            result.put("eligible", false);
            result.put("delayedFiling", false);
            result.put("reason", "Complaint not found: " + originalComplaintNumber);
            return result;
        }

        Complaint complaint = opt.get();

        // 2. Complaint must be in a terminal state (withdrawn is excluded)
        if (!TERMINAL_STATUSES.contains(complaint.getStatus())) {
            result.put("eligible", false);
            result.put("delayedFiling", false);
            result.put("reason", "Complaint is still active (status: " + complaint.getStatus()
                    + "). Appeals can only be filed against closed/resolved complaints.");
            return result;
        }

        // 3. Compute days since closure using LocalDate for timezone safety
        LocalDateTime closureDateTime = complaint.getClosedAt() != null ? complaint.getClosedAt()
                : complaint.getResolvedAt() != null ? complaint.getResolvedAt()
                : complaint.getUpdatedAt();

        long daysSinceDecision = 0;
        if (closureDateTime != null) {
            daysSinceDecision = ChronoUnit.DAYS.between(closureDateTime.toLocalDate(), LocalDate.now());
        }

        result.put("daysSinceDecision", daysSinceDecision);

        // 4. Must not already have an active appeal
        List<Appeal> existingAppeals = appealRepository.findByOriginalComplaintNumber(originalComplaintNumber);
        boolean hasActiveAppeal = existingAppeals.stream()
                .anyMatch(a -> ACTIVE_APPEAL_STATUSES.contains(a.getStatus()));
        if (hasActiveAppeal) {
            result.put("eligible", false);
            result.put("delayedFiling", false);
            result.put("reason", "An active appeal already exists for this complaint.");
            result.put("complaintSummary", buildComplaintSummary(complaint, closureDateTime, daysSinceDecision));
            return result;
        }

        // 5. 3-tier filing window check
        if (daysSinceDecision > extendedWindowDays) {
            // Tier 3: Ineligible (>60 days)
            result.put("eligible", false);
            result.put("delayedFiling", false);
            result.put("reason", WINDOW_CLOSED_MESSAGE);
            // The arithmetic stays available for operators and support, just not in the citizen's face.
            result.put("reasonDetail", "Filing deadline exceeded. Appeals must be filed within "
                    + extendedWindowDays + " days of closure. Days elapsed: " + daysSinceDecision);
            result.put("filingWindowDays", filingWindowDays);
            result.put("extendedWindowDays", extendedWindowDays);
            result.put("complaintSummary", buildComplaintSummary(complaint, closureDateTime, daysSinceDecision));
            return result;
        }

        boolean delayedFiling = daysSinceDecision > filingWindowDays && daysSinceDecision <= extendedWindowDays;

        // 6. Appealability comes from the closure clause, not from a heuristic.
        //
        // A complainant may appeal a closure under 15(1)(a) or 15(1)(b) and nothing else; every other
        // clause is at most a REPRESENTATION. That decision lives in CLOSURE_CLAUSE_MASTER, so this
        // asks the clause service rather than re-deriving the rule.
        //
        // `appealable` is a SEPARATE flag from `eligible` on purpose. Setting eligible=false here would
        // also block the REPRESENTATION route, which POST /appeals/file gates on eligible==true — a
        // non-appealable closure would then have no escalation path at all. So the citizen appeal screen
        // refuses on `appealable`, while the representation route stays open.
        Appealability appealability = resolveAppealability(complaint);

        result.put("eligible", true);
        result.put("delayedFiling", delayedFiling);
        result.put("appealable", appealability.appealable());
        result.put("closureClause", complaint.getClosureClause());
        result.put("filingWindowDays", filingWindowDays);
        result.put("extendedWindowDays", extendedWindowDays);
        if (!appealability.appealable()) {
            result.put("appealableReason", appealability.reason());
        }

        // suggestedType keeps its existing advisoryText heuristic UNCHANGED. It has no basis in the
        // Scheme — AppealClassificationService says so, and that service is the authority the FILING path
        // uses — but it is only a display hint on a read, and correcting it is a separate change from
        // clause-based appealability. `appealable` above is the flag that now gates the appeal route.
        String suggestedType = "APPEAL";
        if (complaint.getAdvisoryText() != null && !complaint.getAdvisoryText().isBlank()) {
            suggestedType = "REPRESENTATION";
        }
        if (delayedFiling) {
            result.put("reason", "Eligible for appeal with reason for delay. Filed " + daysSinceDecision
                    + " days after closure (standard window: " + filingWindowDays + " days).");
        } else {
            result.put("reason", "Complaint is eligible for appeal/representation.");
        }
        result.put("suggestedType", suggestedType);
        result.put("originalStatus", complaint.getStatus());
        result.put("closureDate", closureDateTime != null ? closureDateTime.toString() : null);
        result.put("complaintSummary", buildComplaintSummary(complaint, closureDateTime, daysSinceDecision));

        return result;
    }

    /**
     * Whether this closure is appealable by the complainant, and what the escalation is called.
     *
     * <p>Fails OPEN to REPRESENTATION rather than throwing. The filing endpoint itself fails closed on an
     * unmapped clause (AppealClassificationService), which is the right place for that: refusing to
     * CLASSIFY is a data problem an AA Admin fixes. But the eligibility CHECK is a read a citizen makes
     * before filing, and turning it into a 503 would replace a usable refusal with an error page.
     */
    private Appealability resolveAppealability(Complaint complaint) {
        String clause = complaint.getClosureClause();
        if (clause == null || clause.isBlank()) {
            return new Appealability(false,
                    "This complaint was closed without a clause of the Scheme being recorded, so it"
                            + " cannot be appealed. Please contact the office that closed it.");
        }
        try {
            ClosureClauseMaster master = classificationService.resolveClause(complaint);
            if (master.isAppealableBy(ClosureClauseMaster.AppealParty.COMPLAINANT)) {
                return new Appealability(true, null);
            }
            return new Appealability(false,
                    "Complaints closed under clause " + master.getClauseCode()
                            + " are not appealable. Only closures under 15(1)(a) or 15(1)(b) may be appealed.");
        } catch (AppealClassificationService.UnmappedClauseException e) {
            return new Appealability(false,
                    "Closure clause " + clause + " is not configured in the Clause Master, so"
                            + " appealability cannot be determined.");
        }
    }

    private record Appealability(boolean appealable, String reason) {
    }

    /**
     * Build a complaint summary object for the frontend eligibility screen.
     */
    private Map<String, Object> buildComplaintSummary(Complaint complaint, LocalDateTime closureDateTime, long daysSinceDecision) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", complaint.getStatus());
        summary.put("decisionDate", closureDateTime != null ? closureDateTime.toString() : null);
        summary.put("description", complaint.getDescription());
        summary.put("closureClause", complaint.getClosureClause());
        summary.put("awardAmount", complaint.getAwardAmount() != null ? complaint.getAwardAmount().toPlainString() : null);
        summary.put("daysSinceDecision", daysSinceDecision);

        // Resolve category name from categoryId
        if (complaint.getCategoryId() != null) {
            complaintCategoryRepository.findById(complaint.getCategoryId())
                    .ifPresentOrElse(
                            cat -> summary.put("category", cat.getName()),
                            () -> summary.put("category", null)
                    );
        } else {
            summary.put("category", null);
        }

        // Resolve entity name from bankId
        if (complaint.getBankId() != null) {
            bankRepository.findById(complaint.getBankId())
                    .ifPresentOrElse(
                            bank -> summary.put("entityName", bank.getName()),
                            () -> summary.put("entityName", null)
                    );
        } else {
            summary.put("entityName", null);
        }

        // Speaking order / closure reason for UST112
        summary.put("speakingOrder", complaint.getCustomClosureText());
        summary.put("closureReason", complaint.getClosureCause());

        return summary;
    }

    private int getConfigInt(String key, int defaultValue) {
        try {
            return systemConfigRepository.findByConfigKey(key)
                    .map(SystemConfig::getConfigValue)
                    .map(Integer::parseInt)
                    .orElse(defaultValue);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for config key '{}', using default: {}", key, defaultValue);
            return defaultValue;
        }
    }
}
