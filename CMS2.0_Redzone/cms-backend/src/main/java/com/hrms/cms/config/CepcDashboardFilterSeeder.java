package com.hrms.cms.config;

import com.hrms.cms.entity.CepcDashboardFilter;
import com.hrms.cms.repository.CepcDashboardFilterRepository;
import com.hrms.cms.service.CepcStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.hrms.cms.entity.CepcDashboardFilter.DIMENSION_KPI;
import static com.hrms.cms.entity.CepcDashboardFilter.DIMENSION_STATUS;
import static com.hrms.cms.entity.CepcDashboardFilter.DIMENSION_TAB;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_ASSIGNED_ROLE_IN;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_ASSIGNED_TO_ME;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_NONE;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_SENT_BACK_TO_ME;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_SLA_BREACHED;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_SLA_WINDOW;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_STAGE_IN;
import static com.hrms.cms.entity.CepcDashboardFilter.PREDICATE_STATUS_IN;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_ALL_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_CLOSED_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_COMPLAINT_ASSIGNED_TO_ME;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_DRAFT_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_MARK_FOR_CLOSURE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_MEETING_SCHEDULED;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_NEW_COMPLAINT;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_REOPENED_COMPLAINTS;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_BACK_TO_CEPC_DO;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_BACK_TO_CEPC_IN_CHARGE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_BACK_TO_CEPC_REVIEWER;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CEPC_DEALING_OFFICIAL;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CEPC_IN_CHARGE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CEPC_REVIEWER;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_CLOSING_AUTHORITY;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_OTHER_OFFICE;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_OTHER_RBI_DEPARTMENT;
import static com.hrms.cms.entity.CepcDashboardFilter.STATUS_SENT_TO_OTHER_REGULATED_BODIES;

/**
 * Seeds {@code CEPC_DASHBOARD_FILTER}: the status dropdown, the six dashboard tabs and the KPI cards.
 *
 * <p>Insert-if-absent, matching {@link RbioStatusMasterSeeder}: editing a label here does not update a row
 * that already exists, so a correction needs a code-scoped UPDATE in both migration directories.
 *
 * <p><b>The codes below are a contract and must match their counterparts character for character.</b> A
 * mismatch is not an error anywhere — an unrecognised code deliberately matches nothing, so it shows up as a
 * permanently empty grid. The two dimensions answer to different counterparts:
 *
 * <ul>
 *   <li>{@code TAB} and {@code KPI} codes are hardcoded in the frontend as display labels
 *       ({@code translateTabIdToStatus} in {@code cepc-dashboard.component.ts}, and the card ids in
 *       {@code cepc-dashboard-kpi}), and arrive on the wire exactly as written, spaces and capitals
 *       included.</li>
 *   <li>{@code STATUS} codes must match the {@code STATUS_CODE} values {@link RoleStatusMappingSeeder}
 *       writes, which is where the frontend gets them from. Both sides read the
 *       {@code CepcDashboardFilter.STATUS_*} constants so the compiler holds them together; the seeded rows
 *       in an existing database are what a rename would leave behind.</li>
 * </ul>
 *
 * <p>{@code ALL_COMPLAINTS} is the dashboard's {@code DEFAULT_STATUS_CODE}, sent on the very first request
 * before the user has touched anything.
 */
@Component
@Order(30)
@RequiredArgsConstructor
@Slf4j
public class CepcDashboardFilterSeeder implements CommandLineRunner {

    private final CepcDashboardFilterRepository filterRepo;

    @Override
    @Transactional
    public void run(String... args) {
        List<CepcDashboardFilter> rows = new ArrayList<>();
        rows.addAll(statusFilters());
        rows.addAll(tabFilters());
        rows.addAll(kpiFilters());

        int inserted = 0;
        int repaired = 0;
        for (CepcDashboardFilter row : rows) {
            CepcDashboardFilter existing =
                    filterRepo.findByDimensionAndFilterCode(row.getDimension(), row.getFilterCode()).orElse(null);
            if (existing == null) {
                row.setCreatedAt(LocalDateTime.now());
                filterRepo.save(row);
                inserted++;
                continue;
            }
            // Insert-if-absent alone is not enough for the PREDICATE side. The codes are a stable contract
            // with the frontend, but the predicate that backs a code is an implementation detail we do
            // revise — and a database seeded before a revision kept the stale expression, resolving fine and
            // returning zero rows forever. That is precisely what a status rename used to leave behind.
            // Labels and display order stay untouched: an operator may legitimately have retitled a filter.
            // pendingOnly is part of the predicate, not presentation: it decides whether concluded complaints
            // appear. Left out of this comparison it would be the one piece of the expression a seeded
            // database could never receive a correction to.
            if (!java.util.Objects.equals(existing.getPredicateKind(), row.getPredicateKind())
                    || !java.util.Objects.equals(existing.getPredicateValues(), row.getPredicateValues())
                    || existing.isPendingOnly() != row.isPendingOnly()) {
                existing.setPredicateKind(row.getPredicateKind());
                existing.setPredicateValues(row.getPredicateValues());
                existing.setPendingOnly(row.isPendingOnly() ? "Y" : "N");
                filterRepo.save(existing);
                repaired++;
            }
        }
        if (inserted > 0 || repaired > 0) {
            log.info("CEPC dashboard filter vocabulary seeded: {} of {} rows inserted, {} predicates refreshed",
                    inserted, rows.size(), repaired);
        }
    }

    /**
     * What each status-dropdown code selects. Which roles are offered which code lives in
     * {@code ROLE_STATUS_MAPPING} — see {@link RoleStatusMappingSeeder}.
     *
     * <p>These are scope and stage selectors as much as statuses — {@code COMPLAINT_ASSIGNED_TO_ME}
     * constrains the caller, not the complaint. That is why the dimension carries a predicate kind rather
     * than a status value: a row whose only expression is {@code status = 'COMPLAINT_ASSIGNED_TO_ME'} can
     * never match anything, which is the bug the old search service shipped.
     *
     * <p><b>Every value below is a literal the CEPC workflow actually writes</b>, taken from
     * {@code CepcWorkflowService.executeAction()} and {@code InterOfficeTransferService}. Inventing a
     * plausible-looking one is not a harmless error: the code resolves, the predicate builds, and the filter
     * returns zero rows forever.
     *
     * <p><b>Prefer {@code STATUS_IN} where a 1:1 status exists.</b> {@code CepcDevSeeder} writes stage values
     * the real workflow never writes ({@code PENDING_REVIEW} for {@code REVIEWER_REVIEW},
     * {@code UNDER_EXAMINATION} for {@code EXAMINATION}, and others), so a {@code STAGE_IN} filter can look
     * broken against dev-local seed data while being correct in production.
     */
    private List<CepcDashboardFilter> statusFilters() {
        List<CepcDashboardFilter> rows = new ArrayList<>();
        int order = 0;

        rows.add(status(STATUS_ALL_COMPLAINTS, "All Complaints", PREDICATE_NONE, null, ++order));
        // pendingOnly, to match the "Pending with Me" KPI card: both answer "what is in my bucket", and a
        // complaint that is closed, rejected or withdrawn is not work anyone still holds. Without it the card
        // and the dropdown entry return different row counts for the same question.
        rows.add(status(STATUS_COMPLAINT_ASSIGNED_TO_ME, "Complaint Assigned To Me",
                PREDICATE_ASSIGNED_TO_ME, null, true, ++order));

        // Intake. 'pending' is what @PrePersist writes, and is the same status under its legacy spelling.
        // ASSIGNED is deliberately NOT included: it is a status of its own, so selecting "New Complaint" and
        // getting rows whose chip reads "Assigned" looks like the filter is broken.
        rows.add(status(STATUS_NEW_COMPLAINT, "New Complaint", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.NEW_COMPLAINT), ++order));

        // Complaints the officer has started and not submitted, which carry status DRAFT. Previously
        // STAFF_DRAFT, which listed half-finished intake forms out of the STAFF_DRAFT table — rows with no
        // complaint number, no status and nothing for the grid's other columns to show.
        rows.add(status(STATUS_DRAFT_COMPLAINTS, "Draft Complaints", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.DRAFT), ++order));

        rows.add(status(STATUS_MEETING_SCHEDULED, "Meeting Scheduled", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.MEETING_SCHEDULED), ++order));

        // Just SENT_TO_REVIEWER now. "UNDER_REVIEW" was listed here and matched nothing, and
        // "reviewer_review" went when CepcWorkflowService's SUBMIT_FOR_REVIEW was changed to write the
        // canonical name — so there is no second spelling left for this queue.
        rows.add(status(STATUS_SENT_TO_CEPC_REVIEWER, "Sent to CEPC Reviewer",
                PREDICATE_STATUS_IN, CepcStatus.orLegacy(CepcStatus.SENT_TO_REVIEWER), ++order));
        // Keyed on the hop, SENT_TO_INCHARGE, not on PENDING_OFFICE_HEAD_APPROVAL: the old vocabulary made
        // "in-charge" and "office head approval" one concept, so keying on the approval status listed
        // complaints that had never been sent here. "incharge_review" is gone too — FORWARD_TO_INCHARGE and
        // APPROVE_REVIEW now write SENT_TO_INCHARGE directly, which also settles the old disagreement where
        // those complaints filtered in here but rendered a "Pending Office Head Approval" chip.
        rows.add(status(STATUS_SENT_TO_CEPC_IN_CHARGE, "Sent to CEPC In-charge",
                PREDICATE_STATUS_IN, CepcStatus.orLegacy(CepcStatus.SENT_TO_INCHARGE), ++order));
        // Keyed on the status of the same name, not on COMPLAINT_SETTLED. It was the latter only because no
        // SENT_TO_CLOSING_AUTHORITY status existed to key on, which made this row answer "is settled" while
        // claiming to answer "was sent to the closing authority", and made it a duplicate of MARK_FOR_CLOSURE.
        rows.add(status(STATUS_SENT_TO_CLOSING_AUTHORITY, "Sent to Closing Authority",
                PREDICATE_STATUS_IN, CepcStatus.orLegacy(CepcStatus.SENT_TO_CLOSING_AUTHORITY), ++order));

        // No forward-to-DO action exists in executeAction, so there is no status or stage that records the
        // hop. Approximated by where the complaint currently sits, which is the nearest true statement
        // available: "assigned to a dealing officer" rather than "was sent to one".
        //
        // pendingOnly because ASSIGNED_ROLE_IN keeps matching after the complaint is finished — a closed or
        // withdrawn complaint retains its last assigned role, so without this the list answers "was ever with
        // a dealing official" while claiming to answer "is with one".
        rows.add(status(STATUS_SENT_TO_CEPC_DEALING_OFFICIAL, "Sent to CEPC Dealing Official",
                PREDICATE_ASSIGNED_ROLE_IN, "CEPC_DO", true, ++order));

        // Send-backs all write status='sent_back' and distinguish the destination only in the stage, so
        // these three must be STAGE_IN — a STATUS_IN would make all three return the same rows.
        rows.add(status(STATUS_SENT_BACK_TO_CEPC_DO, "Sent Back to CEPC Dealing Official",
                PREDICATE_STAGE_IN, CepcStatus.SENT_BACK_TO_DO, ++order));
        rows.add(status(STATUS_SENT_BACK_TO_CEPC_REVIEWER, "Sent Back to CEPC Reviewer",
                PREDICATE_STAGE_IN, CepcStatus.SENT_BACK_TO_REVIEWER, ++order));
        rows.add(status(STATUS_SENT_BACK_TO_CEPC_IN_CHARGE, "Sent Back to CEPC In-charge",
                PREDICATE_STAGE_IN, CepcStatus.SENT_BACK_TO_INCHARGE, ++order));

        // Outbound transfers. status='forwarded_external' covers the regulatory-body and other-RBI-dept
        // arms indiscriminately, so only the stage separates them.
        rows.add(status(STATUS_SENT_TO_OTHER_RBI_DEPARTMENT, "Sent to Other RBI Department",
                PREDICATE_STAGE_IN, CepcStatus.SENT_TO_OTHER_DEPARTMENTS, ++order));
        rows.add(status(STATUS_SENT_TO_OTHER_REGULATED_BODIES, "Sent to Other Regulated Bodies",
                PREDICATE_STAGE_IN, CepcStatus.SENT_TO_OTHER_REGULATED_BODIES, ++order));

        // Two stages for one concept: InterOfficeTransferService writes SENT_TO_OTHER_OFFICE, and the
        // no-service fallback in CepcWorkflowService writes FORWARDED_OTHER_OFFICE. Rows exist under both.
        rows.add(status(STATUS_SENT_TO_OTHER_OFFICE, "Sent to Other Office",
                PREDICATE_STAGE_IN, CepcStatus.SENT_TO_OTHER_OFFICE, ++order));

        // Keyed on COMPLAINT_REOPEN rather than stage='REOPENED', which returned rows whose chip read
        // "In Progress". Still means "reopened and not yet picked up again": REOPEN_COUNT > 0 is the durable
        // marker for "has ever been reopened" and no predicate kind expresses it.
        rows.add(status(STATUS_REOPENED_COMPLAINTS, "Reopened Complaints", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.COMPLAINT_REOPEN), ++order));

        // COMPLAINT_SETTLED is this row's own meaning — settled, so due for closure. It no longer overlaps
        // SENT_TO_CLOSING_AUTHORITY, which now keys on the status of that name rather than borrowing this one.
        rows.add(status(STATUS_MARK_FOR_CLOSURE, "Mark for Closure",
                PREDICATE_STATUS_IN, CepcStatus.orLegacy(CepcStatus.MARK_FOR_CLOSURE), ++order));

        rows.add(status(STATUS_CLOSED_COMPLAINTS, "Closed Complaints", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.COMPLAINT_CLOSED), ++order));
        return rows;
    }

    /**
     * The six tabs, keyed by the exact id {@code translateTabIdToStatus} emits.
     *
     * <p><b>Every tab but {@code All} selects on status.</b> A tab that keyed on the workflow stage, the
     * assigned role or the RE activity ladder returned rows whose status chip contradicted the tab the
     * officer had clicked — "Meeting Scheduled" listed New Complaint, Assigned, In Progress and Sent Back
     * rows, and a complaint carrying both {@code status='sent_back'} and stage {@code MEETING_SCHEDULED}
     * appeared under two tabs at once. Status is the one field a complaint has exactly one of, so keying on
     * it is what makes the grid agree with the tab.
     */
    private List<CepcDashboardFilter> tabFilters() {
        List<CepcDashboardFilter> rows = new ArrayList<>();
        int order = 0;

        rows.add(tab("All", PREDICATE_NONE, null, ++order));
        rows.add(tab("Draft", PREDICATE_STATUS_IN, CepcStatus.orLegacy(CepcStatus.DRAFT), ++order));
        rows.add(tab("Meeting Scheduled", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.MEETING_SCHEDULED), ++order));

        // The one caller-scoped tab: "to me" is a fact about the viewer, not the complaint, so it cannot be
        // a status. The predicate already requires status='sent_back' as well as the caller, so the chip
        // reads "Sent Back" throughout.
        rows.add(tab("Sent Back to Me", PREDICATE_SENT_BACK_TO_ME, null, ++order));

        rows.add(tab("Sent to RE", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.INFORMATION_REQUIRED), ++order));

        // Renamed from "Response from RE": the response comes back from the contact person, and the status
        // that records it is SENT_TO_RBI. The filter code is the literal string the frontend sends, so
        // translateTabIdToStatus and the tab label move with this.
        rows.add(tab("Contact Person", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.SENT_TO_RBI), ++order));
        return rows;
    }

    /**
     * The KPI cards, keyed by the exact card ids in {@code cepc-dashboard-kpi}.
     *
     * <p>The last card renders three metrics from one tile, so the two SLA windows get rows of their own
     * even though no card id corresponds to them — the count service reads all of these by code.
     *
     * <p>The SLA windows are <b>forward</b>-looking: "due in the next 0-15 days", not "overdue by up to 15
     * days". A deliberate divergence from the old search service, which measured backwards and therefore
     * double-counted every breached complaint in both the breach count and a window.
     */
    private List<CepcDashboardFilter> kpiFilters() {
        List<CepcDashboardFilter> rows = new ArrayList<>();
        int order = 0;

        rows.add(kpi("Total Pending Complaints", PREDICATE_NONE, null, true, ++order));
        rows.add(kpi("Pending with Me", PREDICATE_ASSIGNED_TO_ME, null, true, ++order));

        // These two pair with the "Sent to RE" and "Meeting Scheduled" tabs and resolve to the same
        // predicates, so clicking the card and clicking the tab agree. pendingOnly is left off: neither
        // status can be terminal, so the clause would only be another thing cepcDashboardCounts has to
        // restate identically.
        rows.add(kpi("Pending with RE", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.INFORMATION_REQUIRED), false, ++order));
        rows.add(kpi("Pending at Meeting Scheduled", PREDICATE_STATUS_IN,
                CepcStatus.orLegacy(CepcStatus.MEETING_SCHEDULED), false, ++order));

        rows.add(kpi("SLA Breached", PREDICATE_SLA_BREACHED, null, true, ++order));

        rows.add(slaWindow("SLA 0-15 Days", 0, 15, ++order));
        rows.add(slaWindow("SLA 16-30 Days", 16, 30, ++order));
        return rows;
    }

    /**
     * @param code  the normalised code, matching a {@code STATUS_CODE} in {@code ROLE_STATUS_MAPPING}
     * @param label the human reading of it, for callers that want a label rather than the raw code
     */
    private static CepcDashboardFilter status(String code, String label, String predicateKind,
                                             String values, int order) {
        return status(code, label, predicateKind, values, false, order);
    }

    private static CepcDashboardFilter status(String code, String label, String predicateKind,
                                             String values, boolean pendingOnly, int order) {
        return base(code, order)
                .labelEn(label)
                .dimension(DIMENSION_STATUS)
                .predicateKind(predicateKind)
                .predicateValues(values)
                .pendingOnly(pendingOnly ? "Y" : "N")
                .build();
    }

    private static CepcDashboardFilter tab(String code, String predicateKind, String values, int order) {
        return base(code, order)
                .dimension(DIMENSION_TAB)
                .predicateKind(predicateKind)
                .predicateValues(values)
                .build();
    }

    private static CepcDashboardFilter kpi(String code, String predicateKind, String values,
                                          boolean pendingOnly, int order) {
        return base(code, order)
                .dimension(DIMENSION_KPI)
                .predicateKind(predicateKind)
                .predicateValues(values)
                .pendingOnly(pendingOnly ? "Y" : "N")
                .build();
    }

    private static CepcDashboardFilter slaWindow(String code, int fromDays, int toDays, int order) {
        return base(code, order)
                .dimension(DIMENSION_KPI)
                .predicateKind(PREDICATE_SLA_WINDOW)
                .windowFromDays(fromDays)
                .windowToDays(toDays)
                .pendingOnly("Y")
                .build();
    }

    private static CepcDashboardFilter.CepcDashboardFilterBuilder base(String code, int order) {
        return CepcDashboardFilter.builder()
                .filterCode(code)
                .labelEn(code)
                .translationKey("cepc.filter." + code.toLowerCase(java.util.Locale.ROOT)
                        .replace(' ', '_').replace('-', '_'))
                .displayOrder(order)
                .isActive("Y");
    }
}
