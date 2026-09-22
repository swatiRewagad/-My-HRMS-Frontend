package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row per conciliation meeting EVENT on a complaint — scheduled, rescheduled, completed, cancelled
 * (UST496-503, 643-651).
 *
 * <p><b>Why a table at all.</b> Meeting particulars previously had two homes: the single scalar
 * {@code COMPLAINTS.conciliation_date}, and the free-text {@code remarks} of a timeline row. Both are
 * overwritten by the next schedule, so "the meeting was moved twice" and "the meeting was always on this
 * date" were the same stored fact. UST502-503 require the PREVIOUS meeting details to be RETAINED on a
 * reschedule, which that shape cannot express.
 *
 * <p>In practice nothing was persisted at all: the FX_MEETING_DATE side effect read {@code meetingDate}
 * while its only client sent {@code hearingDate}, so {@code conciliation_date} stayed NULL after every
 * schedule and the time, venue and participants had no column to land in even in principle.
 *
 * <p><b>Append-only, modelled on {@link AppealHearing}.</b> Superseding a row stamps {@link #supersededAt}
 * and {@link #supersededById} on it — a LINKAGE, never a rewrite of the particulars. The operative meeting
 * is the single row per complaint with {@code supersededAt IS NULL}; an outcome is recorded by APPENDING a
 * COMPLETED row rather than mutating the scheduled one, so the date and participants the parties were
 * actually notified about survive verbatim. The AA module already proved this shape for a statutory
 * hearing; a second history shape would be a second set of bugs.
 *
 * <p>Every particular is {@code updatable = false} for that reason. The only mutable columns are the two
 * supersession pointers, which is what makes "append-only" a schema property rather than a convention.
 *
 * <p><b>The meeting is not hosted here.</b> UST501 is explicit that it is conducted OUTSIDE the CMS
 * application; only the particulars and the minutes are captured. So there is no join link and no observed
 * attendance — {@code RbioMeetingParticipant.participantConfirmed} is an officer's assertion.
 *
 * <p><b>Why date and time are separate.</b> Both are independently mandatory (UST496) and a single
 * timestamp cannot express "date supplied, time missing", which is the exact refusal the story demands. It
 * also avoids the live defect where a date-only {@code yyyy-MM-dd} from an {@code <input type=date>} failed
 * {@code LocalDateTime.parse} and was swallowed at {@code log.debug}.
 */
@Entity
@Table(name = "RBIO_MEETING", indexes = {
    @Index(name = "idx_rbio_meeting_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_rbio_meeting_active", columnList = "complaintNumber,supersededAt"),
    @Index(name = "idx_rbio_meeting_date", columnList = "meetingDate")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioMeeting {

    public static final String EVENT_SCHEDULED = "SCHEDULED";
    public static final String EVENT_RESCHEDULED = "RESCHEDULED";
    public static final String EVENT_COMPLETED = "COMPLETED";
    public static final String EVENT_CANCELLED = "CANCELLED";

    /** Participant selections offered by UST496. */
    public static final String PARTICIPANTS_ENTITY = "ENTITY";
    public static final String PARTICIPANTS_COMPLAINANT = "COMPLAINANT";
    public static final String PARTICIPANTS_BOTH = "BOTH";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50, updatable = false)
    private String complaintNumber;

    /**
     * 1-based per complaint. Gives the parties a stable "third meeting" reference that survives
     * rescheduling, which an id cannot because ids are also consumed by superseded rows.
     */
    @Column(name = "SEQUENCE_NO", updatable = false)
    private Integer sequenceNo;

    /** SCHEDULED | RESCHEDULED | COMPLETED | CANCELLED */
    @Column(name = "EVENT_TYPE", nullable = false, length = 20, updatable = false)
    private String eventType;

    @Column(name = "MEETING_DATE", updatable = false)
    private LocalDate meetingDate;

    /** Stored as the officer typed it (HH:mm). See the class comment for why it is not folded into a timestamp. */
    @Column(name = "MEETING_TIME", length = 10, updatable = false)
    private String meetingTime;

    /** ENTITY | COMPLAINANT | BOTH */
    @Column(name = "PARTICIPANTS", length = 20, updatable = false)
    private String participants;

    @Column(name = "MEETING_MODE", length = 20, updatable = false)
    private String meetingMode;

    @Column(name = "MEETING_VENUE", length = 500, updatable = false)
    private String meetingVenue;

    /** Mandatory for RESCHEDULED at the service boundary (UST502). A SCHEDULED row legitimately has none. */
    @Column(name = "RESCHEDULE_REASON", length = 1000, updatable = false)
    private String rescheduleReason;

    /**
     * COMPLETED rows only (UST499). 'Y' | 'N'.
     *
     * <p>Nullable and never defaulted: "not yet recorded" and "the entity refused" must stay
     * distinguishable, and defaulting to 'N' would fabricate a refusal the entity never made.
     */
    @Column(name = "ENTITY_ACCEPTED", length = 1, updatable = false)
    private String entityAccepted;

    /** The Minutes of Meeting. Mandatory for COMPLETED at the service boundary (UST499). */
    @Lob
    @Column(name = "MINUTES_OF_MEETING", updatable = false)
    private String minutesOfMeeting;

    /** NULL means this is the operative meeting row. */
    @Column(name = "SUPERSEDED_AT")
    private LocalDateTime supersededAt;

    /** The event row that replaced this one, so the chain is walkable in either direction. */
    @Column(name = "SUPERSEDED_BY_ID")
    private Long supersededById;

    @Column(name = "PERFORMED_BY", length = 200, updatable = false)
    private String performedBy;

    @Column(name = "PERFORMED_BY_ROLE", length = 50, updatable = false)
    private String performedByRole;

    @Column(name = "PERFORMED_AT", updatable = false)
    private LocalDateTime performedAt;

    @Column(name = "CREATED_AT", updatable = false)
    private LocalDateTime createdAt;

    /** True when this row is the operative meeting rather than a superseded one. */
    public boolean operative() {
        return supersededAt == null;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (performedAt == null) performedAt = now;
        if (createdAt == null) createdAt = now;
    }
}
