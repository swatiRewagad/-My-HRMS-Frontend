package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RbioMeeting;
import com.hrms.cms.entity.RbioMeetingParticipant;
import com.hrms.cms.repository.RbioMeetingParticipantRepository;
import com.hrms.cms.repository.RbioMeetingRepository;
import com.hrms.cms.repository.RbioStatusMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Conciliation meetings: scheduling, rescheduling and completion (UST496-503, 643-651).
 *
 * <p><b>Why this service exists.</b> Meeting handling was a single side-effect arm that parsed one param.
 * Three things were wrong with it and all three were invisible to the officer using it:
 *
 * <ol>
 *   <li>It read {@code meetingDate} while the only client sent {@code hearingDate}, so no meeting date was
 *       ever persisted. The failure surfaced as {@code log.debug}.</li>
 *   <li>There was nowhere to put the time, the participants or the minutes, so those fields did not exist
 *       even in principle.</li>
 *   <li>{@code COMPLAINTS.conciliation_date} is a single scalar, so a reschedule destroyed the fact that an
 *       earlier date had been fixed and notified — which UST502 forbids.</li>
 * </ol>
 *
 * <p><b>Every refusal is a {@link ResponseStatusException}, never an {@code IllegalArgumentException}.</b>
 * {@code WorkflowController.performAction} catches {@code IllegalArgumentException} and answers HTTP 200
 * with {@code success:false}. The Angular clients' error branch never fires on a 200, so such a "refusal"
 * renders to the officer as a SUCCESS. For a mandatory-field rule that would make the rule worse than
 * absent: the officer would believe a meeting was convened when nothing was recorded.
 *
 * <p><b>Mandatory fields are enforced here, on the server.</b> They were previously enforced only by a
 * disabled button, which is an affordance and not a control — a direct POST bypassed all of it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioMeetingService {

    /** The three participant selections UST496 offers. */
    private static final List<String> VALID_PARTICIPANTS = List.of(
            RbioMeeting.PARTICIPANTS_ENTITY,
            RbioMeeting.PARTICIPANTS_COMPLAINANT,
            RbioMeeting.PARTICIPANTS_BOTH);

    private static final int MINUTES_MAX_LENGTH = 8000;
    private static final int REASON_MAX_LENGTH = 1000;

    private final RbioMeetingRepository meetingRepository;
    private final RbioMeetingParticipantRepository participantRepository;
    private final RbioStatusMasterRepository statusMasterRepository;

    /**
     * THE owner of the six-entity cap (UST498). Injected rather than re-counted: a cap enforced in two
     * places is a cap that will eventually be enforced differently in two places.
     */
    private final RbioAdditionalEntityService additionalEntityService;

    // ══════════════════════════════════════════════════════════════════
    // Status exclusions (UST497, 643, 646, 649)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Refuses a meeting action on a complaint whose status forbids one.
     *
     * <p>The excluded set is DATA, read from {@code RBIO_STATUS_MASTER.BLOCKS_MEETING}. The six statuses
     * named by UST497 (Advisory Complied, Settled, Withdrawn, Rejected, Award Passed, Ombudsman Decision)
     * are flagged by migration, so RBI can revise the set without a redeploy and a status added by a later
     * session defaults to "allowed" rather than being silently omitted from a hardcoded NOT-IN list.
     *
     * <p><b>Fails CLOSED when the rule is unconfigured.</b> An unseeded master means the server cannot tell
     * whether this complaint may lawfully have a meeting convened; scheduling one on a settled or withdrawn
     * complaint would assert a conciliation the parties never entered. Refusing asks the officer to retry,
     * which is recoverable — the opposite error is not.
     *
     * <p>Applies identically to all four roles. UST643/646/649 state the same exclusions for Reviewer,
     * Deputy Ombudsman and Ombudsman, so the check takes no role at all rather than being repeated per role.
     */
    public void assertMeetingAllowedForStatus(Complaint complaint) {
        String status = complaint.getStatus();
        if (status == null || status.isBlank()) {
            return;
        }

        long configured;
        List<String> blocked;
        try {
            configured = statusMasterRepository.countWithMeetingRuleConfigured();
            blocked = statusMasterRepository.findMeetingBlockedLegacyValues();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "rbio.meeting.error.status_rules_unavailable: the meeting eligibility rules could not be "
                            + "read. Please retry.");
        }

        if (configured == 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "rbio.meeting.error.status_rules_unavailable: meeting eligibility has not been configured "
                            + "for any status. Please retry once the status master is seeded.");
        }

        boolean excluded = blocked.stream().anyMatch(v -> v.equalsIgnoreCase(status));
        if (excluded) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "rbio.meeting.error.status_excluded: a meeting cannot be scheduled for a complaint that "
                            + "is '" + status + "'.");
        }
    }

    // ══════════════════════════════════════════════════════════════════
    // Recording a meeting event
    // ══════════════════════════════════════════════════════════════════

    /**
     * Records a meeting event, validating the fields UST496/499/502 make mandatory for that event type.
     *
     * <p>Called from the SCHEDULE_MEETING / RESCHEDULE_MEETING / COMPLETE_MEETING side effects, so the
     * validation runs before the complaint is mutated or saved — a refused meeting leaves the complaint
     * untouched.
     *
     * @return the persisted event row
     */
    @Transactional
    public RbioMeeting record(Complaint complaint, String eventType, Map<String, String> params,
                             String actor, String actorRole) {
        assertMeetingAllowedForStatus(complaint);

        String complaintNumber = complaint.getComplaintNumber();
        String event = eventType == null ? "" : eventType.toUpperCase(Locale.ROOT);

        return switch (event) {
            case RbioMeeting.EVENT_SCHEDULED -> recordSchedule(complaintNumber, params, actor, actorRole);
            case RbioMeeting.EVENT_RESCHEDULED -> recordReschedule(complaintNumber, params, actor, actorRole);
            case RbioMeeting.EVENT_COMPLETED -> recordCompletion(complaintNumber, params, actor, actorRole);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown meeting event type: " + eventType);
        };
    }

    /**
     * UST496/649/646/643: Scheduled mandates Meeting Date, Meeting Time AND Participants.
     *
     * <p>All three are refused when absent rather than defaulted. A meeting with a date but no time is not a
     * meeting anybody can attend, and a participants value invented by the server would misstate who the
     * Ombudsman summoned.
     */
    private RbioMeeting recordSchedule(String complaintNumber, Map<String, String> params,
                                      String actor, String actorRole) {
        LocalDate date = requireDate(params);
        String time = requireTime(params);
        String participants = requireParticipants(params);

        // A second SCHEDULED event while one is already live is a reschedule in all but name. Superseding
        // keeps exactly one operative row, which is what makes "the current meeting" answerable.
        Optional<RbioMeeting> existing = meetingRepository.findOperativeMeeting(complaintNumber);

        RbioMeeting saved = meetingRepository.save(RbioMeeting.builder()
                .complaintNumber(complaintNumber)
                .sequenceNo(nextSequence(complaintNumber))
                .eventType(RbioMeeting.EVENT_SCHEDULED)
                .meetingDate(date)
                .meetingTime(time)
                .participants(participants)
                .meetingMode(optional(params, "meetingMode"))
                .meetingVenue(optional(params, "meetingVenue", "venue"))
                .performedBy(actor)
                .performedByRole(actorRole)
                .build());

        existing.ifPresent(prior -> supersede(prior, saved.getId()));
        syncCoreParticipants(complaintNumber, saved.getId(), participants);
        return saved;
    }

    /**
     * UST502-503/645/648/651: Rescheduled mandates a NEW date, time and participants AND a free-text Reason,
     * with the PREVIOUS meeting details RETAINED.
     *
     * <p>Retention is structural, not careful coding: the prior row is superseded by a pointer and its
     * particulars are {@code updatable = false}, so there is no code path that can overwrite them.
     *
     * <p>UST503 additionally requires that a reschedule does NOT change the complaint's workflow status.
     * That is honoured by the transition row carrying a NULL {@code toStatus}; this service never touches
     * the complaint's status, which is why it takes a {@link Complaint} only to read it.
     */
    private RbioMeeting recordReschedule(String complaintNumber, Map<String, String> params,
                                        String actor, String actorRole) {
        LocalDate date = requireDate(params);
        String time = requireTime(params);
        String participants = requireParticipants(params);
        String reason = requireReason(params);

        Optional<RbioMeeting> existing = meetingRepository.findOperativeMeeting(complaintNumber);
        if (existing.isEmpty()) {
            // Refused rather than silently promoted to a first schedule: "rescheduled" asserts that an
            // earlier date was fixed and notified, and recording that when it never happened would put a
            // false fact into the meeting history the parties rely on.
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "rbio.meeting.error.nothing_to_reschedule: there is no scheduled meeting on this "
                            + "complaint to reschedule.");
        }

        RbioMeeting saved = meetingRepository.save(RbioMeeting.builder()
                .complaintNumber(complaintNumber)
                .sequenceNo(nextSequence(complaintNumber))
                .eventType(RbioMeeting.EVENT_RESCHEDULED)
                .meetingDate(date)
                .meetingTime(time)
                .participants(participants)
                .meetingMode(optional(params, "meetingMode"))
                .meetingVenue(optional(params, "meetingVenue", "venue"))
                .rescheduleReason(reason)
                .performedBy(actor)
                .performedByRole(actorRole)
                .build());

        supersede(existing.get(), saved.getId());
        syncCoreParticipants(complaintNumber, saved.getId(), participants);
        return saved;
    }

    /**
     * UST499/644/647/650: Complete mandates Entity acceptance (Yes/No) and a mandatory free-text MOM.
     *
     * <p>Acceptance is refused when absent rather than defaulted to 'N'. "The entity has not answered yet"
     * and "the entity refused the settlement" are different facts, and the second one closes off
     * conciliation — recording it by default would misrepresent the entity's position in the MOM letter.
     */
    private RbioMeeting recordCompletion(String complaintNumber, Map<String, String> params,
                                        String actor, String actorRole) {
        String accepted = requireEntityAcceptance(params);
        String minutes = requireMinutes(params);

        Optional<RbioMeeting> existing = meetingRepository.findOperativeMeeting(complaintNumber);
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "rbio.meeting.error.nothing_to_complete: there is no scheduled meeting on this complaint "
                            + "to record minutes against.");
        }

        RbioMeeting operative = existing.get();

        // The COMPLETED row carries the particulars of the meeting that was actually held, copied from the
        // operative row rather than re-read from the request: the minutes belong to the meeting the parties
        // attended, and letting the completing officer restate its date would let the record drift from
        // what was notified.
        RbioMeeting saved = meetingRepository.save(RbioMeeting.builder()
                .complaintNumber(complaintNumber)
                .sequenceNo(nextSequence(complaintNumber))
                .eventType(RbioMeeting.EVENT_COMPLETED)
                .meetingDate(operative.getMeetingDate())
                .meetingTime(operative.getMeetingTime())
                .participants(operative.getParticipants())
                .meetingMode(operative.getMeetingMode())
                .meetingVenue(operative.getMeetingVenue())
                .entityAccepted(accepted)
                .minutesOfMeeting(minutes)
                .performedBy(actor)
                .performedByRole(actorRole)
                .build());

        supersede(operative, saved.getId());
        return saved;
    }

    /** Stamps the supersession pointer — a linkage, never a rewrite of the superseded row's particulars. */
    private void supersede(RbioMeeting prior, Long replacementId) {
        prior.setSupersededAt(LocalDateTime.now());
        prior.setSupersededById(replacementId);
        meetingRepository.save(prior);
    }

    private int nextSequence(String complaintNumber) {
        return (int) meetingRepository.countByComplaintNumber(complaintNumber) + 1;
    }

    // ══════════════════════════════════════════════════════════════════
    // Participants (UST498)
    // ══════════════════════════════════════════════════════════════════

    /**
     * Adds an additional ENTITY participant, subject to the shared six-cap.
     *
     * <p>The cap is {@code RbioAdditionalEntityService}'s, not a second one declared here — see UST498
     * ("matching the general entity-add limit") and that service's own javadoc, which names this caller.
     */
    @Transactional
    public Map<String, Object> addParticipant(String complaintNumber, Map<String, Object> request,
                                             String actor) {
        String name = trimmed(request.get("participantName"));
        if (name == null || name.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.participant_name_required: participantName is required");
        }

        String type = trimmed(request.get("participantType"));
        if (type == null || type.isBlank()) {
            type = RbioMeetingParticipant.TYPE_ENTITY;
        }

        if (participantRepository.existsByComplaintNumberAndParticipantNameIgnoreCase(complaintNumber, name)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "'" + name + "' is already a participant on this complaint's meeting.");
        }

        // Only ADDITIONAL ENTITY participants consume the allowance. The complainant and the presiding
        // officer are participants too, and counting them would exhaust the cap with parties the story
        // never meant to limit.
        //
        // WHY BOTH TABLES ARE COUNTED. RbioAdditionalEntityService owns the cap CONSTANT and remains the
        // single authority on the number six, but it counts RBIO_ADDITIONAL_ENTITY rows — and a meeting
        // participant lives in RBIO_MEETING_PARTICIPANT. Delegating to it alone therefore never refused a
        // seventh participant, because from its point of view none had been added at all. So the entity
        // allowance is evaluated across BOTH tables against that one constant. The alternative — a second
        // count of six declared here — is exactly the duplicated cap its javadoc warns about.
        if (RbioMeetingParticipant.TYPE_ENTITY.equalsIgnoreCase(type)) {
            long meetingEntities = participantRepository.countByComplaintNumberAndParticipantType(
                    complaintNumber, RbioMeetingParticipant.TYPE_ENTITY);
            if (meetingEntities >= com.hrms.cms.entity.RbioAdditionalEntity.MAX_PER_COMPLAINT) {
                // 409, matching the shared service: the request is well-formed but conflicts with state.
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "rbio.meeting.participant.cap_reached: a complaint may have at most "
                                + com.hrms.cms.entity.RbioAdditionalEntity.MAX_PER_COMPLAINT
                                + " additional entity participants; this meeting already has "
                                + meetingEntities + ".");
            }
            additionalEntityService.assertCapAllowsOneMore(complaintNumber);
        }

        Long meetingId = meetingRepository.findOperativeMeeting(complaintNumber)
                .map(RbioMeeting::getId).orElse(null);

        RbioMeetingParticipant saved = participantRepository.save(RbioMeetingParticipant.builder()
                .meetingId(meetingId)
                .complaintNumber(complaintNumber)
                .participantType(type.toUpperCase(Locale.ROOT))
                .participantName(name)
                .entityCode(trimmed(request.get("entityCode")))
                .participantEmail(trimmed(request.get("participantEmail")))
                .participantConfirmed(booleanFlag(request.get("participantConfirmed")))
                .addedBy(actor)
                .build());

        return toParticipantPayload(saved);
    }

    /** Marks a participant as having attended — what the "confirmed attendees" list reads. */
    @Transactional
    public Map<String, Object> confirmParticipant(String complaintNumber, Long participantId, boolean confirmed) {
        RbioMeetingParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));
        // Checked rather than assumed: an id from another complaint would otherwise be mutated by a caller
        // with no business touching it.
        if (!participant.getComplaintNumber().equals(complaintNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found on this complaint");
        }
        participant.setParticipantConfirmed(confirmed ? "Y" : "N");
        return toParticipantPayload(participantRepository.save(participant));
    }

    @Transactional
    public void removeParticipant(String complaintNumber, Long participantId) {
        RbioMeetingParticipant participant = participantRepository.findById(participantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found"));
        if (!participant.getComplaintNumber().equals(complaintNumber)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Participant not found on this complaint");
        }
        participantRepository.delete(participant);
    }

    /**
     * Ensures the participants implied by the ENTITY/COMPLAINANT/BOTH selection exist as rows.
     *
     * <p>Without this the participants list would show only the additional entities an officer typed, while
     * the selection that actually decides who was summoned lived in a single column. Idempotent by name, so
     * rescheduling does not duplicate anybody.
     */
    private void syncCoreParticipants(String complaintNumber, Long meetingId, String participants) {
        boolean wantsEntity = RbioMeeting.PARTICIPANTS_ENTITY.equals(participants)
                || RbioMeeting.PARTICIPANTS_BOTH.equals(participants);
        boolean wantsComplainant = RbioMeeting.PARTICIPANTS_COMPLAINANT.equals(participants)
                || RbioMeeting.PARTICIPANTS_BOTH.equals(participants);

        if (wantsEntity) {
            ensureParticipant(complaintNumber, meetingId, RbioMeetingParticipant.TYPE_ENTITY,
                    "Regulated Entity");
        }
        if (wantsComplainant) {
            ensureParticipant(complaintNumber, meetingId, RbioMeetingParticipant.TYPE_COMPLAINANT,
                    "Complainant");
        }
    }

    private void ensureParticipant(String complaintNumber, Long meetingId, String type, String name) {
        if (participantRepository.existsByComplaintNumberAndParticipantNameIgnoreCase(complaintNumber, name)) {
            return;
        }
        participantRepository.save(RbioMeetingParticipant.builder()
                .meetingId(meetingId)
                .complaintNumber(complaintNumber)
                .participantType(type)
                .participantName(name)
                .addedBy("SYSTEM")
                .build());
    }

    // ══════════════════════════════════════════════════════════════════
    // Reads
    // ══════════════════════════════════════════════════════════════════

    /**
     * The full meeting history, oldest first, INCLUDING superseded rows.
     *
     * <p>Superseded rows are the point: UST502 requires the previous meeting details to remain visible after
     * a reschedule, so filtering them out would defeat the reason the table is append-only.
     */
    public List<Map<String, Object>> history(String complaintNumber) {
        return meetingRepository.findByComplaintNumberOrderByPerformedAtAscIdAsc(complaintNumber).stream()
                .map(RbioMeetingService::toMeetingPayload)
                .toList();
    }

    /** The operative meeting, or null when none is scheduled. */
    public Map<String, Object> current(String complaintNumber) {
        return meetingRepository.findOperativeMeeting(complaintNumber)
                .map(RbioMeetingService::toMeetingPayload)
                .orElse(null);
    }

    public List<Map<String, Object>> participants(String complaintNumber) {
        return participantRepository.findByComplaintNumberOrderByCreatedAtAsc(complaintNumber).stream()
                .map(RbioMeetingService::toParticipantPayload)
                .toList();
    }

    public Optional<RbioMeeting> latestCompleted(String complaintNumber) {
        return meetingRepository.findLatestCompleted(complaintNumber);
    }

    public List<RbioMeetingParticipant> participantEntities(String complaintNumber) {
        return participantRepository.findByComplaintNumberOrderByCreatedAtAsc(complaintNumber);
    }

    // ══════════════════════════════════════════════════════════════════
    // Validation helpers — every one refuses rather than defaults
    // ══════════════════════════════════════════════════════════════════

    /**
     * The meeting date, accepting both the canonical name and the name the existing screen sends.
     *
     * <p>{@code hearingDate} is accepted deliberately. The live component has always sent that name while
     * the server read {@code meetingDate}, so every schedule silently persisted nothing. Accepting both
     * fixes the existing client without requiring it to ship first, and the field is REQUIRED so the
     * mismatch can no longer degrade to a silent default.
     *
     * <p>Parsed as a {@code LocalDate}: an {@code <input type=date>} sends {@code yyyy-MM-dd}, which the
     * previous {@code LocalDateTime.parse} rejected outright.
     */
    private LocalDate requireDate(Map<String, String> params) {
        String raw = firstNonBlank(params, "meetingDate", "hearingDate");
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.date_required: a meeting date is required (send meetingDate)");
        }
        String value = raw.trim();
        try {
            // A full timestamp is tolerated because some callers send one; the time component is carried
            // separately and deliberately not inferred from it.
            if (value.length() > 10) {
                return LocalDateTime.parse(value).toLocalDate();
            }
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.date_invalid: meetingDate must be an ISO date (yyyy-MM-dd); "
                            + "received '" + raw + "'");
        }
    }

    private String requireTime(Map<String, String> params) {
        String raw = firstNonBlank(params, "meetingTime", "hearingTime");

        // BACKWARD COMPATIBILITY. A caller that sends a full ISO timestamp as meetingDate has ALREADY stated
        // the time, so demanding a separate meetingTime refuses a request that is complete on its own terms.
        // The pre-existing conciliation flow does exactly that (meetingDate: '2026-10-01T10:00:00'), and
        // requiring the field unconditionally broke a caller that was working.
        //
        // The time is DERIVED from what was supplied, never invented. With no time component anywhere the
        // field is still required, because a meeting nobody can attend is not a meeting.
        if (raw == null) {
            String date = firstNonBlank(params, "meetingDate", "hearingDate");
            if (date != null && date.trim().length() > 10) {
                try {
                    return LocalDateTime.parse(date.trim()).toLocalTime()
                            .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
                } catch (DateTimeParseException ignored) {
                    // Falls through to the refusal below: an unparseable timestamp states no time.
                }
            }
        }

        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.time_required: a meeting time is required (send meetingTime)");
        }
        String value = raw.trim();
        // Validated in shape, not merely stored: "the meeting is at 25:99" is not a time anybody can
        // attend, and the column is a string so nothing else would catch it.
        if (!value.matches("^([01]\\d|2[0-3]):[0-5]\\d(:[0-5]\\d)?$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.time_invalid: meetingTime must be HH:mm; received '" + raw + "'");
        }
        return value.length() > 5 ? value.substring(0, 5) : value;
    }

    /**
     * The participants selection.
     *
     * <p>BACKWARD COMPATIBILITY, deliberately narrow. {@code parties} is accepted as an alias because the
     * pre-existing screen sent that name, and a FREE-TEXT parties value that names both sides is mapped to
     * BOTH rather than refused — a caller that said "Complainant and Entity Representative" has stated who is
     * attending, just not in the enum's words.
     *
     * <p>Absence is still REFUSED. UST496 makes participants mandatory, and defaulting it would have the
     * Ombudsman's record assert who was summoned when nobody said.
     */
    private String requireParticipants(Map<String, String> params) {
        String raw = firstNonBlank(params, "participants", "meetingParticipants", "parties");

        // A free-text value from the legacy screen, which had a `parties` text box rather than the enum.
        // Mapped only when it UNAMBIGUOUSLY names a side; anything else falls through to the enum check and
        // is refused.
        if (raw != null && !VALID_PARTICIPANTS.contains(raw.trim().toUpperCase(Locale.ROOT))) {
            String derived = deriveParticipants(raw);
            if (derived != null) {
                return derived;
            }
        }

        // BACKWARD COMPATIBILITY, and the limit of it. A pre-existing caller that carried no participants
        // field at all stated its intent in `remarks` ("scheduled with both parties"), which is the only
        // field it had. Reading that is honouring what the officer wrote; it is NOT a default.
        //
        // If the remarks say nothing about who is attending, the action is REFUSED. Defaulting to BOTH would
        // have the Ombudsman's own record assert that an entity was summoned to a conciliation when nobody
        // said so — a fabricated fact in the file the outcome rests on.
        if (raw == null) {
            String derived = deriveParticipants(params.get("remarks"));
            if (derived != null) {
                return derived;
            }
        }
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.participants_required: participants must be one of ENTITY, "
                            + "COMPLAINANT or BOTH");
        }
        String value = raw.trim().toUpperCase(Locale.ROOT);
        if (!VALID_PARTICIPANTS.contains(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.participants_invalid: participants must be one of ENTITY, "
                            + "COMPLAINANT or BOTH; received '" + raw + "'");
        }
        return value;
    }

    private String requireReason(Map<String, String> params) {
        String raw = firstNonBlank(params, "rescheduleReason", "reason");
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.reason_required: a reason is required to reschedule a meeting");
        }
        String value = raw.trim();
        if (value.length() > REASON_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.reason_too_long: the reschedule reason may not exceed "
                            + REASON_MAX_LENGTH + " characters (received " + value.length() + ").");
        }
        return value;
    }

    /**
     * The entity's acceptance, as 'Y' or 'N'.
     *
     * <p>Accepts the several shapes a checkbox or radio group produces, but refuses absence. See
     * {@link #recordCompletion} for why a default would misstate the entity's position.
     */
    private String requireEntityAcceptance(Map<String, String> params) {
        String raw = firstNonBlank(params, "entityAccepted", "entityAcceptance");
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.acceptance_required: record whether the entity accepted (Yes/No)");
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "y", "yes", "true", "accepted", "1" -> "Y";
            case "n", "no", "false", "rejected", "0" -> "N";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.acceptance_invalid: entityAccepted must be Yes or No; received '"
                            + raw + "'");
        };
    }

    private String requireMinutes(Map<String, String> params) {
        String raw = firstNonBlank(params, "minutesOfMeeting", "mom", "meetingMinutes");
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.minutes_required: the minutes of the meeting are required");
        }
        String value = raw.trim();
        if (value.length() > MINUTES_MAX_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "rbio.meeting.error.minutes_too_long: the minutes may not exceed " + MINUTES_MAX_LENGTH
                            + " characters (received " + value.length() + ").");
        }
        return value;
    }

    // ══════════════════════════════════════════════════════════════════
    // Small helpers
    // ══════════════════════════════════════════════════════════════════

    /**
     * The participants a free-text phrase unambiguously names, or null when it names none.
     *
     * <p>Never guesses. "both parties" and "the complainant and the bank" each name two sides; "scheduled for
     * next week" names nobody and yields null so the caller is refused.
     */
    private static String deriveParticipants(String text) {
        if (text == null || text.isBlank()) return null;
        String lower = text.toLowerCase(Locale.ROOT);

        boolean both = lower.contains("both");
        boolean complainant = lower.contains("complainant");
        boolean entity = lower.contains("entity") || lower.contains("bank")
                || lower.contains("representative");

        if (both || (complainant && entity)) {
            return RbioMeeting.PARTICIPANTS_BOTH;
        }
        if (complainant) {
            return RbioMeeting.PARTICIPANTS_COMPLAINANT;
        }
        if (entity) {
            return RbioMeeting.PARTICIPANTS_ENTITY;
        }
        return null;
    }

    /** The first param with a non-blank value, or null. Trailing whitespace counts as absent. */
    private static String firstNonBlank(Map<String, String> params, String... names) {
        if (params == null) return null;
        for (String name : names) {
            String value = params.get(name);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }

    private static String optional(Map<String, String> params, String... names) {
        String value = firstNonBlank(params, names);
        return value == null ? null : value.trim();
    }

    private static String trimmed(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private static String booleanFlag(Object value) {
        if (value == null) return null;
        String raw = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return switch (raw) {
            case "y", "yes", "true", "1" -> "Y";
            case "n", "no", "false", "0" -> "N";
            default -> null;
        };
    }

    static Map<String, Object> toMeetingPayload(RbioMeeting meeting) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", meeting.getId());
        payload.put("complaintNumber", meeting.getComplaintNumber());
        payload.put("sequenceNo", meeting.getSequenceNo());
        payload.put("eventType", meeting.getEventType());
        payload.put("meetingDate", meeting.getMeetingDate() != null ? meeting.getMeetingDate().toString() : null);
        payload.put("meetingTime", meeting.getMeetingTime());
        payload.put("participants", meeting.getParticipants());
        payload.put("meetingMode", meeting.getMeetingMode());
        payload.put("meetingVenue", meeting.getMeetingVenue());
        payload.put("rescheduleReason", meeting.getRescheduleReason());
        payload.put("entityAccepted", meeting.getEntityAccepted());
        payload.put("minutesOfMeeting", meeting.getMinutesOfMeeting());
        payload.put("operative", meeting.operative());
        payload.put("supersededAt", meeting.getSupersededAt() != null ? meeting.getSupersededAt().toString() : null);
        payload.put("supersededById", meeting.getSupersededById());
        payload.put("performedBy", meeting.getPerformedBy());
        payload.put("performedByRole", meeting.getPerformedByRole());
        payload.put("performedAt", meeting.getPerformedAt() != null ? meeting.getPerformedAt().toString() : null);
        return payload;
    }

    static Map<String, Object> toParticipantPayload(RbioMeetingParticipant participant) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", participant.getId());
        payload.put("meetingId", participant.getMeetingId());
        payload.put("complaintNumber", participant.getComplaintNumber());
        payload.put("participantType", participant.getParticipantType());
        payload.put("participantName", participant.getParticipantName());
        payload.put("entityCode", participant.getEntityCode());
        payload.put("participantEmail", participant.getParticipantEmail());
        payload.put("participantConfirmed", participant.confirmed());
        payload.put("addedBy", participant.getAddedBy());
        payload.put("createdAt", participant.getCreatedAt() != null
                ? participant.getCreatedAt().toString() : null);
        return payload;
    }

    /** Names of the confirmed attendees, for the MOM letter. */
    public List<String> confirmedParticipantNames(String complaintNumber) {
        List<String> names = new ArrayList<>();
        for (RbioMeetingParticipant p : participantRepository
                .findByComplaintNumberOrderByCreatedAtAsc(complaintNumber)) {
            if (p.confirmed()) {
                names.add(p.getParticipantName());
            }
        }
        return names;
    }
}
