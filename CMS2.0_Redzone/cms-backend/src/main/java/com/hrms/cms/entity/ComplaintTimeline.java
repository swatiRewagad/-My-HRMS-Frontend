package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One append-only row per complaint status change or milestone (UST594-597).
 *
 * <p>IMMUTABILITY (UST597). Every column is {@code updatable = false} and there is no setter, so an
 * accidental {@code save()} on a managed instance cannot rewrite history — Hibernate omits the columns
 * from the UPDATE statement entirely. Previously the class carried Lombok {@code @Setter} on all
 * fields with no {@code updatable} guard, which meant any service holding a row read from
 * {@code GET /api/complaints/{id}/timeline} could mutate it and have the change flushed silently.
 * Contrast {@link NotificationDeliveryLog}, which already had this shape.
 *
 * <p>{@code @PrePersist} is CONDITIONAL. It previously overwrote {@code performedAt} unconditionally,
 * so a caller could not record an event at the time it actually happened — a backdated or replayed
 * entry silently became "now", which is exactly the corruption an immutable log exists to prevent.
 */
@Entity
@Table(name = "COMPLAINT_TIMELINE", indexes = {
    @Index(name = "idx_timeline_complaint", columnList = "COMPLAINT_ID"),
    @Index(name = "idx_timeline_performed_at", columnList = "performedAt")
})
@Getter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false, updatable = false)
    private Long complaintId;

    @Column(nullable = false, length = 50, updatable = false)
    private String action;

    @Column(length = 200, updatable = false)
    private String performedBy;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String remarks;

    @Column(length = 30, updatable = false)
    private String fromStatus;

    @Column(length = 30, updatable = false)
    private String toStatus;

    @Column(updatable = false)
    private LocalDateTime performedAt;

    /**
     * The actor's role at the time of the event, copied from {@code AppealTimeline}, which already
     * records it.
     *
     * <p>Needed because {@code performedBy} alone cannot answer "who was allowed to do this?" once an
     * officer's role changes — and a reassignment audit that cannot state the actor's authority is not
     * an audit.
     */
    @Column(name = "performed_by_role", length = 50, updatable = false)
    private String performedByRole;

    /**
     * ═══ The old→new value triple (UST596) ═══
     *
     * <p>UST596 requires a reassignment to show BOTH the previous and the new owner. That was
     * previously impossible, not merely unwired: the entity had no column for a previous value, and
     * {@code applyAssignee} overwrites {@code Complaint.assignedOfficer} BEFORE the timeline row is
     * written, so by write time the old owner no longer existed anywhere except an AuditLog metadata
     * JSON blob holding post-change values.
     *
     * <p>Generic {@code fieldName/oldValue/newValue} rather than {@code oldOwner/newOwner} because the
     * same requirement recurs for closure clause and destination office, and because
     * {@code AppealTimeline} already established this shape — one renderer can then serve both.
     */
    @Column(name = "field_name", length = 60, updatable = false)
    private String fieldName;

    @Column(name = "old_value", length = 500, updatable = false)
    private String oldValue;

    @Column(name = "new_value", length = 500, updatable = false)
    private String newValue;

    /**
     * The Scheme clause a closure was made under (UST596).
     *
     * <p>Recorded ON THE EVENT because {@code Complaint.closureClause} holds only the CURRENT value and
     * is mutable — a reopen followed by a re-closure under a different clause destroys the original,
     * and the clause is the citizen's basis for appeal. A closure record that cannot state its own
     * clause cannot support an appeal.
     */
    @Column(name = "closure_clause", length = 100, updatable = false)
    private String closureClause;

    /**
     * Destination office for a routing or inter-office transfer event (UST596).
     *
     * <p>Nullable and unused by cms-backend's own writers today: inter-office transfer approval writes
     * no timeline row at all (see InterOfficeTransferService.approveTransfer). This column is the
     * write contract published for the session that owns transfers.
     */
    @Column(name = "destination_office", length = 100, updatable = false)
    private String destinationOffice;

    // ═══ Automatic-vs-manual discriminator (UST848) ═══
    // Before this existed, the only way to tell a system event from a user action was to guess from
    // performedBy, which is written as "SYSTEM", "System" and "system" by different services — so
    // "show me only what the entity actually did" could not be answered reliably. Nullable rather
    // than NOT NULL because pre-existing rows have no recoverable answer; the migration backfills
    // them from performedBy and leaves genuinely ambiguous ones MANUAL.
    @Enumerated(EnumType.STRING)
    @Column(name = "event_source", length = 20, updatable = false)
    @Builder.Default
    private TimelineEventSource eventSource = TimelineEventSource.MANUAL;

    /**
     * Conditional, deliberately: an explicitly supplied {@code performedAt} is preserved so an event
     * can be recorded at the time it actually occurred. Only an unset value defaults to now.
     */
    @PrePersist
    protected void onCreate() {
        if (this.performedAt == null) {
            this.performedAt = LocalDateTime.now();
        }
        if (this.eventSource == null) {
            this.eventSource = TimelineEventSource.MANUAL;
        }
    }
}
