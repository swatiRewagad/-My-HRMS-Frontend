package com.hrms.cms.service;

import java.util.Locale;
import java.util.Map;

/**
 * The CEPC status vocabulary: the canonical {@code COMPLAINTS.STATUS} values and the legacy tokens that
 * mean the same thing.
 *
 * <p>The canonical names mirror the constants of {@code com.rbi.cms.common.enums.ComplaintStatus}, which
 * cannot be imported here because {@code cms-backend} does not depend on {@code cms-common}. The strings are
 * therefore the contract, and this class exists so the dashboard filter vocabulary and the search predicates
 * cannot drift away from them — the hazard {@code CepcDashboardFilterSeeder}'s javadoc calls out, where a
 * renamed status leaves a filter resolving fine and returning zero rows forever.
 *
 * <p><b>Why a few statuses still have two names.</b> CEPC used to persist lower snake_case tokens for most
 * statuses and the read path accepted both spellings. Those have since been removed: {@code pending},
 * {@code info_requested}, {@code incharge_review}, {@code awaiting_closure}, {@code reviewer_review},
 * {@code closed} and {@code withdrawn} are gone, and {@code CepcWorkflowService} now writes the canonical
 * name directly. Only the three outbound-transfer statuses keep a legacy synonym, because
 * {@code InterOfficeTransferService} and the {@code STATUS_MASTER} values behind it are shared with RBIO and
 * are not CEPC's to change.
 *
 * <p>Removing a spelling is not free: a stored value outside {@link #everySpelling()} is invisible to every
 * dashboard filter and, on {@code dev-local}, is deleted by {@code CepcDevSeeder}'s unrecognised-status
 * purge. So a token may only be dropped here once nothing writes it.
 *
 * <p>Comparisons in {@code CepcComplaintSearchService} and {@code ComplaintRepository.cepcDashboardCounts}
 * case-fold both sides, so case never matters. Anything comparing raw values must lowercase deliberately.
 */
public final class CepcStatus {

    /** Intake: what a freshly filed complaint carries before anyone picks it up or examines it. */
    public static final String NEW_COMPLAINT = "NEW_COMPLAINT";
    public static final String INFORMATION_REQUIRED = "INFORMATION_REQUIRED";

    /** Forwarded to the reviewer's queue. */
    public static final String SENT_TO_REVIEWER = "SENT_TO_REVIEWER";

    /**
     * A conciliation meeting is ahead. Written by {@code CepcConciliationService} only, and it has no legacy
     * synonym: CEPC recorded this as a workflow stage alone until the Conciliation tab's Confirm began
     * setting the status too, so there is no older lowercase token to accept alongside it.
     */
    public static final String MEETING_SCHEDULED = "MEETING_SCHEDULED";

    /**
     * Awaiting the office head's sign-off on an outbound inter-office transfer, which cannot proceed until
     * the CRPC head approves it. Distinct from {@link #SENT_TO_INCHARGE}, which is the in-charge rung itself.
     */
    public static final String PENDING_OFFICE_HEAD_APPROVAL = "PENDING_OFFICE_HEAD_APPROVAL";

    /** Forwarded to the in-charge for the in-charge rung of the approval ladder. */
    public static final String SENT_TO_INCHARGE = "SENT_TO_INCHARGE";

    /** Forwarded to the closing authority to record the final decision. */
    public static final String SENT_TO_CLOSING_AUTHORITY = "SENT_TO_CLOSING_AUTHORITY";

    /** Relief delivered, closure letter still to go out. */
    public static final String COMPLAINT_SETTLED = "COMPLAINT_SETTLED";

    /** The advisory issued to the entity has been confirmed complied with. */
    public static final String ADVISORY_COMPLIED = "ADVISORY_COMPLIED";

    /**
     * A complaint the officer has started filing and not submitted.
     *
     * <p>No legacy synonym: {@code WorkflowController} overwrites an incoming {@code draft} to
     * {@code assigned}, so the lowercase token never survives intake.
     */
    public static final String DRAFT = "DRAFT";

    /** Referred up to RBI and awaiting the contact person's response. */
    public static final String SENT_TO_RBI = "SENT_TO_RBI";

    /** Reopened after conclusion, and not yet picked up again. */
    public static final String COMPLAINT_REOPEN = "COMPLAINT_REOPEN";

    /** The three send-back destinations, each its own status rather than one status plus a stage. */
    public static final String SENT_BACK_TO_DO = "SENT_BACK_TO_DO";
    public static final String SENT_BACK_TO_REVIEWER = "SENT_BACK_TO_REVIEWER";
    public static final String SENT_BACK_TO_INCHARGE = "SENT_BACK_TO_INCHARGE";

    public static final String SENT_TO_OTHER_DEPARTMENTS = "SENT_TO_OTHER_DEPARTMENTS";
    public static final String SENT_TO_OTHER_REGULATED_BODIES = "SENT_TO_OTHER_REGULATED_BODIES";
    public static final String SENT_TO_OTHER_OFFICE = "SENT_TO_OTHER_OFFICE";

    /** Terminal. Every {@code pendingOnly} KPI excludes these three. */
    public static final String COMPLAINT_CLOSED = "COMPLAINT_CLOSED";
    public static final String COMPLAINT_WITHDRAWN = "COMPLAINT_WITHDRAWN";
    public static final String COMPLAINT_REJECTED = "COMPLAINT_REJECTED";
    public static final String MARK_FOR_CLOSURE = "MARK_FOR_CLOSURE";
    /**
     * The statuses that end a complaint's life, lowercased.
     *
     * <p>Held here rather than in the search service so the {@code pendingOnly} predicate and
     * {@code ComplaintRepository.cepcDashboardCounts} cannot disagree about what "pending" excludes — a
     * disagreement shows up as a badge that does not match its own grid.
     */
    public static java.util.Set<String> terminalSpellings() {
        return java.util.stream.Stream.of(COMPLAINT_CLOSED, COMPLAINT_WITHDRAWN, COMPLAINT_REJECTED)
                .flatMap(s -> allSpellings(s).stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * The token the CEPC write path persists for each canonical status.
     *
     * <p>Only the three outbound-transfer statuses still have one. {@code pending}, {@code info_requested},
     * {@code incharge_review}, {@code awaiting_closure}, {@code reviewer_review}, {@code closed} and
     * {@code withdrawn} were removed from the CEPC vocabulary: {@code CepcWorkflowService} was changed to
     * write the canonical names instead, so there is no longer a second spelling for the read path to accept.
     * Every other status carries no entry because nothing writes an older token for it.
     *
     * <p>Taken from {@code CepcWorkflowService.executeAction()} and {@code InterOfficeTransferService}.
     * Inventing a plausible-looking one is not a harmless error: the filter resolves, the predicate builds,
     * and it returns zero rows forever.
     */
    private static final Map<String, String> LEGACY = Map.ofEntries(
            Map.entry(SENT_TO_OTHER_DEPARTMENTS, "forwarded_to_contact"),
            Map.entry(SENT_TO_OTHER_REGULATED_BODIES, "forwarded_external"),
            Map.entry(SENT_TO_OTHER_OFFICE, "sent_to_other"));

    /**
     * A {@code STATUS_IN} predicate value matching {@code status} under either its canonical name or the
     * legacy token the workflow writes.
     *
     * <p>{@code predicateValueList()} splits on the comma and both sides of the comparison are lowercased,
     * so the pair behaves as one status.
     */
    public static String orLegacy(String status) {
        String legacy = LEGACY.get(status);
        return legacy == null ? status : status + "," + legacy;
    }

    /** Every spelling of {@code status}, lowercased, for callers comparing raw values themselves. */
    public static java.util.Set<String> allSpellings(String status) {
        String legacy = LEGACY.get(status);
        String canonical = status.toLowerCase(Locale.ROOT);
        return legacy == null ? java.util.Set.of(canonical)
                : java.util.Set.of(canonical, legacy.toLowerCase(Locale.ROOT));
    }

    /** Every status this class recognises, canonical name first. */
    private static final java.util.List<String> CANONICAL = java.util.List.of(
            DRAFT, NEW_COMPLAINT, INFORMATION_REQUIRED, SENT_TO_RBI, SENT_TO_OTHER_REGULATED_BODIES,
            SENT_TO_OTHER_DEPARTMENTS, SENT_TO_OTHER_OFFICE, COMPLAINT_REOPEN, COMPLAINT_REJECTED,
            COMPLAINT_SETTLED, COMPLAINT_WITHDRAWN, COMPLAINT_CLOSED, MEETING_SCHEDULED, ADVISORY_COMPLIED,
            SENT_BACK_TO_REVIEWER, SENT_BACK_TO_DO, SENT_TO_REVIEWER, PENDING_OFFICE_HEAD_APPROVAL,
            SENT_TO_INCHARGE, SENT_TO_CLOSING_AUTHORITY, SENT_BACK_TO_INCHARGE);

    /**
     * Every accepted spelling of every status, lowercased — the CEPC status vocabulary.
     *
     * <p>A CEPC complaint whose status is not in here is one no filter can select and no chip can style, so
     * it is invisible on the dashboard however it got there.
     */
    public static java.util.Set<String> everySpelling() {
        return CANONICAL.stream()
                .flatMap(s -> allSpellings(s).stream())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * Display labels, keyed by every lowercase spelling so either the canonical name or the legacy token
     * resolves. Mirrors {@code ComplaintStatus.getValue()}.
     *
     * <p>Includes tokens with no canonical constant of their own ({@code forwarded}, {@code resolved}) because
     * the grid still has to render complaints the workflow left in them.
     */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("new_complaint", "New Complaint"),
            Map.entry("information_required", "Information Required"),
            Map.entry("meeting_scheduled", "Meeting Scheduled"),
            Map.entry("advisory_complied", "Advisory Complied"),
            Map.entry("sent_back_to_reviewer", "Sent Back To Reviewer"),
            Map.entry("sent_back_to_do", "Sent Back To DO"),
            Map.entry("sent_to_reviewer", "Sent To Reviewer"),
            Map.entry("sent_to_incharge", "Sent To Incharge"),
            Map.entry("sent_to_closing_authority", "Sent To Closing Authority"),
            Map.entry("sent_back_to_incharge", "Sent Back To Incharge"),
            Map.entry("sent_to_rbi", "Sent To RBI"),
            Map.entry("complaint_reopen", "Complaint Re Open"),
            Map.entry("complaint_rejected", "Complaint Rejected"),
            Map.entry("pending_office_head_approval", "Pending Office Head Approval"),
            Map.entry("complaint_settled", "Complaint Settled"),
            Map.entry("marked_for_closure", "Marked For Closure"),
            Map.entry("sent_to_other_departments", "Sent To Other Departments"),
            Map.entry("forwarded_to_contact", "Sent To Other Departments"),
            Map.entry("sent_to_other_regulated_bodies", "Sent To Other Regulated Bodies"),
            Map.entry("forwarded_external", "Sent To Other Regulated Bodies"),
            Map.entry("sent_to_other_office", "Sent To Other Office"),
            Map.entry("sent_to_other", "Sent To Other Office"),
            Map.entry("complaint_closed", "Complaint Closed"),
            Map.entry("complaint_withdrawn", "Complaint Withdrawn"),
            Map.entry("forwarded", "Forwarded"),
            Map.entry("resolved", "Resolved"),
            Map.entry("rejected", "Complaint Rejected"),
            Map.entry("draft", "Draft"));

    /**
     * The human reading of a stored status, for display only.
     *
     * <p>An unrecognised value is title-cased rather than dropped, so a status this class has not caught up
     * with still renders as words instead of a blank chip or a raw {@code snake_case} token.
     */
    public static String label(String status) {
        if (status == null || status.isBlank()) {
            return "";
        }
        String key = status.strip().toLowerCase(Locale.ROOT);
        String known = LABELS.get(key);
        if (known != null) {
            return known;
        }
        StringBuilder out = new StringBuilder(key.length());
        for (String word : key.split("[_\\s]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word, 1, word.length());
        }
        return out.toString();
    }

    /**
     * The human reading of a stored status, disambiguated by {@code workflowStage} where the status alone
     * cannot tell two things apart.
     *
     * <p>The three send-back actions ({@code SEND_BACK_DO/REVIEWER/INCHARGE} in
     * {@code CepcWorkflowService#executeAction}) all persist the generic {@code "sent_back"} status and keep
     * the destination only in the stage, by design — the stage is what the dashboard filter's
     * {@code PREDICATE_STAGE_IN} rows already key on. The generic status alone renders the generic "Sent
     * Back"; this resolves the specific label the stage names instead.
     *
     * <p>Similarly, {@code FORWARD_TO_CLOSING_AUTHORITY} (and {@code APPROVE_CLOSURE}) persist
     * {@code COMPLAINT_SETTLED} with stage {@code AWAITING_CLOSURE} for "sent up to the closing authority" —
     * a different thing from the complaint actually being settled, which is every other
     * {@code COMPLAINT_SETTLED} stage (advisory issued, award passed, marked for closure).
     *
     * <p>{@code MARK_FOR_CLOSURE} persists the same {@code COMPLAINT_SETTLED} status with stage
     * {@code MARKED_FOR_CLOSURE} — the decision has been made and the complaint has gone back to the
     * dealing officer to carry it out, a different thing again from "sent up to the closing authority".
     */
    public static String label(String status, String stage) {
        if (status == null) {
            return label(status);
        }
        return label(resolveSpelling(status, stage));
    }

    /**
     * The one spelling that {@link #label(String, String)} and {@link #labelKey(String, String)} must agree on.
     *
     * <p>Extracted so the chip's text and the chip's translation key cannot disambiguate differently: a
     * {@code COMPLAINT_SETTLED} row at stage {@code AWAITING_CLOSURE} reading "Sent To Closing Authority" in
     * English but keyed on {@code complaint_settled} would translate into a different status than it displays.
     */
    private static String resolveSpelling(String status, String stage) {
        String statusKey = status.strip().toLowerCase(Locale.ROOT);
        String stageKey = stage == null ? "" : stage.strip().toLowerCase(Locale.ROOT);

        if ("sent_back".equals(statusKey) && LABELS.containsKey(stageKey)) {
            return stageKey;
        }

        if (COMPLAINT_SETTLED.toLowerCase(Locale.ROOT).equals(statusKey)) {
            if ("awaiting_closure".equals(stageKey)) {
                return SENT_TO_CLOSING_AUTHORITY.toLowerCase(Locale.ROOT);
            }
            if ("marked_for_closure".equals(stageKey)) {
                return "marked_for_closure";
            }
        }

        return statusKey;
    }

    /**
     * The {@code TRANSLATION_KEYS.CODE} under which this status's wording is held, so the grid chip can be
     * localised without the frontend re-deriving the stage disambiguation {@link #label(String, String)} does.
     *
     * <p>Returns null for a blank status rather than a key, because a key that nothing seeded renders as the
     * key itself. Callers that receive null fall back to {@link #label(String, String)}, which is also what the
     * frontend does for a status this class has not caught up with: the English title-cased reading beats a
     * raw {@code ui.status.*} token on screen.
     */
    public static String labelKey(String status, String stage) {
        if (status == null || status.isBlank()) {
            return null;
        }
        return "ui.status." + resolveSpelling(status, stage);
    }

    /**
     * Every stored spelling whose display label contains {@code term}, lowercased.
     *
     * <p>The grid shows the label, so the column filter has to accept it: typing "Other Departments" has to
     * reach both {@code SENT_TO_OTHER_DEPARTMENTS} and {@code forwarded_to_contact}, whose token shares no
     * substring with what the officer read off the screen.
     */
    public static java.util.Set<String> matchingLabel(String term) {
        String needle = term == null ? "" : term.strip().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) {
            return java.util.Set.of();
        }
        return LABELS.entrySet().stream()
                .filter(e -> e.getValue().toLowerCase(Locale.ROOT).contains(needle))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private CepcStatus() {
    }
}
