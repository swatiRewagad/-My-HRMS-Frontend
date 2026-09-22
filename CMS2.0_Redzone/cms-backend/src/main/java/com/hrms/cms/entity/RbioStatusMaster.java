package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * The RBIO status vocabulary. One row per filter the backlog names (UST426-433).
 *
 * <p>The ~25 backlog "statuses" are three different kinds of thing, distinguished by
 * {@link #filterKind}: a STATUS is a value {@code COMPLAINTS.status} takes; a QUEUE is a routing
 * position (status + assigned role); a SCOPE is a predicate on the CALLER, not on the complaint at
 * all. "Complaint Assigned to Me" is a SCOPE — it has no {@code legacyValue} and no complaint anywhere
 * has {@code status = 'ASSIGNED_TO_ME'}, so treating it as a status yields a permanently empty grid.
 */
@Entity
@Table(name = "RBIO_STATUS_MASTER")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioStatusMaster {

    @Id
    @Column(name = "STATUS_CODE", nullable = false, length = 50)
    private String statusCode;

    /**
     * The lowercase string {@code COMPLAINTS.status} actually holds today, and the join key from live
     * data to this vocabulary. NULL for QUEUE/SCOPE rows and for statuses no code yet writes.
     * Deliberately NOT unique: the legacy vocabulary is coarser than the backlog's, so several codes
     * share one legacy value.
     */
    @Column(name = "LEGACY_VALUE", length = 30)
    private String legacyValue;

    /** STATUS | QUEUE | SCOPE. */
    @Column(name = "FILTER_KIND", nullable = false, length = 10)
    @Builder.Default
    private String filterKind = "STATUS";

    @Column(name = "LABEL_EN", nullable = false, length = 150)
    private String labelEn;

    @Column(name = "TRANSLATION_KEY", length = 150)
    private String translationKey;

    @Column(name = "MILESTONE_CODE", length = 30)
    private String milestoneCode;

    /** For QUEUE rows: the role whose inbox this queue is. */
    @Column(name = "QUEUE_ROLE", length = 50)
    private String queueRole;

    /** The authoritative answer to "is the file shut?", replacing the hardcoded CLOSED_STATUSES lists. */
    @Column(name = "IS_CLOSED", nullable = false, length = 1)
    @Builder.Default
    private String isClosed = "N";

    /** Narrower than closed: closed AND not reopenable. */
    @Column(name = "IS_TERMINAL", nullable = false, length = 1)
    @Builder.Default
    private String isTerminal = "N";

    /**
     * Whether a citizen may see this status verbatim on the tracker. Internal routing states
     * ("Sent Back to Reviewer") disclose RBI's internal disagreement about a live case.
     */
    @Column(name = "IS_CITIZEN_VISIBLE", nullable = false, length = 1)
    @Builder.Default
    private String isCitizenVisible = "N";

    @Column(name = "DISPLAY_ORDER", nullable = false)
    @Builder.Default
    private Integer displayOrder = 999;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    @Builder.Default
    private String isActive = "Y";

    @Column(name = "SCHEME_VERSION", nullable = false, length = 20)
    @Builder.Default
    private String schemeVersion = "RBIOS_2021";

    /**
     * Whether a conciliation meeting may NOT be scheduled from this status (UST497, 643, 646, 649).
     *
     * <p>A flag here rather than a from-status enumeration in RBIO_WORKFLOW_TRANSITION because the
     * requirement is a NOT-IN: the meeting option is hidden for six named statuses. Enumerating "every
     * status except six" as positive transition rows would silently omit any status a later session adds —
     * the same trap {@code RbioTransitionRegistry} documents for REASSIGN. Inverted as a flag, a new status
     * defaults to "meetings allowed" and must be opted out deliberately.
     *
     * <p>Nullable, and the service treats NULL as "not configured" and fails CLOSED. That matters because
     * this decides whether an officer may convene a statutory conciliation: guessing "allowed" on an
     * unseeded database would let a meeting be scheduled on a settled or withdrawn complaint.
     */
    @Column(name = "BLOCKS_MEETING", length = 1)
    private String blocksMeeting;

    @Column(name = "CREATED_AT")
    private java.time.LocalDateTime createdAt;

    public boolean closed() {
        return "Y".equalsIgnoreCase(isClosed);
    }

    /** True when this status forbids scheduling a meeting. */
    public boolean blocksMeeting() {
        return "Y".equalsIgnoreCase(blocksMeeting);
    }

    public boolean citizenVisible() {
        return "Y".equalsIgnoreCase(isCitizenVisible);
    }
}
