package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per hearing EVENT on an appeal -- scheduled, rescheduled, adjourned, completed, cancelled.
 *
 * <p>Why a table at all: {@code APPEALS.hearing_date} / {@code hearing_venue} were overwritten in
 * place by SCHEDULE_HEARING, so rescheduling silently destroyed the fact that an earlier date had
 * ever been fixed and notified. For a statutory hearing that is an audit failure: "the hearing was
 * moved twice" and "the hearing was always on this date" have to be different, provable facts.
 *
 * <p>History is append-only. Superseding a row stamps {@code supersededAt} and
 * {@code supersededById} on it -- a LINKAGE, never a rewrite of the hearing particulars. The
 * operative hearing is the single row with {@code supersededAt IS NULL}; the outcome of a hearing is
 * recorded by APPENDING a COMPLETED/ADJOURNED row rather than by mutating the scheduled one, so the
 * date and venue that the parties were actually notified about survive verbatim.
 */
@Entity
@Table(name = "APPEAL_HEARING", indexes = {
    @Index(name = "idx_ah_appeal", columnList = "appealNumber"),
    @Index(name = "idx_ah_active", columnList = "appealNumber,supersededAt"),
    @Index(name = "idx_ah_officer_date", columnList = "presidingOfficer,hearingDate"),
    @Index(name = "idx_ah_performed_at", columnList = "performedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppealHearing {

    public static final String EVENT_SCHEDULED = "SCHEDULED";
    public static final String EVENT_RESCHEDULED = "RESCHEDULED";
    public static final String EVENT_ADJOURNED = "ADJOURNED";
    public static final String EVENT_COMPLETED = "COMPLETED";
    public static final String EVENT_CANCELLED = "CANCELLED";

    public static final String MODE_IN_PERSON = "IN_PERSON";
    public static final String MODE_VIDEO = "VIDEO";
    public static final String MODE_HYBRID = "HYBRID";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50, updatable = false)
    private String appealNumber;

    /** 1-based, per appeal. Gives the parties a stable "third hearing" reference. */
    @Column(nullable = false, updatable = false)
    private Integer sequenceNo;

    /** SCHEDULED | RESCHEDULED | ADJOURNED | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 20, updatable = false)
    private String eventType;

    @Column(nullable = false, updatable = false)
    private LocalDateTime hearingDate;

    @Column(length = 500, updatable = false)
    private String hearingVenue;

    /** IN_PERSON | VIDEO | HYBRID */
    @Column(length = 20, updatable = false)
    private String hearingMode;

    /**
     * The officer whose calendar this booking occupies. Stored per event rather than read off the
     * appeal, because the appeal's assignee can change after a hearing was fixed and the
     * double-booking check has to reflect who was actually listed at the time.
     */
    @Column(length = 200, updatable = false)
    private String presidingOfficer;

    /** Set only on COMPLETED/ADJOURNED rows: HEARD | ADJOURNED | NOT_HELD | CONCLUDED. */
    @Column(length = 40, updatable = false)
    private String outcome;

    @Column(length = 2000, updatable = false)
    private String outcomeRemarks;

    /** Why it was rescheduled/adjourned. Mandatory for those events at the service boundary. */
    @Column(length = 1000, updatable = false)
    private String reason;

    /** NULL means this is the operative hearing row. */
    private LocalDateTime supersededAt;

    /** The event row that replaced this one, so the chain is walkable in either direction. */
    private Long supersededById;

    @Column(nullable = false, length = 200, updatable = false)
    private String performedBy;

    @Column(length = 50, updatable = false)
    private String performedByRole;

    @Column(nullable = false, updatable = false)
    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        if (performedAt == null) performedAt = LocalDateTime.now();
    }
}
