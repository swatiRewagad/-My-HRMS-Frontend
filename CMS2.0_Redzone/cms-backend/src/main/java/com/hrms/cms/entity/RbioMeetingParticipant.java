package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An invited participant in a conciliation meeting (UST498).
 *
 * <p><b>Invitee is not attendee.</b> {@link #participantConfirmed} is what the "participants list displays
 * all confirmed attendee entities" requirement reads. Conflating the two would let the Minutes of Meeting
 * letter assert attendance that nobody confirmed — and the MOM is the record of what the parties agreed, so
 * an over-claim there is a factual defect in a document the complaint's outcome rests on.
 *
 * <p><b>The six-cap is not implemented here.</b> UST498 caps additional entity participants at six,
 * "matching the general entity-add limit". {@code RbioAdditionalEntityService.assertCapAllowsOneMore} owns
 * that rule and its javadoc names this feature as a required caller. A second constant in this class would
 * be a second cap that will eventually disagree with the first.
 *
 * <p>Attendance is an officer's assertion rather than something the system observes, because UST501 places
 * the meeting itself outside the application. There is deliberately no join link, no dial-in and no
 * presence timestamp.
 */
@Entity
@Table(name = "RBIO_MEETING_PARTICIPANT", indexes = {
    @Index(name = "idx_rbio_mtg_part_meeting", columnList = "meetingId"),
    @Index(name = "idx_rbio_mtg_part_complaint", columnList = "complaintNumber")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RbioMeetingParticipant {

    public static final String TYPE_ENTITY = "ENTITY";
    public static final String TYPE_COMPLAINANT = "COMPLAINANT";
    public static final String TYPE_OFFICER = "OFFICER";
    public static final String TYPE_OTHER = "OTHER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The meeting event this participant was invited to.
     *
     * <p>Nullable so a participant list can be assembled for a complaint before the first meeting row
     * exists; the service always sets it when recording against a meeting.
     */
    @Column(name = "MEETING_ID")
    private Long meetingId;

    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    /** ENTITY | COMPLAINANT | OFFICER | OTHER */
    @Column(name = "PARTICIPANT_TYPE", length = 20)
    private String participantType;

    @Column(name = "PARTICIPANT_NAME", nullable = false, length = 250)
    private String participantName;

    /** Set when the participant is a regulated entity carried on the complaint or impleaded into it. */
    @Column(name = "ENTITY_CODE", length = 50)
    private String entityCode;

    @Column(name = "PARTICIPANT_EMAIL", length = 320)
    private String participantEmail;

    /** 'Y' once the officer records the party as having attended. See the class comment. */
    @Column(name = "PARTICIPANT_CONFIRMED", length = 1)
    private String participantConfirmed;

    @Column(name = "ADDED_BY", length = 200)
    private String addedBy;

    @Column(name = "CREATED_AT")
    private LocalDateTime createdAt;

    public boolean confirmed() {
        return "Y".equalsIgnoreCase(participantConfirmed);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
