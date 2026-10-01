package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * One row of the RBIO transition table: who may do what, from where, and what results.
 *
 * <p>Replaces the three switch statements that used to answer that question separately
 * ({@code performAction}'s role check, {@code executeAction}'s state writes, and
 * {@code isActionValidForState}'s advertisement rules). Enforcement and advertisement now read the
 * same rows, so the UI cannot offer an action the server refuses.
 *
 * <p>Rows are seeded by {@code RbioWorkflowTransitionSeeder} on every boot; see V58 for why they are
 * not in SQL.
 */
@Entity
@Table(name = "RBIO_WORKFLOW_TRANSITION")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioWorkflowTransition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ACTION_CODE", nullable = false, length = 50)
    private String actionCode;

    @Column(name = "ROLE_NAME", nullable = false, length = 50)
    private String roleName;

    /** The legacy lowercase {@code COMPLAINTS.status} this transition departs from. NULL = any. */
    @Column(name = "FROM_STATUS", length = 30)
    private String fromStatus;

    /**
     * NULL means the status is deliberately UNCHANGED — not that the row is unconfigured.
     * SCHEDULE_MEETING, ISSUE_NOTICE_13_1 and IMPLEAD_PARTY all move only the stage. Treating NULL as
     * "unset" and writing it through would blank the status of a live complaint.
     */
    @Column(name = "TO_STATUS", length = 30)
    private String toStatus;

    @Column(name = "TO_STAGE", length = 50)
    private String toStage;

    @Column(name = "TO_MILESTONE", length = 30)
    private String toMilestone;

    @Column(name = "ASSIGN_TO_ROLE", length = 50)
    private String assignToRole;

    /** NULL | ROUND_ROBIN | TARGET_PARAM | KEEP — see {@code RbioAssignStrategy}. */
    @Column(name = "ASSIGN_STRATEGY", length = 20)
    private String assignStrategy;

    @Column(name = "REQUIRES_COMMENT", nullable = false, length = 1)
    @Builder.Default
    private String requiresComment = "N";

    /**
     * Params that must be present and non-blank, comma-separated. A group of alternatives is
     * pipe-separated: {@code awardAmount|compensationAmount} means at least one of the two.
     *
     * <p>This is how the award-amount refusal survives the switch removal. Reading only
     * {@code awardAmount} once meant the adjudication screen's {@code compensationAmount} never bound,
     * so an award defaulted to 0.00 and was persisted as a lawful zero on an irreversible statutory
     * act. Absence is refused, never defaulted.
     */
    @Column(name = "REQUIRED_PARAMS", length = 255)
    private String requiredParams;

    @Column(name = "IS_TERMINAL", nullable = false, length = 1)
    @Builder.Default
    private String isTerminal = "N";

    /** Named Java hook for effects a table cannot express. See {@code RbioTransitionEffects}. */
    @Column(name = "SIDE_EFFECT", length = 50)
    private String sideEffect;

    @Column(name = "SLA_STAGE", length = 50)
    private String slaStage;

    @Column(name = "CLOSURE_CAUSE", length = 50)
    private String closureCause;

    @Column(name = "DISPLAY_ORDER", nullable = false)
    @Builder.Default
    private Integer displayOrder = 999;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    @Builder.Default
    private String isActive = "Y";

    @Column(name = "SCHEME_VERSION", nullable = false, length = 20)
    @Builder.Default
    private String schemeVersion = "RBIOS_2021";

    @Column(name = "OWNED_BY", length = 20)
    private String ownedBy;

    public boolean active() {
        return "Y".equalsIgnoreCase(isActive);
    }

    public boolean terminal() {
        return "Y".equalsIgnoreCase(isTerminal);
    }

    public boolean commentRequired() {
        return "Y".equalsIgnoreCase(requiresComment);
    }

    /** True when this row applies from any status. */
    public boolean fromAnyStatus() {
        return fromStatus == null || fromStatus.isBlank();
    }
}
