package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "INTER_OFFICE_TRANSFERS", indexes = {
    @Index(name = "idx_iot_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_iot_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterOfficeTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 50)
    private String fromOffice;

    @Column(nullable = false, length = 50)
    private String toOffice;

    @Column(nullable = false, length = 20)
    private String transferType; // RBIO_RBIO, RBIO_CEPC, CEPC_CEPC, CEPC_RBIO, CEPD_RBIO

    @Column(nullable = false, length = 20)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String reason;

    /** "Sent from office comments" — distinct from {@link #reason}, per the Forward milestone's field split. */
    @Column(columnDefinition = "TEXT")
    private String officeComments;

    @Column(columnDefinition = "TEXT")
    private String rejectionComment;

    @Column(length = 100)
    private String requestedBy;

    @Column(length = 100)
    private String approvedBy;

    /**
     * The officer who held the complaint when the transfer was REQUESTED.
     *
     * <p>Set at request time, not at approval. It was previously assigned inside {@code approveTransfer}, so
     * on a REJECTION it had never been populated — the {@code != null} guard in the rejection path silently
     * skipped the restore and the complaint kept whatever owner it had. UST526/567 require a rejected
     * transfer to return to its previous owner, which is only possible if that owner was recorded before the
     * move was contemplated.
     */
    @Column(length = 200)
    private String previousOwner;

    // ═══ Provenance and layout conversion (UST634, 568, 565, 632, 633) ═══

    /**
     * The module the complaint belonged to when the transfer was requested.
     *
     * <p>Captured rather than inferred. The previous {@code resolveDepartment} tested a string PREFIX on the
     * destination office id, but offices are keyed by a numeric OFFICE_CODE ("013"), so every real office fell
     * off the end and resolved "RBIO" — an RBIO-to-CEPC conversion recorded itself as RBIO-to-RBIO.
     */
    @Column(name = "origin_module", length = 20)
    private String originModule;

    /** The layout (owning module) before the conversion — RBIO | CEPC | CEPD. */
    @Column(name = "from_layout", length = 20)
    private String fromLayout;

    /** The layout after the conversion. UST634 requires both old and new to be recorded. */
    @Column(name = "to_layout", length = 20)
    private String toLayout;

    /**
     * The officer the complaint actually landed on at the destination.
     *
     * <p>Previously the destination OFFICE CODE was written into {@code COMPLAINTS.assigned_officer} — a
     * non-user value in a user column — so after a transfer "who owns this complaint" had no answer.
     */
    @Column(name = "assigned_officer", length = 200)
    private String assignedOfficer;

    /** How that officer was chosen (ROUND_ROBIN, ENTITY_MAPPING, OMBUDSMAN_ADMIN_FALLBACK, ...). */
    @Column(name = "assignment_strategy", length = 40)
    private String assignmentStrategy;

    @Column(name = "assignment_reason", length = 500)
    private String assignmentReason;

    /** The jurisdiction (region) keys on each side, so UST760's region change is evidenced on the row. */
    @Column(name = "from_office_code", length = 10)
    private String fromOfficeCode;

    @Column(name = "to_office_code", length = 10)
    private String toOfficeCode;

    /** The complaint's language, carried with the forward so the receiving office knows it can read it (UST765). */
    @Column(name = "language", length = 10)
    private String language;

    // ═══ Non-office destinations ═══

    /** Set when this is a forward to an RBI department rather than an office-to-office move (UST761). */
    @Column(name = "target_department", length = 100)
    private String targetDepartment;

    @Column(name = "target_body_id")
    private Long targetBodyId;

    /** Set when this is a referral to an external regulator (UST766). */
    @Column(name = "target_body_name", length = 250)
    private String targetBodyName;

    /**
     * 'Y' when the approver deliberately pushed the complaint into an office already at its threshold.
     *
     * <p>Exists because {@code OfficeRoutingService.incrementOffice} now reports capacity refusal and the
     * previous code discarded that verdict, so a transfer could silently overfill an office. The transfer is
     * now refused by default; when an administrator permits the override, it is recorded here rather than
     * being invisible.
     */
    @Column(name = "overflow_accepted", length = 1)
    private String overflowAccepted;

    private LocalDateTime requestedAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
