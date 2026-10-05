package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.repository.RbioWorkflowTransitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Whether a FINAL DECISION has already been taken on a complaint.
 *
 * <p>Replaces a phantom, and a consequential one. {@code rbio-workflow.service.ts:243} has always
 * called {@code GET /api/v1/complaints/{n}/final-decision-status}; no controller served it; the
 * {@code catchError(() => of({hasFinalDecision: false}))} meant the answer was "no decision" for every
 * complaint that has ever existed. The frontend then uses that answer to decide whether a Dealing
 * Official may still edit the file ({@code task-action.component.ts:1113}
 * {@code isFieldReadOnlyDueToDecision()}), so the guard failed OPEN everywhere.
 *
 * <h2>Where the answer comes from</h2>
 * <p>No new column and no new table. {@code RBIO_WORKFLOW_TRANSITION} already says which actions take a
 * final decision: they are exactly the rows whose {@code TO_MILESTONE} is {@code FINAL_DECISION}
 * (ADJUDICATION_AWARD, ADJUDICATION_REJECT, DECIDE_NON_MAINTAINABLE, FACILITATION, SETTLED,
 * ADVISORY_COMPLIED, CLOSE_COMPLAINT and the rest — 'rejected' and 'adjudicated' included). A complaint
 * has a final decision iff {@code COMPLAINT_TIMELINE} records one of those actions against it.
 *
 * <p>That indirection is the point. A session that adds a decision action, or deactivates one, changes
 * this answer with it; a hardcoded list in Java would quietly disagree with the ladder the product
 * actually walks. The milestone vocabulary is the same one the CEPC detail screen's phase strip reads
 * ({@code ComplaintApiV1Controller.milestoneLadder}).
 *
 * <h2>What it does NOT do</h2>
 * <p>It does not treat a terminal STATUS as a decision. A complaint can reach {@code closed} by
 * withdrawal or by an administrative close, and {@code closed} alone does not say who decided what; the
 * timeline entry does. Reading the status would also have made the answer unavailable for
 * {@code adjudicated} and {@code resolved}, which are decisions but are not terminal.
 *
 * <p>An absent decision is {@code hasFinalDecision: false} on a 200, never a 404 — this codebase's
 * refusal convention, and "not decided yet" is a normal state of a live complaint.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioFinalDecisionService {

    /** The milestone whose actions constitute a final decision. */
    private static final String FINAL_DECISION_MILESTONE = "FINAL_DECISION";

    /**
     * Fallback decision actions, used ONLY when the transition table knows no FINAL_DECISION row at all
     * (an unseeded database). Deliberately the terminal/outcome codes {@code RbioLadderActions} declares
     * with that milestone, so the fallback cannot say something the compiled ladder does not.
     */
    private static final Set<String> FALLBACK_DECISION_ACTIONS = Set.of(
            "ADJUDICATION_AWARD", "ADJUDICATION_REJECT", "DECIDE_NON_MAINTAINABLE",
            "FACILITATION", "SETTLED", "ADVISORY_COMPLIED", "CLOSE_COMPLAINT",
            "CONCILIATION_SUCCESS", "DEPUTY_DECISION");

    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;
    private final RbioWorkflowTransitionRepository transitionRepository;

    /**
     * The payload the two detail screens consume: {@code hasFinalDecision}, and when true, who took it.
     *
     * <p>Shape is dictated by the existing caller — {@code rbio-workflow.service.ts:243} maps
     * {@code res.data} straight onto {@code {hasFinalDecision, decidedBy, decidedByRole}} — so the keys
     * are not negotiable without breaking it.
     *
     * <p>An unknown complaint number answers {@code hasFinalDecision: false} rather than throwing. The
     * caller asks this question on every screen load, including for a complaint it has just failed to
     * load, and turning that into a 404 would reintroduce exactly the failing-request noise this
     * endpoint was built to remove.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> statusFor(String complaintNumber) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("complaintNumber", complaintNumber);
        payload.put("hasFinalDecision", false);
        payload.put("decidedBy", null);
        payload.put("decidedByRole", null);
        payload.put("decisionAction", null);
        payload.put("decidedAt", null);

        Optional<Complaint> complaint = complaintRepository.findByComplaintNumber(complaintNumber);
        if (complaint.isEmpty()) {
            log.debug("final-decision-status asked about unknown complaint {}", complaintNumber);
            return payload;
        }

        Set<String> decisionActions = decisionActionCodes();

        // Ascending, so the FIRST final decision is reported rather than the latest. A complaint that is
        // decided, reopened and decided again was decided at the earlier moment, and the read-only guard
        // this feeds asks whether a decision exists at all.
        Optional<ComplaintTimeline> decision =
                timelineRepository.findByComplaintIdOrderByPerformedAtAscIdAsc(complaint.get().getId())
                        .stream()
                        .filter(t -> t.getAction() != null
                                && decisionActions.contains(t.getAction().trim().toUpperCase(Locale.ROOT)))
                        .findFirst();

        if (decision.isEmpty()) {
            return payload;
        }

        ComplaintTimeline row = decision.get();
        payload.put("hasFinalDecision", true);
        payload.put("decidedBy", row.getPerformedBy());
        payload.put("decidedByRole", row.getPerformedByRole());
        payload.put("decisionAction", row.getAction());
        payload.put("decidedAt", row.getPerformedAt() != null ? row.getPerformedAt().toString() : null);
        return payload;
    }

    /**
     * The action codes that constitute a final decision, read from the transition table.
     *
     * <p>Falls back to the compiled set only when the table holds no FINAL_DECISION row whatsoever,
     * which means an unseeded database rather than a deliberate configuration. A configured-empty answer
     * is not distinguishable from an unseeded one here, so the fallback is kept as narrow as possible.
     */
    private Set<String> decisionActionCodes() {
        List<String> configured = transitionRepository.findActionCodesForMilestone(FINAL_DECISION_MILESTONE);
        if (configured == null || configured.isEmpty()) {
            log.debug("RBIO_WORKFLOW_TRANSITION has no {} rows; using the compiled decision-action set",
                    FINAL_DECISION_MILESTONE);
            return FALLBACK_DECISION_ACTIONS;
        }
        return configured.stream()
                .filter(c -> c != null && !c.isBlank())
                .map(c -> c.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
