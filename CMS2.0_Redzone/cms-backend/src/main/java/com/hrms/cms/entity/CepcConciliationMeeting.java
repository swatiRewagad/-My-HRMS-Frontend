package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * One conciliation meeting in a complaint's series, as the Conciliation tab works with it.
 *
 * <p><b>Not {@link RbioMeeting}, deliberately.</b> That entity is event-sourced: nearly every column is
 * {@code updatable = false} and a change is expressed by writing a successor and stamping
 * {@code supersededAt}/{@code supersededById} on the original. That is the right shape for an Ombudsman
 * proceeding, where the record of what was decided when must not be rewritable. The CEPC tab is a working
 * form — the officer opens it, corrects the time, adds minutes and saves again — so reusing that entity
 * would mean either one archived row per keystroke-level correction or quietly making its columns mutable,
 * which would remove the audit guarantee the RBIO side depends on.
 *
 * <p>The series is still kept rather than overwritten: rescheduling inserts a row so the history panel can
 * show that a meeting moved, and the original date survives. {@link #sequenceNo} orders the series; the
 * highest is the live meeting.
 */
@Entity
@Table(name = "CEPC_CONCILIATION_MEETINGS",
        indexes = {
                @Index(name = "idx_cepc_conciliation_complaint", columnList = "complaintNumber"),
                @Index(name = "idx_cepc_conciliation_sequence", columnList = "complaintNumber,sequenceNo"),
                @Index(name = "idx_cepc_conciliation_contact", columnList = "contactPersonId,sequenceNo")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcConciliationMeeting {

    /**
     * Statuses in which a meeting is still ahead of the office.
     *
     * <p>The same two the client treats as requiring a date and time. A meeting in either of these is the
     * one the form edits; anything else has run its course and belongs in the history panel.
     */
    public static final Set<String> OPEN_STATUSES = Set.of("SCHEDULED", "RESCHEDULED");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String complaintNumber;

    /**
     * Scopes this meeting to one contact person's own series rather than the complaint's shared one.
     *
     * <p>Null on every meeting the complaint-level Conciliation tab writes — that series is, and stays,
     * shared across the whole complaint. Set only by the per-contact-person Conciliation tab, whose
     * meetings are a private record for that contact and never mirrored onto the complaint's status.
     */
    private Long contactPersonId;

    /** 1 for the first meeting, incrementing per reschedule. Orders the history panel. */
    private Integer sequenceNo;

    /** One of {@code SCHEDULED}, {@code RESCHEDULED}, {@code COMPLETED}, {@code CANCELLED}. */
    @Column(nullable = false, length = 30)
    private String meetingStatus;

    private LocalDate meetingDate;

    /** {@code HH:mm}. Stored as text because the client sends a bare wall-clock time with no zone. */
    @Column(length = 10)
    private String meetingTime;

    /**
     * Whether each side accepted the conciliation, with null meaning not yet answered.
     *
     * <p>Three-valued on purpose: a meeting that has not happened yet has no answer, and collapsing that
     * into {@code false} would report every future meeting as one both sides had already rejected.
     */
    private Boolean acceptedByComplainant;

    private Boolean acceptedByEntity;

    private Boolean conductedThroughVc;

    /** Minutes of the meeting. The client caps this at 4000 characters and so does the service. */
    @Column(length = 4000)
    private String meetingComments;

    /** The officer's own note about the conciliation, kept apart from the minutes. */
    @Column(length = 4000)
    private String comments;

    /** Whether the officer picked any entities beyond the complaint's own regulated entity. */
    private Boolean wantOtherEntities;

    /**
     * Ids of up to six additional entities, comma-separated and positionally paired with
     * {@link #otherEntityNames} — the same denormalisation {@code Complaint.entityName} uses
     * alongside its regulated-entity id: the ids are authoritative, the names are the display label.
     */
    @Column(length = 200)
    private String otherEntityIds;

    @Column(length = 1000)
    private String otherEntityNames;

    @Column(length = 100)
    private String createdBy;

    private LocalDateTime createdAt;

    @Column(length = 100)
    private String updatedBy;

    private LocalDateTime updatedAt;

    /** True while this meeting is the one the form should edit rather than list. */
    public boolean isOpen() {
        return meetingStatus != null && OPEN_STATUSES.contains(meetingStatus.trim().toUpperCase());
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
        if (sequenceNo == null) {
            sequenceNo = 1;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
