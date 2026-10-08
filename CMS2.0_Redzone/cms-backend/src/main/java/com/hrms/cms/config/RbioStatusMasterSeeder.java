package com.hrms.cms.config;

import com.hrms.cms.entity.RbioStatusMaster;
import com.hrms.cms.entity.RbioStatusRoleVisibility;
import com.hrms.cms.repository.RbioStatusMasterRepository;
import com.hrms.cms.repository.RbioStatusRoleVisibilityRepository;
import com.hrms.cms.service.RbioRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds RBIO_STATUS_MASTER and the per-role filter visibility (UST426-433).
 *
 * <p>Insert-if-absent, matching the established seeder convention: editing a label here does NOT
 * update an existing row, so a correction needs a code-scoped UPDATE in both migration directories.
 *
 * <p><b>Why this is a seeder and not SQL.</b> There is no Flyway; {@code database/*.sql} is hand-run,
 * so a database on which V57 was never applied is normal. Statuses in SQL would mean such a database
 * has an empty vocabulary and every RBIO filter list renders blank. A seeder runs on every boot.
 */
@Component
@Order(20)
@RequiredArgsConstructor
@Slf4j
public class RbioStatusMasterSeeder implements CommandLineRunner {

    private final RbioStatusMasterRepository statusRepo;
    private final RbioStatusRoleVisibilityRepository visibilityRepo;

    // FILTER_KIND values.
    private static final String STATUS = "STATUS";
    private static final String QUEUE  = "QUEUE";
    private static final String SCOPE  = "SCOPE";

    @Override
    @Transactional
    public void run(String... args) {
        seedStatuses();
        seedVisibility();
    }

    private void seedStatuses() {
        List<RbioStatusMaster> rows = new ArrayList<>();
        int order = 0;

        // ── SCOPE rows: predicates on the CALLER, not on the complaint ──
        // legacyValue is deliberately null. No complaint has status='ALL'; these constrain the QUERY.
        rows.add(scope("ALL",            "All Complaints",           ++order));
        rows.add(scope("ASSIGNED_TO_ME", "Complaint Assigned to Me", ++order));
        rows.add(scope("CREATED_BY_ME",  "Complaint Created by Me",  ++order));

        // ── Intake ──
        // "New Complaint" maps to the two legacy values a just-arrived complaint can hold. Only one can
        // go in legacyValue, so 'pending' is used (what @PrePersist writes) and NEW_ASSIGNED carries
        // 'assigned'; both are citizen-visible and both sit in the REGISTER milestone.
        rows.add(status("NEW_COMPLAINT", "pending",  "New Complaint", "REGISTER", ++order, true));
        rows.add(status("ASSIGNED",      "assigned", "Complaint Assigned", "REGISTER", ++order, true));

        // ── Assessment / in flight ──
        rows.add(status("IN_PROGRESS",     "in_progress",     "Under Assessment",        "ASSESSMENT", ++order, true));
        rows.add(status("INFO_REQUESTED",  "info_requested",  "Information Requested",   "ASSESSMENT", ++order, true));
        rows.add(status("MEETING_SCHEDULED", null,            "Meeting Scheduled",       "CONCILIATION", ++order, true));
        rows.add(status("ESCALATED",       "escalated",       "Escalated",               "ASSESSMENT", ++order, false));
        rows.add(status("CONCILIATION",    "conciliation",    "In Conciliation",         "CONCILIATION", ++order, true));
        rows.add(status("ADJUDICATION",    "adjudication",    "In Adjudication",         "FINAL_DECISION", ++order, true));

        // ── QUEUE rows: routing positions. Internal — a citizen must not see which desk holds the file,
        // and "Sent Back to X" in particular discloses internal disagreement about a live case.
        rows.add(queue("SENT_TO_DO",         RbioRoles.DEALING_OFFICIAL, "Sent to DO",                    "ASSESSMENT", ++order));
        rows.add(queue("SENT_TO_REVIEWER",   RbioRoles.REVIEWER,         "Sent to Reviewer",              "ASSESSMENT", ++order));
        rows.add(queue("SENT_TO_DY_OMB",     RbioRoles.DEPUTY_OMBUDSMAN, "Sent to Deputy Ombudsman",      "FINAL_DECISION", ++order));
        rows.add(queue("SENT_TO_OMBUDSMAN",  RbioRoles.OMBUDSMAN,        "Sent to Ombudsman",             "FINAL_DECISION", ++order));
        rows.add(queue("SENT_BACK_DO",       RbioRoles.DEALING_OFFICIAL, "Sent Back to DO",               "ASSESSMENT", ++order));
        rows.add(queue("SENT_BACK_REVIEWER", RbioRoles.REVIEWER,         "Sent Back to Reviewer",         "ASSESSMENT", ++order));
        rows.add(queue("SENT_BACK_DY_OMB",   RbioRoles.DEPUTY_OMBUDSMAN, "Sent Back to Deputy Ombudsman", "FINAL_DECISION", ++order));

        // ── Forwarding out ──
        rows.add(status("SENT_TO_OTHER_OFFICE",  "sent_to_other",       "Sent to Other Office",           "FORWARD", ++order, true));
        rows.add(status("SENT_TO_OTHER_DEPT",    "forwarded_external",  "Sent to Other Departments",      "FORWARD", ++order, true));
        rows.add(status("SENT_TO_OTHER_RE",      null,                  "Sent to Other Regulated Bodies", "FORWARD", ++order, true));

        // ── Decisions and outcomes ──
        rows.add(status("ADVISORY_COMPLIED", "advisory_issued", "Advisory Complied",         "FINAL_DECISION", ++order, true));
        rows.add(status("FACILITATION",      null,              "Facilitation/Rejection",    "FINAL_DECISION", ++order, false));
        rows.add(status("NOT_A_COMPLAINT",   null,              "Not a Complaint",           "REGISTER", ++order, true));
        rows.add(status("DY_OMB_DECISION",   null,              "Deputy Ombudsman Decision", "FINAL_DECISION", ++order, true));
        rows.add(status("OMBUDSMAN_DECISION", null,             "Ombudsman Decision",        "FINAL_DECISION", ++order, true));

        // ── Closed. IS_CLOSED='Y' here is the authoritative closed list. The legacy values below are
        // exactly WorkflowController's six-value CLOSED_STATUSES, so consolidating on this table is
        // behaviour-preserving for that caller. NotificationScheduledTasks' four-value list is the
        // narrower one and gains 'adjudicated'/'conciliated' — see the contract report.
        rows.add(closed("AWARD_PASSED",      "adjudicated", "Award Passed",         "FINAL_DECISION", ++order, true,  false));
        rows.add(closed("SETTLED",           "conciliated", "Complaint Settled",    "FINAL_DECISION", ++order, true,  false));
        rows.add(closed("RESOLVED",          "resolved",    "Complaint Resolved",   "FINAL_DECISION", ++order, true,  false));
        rows.add(closed("REJECTED",          "rejected",    "Complaint Rejected",   "FINAL_DECISION", ++order, true,  false));
        rows.add(closed("WITHDRAWN",         "withdrawn",   "Complaint Withdrawn",  "FINAL_DECISION", ++order, true,  true));
        rows.add(closed("CLOSED",            "closed",      "Complaint Closed",     "FINAL_DECISION", ++order, true,  false));
        rows.add(closed("APPEAL_CLOSED",     null,          "Appeal Closed",        "FINAL_DECISION", ++order, true,  true));

        // FR-G-013: eligibility-screening outcome, not a decision made after assessment — the
        // complaint never leaves REGISTER. Terminal and citizen-visible: this is where the citizen's
        // closure-letter download for a Non-Maintainable complaint comes from.
        rows.add(closed("PORTAL_REJECTION",  "portal_rejection", "Portal Rejection", "REGISTER", ++order, true, true));

        // Reopen is an OPEN state despite naming a closed one — a reopened complaint is live work.
        rows.add(status("REOPENED", null, "Complaint Re-Open", "ASSESSMENT", ++order, true));

        int inserted = 0;
        for (RbioStatusMaster row : rows) {
            if (!statusRepo.existsById(row.getStatusCode())) {
                row.setCreatedAt(LocalDateTime.now());
                statusRepo.save(row);
                inserted++;
            }
        }
        if (inserted > 0) {
            log.info("RBIO status master seeded: {} of {} rows inserted", inserted, rows.size());
        }
    }

    /**
     * Per-role filter lists. Five different lists driven from DATA, which is the whole point of
     * UST426-433: S1 must not hardcode an array per role.
     *
     * <p>Every case-holding role gets the three SCOPE filters plus the states it can actually
     * encounter. ADMIN sees everything, because an administrator reconciling a stuck complaint needs to
     * find it in whatever state it is stuck in.
     */
    private void seedVisibility() {
        List<String> allCodes = statusRepo.findAll().stream().map(RbioStatusMaster::getStatusCode).toList();

        // ADMIN: everything.
        grant(RbioRoles.ADMIN, allCodes, "ALL");

        List<String> common = List.of("ALL", "ASSIGNED_TO_ME", "CREATED_BY_ME", "NEW_COMPLAINT", "ASSIGNED",
                "IN_PROGRESS", "INFO_REQUESTED", "MEETING_SCHEDULED", "REOPENED");

        // Dealing Official: own work plus what comes back to them. No decision states — a DO does not
        // pass awards, so offering an "Award Passed" filter would advertise authority they lack.
        grant(RbioRoles.DEALING_OFFICIAL, concat(common, List.of(
                "SENT_TO_DO", "SENT_BACK_DO", "SENT_TO_REVIEWER",
                "SENT_TO_OTHER_OFFICE", "SENT_TO_OTHER_DEPT", "SENT_TO_OTHER_RE",
                "NOT_A_COMPLAINT", "CLOSED", "PORTAL_REJECTION")), "ASSIGNED_TO_ME");

        // Reviewer: the DO's queue plus its own, and upward routing.
        grant(RbioRoles.REVIEWER, concat(common, List.of(
                "SENT_TO_REVIEWER", "SENT_BACK_REVIEWER", "SENT_BACK_DO",
                "SENT_TO_DY_OMB", "ESCALATED", "FACILITATION", "NOT_A_COMPLAINT",
                "ADVISORY_COMPLIED", "CLOSED", "PORTAL_REJECTION")), "SENT_TO_REVIEWER");

        // Deputy Ombudsman: decides within delegated authority, so sees decision states but not the
        // Ombudsman's own appealable-order states.
        grant(RbioRoles.DEPUTY_OMBUDSMAN, concat(common, List.of(
                "SENT_TO_DY_OMB", "SENT_BACK_DY_OMB", "SENT_BACK_REVIEWER",
                "SENT_TO_OMBUDSMAN", "ESCALATED", "CONCILIATION", "ADJUDICATION",
                "DY_OMB_DECISION", "ADVISORY_COMPLIED", "SETTLED", "RESOLVED",
                "REJECTED", "CLOSED")), "SENT_TO_DY_OMB");

        // Ombudsman: the full decision surface including awards and appeals.
        grant(RbioRoles.OMBUDSMAN, concat(common, List.of(
                "SENT_TO_OMBUDSMAN", "SENT_BACK_DY_OMB", "ESCALATED",
                "CONCILIATION", "ADJUDICATION", "OMBUDSMAN_DECISION", "DY_OMB_DECISION",
                "AWARD_PASSED", "SETTLED", "RESOLVED", "REJECTED", "WITHDRAWN",
                "ADVISORY_COMPLIED", "APPEAL_CLOSED", "CLOSED")), "SENT_TO_OMBUDSMAN");

        // Legacy roles keep a working list so existing users are not left with an empty filter bar.
        grant(RbioRoles.OFFICER, concat(common, List.of(
                "SENT_TO_DO", "SENT_BACK_DO", "ESCALATED", "CLOSED", "RESOLVED", "REJECTED")), "ASSIGNED_TO_ME");
        grant(RbioRoles.SUPERVISOR, concat(common, List.of(
                "ESCALATED", "CONCILIATION", "ADJUDICATION", "RESOLVED", "REJECTED",
                "SETTLED", "AWARD_PASSED", "CLOSED")), "ESCALATED");
        grant(RbioRoles.CONCILIATOR, concat(common, List.of(
                "CONCILIATION", "MEETING_SCHEDULED", "SETTLED", "ADJUDICATION")), "CONCILIATION");
        grant(RbioRoles.ADJUDICATOR, concat(common, List.of(
                "ADJUDICATION", "AWARD_PASSED", "REJECTED", "CLOSED")), "ADJUDICATION");
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> out = new ArrayList<>(a);
        for (String s : b) {
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    private void grant(String role, List<String> statusCodes, String defaultCode) {
        int order = 0;
        for (String code : statusCodes) {
            order++;
            if (visibilityRepo.existsByStatusCodeAndRoleName(code, role)) continue;
            visibilityRepo.save(RbioStatusRoleVisibility.builder()
                    .statusCode(code)
                    .roleName(role)
                    .displayOrder(order)
                    .isDefault(code.equals(defaultCode) ? "Y" : "N")
                    .build());
        }
    }

    private static RbioStatusMaster scope(String code, String label, int order) {
        return base(code, null, label, null, order).filterKind(SCOPE).isCitizenVisible("N").build();
    }

    private static RbioStatusMaster status(String code, String legacy, String label, String milestone,
                                          int order, boolean citizenVisible) {
        return base(code, legacy, label, milestone, order)
                .filterKind(STATUS)
                .isCitizenVisible(citizenVisible ? "Y" : "N")
                .build();
    }

    private static RbioStatusMaster queue(String code, String queueRole, String label, String milestone, int order) {
        return base(code, null, label, milestone, order)
                .filterKind(QUEUE)
                .queueRole(queueRole)
                .isCitizenVisible("N")
                .build();
    }

    private static RbioStatusMaster closed(String code, String legacy, String label, String milestone,
                                           int order, boolean citizenVisible, boolean terminal) {
        return base(code, legacy, label, milestone, order)
                .filterKind(STATUS)
                .isClosed("Y")
                .isTerminal(terminal ? "Y" : "N")
                .isCitizenVisible(citizenVisible ? "Y" : "N")
                .build();
    }

    private static RbioStatusMaster.RbioStatusMasterBuilder base(String code, String legacy, String label,
                                                                 String milestone, int order) {
        return RbioStatusMaster.builder()
                .statusCode(code)
                .legacyValue(legacy)
                .labelEn(label)
                .translationKey("rbio.status." + code.toLowerCase())
                .milestoneCode(milestone)
                .displayOrder(order)
                .isActive("Y")
                .schemeVersion("RBIOS_2021");
    }
}
