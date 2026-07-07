package com.hrms.cms.service.mre;

import com.hrms.cms.service.BusinessHoursService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class MaintainabilityRulesEngine {

    private final MreProperties config;
    private final BusinessHoursService businessHoursService;
    private final MreEntityCoverageService entityCoverageService;

    @Cacheable(value = "mre-rules", key = "'version-' + #root.target.config.version")
    public int getCachedRuleVersion() {
        return config.getVersion();
    }

    public MreVerdict evaluate(ComplaintFacts facts) {
        List<GroundVerdict> verdicts = new ArrayList<>();

        verdicts.add(evaluateEntityCoverage(facts));
        verdicts.add(evaluateNoPriorReComplaint(facts));
        verdicts.add(evaluateFiledBeforeWindow(facts));
        verdicts.add(evaluateFiledBeyondDeadline(facts));
        verdicts.add(evaluateLimitationPeriod(facts));
        verdicts.add(evaluateSameGrievancePending(facts));

        MreVerdict.OverallSignal signal = computeOverallSignal(verdicts);
        Map<String, Object> timeline = buildTimeline(facts);

        String summary = buildSummary(verdicts, signal);

        return MreVerdict.builder()
                .ruleVersion(config.getVersion())
                .overallSignal(signal)
                .groundVerdicts(verdicts)
                .timeline(timeline)
                .summary(summary)
                .build();
    }

    private GroundVerdict evaluateEntityCoverage(ComplaintFacts facts) {
        if (facts.getEntityCode() == null || facts.getEntityCode().isBlank()) {
            return GroundVerdict.needsReview(MreGround.ENTITY_NOT_COVERED, "No entity code provided");
        }
        boolean covered = entityCoverageService.isEntityCovered(facts.getEntityCode(), facts.getEntityType());
        if (!covered) {
            return GroundVerdict.fail(MreGround.ENTITY_NOT_COVERED,
                    "Entity '" + facts.getEntityCode() + "' is not covered under the RB-IOS 2026 Scheme");
        }
        return GroundVerdict.pass(MreGround.ENTITY_NOT_COVERED, "Entity is covered under the Scheme");
    }

    private GroundVerdict evaluateNoPriorReComplaint(ComplaintFacts facts) {
        if (!facts.isPriorReComplaint()) {
            return GroundVerdict.fail(MreGround.NO_PRIOR_RE_COMPLAINT,
                    "Complainant has not first complained to the Regulated Entity as required by Q16");
        }
        return GroundVerdict.pass(MreGround.NO_PRIOR_RE_COMPLAINT,
                "Complainant has lodged prior complaint with the RE (ref: " + facts.getReComplaintReference() + ")");
    }

    private GroundVerdict evaluateFiledBeforeWindow(ComplaintFacts facts) {
        if (!facts.isPriorReComplaint() || facts.getReComplaintDate() == null) {
            return GroundVerdict.notApplicable(MreGround.FILED_BEFORE_WINDOW);
        }

        if (facts.isReRepliedAndDissatisfied()) {
            return GroundVerdict.pass(MreGround.FILED_BEFORE_WINDOW,
                    "RE has replied and complainant is dissatisfied — window requirement waived per Q17");
        }

        int windowDays = getApplicableWindowDays(facts.getCategoryCode());
        long daysSinceReComplaint = computeDaysBetween(facts.getReComplaintDate(), facts.getFilingDate());

        if (daysSinceReComplaint < windowDays) {
            return GroundVerdict.fail(MreGround.FILED_BEFORE_WINDOW,
                    "Filed after " + daysSinceReComplaint + " days, but the " + windowDays +
                            "-day RE window has not elapsed (applicable window for category: " + facts.getCategoryCode() + ")");
        }

        return GroundVerdict.pass(MreGround.FILED_BEFORE_WINDOW,
                "Filed after " + daysSinceReComplaint + " days — beyond the " + windowDays + "-day window");
    }

    private GroundVerdict evaluateFiledBeyondDeadline(ComplaintFacts facts) {
        if (!facts.isPriorReComplaint() || facts.getReComplaintDate() == null) {
            return GroundVerdict.notApplicable(MreGround.FILED_BEYOND_DEADLINE);
        }

        int windowDays = getApplicableWindowDays(facts.getCategoryCode());
        LocalDate windowExpiry = addDays(facts.getReComplaintDate(), windowDays);

        LocalDate referenceDate = facts.getReLastCommunicationDate() != null
                ? facts.getReLastCommunicationDate()
                : windowExpiry;

        long daysSinceReference = computeDaysBetween(referenceDate, facts.getFilingDate());

        if (daysSinceReference > config.getFilingDeadlineDays()) {
            return GroundVerdict.fail(MreGround.FILED_BEYOND_DEADLINE,
                    "Filed " + daysSinceReference + " days after the reference date (limit: " +
                            config.getFilingDeadlineDays() + " days per Q16/Q17)");
        }

        return GroundVerdict.pass(MreGround.FILED_BEYOND_DEADLINE,
                "Filed within " + config.getFilingDeadlineDays() + " days of the deadline reference");
    }

    private GroundVerdict evaluateLimitationPeriod(ComplaintFacts facts) {
        if (!facts.isPriorReComplaint() || facts.getReComplaintDate() == null) {
            return GroundVerdict.notApplicable(MreGround.RE_COMPLAINT_BEYOND_LIMITATION);
        }

        LocalDate limitationCutoff = facts.getFilingDate().minusYears(config.getLimitationPeriodYears());

        if (facts.getReComplaintDate().isBefore(limitationCutoff)) {
            return GroundVerdict.fail(MreGround.RE_COMPLAINT_BEYOND_LIMITATION,
                    "RE complaint date (" + facts.getReComplaintDate() + ") is beyond the " +
                            config.getLimitationPeriodYears() + "-year Limitation Act period");
        }

        return GroundVerdict.pass(MreGround.RE_COMPLAINT_BEYOND_LIMITATION,
                "RE complaint is within the Limitation Act period");
    }

    private GroundVerdict evaluateSameGrievancePending(ComplaintFacts facts) {
        if (facts.isSameGrievancePendingOrDecided()) {
            return GroundVerdict.fail(MreGround.SAME_GRIEVANCE_PENDING,
                    "Same grievance is already pending or has been decided by the Ombudsman or a court/tribunal (Q16)");
        }
        return GroundVerdict.pass(MreGround.SAME_GRIEVANCE_PENDING,
                "No duplicate grievance found pending or decided");
    }

    private MreVerdict.OverallSignal computeOverallSignal(List<GroundVerdict> verdicts) {
        boolean anyFail = verdicts.stream().anyMatch(v -> v.getStatus() == GroundVerdict.Status.FAIL);
        boolean anyNeedsReview = verdicts.stream().anyMatch(v -> v.getStatus() == GroundVerdict.Status.NEEDS_REVIEW);

        if (anyFail) return MreVerdict.OverallSignal.OBJECTIVELY_NON_MAINTAINABLE;
        if (anyNeedsReview) return MreVerdict.OverallSignal.NEEDS_HUMAN_REVIEW;
        return MreVerdict.OverallSignal.OBJECTIVELY_CLEAR;
    }

    private Map<String, Object> buildTimeline(ComplaintFacts facts) {
        Map<String, Object> timeline = new LinkedHashMap<>();
        timeline.put("filingDate", facts.getFilingDate());

        if (facts.isPriorReComplaint() && facts.getReComplaintDate() != null) {
            int windowDays = getApplicableWindowDays(facts.getCategoryCode());
            LocalDate windowOpens = addDays(facts.getReComplaintDate(), windowDays);
            LocalDate filingDeadline = addDays(windowOpens, config.getFilingDeadlineDays());

            timeline.put("reComplaintDate", facts.getReComplaintDate());
            timeline.put("windowDays", windowDays);
            timeline.put("windowOpens", windowOpens);
            timeline.put("filingDeadline", filingDeadline);
            timeline.put("windowBasis", config.getWindowBasis().name());

            long daysFromReComplaint = computeDaysBetween(facts.getReComplaintDate(), facts.getFilingDate());
            timeline.put("daysSinceReComplaint", daysFromReComplaint);

            boolean withinWindow = daysFromReComplaint >= windowDays;
            boolean withinDeadline = computeDaysBetween(windowOpens, facts.getFilingDate()) <= config.getFilingDeadlineDays();
            timeline.put("withinWindow", withinWindow);
            timeline.put("withinDeadline", withinDeadline);
        }

        return timeline;
    }

    private String buildSummary(List<GroundVerdict> verdicts, MreVerdict.OverallSignal signal) {
        long failCount = verdicts.stream().filter(v -> v.getStatus() == GroundVerdict.Status.FAIL).count();
        long reviewCount = verdicts.stream().filter(v -> v.getStatus() == GroundVerdict.Status.NEEDS_REVIEW).count();

        return switch (signal) {
            case OBJECTIVELY_CLEAR -> "All objective grounds pass — complaint appears maintainable on objective criteria.";
            case NEEDS_HUMAN_REVIEW -> reviewCount + " ground(s) require human review.";
            case OBJECTIVELY_NON_MAINTAINABLE -> failCount + " objective ground(s) indicate non-maintainability.";
        };
    }

    private int getApplicableWindowDays(String categoryCode) {
        if (categoryCode == null) return config.getReWindowDays();
        return switch (categoryCode.toUpperCase()) {
            case "NPCI", "UPI", "IMPS", "NACH" -> config.getNpciWindowDays();
            case "CARD", "CREDIT_CARD", "DEBIT_CARD" -> config.getCardNetworkWindowDays();
            default -> config.getReWindowDays();
        };
    }

    private long computeDaysBetween(LocalDate from, LocalDate to) {
        if (from == null || to == null) return 0;
        if (config.getWindowBasis() == MreProperties.WindowBasis.BUSINESS) {
            return computeBusinessDaysBetween(from, to);
        }
        return ChronoUnit.DAYS.between(from, to);
    }

    private LocalDate addDays(LocalDate date, int days) {
        if (config.getWindowBasis() == MreProperties.WindowBasis.BUSINESS) {
            return addBusinessDays(date, days);
        }
        return date.plusDays(days);
    }

    private long computeBusinessDaysBetween(LocalDate from, LocalDate to) {
        long count = 0;
        LocalDate current = from;
        while (current.isBefore(to)) {
            current = current.plusDays(1);
            if (businessHoursService.isBusinessDay(current)) {
                count++;
            }
        }
        return count;
    }

    private LocalDate addBusinessDays(LocalDate start, int businessDays) {
        LocalDate current = start;
        int added = 0;
        while (added < businessDays) {
            current = current.plusDays(1);
            if (businessHoursService.isBusinessDay(current)) {
                added++;
            }
        }
        return current;
    }
}
