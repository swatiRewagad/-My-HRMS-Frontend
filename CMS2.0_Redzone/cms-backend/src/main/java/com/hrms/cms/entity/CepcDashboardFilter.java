package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The CEPC dashboard's filter vocabulary: every status-dropdown entry, tab and KPI card the CEPC complaint
 * list can be narrowed by, and the predicate each one stands for.
 *
 * <p><b>Why a separate table from {@code RBIO_STATUS_MASTER}.</b> That table has zero CEPC rows and cannot
 * gain any, because {@code RbioStatusMasterSeeder.seedVisibility()} does a {@code findAll()} and grants the
 * whole result to {@code ADMIN} — adding CEPC codes there would surface them in the RBIO admin filter bar,
 * where they mean nothing and match nothing.
 *
 * <p><b>Why a table at all rather than three switch statements.</b> Once the codes live in a row, one place
 * answers "is this a filter code I recognise" — and an unrecognised code can then
 * {@link #PREDICATE_NONE fail closed} instead of quietly widening the grid.
 *
 * <p><b>This table says what a code means, not who may pick it.</b> Role visibility for the status dropdown
 * lives in {@link RoleStatusMapping}, which is what {@code GET /api/v1/departments/{code}/role-status}
 * reads. There is deliberately no role column here: a status is visible to several roles, so gating it here
 * as well would give two answers to one question.
 *
 * <p>{@code filterCode} is the exact string the Angular dashboard sends back. The two dimensions spell it
 * differently and that is not an oversight: {@code STATUS} codes are normalised
 * ({@code SENT_BACK_TO_CEPC_DO}) because they arrive from {@code ROLE_STATUS_MAPPING} and the frontend
 * humanises them for display, whereas {@code TAB} and {@code KPI} codes are display labels
 * ({@code "Sent to RE"}, {@code "SLA Breached"}) because the frontend hardcodes those as ids — see
 * {@code translateTabIdToStatus} and the KPI card ids.
 */
@Entity
@Table(name = "CEPC_DASHBOARD_FILTER",
        uniqueConstraints = @UniqueConstraint(name = "UK_CEPC_FILTER_DIM_CODE",
                columnNames = {"DIMENSION", "FILTER_CODE"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CepcDashboardFilter {

    /** {@code dimension} values — which request field a code arrives in. */
    public static final String DIMENSION_STATUS = "STATUS";
    public static final String DIMENSION_TAB = "TAB";
    public static final String DIMENSION_KPI = "KPI";

    // ── STATUS-dimension codes ───────────────────────────────────────────────────────────────────
    //
    // The status dropdown's vocabulary. Named constants rather than literals because each one is written
    // twice — once here to define its predicate, once in ROLE_STATUS_MAPPING to say which roles see it —
    // and the two are matched by exact string equality. A typo in either place produces a dropdown entry
    // that resolves to nothing and therefore selects nothing, with no error to notice.

    public static final String STATUS_ALL_COMPLAINTS = "ALL_COMPLAINTS";
    public static final String STATUS_COMPLAINT_ASSIGNED_TO_ME = "COMPLAINT_ASSIGNED_TO_ME";
    public static final String STATUS_NEW_COMPLAINT = "NEW_COMPLAINT";
    public static final String STATUS_DRAFT_COMPLAINTS = "DRAFT_COMPLAINTS";
    public static final String STATUS_MEETING_SCHEDULED = "MEETING_SCHEDULED";
    public static final String STATUS_SENT_TO_CEPC_DEALING_OFFICIAL = "SENT_TO_CEPC_DEALING_OFFICIAL";
    public static final String STATUS_SENT_TO_CEPC_REVIEWER = "SENT_TO_CEPC_REVIEWER";
    public static final String STATUS_SENT_TO_CEPC_IN_CHARGE = "SENT_TO_INCHARGE";
    public static final String STATUS_SENT_TO_CLOSING_AUTHORITY = "SENT_TO_CLOSING_AUTHORITY";
    public static final String STATUS_SENT_BACK_TO_CEPC_DO = "SENT_BACK_TO_CEPC_DO";
    public static final String STATUS_SENT_BACK_TO_CEPC_REVIEWER = "SENT_BACK_TO_CEPC_REVIEWER";
    public static final String STATUS_SENT_BACK_TO_CEPC_IN_CHARGE = "SENT_BACK_TO_INCHARGE";
    public static final String STATUS_SENT_TO_OTHER_RBI_DEPARTMENT = "SENT_TO_OTHER_RBI_DEPARTMENT";
    public static final String STATUS_SENT_TO_OTHER_REGULATED_BODIES = "SENT_TO_OTHER_REGULATED_BODIES";
    public static final String STATUS_SENT_TO_OTHER_OFFICE = "SENT_TO_OTHER_OFFICE";
    public static final String STATUS_REOPENED_COMPLAINTS = "REOPENED_COMPLAINTS";
    public static final String STATUS_MARK_FOR_CLOSURE = "MARK_FOR_CLOSURE";
    public static final String STATUS_CLOSED_COMPLAINTS = "CLOSED_COMPLAINTS";

    /** No predicate: the base scope alone. Used by "All Complaints" and the "All" tab. */
    public static final String PREDICATE_NONE = "NONE";
    /** {@code assignedOfficer = caller}. Matches nothing when the caller cannot be identified. */
    public static final String PREDICATE_ASSIGNED_TO_ME = "ASSIGNED_TO_ME";
    /** {@code status IN predicateValues}, case-folded. */
    public static final String PREDICATE_STATUS_IN = "STATUS_IN";
    /** {@code workflowStage IN predicateValues}. */
    public static final String PREDICATE_STAGE_IN = "STAGE_IN";
    /** {@code assignedRole IN predicateValues}. */
    public static final String PREDICATE_ASSIGNED_ROLE_IN = "ASSIGNED_ROLE_IN";
    /**
     * Rework sitting with the caller: {@code (status='sent_back' OR workflowStage LIKE 'SENT_BACK%')} and
     * assigned to them. The stage arm is there because rows written before the {@code SEND_BACK_*} fix
     * carry a review status with a send-back stage.
     */
    public static final String PREDICATE_SENT_BACK_TO_ME = "SENT_BACK_TO_ME";
    /** With the RE and not yet answered: {@code assignedRole='RE'} and {@code reActivityStatus} not in the listed values. */
    public static final String PREDICATE_RE_PENDING = "RE_PENDING";
    /** {@code reActivityStatus IN predicateValues}. */
    public static final String PREDICATE_RE_ACTIVITY_IN = "RE_ACTIVITY_IN";
    /** Past its SLA and still live: {@code slaDeadline < now} and not terminal. */
    public static final String PREDICATE_SLA_BREACHED = "SLA_BREACHED";
    /**
     * Due inside a forward-looking day window and still live:
     * {@code slaDeadline BETWEEN now+windowFromDays AND now+windowToDays}.
     */
    public static final String PREDICATE_SLA_WINDOW = "SLA_WINDOW";
    /**
     * Unsubmitted {@code STAFF_DRAFT} rows owned by the caller.
     *
     * <p>Handled outside the {@code Specification<Complaint>} — a staff draft has no complaint row yet, so
     * there is nothing for a criteria predicate to select.
     */
    public static final String PREDICATE_STAFF_DRAFT = "STAFF_DRAFT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    /**
     * The literal string the frontend sends for this filter.
     *
     * <p>Not the primary key, because it is only unique within a dimension: "Meeting Scheduled" is both a
     * status a user can select and a tab they can click, and the two carry different predicates.
     */
    @Column(name = "FILTER_CODE", nullable = false, length = 80)
    private String filterCode;

    /** {@code STATUS}, {@code TAB} or {@code KPI} — see the {@code DIMENSION_*} constants. */
    @Column(name = "DIMENSION", nullable = false, length = 10)
    @Builder.Default
    private String dimension = DIMENSION_STATUS;

    /** Which predicate to build — see the {@code PREDICATE_*} constants. */
    @Column(name = "PREDICATE_KIND", nullable = false, length = 30)
    @Builder.Default
    private String predicateKind = PREDICATE_NONE;

    /** Comma-separated operands for the predicate kind; null where the kind takes none. */
    @Column(name = "PREDICATE_VALUES", length = 1000)
    private String predicateValues;

    /** Inclusive lower bound of a {@code SLA_WINDOW}, in days from today. */
    @Column(name = "WINDOW_FROM_DAYS")
    private Integer windowFromDays;

    /** Inclusive upper bound of a {@code SLA_WINDOW}, in days from today. */
    @Column(name = "WINDOW_TO_DAYS")
    private Integer windowToDays;

    /**
     * Whether to AND "not terminal" onto the predicate.
     *
     * <p>Carried separately from the predicate kind because it is orthogonal to it: a KPI can be
     * "assigned to me" and "still open" at once, and without this every count that should exclude closed
     * work would need its own predicate kind.
     */
    @Column(name = "PENDING_ONLY", nullable = false, length = 1)
    @Builder.Default
    private String pendingOnly = "N";

    @Column(name = "LABEL_EN", nullable = false, length = 150)
    private String labelEn;

    @Column(name = "TRANSLATION_KEY", length = 150)
    private String translationKey;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    @Builder.Default
    private Integer displayOrder = 999;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    @Builder.Default
    private String isActive = "Y";

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean isActiveFilter() {
        return "Y".equalsIgnoreCase(isActive);
    }

    public boolean isPendingOnly() {
        return "Y".equalsIgnoreCase(pendingOnly);
    }

    /** {@link #predicateValues} split and trimmed; empty when unset. */
    public List<String> predicateValueList() {
        if (predicateValues == null || predicateValues.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(predicateValues.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
