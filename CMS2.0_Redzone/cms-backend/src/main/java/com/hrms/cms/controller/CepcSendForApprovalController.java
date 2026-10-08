package com.hrms.cms.controller;

import com.hrms.cms.entity.CepcComplaintAssessment;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.InterOfficeTransfer;
import com.hrms.cms.repository.CepcComplaintAssessmentRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.InterOfficeTransferRepository;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcStatus;
import com.hrms.cms.service.CepcWorkflowService;
import com.hrms.cms.service.InterOfficeTransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The single endpoint behind every "move this complaint on" button in the CEPC detail view: the approval
 * dialog, the send-back dialog, the Forward tab's external targets and the whole Final Decision tab.
 *
 * <p><b>This is an adapter, not a port.</b> The equivalent in the older tree wrote its own UPPERCASE statuses
 * straight onto the complaint — {@code SENT_TO_REVIEWER}, {@code CLOSED}, {@code PENDING_OFFICE_HEAD_APPROVAL}
 * — none of which any dashboard filter matches, so a complaint moved through it disappeared from every tab
 * and KPI on the list screen. It also wrote no timeline transition through the workflow router, so the
 * audit trail forked, and it checked no role at all: any authenticated caller could close any complaint.
 * What follows instead translates the screen's vocabulary into the workflow actions
 * {@link CepcWorkflowService} already owns, so the status vocabulary, the timeline, the audit log and the
 * notifications are produced by the same code path as every other CEPC transition.
 *
 * <p><b>Why the target has to be disambiguated.</b> {@code DEALING_OFFICER} and {@code REVIEWER} are sent by
 * BOTH the approval dialog and the send-back dialog, with payloads of the same shape, and they mean opposite
 * things: a reviewer sending to {@code DEALING_OFFICER} is pushing work back down for rework, while a
 * dealing officer sending to {@code REVIEWER} is submitting it up for review. The body cannot tell them
 * apart, so the direction is derived from where the CALLER sits on the ladder relative to the target. Taking
 * the client's word for it would let a send-back and a forward write each other's status.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class CepcSendForApprovalController {

    /**
     * The CEPC approval ladder. A rank comparison, not a role check: the role check is
     * {@link CepcWorkflowService#validateRoleAuthorization} on the action this resolves to.
     */
    private static final Map<String, Integer> LADDER = Map.of(
            "CEPC_DO", 1,
            "CEPC_OFFICER", 1,
            "CEPC_REVIEWER", 2,
            "CEPC_INCHARGE", 3,
            "CEPC_CLOSING_AUTHORITY", 4,
            "CEPC_ADMIN", 5);

    /** The ladder rung each forwarding target sits on, keyed by the value the screen sends. */
    private static final Map<String, Integer> TARGET_RANK = Map.of(
            "DEALING_OFFICER", 1,
            "REVIEWER", 2,
            "INCHARGE", 3,
            "CLOSING_AUTHORITY", 4);

    /** The forward action for each rung — used when the caller is BELOW the target. */
    private static final Map<Integer, String> FORWARD_ACTION = Map.of(
            2, "SUBMIT_FOR_REVIEW",
            3, "FORWARD_TO_INCHARGE",
            4, "FORWARD_TO_CLOSING_AUTHORITY");

    /** The send-back action for each rung — used when the caller is ABOVE the target. */
    private static final Map<Integer, String> SEND_BACK_ACTION = Map.of(
            1, "SEND_BACK_DO",
            2, "SEND_BACK_REVIEWER",
            3, "SEND_BACK_INCHARGE");

    /** The five Final Decision outcomes, whose extra narrative fields land on the assessment row. */
    private static final Set<String> FINAL_DECISION_TARGETS =
            Set.of("CLOSE", "ADVISORY", "AWARD", "REJECT", "WITHDRAW", "SETTLE");

    /**
     * Mark for Closure (both target spellings — see {@code resolveAction}) and Reopen also carry a
     * Complaint Status on Portal value, but not the rest of the closure narrative: kept out of
     * {@code FINAL_DECISION_TARGETS} so {@code applyFinalDecisionNarrative} doesn't write a reopen or a
     * mark-for-closure's status into the Gist columns CLOSE uses for its own narrative.
     *
     * <p>The Forward tab's RBI Department and Regulatory Bodies options also carry a Complaint Status on
     * Portal value (the Other Office option does not — it captures Reason for Transfer / Sent from Office
     * Comments instead, on the transfer row rather than the assessment row).
     */
    private static final Set<String> STATUS_ON_PORTAL_TARGETS =
            Set.of("CLOSING_AUTHORITY", "MARK_FOR_CLOSURE", "REOPEN",
                    "OTHER_RBI_DEPARTMENT", "OTHER_REGULATORY_BODIES");

    private final CepcWorkflowService workflowService;
    private final ComplaintRepository complaintRepository;
    private final CepcComplaintAssessmentRepository assessmentRepository;
    private final InterOfficeTransferRepository transferRepository;
    private final InterOfficeTransferService transferService;
    private final CepcIdentityResolver identity;

    @PostMapping("/{complaintNumber}/send-for-approval")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY",
            "CEPC_SUPERVISOR", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> sendForApproval(
            @PathVariable String complaintNumber,
            @RequestBody(required = false) Map<String, Object> request) {

        Map<String, Object> body = request == null ? Map.of() : request;
        String target = text(body.get("target"));
        if (target == null) {
            return ResponseEntity.badRequest()
                    .body(envelope(false, "cepc.approval.error.target_required", null));
        }
        target = target.trim().toUpperCase(Locale.ROOT);

        Optional<Complaint> found = complaintRepository.findByComplaintNumber(complaintNumber);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(envelope(false, "No complaint found for " + complaintNumber + ".", null));
        }
        Complaint complaint = found.get();

        String callerRole = actingRole();
        String action = resolveAction(target, callerRole);
        if (action == null) {
            return ResponseEntity.badRequest()
                    .body(envelope(false, "cepc.approval.error.unsupported_target: " + target, null));
        }

        // The role gate for the Final Decision tab and for every send-back. performAction does NOT check
        // this itself — validateRoleAuthorization is a separate helper each caller has to invoke — so
        // without this block a dealing officer could POST a CLOSE and close their own complaint.
        if (!identity.isAdmin() && !workflowService.validateRoleAuthorization(callerRole, action)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(envelope(false,
                    "Your role is not authorised to perform this action (" + action + ") on this complaint.",
                    null));
        }

        // performAction does not consult the workflow's own isActionValidForState — that helper only feeds
        // getAvailableActions — so a reopen's state gate has to be applied here. Without it a POST from any
        // closing authority would reset an in-flight complaint to in_progress, clear its resolved/closed
        // timestamps and bump its reopen count. The UI only offers the toggle on a closed complaint, but the
        // UI is not the check.
        if ("REOPEN".equals(action) && !isReopenable(complaint.getStatus())) {
            return ResponseEntity.badRequest().body(envelope(false,
                    "Only a closed or resolved complaint can be reopened.", null));
        }

        // Every rung can now close a complaint directly from the Final Decision tab, except the dealing
        // officer: their only legitimate route to CLOSE_COMPLAINT is carrying out a closure that an incharge
        // or closing authority already decided on via Mark for Closure, so this is the one role that still
        // needs a state gate here — the same reason REOPEN has one above.
        if ("CLOSE_COMPLAINT".equals(action) && "CEPC_DO".equals(callerRole)
                && !isMarkedForClosure(complaint.getStatus(), complaint.getWorkflowStage())) {
            return ResponseEntity.badRequest().body(envelope(false,
                    "A dealing officer can only close a complaint once it has been marked for closure.", null));
        }

        // Checked before the transition runs, not inside applyFinalDecisionNarrative, because that method
        // only writes these two columns AFTER the transition has already committed — by then it's too late
        // to refuse an over-long gist without leaving the complaint closed with nothing recorded for it.
        if ("CLOSE".equals(target)) {
            String gistError = overLength("gistOfCase", text(body.get("gistOfCase")));
            if (gistError == null) {
                gistError = overLength("gistOfCaseRegional", text(body.get("gistOfCaseRegional")));
            }
            if (gistError != null) {
                return ResponseEntity.badRequest().body(envelope(false, gistError, null));
            }
        }

        // No @Transactional on this method, and the narrative is written only AFTER the transition has
        // succeeded. performAction is itself transactional, so catching the exception it throws inside a
        // transaction of ours would leave that transaction marked rollback-only: the response would be built
        // and then the commit would fail with "marked as rollback-only", replacing the reason the officer
        // needs to read with a message about transaction bookkeeping.
        Map<String, String> params = toWorkflowParams(body, target);
        Map<String, Object> result;
        try {
            result = workflowService.performAction(complaintNumber, action, params);
        } catch (ResponseStatusException e) {
            // The workflow router rejects a missing destination or a refused RE deadline this way, and the
            // screen shows `message` verbatim, so the reason has to survive rather than become a 500.
            return ResponseEntity.status(e.getStatusCode())
                    .body(envelope(false, e.getReason(), null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(envelope(false, e.getMessage(), null));
        }

        if (FINAL_DECISION_TARGETS.contains(target) || STATUS_ON_PORTAL_TARGETS.contains(target)) {
            applyFinalDecisionNarrative(complaint.getComplaintNumber(), target, body);
        }

        Map<String, Object> data = new LinkedHashMap<>(result);
        data.put("target", target);
        data.put("assignedToName", text(body.get("assignedToName")));
        return ResponseEntity.ok(envelope(true, "OK", data));
    }

    /**
     * The CRPC Head's verdict on an "Other Office" forward.
     *
     * <p><b>Keyed on the complaint, not the transfer.</b> The screen POSTs {@code complaintId} here because
     * that is the only identifier the detail view holds — it never learns the transfer row's id — so this
     * finds the complaint's outstanding request itself. Accepts the number as well as the id: the old tree's
     * route took the number while the client sends the id, and that mismatch is what made the button a 404.
     *
     * <p>The decision itself is {@link InterOfficeTransferService}'s, not reimplemented here. Approval has to
     * validate the destination office, check its capacity and pick a receiving officer; rejection has to
     * return the file to whoever held it before the request rather than to an arbitrary holder of the role.
     * The older implementation wrote {@code preForwardOfficer} columns on the complaint directly and did
     * none of that, so the CRPC Head's queue and the complaint's owner could disagree.
     */
    @PostMapping("/{idOrNumber}/office-head-decision")
    @CepcRoleGuard(roles = {"CRPC_HEAD", "CRPC_ADMIN", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> officeHeadDecision(
            @PathVariable String idOrNumber,
            @RequestBody(required = false) Map<String, Object> request) {

        Map<String, Object> body = request == null ? Map.of() : request;
        String decision = text(body.get("decision"));
        if (decision == null) {
            return ResponseEntity.badRequest()
                    .body(envelope(false, "cepc.office_head.error.decision_required", null));
        }
        decision = decision.trim().toUpperCase(Locale.ROOT);
        if (!"APPROVE".equals(decision) && !"REJECT".equals(decision)) {
            return ResponseEntity.badRequest()
                    .body(envelope(false, "cepc.office_head.error.unknown_decision: " + decision, null));
        }

        Optional<Complaint> found = resolveComplaint(idOrNumber);
        if (found.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(envelope(false, "No complaint found for " + idOrNumber + ".", null));
        }
        String complaintNumber = found.get().getComplaintNumber();

        Optional<InterOfficeTransfer> pending = transferRepository
                .findByComplaintNumberOrderByRequestedAtDesc(complaintNumber).stream()
                .filter(t -> "PENDING".equals(t.getStatus()))
                .findFirst();
        if (pending.isEmpty()) {
            // 409, not 404: the complaint exists and the caller is entitled to act on it, but there is
            // nothing outstanding — usually because another Head has already decided it.
            return ResponseEntity.status(HttpStatus.CONFLICT).body(envelope(false,
                    "There is no transfer awaiting a decision on " + complaintNumber + ".", null));
        }

        String actor = identity.resolveActor();
        if (actor == null) {
            actor = firstText(body, "performedBy");
        }
        String comment = firstText(body, "comment", "remarks", "reason");

        InterOfficeTransfer resolvedTransfer;
        try {
            if ("APPROVE".equals(decision)) {
                resolvedTransfer = transferService.approveTransfer(
                        pending.get().getId(), actor, text(body.get("overrideOfficeCode")));
            } else {
                // The service refuses a blank comment with a 400 of its own, which is the rule the screen
                // already enforces client-side; it is repeated there because the client is not the only
                // caller.
                resolvedTransfer = transferService.rejectTransfer(pending.get().getId(), actor, comment);
            }
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(envelope(false, e.getReason(), null));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("complaintNumber", complaintNumber);
        data.put("transferId", resolvedTransfer.getId());
        data.put("decision", decision);
        data.put("status", resolvedTransfer.getStatus());
        data.put("toOffice", resolvedTransfer.getToOffice());
        data.put("assignedOfficer", resolvedTransfer.getAssignedOfficer());
        return ResponseEntity.ok(envelope(true, "OK", data));
    }

    /** Accepts either the numeric id or the complaint number, since the screen sends each in places. */
    private Optional<Complaint> resolveComplaint(String idOrNumber) {
        String trimmed = idOrNumber == null ? "" : idOrNumber.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        if (trimmed.chars().allMatch(Character::isDigit)) {
            try {
                return complaintRepository.findById(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return complaintRepository.findByComplaintNumber(trimmed);
    }

    /**
     * The rung the caller is acting from — the HIGHEST they hold, not the lowest.
     *
     * <p>{@link CepcIdentityResolver#resolveCepcRole()} deliberately returns the lowest held role, because
     * for the dashboard the narrowest scope is the safe default. Here the opposite is true: an officer who
     * holds both DO and INCHARGE is using the in-charge's send-back button, and resolving them as a DO would
     * refuse an action they are entitled to. Authority is still checked against whatever this returns, so a
     * caller cannot reach an action by holding a role they do not have.
     */
    private String actingRole() {
        Set<String> held = identity.resolveRoles();
        String best = null;
        int bestRank = 0;
        for (Map.Entry<String, Integer> rung : LADDER.entrySet()) {
            if (held.contains(rung.getKey()) && rung.getValue() > bestRank) {
                bestRank = rung.getValue();
                best = rung.getKey();
            }
        }
        if (best == null && held.contains("ADMIN")) {
            return "CEPC_ADMIN";
        }
        return best;
    }

    /**
     * Mirrors the REOPEN arm of the workflow's own state gate, which {@code performAction} never invokes.
     *
     * <p>Closure persists {@code COMPLAINT_CLOSED}. {@code resolved} is accepted alongside it so a complaint
     * concluded by some other path is not refused a reopen on spelling alone; the lowercase {@code closed}
     * is gone, since nothing writes it now.
     */
    private static boolean isReopenable(String status) {
        if (status == null) {
            return false;
        }
        String normalised = status.strip().toLowerCase(Locale.ROOT);
        return "resolved".equals(normalised)
                || "complaint_closed".equals(normalised);
    }

    /**
     * The dealing officer's one legal path to CLOSE_COMPLAINT: Mark for Closure persists
     * {@link CepcStatus#COMPLAINT_SETTLED} with workflow stage {@code MARKED_FOR_CLOSURE} (see
     * {@code CepcWorkflowService#executeAction}'s MARK_FOR_CLOSURE case), so both have to match, not just
     * the status — {@code COMPLAINT_SETTLED} alone also covers "awaiting closure" and "settled" sub-stages
     * this role has no business closing out of.
     */
    private static boolean isMarkedForClosure(String status, String stage) {
        if (status == null || stage == null) {
            return false;
        }
        return CepcStatus.COMPLAINT_SETTLED.equalsIgnoreCase(status.strip())
                && "MARKED_FOR_CLOSURE".equalsIgnoreCase(stage.strip());
    }

    /** Maps the screen's target onto a workflow action, using the caller's rung where direction matters. */
    private String resolveAction(String target, String callerRole) {
        switch (target) {
            case "CLOSE":
                return "CLOSE_COMPLAINT";
            case "ADVISORY":
                return "ISSUE_ADVISORY";
            case "AWARD":
                return "PASS_AWARD";
            case "REJECT":
            case "WITHDRAW":
            case "SETTLE":
                return target;
            // Reopening is hosted on the Final Decision tab but is not one of FINAL_DECISION_TARGETS: it
            // carries no closure narrative, and running applyFinalDecisionNarrative for it would write the
            // reopen reason into the closure fields of the assessment row that closed the complaint.
            case "REOPEN":
                return "REOPEN";

            // The Final Decision tab's Mark for Closure outcome for a CLOSING_AUTHORITY caller: they are
            // already at the top rung, so the generic rank comparison below would see caller == target and
            // resolve this to REASSIGN (a peer handover) rather than "flag my own complaint for closure" —
            // two different things sharing one rank. Given its own target string rather than overloading
            // "CLOSING_AUTHORITY", which callers below this rung still use to forward up the ladder.
            case "MARK_FOR_CLOSURE":
                return "MARK_FOR_CLOSURE";
            // The Forward tab's non-office branches. Both close the complaint on this side, which the
            // workflow arms record as `forwarded_external` rather than `closed` — deliberately, because the
            // complaint has left CEPC rather than been resolved by it, and the Forward tab needs to be able
            // to tell those apart.
            case "OTHER_REGULATORY_BODIES":
            case "REGULATORY_BODIES":
                return "FORWARD_TO_REGULATORY_BODY";
            case "OTHER_RBI_DEPARTMENT":
            case "RBI_DEPARTMENT":
                return "FORWARD_TO_OTHER_RBI_DEPT";
            case "OTHER_OFFICE":
                return "FORWARD_TO_OTHER_OFFICE";
            case "CONTACT_PERSON":
                return "FORWARD_TO_CONTACT";
            case "RE":
                return "FORWARD_TO_RE";
            default:
                break;
        }

        Integer targetRank = TARGET_RANK.get(target);
        if (targetRank == null) {
            return null;
        }
        int callerRank = LADDER.getOrDefault(callerRole == null ? "" : callerRole, 0);
        if (callerRank == 0) {
            // No recognised rung: treat it as a forward, which is the only direction an unranked caller
            // could legitimately want, and let validateRoleAuthorization refuse it.
            return FORWARD_ACTION.get(targetRank);
        }
        if (callerRank < targetRank) {
            return FORWARD_ACTION.get(targetRank);
        }
        if (callerRank > targetRank) {
            return SEND_BACK_ACTION.get(targetRank);
        }
        // Same rung: a handover between peers, which is what REASSIGN is for.
        return "REASSIGN";
    }

    /**
     * Flattens the request into the string map the workflow router takes.
     *
     * <p>Every alias the router already understands is filled from whichever key the screen happens to use,
     * because the four dialogs on this tab name the same thing differently — {@code assignedTo} in one,
     * {@code remarks} versus {@code reason} in another.
     */
    private Map<String, String> toWorkflowParams(Map<String, Object> body, String target) {
        Map<String, String> params = new LinkedHashMap<>();

        String actor = firstText(body, "performedBy", "assignedTo");
        String resolved = identity.resolveActor();
        // The resolved identity wins: `performedBy` comes off the client and is what lands in the audit log
        // and in `withdrawnBy`, so a spoofed value would misattribute the decision.
        params.put("actor", resolved != null ? resolved : (actor == null ? "" : actor));

        String remarks = firstText(body, "remarks", "reason", "comment");
        if (remarks != null) {
            params.put("remarks", remarks);
        }
        put(params, "userRole", actingRole());

        // Honoured only in MANUAL mode. On AUTO the screen still sends whoever it happened to show, and
        // passing that through would silently turn every automatic assignment into a manual one.
        String mode = text(body.get("assignmentMode"));
        if (mode == null || !"AUTO".equalsIgnoreCase(mode.trim())) {
            put(params, "targetUser", text(body.get("assignedTo")));
        }

        // Destinations. The router accepts `targetDepartment` for all three external forwards.
        put(params, "targetDepartment", firstText(body, "assignedToName", "officeCode", "targetDepartment"));
        put(params, "targetOffice", firstText(body, "officeCode", "targetOffice"));
        put(params, "transferReason", remarks);

        put(params, "closureClause", firstText(body, "closureClause", "crpcClause", "proposedClause"));
        put(params, "customClosureText", text(body.get("remarks")));
        put(params, "advisoryText", firstText(body, "advisoryText", "remarks"));
        put(params, "awardImplementationDate", text(body.get("awardImplementationDate")));
        put(params, "reopenedDate", text(body.get("reopenedDate")));

        params.put("target", target);
        return params;
    }

    /**
     * Stores the Final Decision tab's narrative and figures on the complaint's assessment row.
     *
     * <p>These have no column on {@code COMPLAINTS} and belong to the officer's assessment, not to the
     * complaint's workflow state — the same split the Summary tab uses, and for the same reason: the
     * complaint row is versioned and a decision form held open would otherwise conflict with any transition
     * that touched it in the meantime. The dates and the closure clause themselves DO live on the complaint
     * and are written by the workflow arm, so they are not duplicated here.
     */
    private void applyFinalDecisionNarrative(String complaintNumber, String target,
                                             Map<String, Object> body) {
        CepcComplaintAssessment assessment = assessmentRepository
                .findByComplaintNumber(complaintNumber)
                .orElseGet(() -> CepcComplaintAssessment.builder()
                        .complaintNumber(complaintNumber)
                        .build());

        switch (target) {
            case "CLOSE":
                set(body, "gistOfCase", v -> assessment.setGistOfCase(text(v)));
                set(body, "gistOfCaseRegional", v -> assessment.setGistOfCaseRegional(text(v)));
                set(body, "complaintStatusOnPortal", v -> assessment.setComplaintStatusOnPortal(text(v)));
                set(body, "speakingOrderGenerated", v -> assessment.setSpeakingOrderGenerated(flag(v)));
                break;
            // Mark for Closure, under either target spelling, Reopen, and the Forward tab's RBI Department /
            // Regulatory Bodies options only ever carry the portal status — never route them through the
            // CLOSE case, which would also write gistOfCase/gistOfCaseRegional.
            case "CLOSING_AUTHORITY":
            case "MARK_FOR_CLOSURE":
            case "REOPEN":
            case "OTHER_RBI_DEPARTMENT":
            case "OTHER_REGULATORY_BODIES":
                set(body, "complaintStatusOnPortal", v -> assessment.setComplaintStatusOnPortal(text(v)));
                break;
            case "ADVISORY":
                set(body, "advisoryComplianceDate", v -> assessment.setAdvisoryComplianceDate(day(v)));
                // The screen calls this `disputeAmount`; it is the same figure the Summary tab collects as
                // `disputedAmount`, so it lands in that one column rather than a near-twin that would make
                // the two tabs disagree about the amount in dispute.
                set(body, "disputeAmount", v -> assessment.setDisputedAmount(amount(v)));
                set(body, "compensationLoss", v -> assessment.setCompensationLoss(amount(v)));
                set(body, "compensationMental", v -> assessment.setCompensationMental(amount(v)));
                break;
            case "AWARD":
                set(body, "awardAcceptanceDate", v -> assessment.setAwardAcceptanceDate(day(v)));
                break;
            default:
                // REJECT / WITHDRAW / SETTLE carry only `remarks`, which the workflow arm records.
                break;
        }
        set(body, "systemicIssue", v -> assessment.setSystemicIssue(text(v)));

        assessment.setLastModifiedBy(identity.resolveActor());
        assessmentRepository.save(assessment);
    }

    // ── Value readers ───────────────────────────────────────────────────────────
    //
    // Presence-checked like the Summary tab's patch, so a key the dialog did not send leaves the stored
    // value alone instead of blanking it. The dialogs send disjoint subsets of these fields.

    private static void set(Map<String, Object> body, String key,
                            java.util.function.Consumer<Object> setter) {
        if (body.containsKey(key)) {
            setter.accept(body.get(key));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    /** The Gist fields' app-level cap — their TEXT/CLOB columns carry no length constraint of their own. */
    private static final int MAX_GIST_LENGTH = 2000;

    private static String overLength(String field, String value) {
        if (value != null && value.length() > MAX_GIST_LENGTH) {
            return field + " may be at most " + MAX_GIST_LENGTH + " characters.";
        }
        return null;
    }

    private static String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String value = text(body.get(key));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null) {
            params.put(key, value);
        }
    }

    private static Boolean flag(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String s = String.valueOf(value).trim();
        if (s.isEmpty()) {
            return null;
        }
        // The screen sends this one as "Yes"/"No" in places and as a boolean in others.
        return List.of("true", "yes", "y", "1").contains(s.toLowerCase(Locale.ROOT));
    }

    private static BigDecimal amount(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return new BigDecimal(s.replace(",", ""));
        } catch (NumberFormatException e) {
            log.debug("Unparseable amount '{}' in a final decision - ignored", s);
            return null;
        }
    }

    private static LocalDate day(Object value) {
        String s = text(value);
        if (s == null) {
            return null;
        }
        try {
            return LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (Exception e) {
            log.debug("Unparseable date '{}' in a final decision - ignored", s);
            return null;
        }
    }

    private static Map<String, Object> envelope(boolean success, String message, Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        out.put("data", data);
        out.put("timestamp", LocalDateTime.now().toString());
        return out;
    }
}
