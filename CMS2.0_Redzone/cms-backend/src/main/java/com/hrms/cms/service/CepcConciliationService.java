package com.hrms.cms.service;

import com.hrms.cms.entity.CepcConciliationMeeting;
import com.hrms.cms.entity.CepcContactPerson;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.CepcConciliationMeetingRepository;
import com.hrms.cms.repository.CepcContactPersonRepository;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Conciliation tab: {@code GET/PUT /api/complaints/cepc/{id}/conciliation}.
 *
 * <p>Answers {@code {current, history[]}}. {@code current} is the meeting the form edits and is null once
 * the series has run its course, which is how the tab offers to schedule a fresh meeting after one was
 * completed or cancelled rather than presenting a finished meeting as though it were still ahead.
 *
 * <p>Every save mirrors the outcome onto the parent complaint, because two other screens read it from there:
 * the dashboard's Meeting Scheduled tab filters on {@code workflowStage}, and the closure screens read
 * {@code conciliationOutcome}. Writing the meeting without the mirror is how a meeting gets scheduled that
 * the dashboard never shows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CepcConciliationService {

    private static final Set<String> STATUSES =
            Set.of("SCHEDULED", "RESCHEDULED", "COMPLETED", "CANCELLED");

    /** What the dashboard's Meeting Scheduled tab filters on. */
    private static final String STAGE_MEETING_SCHEDULED = "MEETING_SCHEDULED";

    /**
     * The statuses a meeting may move to {@code MEETING_SCHEDULED}, in every spelling.
     *
     * <p>Only the dealing officer's own working status, plus rescheduling an existing meeting. Scheduling a
     * meeting on a complaint that has moved up the ladder — sitting with the reviewer, or already settled —
     * must not drag its status back down; the DO's screen hides the tab in those states, and the server
     * should not do through the endpoint what the screen refuses to offer.
     */
    private static final Set<String> STATUSES_OPEN_TO_SCHEDULING = java.util.stream.Stream.of(
                    CepcStatus.NEW_COMPLAINT, CepcStatus.MEETING_SCHEDULED)
            .flatMap(s -> CepcStatus.allSpellings(s).stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final int MAX_COMMENT = 4000;

    /** The form offers exactly six Entity Name dropdowns; more than that is a client bug, not real data. */
    private static final int MAX_OTHER_ENTITIES = 6;

    private static final DateTimeFormatter WALL_CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final CepcConciliationMeetingRepository meetingRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintService complaintService;
    private final CepcAuditService cepcAuditService;
    private final CepcContactPersonRepository contactPersonRepository;

    public static class ComplaintNotFoundException extends RuntimeException {
        public ComplaintNotFoundException(String message) {
            super(message);
        }
    }

    /** The contact-person id named in a per-contact-person conciliation call does not exist. */
    public static class ContactPersonNotFoundException extends RuntimeException {
        public ContactPersonNotFoundException(String message) {
            super(message);
        }
    }

    public static class NotEditableException extends RuntimeException {
        public NotEditableException(String message) {
            super(message);
        }
    }

    /** Thrown for a body the form itself would have refused, so the client can show the reason inline. */
    public static class InvalidMeetingException extends RuntimeException {
        public InvalidMeetingException(String message) {
            super(message);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> read(String idOrNumber) {
        Complaint complaint = resolve(idOrNumber);
        return payload(complaint.getComplaintNumber());
    }

    /**
     * Applies the dialog's body and answers with the re-read series.
     *
     * <p>Unlike the Summary tab this is a whole-form submit: the dialog always sends all eight fields, so an
     * absent key is genuinely absent rather than "leave alone", and the meeting is built from the body as
     * given.
     */
    @Transactional
    public Map<String, Object> update(String idOrNumber, Map<String, Object> body,
                                      String callerUserId, boolean admin) {
        Complaint complaint = resolve(idOrNumber);
        if (!CepcEditPolicy.canEdit(complaint, callerUserId, admin)) {
            throw new NotEditableException("This complaint is not open for you to edit.");
        }

        String status = requireStatus(body);
        LocalDate date = parseDate(body.get("meetingDate"));
        String time = parseTime(body.get("meetingTime"));
        boolean open = CepcConciliationMeeting.OPEN_STATUSES.contains(status);

        // The same two checks the dialog runs client-side. Repeated here because a meeting saved as open
        // with no date is one the dashboard will list as scheduled and nobody can attend.
        if (open && date == null) {
            throw new InvalidMeetingException("A meeting date is required while the meeting is open.");
        }
        if (open && time == null) {
            throw new InvalidMeetingException("A meeting time is required while the meeting is open.");
        }
        String minutes = boundedText(body.get("meetingComments"), "meetingComments");
        String note = boundedText(body.get("comments"), "comments");
        Boolean wantOtherEntities = boolValue(body.get("wantOtherEntities"));
        List<Map<String, Object>> otherEntities = parseOtherEntities(body.get("otherEntities"));

        String complaintNumber = complaint.getComplaintNumber();
        Optional<CepcConciliationMeeting> latest =
                meetingRepository.findFirstByComplaintNumberOrderBySequenceNoDesc(complaintNumber);

        // A reschedule is the one change that must not overwrite: the point of recording it is that the
        // original date survives to be seen in the history panel. Everything else — correcting the time,
        // adding minutes, completing or cancelling — is the officer revising the meeting in front of them.
        boolean supersede = "RESCHEDULED".equals(status)
                || latest.isEmpty()
                || !latest.get().isOpen();

        CepcConciliationMeeting meeting;
        if (supersede) {
            meeting = CepcConciliationMeeting.builder()
                    .complaintNumber(complaintNumber)
                    .sequenceNo(latest.map(m -> nextSequence(m.getSequenceNo())).orElse(1))
                    .createdBy(callerUserId)
                    .build();
        } else {
            meeting = latest.get();
        }

        meeting.setMeetingStatus(status);
        meeting.setMeetingDate(date);
        meeting.setMeetingTime(time);
        meeting.setAcceptedByComplainant(boolValue(body.get("acceptedByComplainant")));
        meeting.setAcceptedByEntity(boolValue(body.get("acceptedByEntity")));
        meeting.setConductedThroughVc(boolValue(body.get("conductedThroughVc")));
        meeting.setMeetingComments(minutes);
        meeting.setComments(note);
        meeting.setWantOtherEntities(wantOtherEntities);
        meeting.setOtherEntityIds(joinOrNull(otherEntities, "id"));
        meeting.setOtherEntityNames(joinOrNull(otherEntities, "name"));
        meeting.setUpdatedBy(callerUserId);
        meetingRepository.save(meeting);

        // Read before the mirror overwrites it, so the timeline records where the complaint came from.
        String previousStatus = complaint.getStatus();
        mirrorOntoComplaint(complaint, meeting);
        complaintRepository.save(complaint);
        recordStatusChange(complaint, meeting, previousStatus, callerUserId);

        // The stage mirrorOntoComplaint just computed, so the caller does not have to guess it. The client
        // previously assumed MEETING_SCHEDULED after every save, which is wrong for a meeting the officer
        // marked COMPLETED or CANCELLED — it left the screen claiming a meeting was still ahead.
        Map<String, Object> out = payload(complaintNumber);
        out.put("workflowStage", complaint.getWorkflowStage());
        out.put("status", complaint.getStatus());
        out.put("statusLabel", CepcStatus.label(complaint.getStatus()));
        return out;
    }

    /**
     * The Conciliation tab reached from one contact person's own detail screen:
     * {@code GET/PUT /api/v1/complaints/contact-persons/{id}/conciliation}.
     *
     * <p>A contact person's own meeting series, kept apart from the complaint-wide one {@link #read}/
     * {@link #update} serve. A complaint can carry several contact persons, each with their own thread of
     * meetings; there is nowhere for several such series to mirror onto the one {@code Complaint} row, so
     * this deliberately never touches it — see {@link #updateForContactPerson}.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> readForContactPerson(Long contactPersonId) {
        requireContactPerson(contactPersonId);
        return payloadForContactPerson(contactPersonId);
    }

    /**
     * Saves a meeting on one contact person's own series.
     *
     * <p>Same field parsing and validation as {@link #update}, deliberately duplicated rather than shared
     * through a common private method taking a discriminator: the two are already diverging (this one
     * skips {@link CepcEditPolicy#canEdit} and never calls {@link #mirrorOntoComplaint}) and forcing them
     * through one method would make the next difference a branch inside it rather than a plain read.
     */
    @Transactional
    public Map<String, Object> updateForContactPerson(Long contactPersonId, Map<String, Object> body,
                                                       String callerUserId) {
        CepcContactPerson contact = requireContactPerson(contactPersonId);

        String status = requireStatus(body);
        LocalDate date = parseDate(body.get("meetingDate"));
        String time = parseTime(body.get("meetingTime"));
        boolean open = CepcConciliationMeeting.OPEN_STATUSES.contains(status);

        if (open && date == null) {
            throw new InvalidMeetingException("A meeting date is required while the meeting is open.");
        }
        if (open && time == null) {
            throw new InvalidMeetingException("A meeting time is required while the meeting is open.");
        }
        String minutes = boundedText(body.get("meetingComments"), "meetingComments");
        String note = boundedText(body.get("comments"), "comments");
        Boolean wantOtherEntities = boolValue(body.get("wantOtherEntities"));
        List<Map<String, Object>> otherEntities = parseOtherEntities(body.get("otherEntities"));

        Optional<CepcConciliationMeeting> latest =
                meetingRepository.findFirstByContactPersonIdOrderBySequenceNoDesc(contactPersonId);

        boolean supersede = "RESCHEDULED".equals(status)
                || latest.isEmpty()
                || !latest.get().isOpen();

        CepcConciliationMeeting meeting;
        if (supersede) {
            meeting = CepcConciliationMeeting.builder()
                    .complaintNumber(contact.getComplaintNumber())
                    .contactPersonId(contactPersonId)
                    .sequenceNo(latest.map(m -> nextSequence(m.getSequenceNo())).orElse(1))
                    .createdBy(callerUserId)
                    .build();
        } else {
            meeting = latest.get();
        }

        meeting.setMeetingStatus(status);
        meeting.setMeetingDate(date);
        meeting.setMeetingTime(time);
        meeting.setAcceptedByComplainant(boolValue(body.get("acceptedByComplainant")));
        meeting.setAcceptedByEntity(boolValue(body.get("acceptedByEntity")));
        meeting.setConductedThroughVc(boolValue(body.get("conductedThroughVc")));
        meeting.setMeetingComments(minutes);
        meeting.setComments(note);
        meeting.setWantOtherEntities(wantOtherEntities);
        meeting.setOtherEntityIds(joinOrNull(otherEntities, "id"));
        meeting.setOtherEntityNames(joinOrNull(otherEntities, "name"));
        meeting.setUpdatedBy(callerUserId);
        meetingRepository.save(meeting);

        // No mirrorOntoComplaint and no complaintRepository.save: this contact's meeting is a private
        // record, not the complaint-wide state the shared Conciliation tab drives.
        return payloadForContactPerson(contactPersonId);
    }

    private CepcContactPerson requireContactPerson(Long contactPersonId) {
        return contactPersonRepository.findById(contactPersonId)
                .orElseThrow(() -> new ContactPersonNotFoundException(
                        "Contact person " + contactPersonId + " was not found."));
    }

    private Map<String, Object> payloadForContactPerson(Long contactPersonId) {
        List<CepcConciliationMeeting> series =
                meetingRepository.findByContactPersonIdOrderBySequenceNoAsc(contactPersonId);

        List<Map<String, Object>> history = new ArrayList<>(series.size());
        for (CepcConciliationMeeting meeting : series) {
            history.add(toDto(meeting));
        }

        CepcConciliationMeeting last = series.isEmpty() ? null : series.get(series.size() - 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", last != null && last.isOpen() ? toDto(last) : null);
        out.put("history", history);
        return out;
    }

    /**
     * Writes the timeline and audit entries for a status the mirror just changed, and only then.
     *
     * <p>A save that corrects a meeting's time or adds its minutes leaves the status alone, and recording a
     * status change for it would fill the timeline with transitions that never happened.
     */
    private void recordStatusChange(Complaint complaint, CepcConciliationMeeting meeting,
                                    String previousStatus, String callerUserId) {
        String newStatus = complaint.getStatus();
        if (java.util.Objects.equals(previousStatus, newStatus)) {
            return;
        }
        String action = meeting.isOpen() ? "SCHEDULE_CONCILIATION" : "CONCILIATION_CLOSED";
        String remarks = meeting.isOpen()
                ? "Conciliation meeting " + meeting.getMeetingStatus().toLowerCase(Locale.ROOT)
                  + " for " + meeting.getMeetingDate() + " at " + meeting.getMeetingTime() + "."
                : "Conciliation meeting " + meeting.getMeetingStatus().toLowerCase(Locale.ROOT) + ".";

        complaintService.addTimeline(
                complaint.getId(), action, callerUserId, remarks, previousStatus, newStatus);
        cepcAuditService.logActionAsync(complaint.getComplaintNumber(), action, callerUserId, null,
                remarks, Map.of("meetingStatus", meeting.getMeetingStatus()), previousStatus, newStatus);
    }

    /**
     * Copies the meeting's outcome onto the complaint the dashboard and closure screens read.
     *
     * <p>The stage is only set while a meeting is actually ahead, and cleared when the series ends. Leaving
     * it set would keep a complaint in the Meeting Scheduled tab after its conciliation was completed or
     * abandoned, which is the tab reporting work that no longer exists.
     *
     * <p>The status follows the same shape but is not the same value: the stage distinguishes how a series
     * ended ({@code CONCILIATION_COMPLETED} / {@code CONCILIATION_CANCELLED}) because the dashboard tab reads
     * it, while the status simply returns to {@code NEW_COMPLAINT} — once the meeting is behind them the
     * officer is working the complaint again, and the status chip should say so rather than name a finished
     * meeting.
     */
    private void mirrorOntoComplaint(Complaint complaint, CepcConciliationMeeting meeting) {
        complaint.setConciliationDate(meeting.getMeetingDate() == null ? null
                : meeting.getMeetingDate().atStartOfDay());
        complaint.setConciliationOutcome(outcomeOf(meeting));

        if (meeting.isOpen()) {
            complaint.setWorkflowStage(STAGE_MEETING_SCHEDULED);
            if (openToScheduling(complaint.getStatus())) {
                complaint.setStatus(CepcStatus.MEETING_SCHEDULED);
            }
        } else {
            if (STAGE_MEETING_SCHEDULED.equals(complaint.getWorkflowStage())) {
                complaint.setWorkflowStage(meeting.getMeetingStatus().equals("COMPLETED")
                        ? "CONCILIATION_COMPLETED" : "CONCILIATION_CANCELLED");
            }
            // Only reverts what this feature set. A status someone else moved the complaint to while the
            // meeting was pending is theirs, and overwriting it here would undo their transition.
            if (isMeetingScheduled(complaint.getStatus())) {
                complaint.setStatus(CepcStatus.NEW_COMPLAINT);
            }
        }
    }

    private static boolean openToScheduling(String status) {
        return status != null
                && STATUSES_OPEN_TO_SCHEDULING.contains(status.strip().toLowerCase(Locale.ROOT));
    }

    private static boolean isMeetingScheduled(String status) {
        return status != null
                && CepcStatus.allSpellings(CepcStatus.MEETING_SCHEDULED)
                        .contains(status.strip().toLowerCase(Locale.ROOT));
    }

    /**
     * The outcome as the closure screens read it.
     *
     * <p>Only a completed meeting has one, and only a completed meeting BOTH sides accepted counts as
     * settled. An unanswered acceptance is not agreement, so a meeting completed with either side still
     * null reads as not settled rather than as a settlement nobody confirmed.
     */
    private static String outcomeOf(CepcConciliationMeeting meeting) {
        if (!"COMPLETED".equals(meeting.getMeetingStatus())) {
            return null;
        }
        return Boolean.TRUE.equals(meeting.getAcceptedByComplainant())
                && Boolean.TRUE.equals(meeting.getAcceptedByEntity())
                ? "SETTLED" : "NOT_SETTLED";
    }

    private Map<String, Object> payload(String complaintNumber) {
        List<CepcConciliationMeeting> series =
                meetingRepository.findByComplaintNumberOrderBySequenceNoAsc(complaintNumber);

        List<Map<String, Object>> history = new ArrayList<>(series.size());
        for (CepcConciliationMeeting meeting : series) {
            history.add(toDto(meeting));
        }

        // The live meeting is the last one in the series, and only if it has not run its course. The client
        // filters it back out of the history it renders, so it appears in both lists here by design.
        CepcConciliationMeeting last = series.isEmpty() ? null : series.get(series.size() - 1);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("current", last != null && last.isOpen() ? toDto(last) : null);
        out.put("history", history);
        return out;
    }

    private static Map<String, Object> toDto(CepcConciliationMeeting m) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", m.getId());
        dto.put("sequenceNo", m.getSequenceNo());
        dto.put("meetingStatus", m.getMeetingStatus());
        dto.put("meetingDate", m.getMeetingDate() == null ? null : m.getMeetingDate().toString());
        dto.put("meetingTime", m.getMeetingTime());
        dto.put("acceptedByComplainant", m.getAcceptedByComplainant());
        dto.put("acceptedByEntity", m.getAcceptedByEntity());
        dto.put("conductedThroughVc", m.getConductedThroughVc());
        dto.put("meetingComments", m.getMeetingComments());
        dto.put("comments", m.getComments());
        dto.put("wantOtherEntities", m.getWantOtherEntities());
        dto.put("otherEntities", otherEntitiesOf(m.getOtherEntityIds(), m.getOtherEntityNames()));
        dto.put("createdBy", m.getCreatedBy());
        dto.put("createdAt", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
        dto.put("updatedBy", m.getUpdatedBy());
        dto.put("updatedAt", m.getUpdatedAt() == null ? null : m.getUpdatedAt().toString());
        return dto;
    }

    private Complaint resolve(String idOrNumber) {
        if (idOrNumber == null || idOrNumber.isBlank()) {
            throw new ComplaintNotFoundException("No complaint was named.");
        }
        String key = idOrNumber.trim();
        Optional<Complaint> found = key.chars().allMatch(Character::isDigit)
                ? complaintRepository.findById(Long.valueOf(key))
                : complaintRepository.findByComplaintNumber(key);
        return found.orElseThrow(() ->
                new ComplaintNotFoundException("No complaint found for " + key + "."));
    }

    private static int nextSequence(Integer current) {
        return current == null ? 1 : current + 1;
    }

    /**
     * The status, refused rather than defaulted when it is not one of the four.
     *
     * <p>Defaulting an unrecognised status to SCHEDULED would silently reopen a meeting the officer was
     * trying to cancel, and the mirror above would put the complaint back in the Meeting Scheduled tab.
     */
    private static String requireStatus(Map<String, Object> body) {
        Object raw = body == null ? null : body.get("meetingStatus");
        String status = raw == null ? "" : raw.toString().trim().toUpperCase();
        if (!STATUSES.contains(status)) {
            throw new InvalidMeetingException(
                    "The meeting status must be one of SCHEDULED, RESCHEDULED, COMPLETED or CANCELLED.");
        }
        return status;
    }

    private static String boundedText(Object raw, String field) {
        String text = raw == null ? null : raw.toString().trim();
        if (text == null || text.isEmpty()) {
            return null;
        }
        if (text.length() > MAX_COMMENT) {
            throw new InvalidMeetingException("The " + field + " field may be at most 4000 characters.");
        }
        return text;
    }

    /**
     * The dialog's {@code otherEntities} array, {@code [{id, name}, ...]}, validated against the six the
     * form offers. A row missing either half is dropped rather than rejected — {@code selectOtherEntity}
     * always sets both together, so a partial row only happens if a slot was left untouched.
     */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> parseOtherEntities(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get("id");
                Object name = map.get("name");
                if (id != null && name != null && !name.toString().isBlank()) {
                    out.add(Map.of("id", id, "name", name));
                }
            }
        }
        if (out.size() > MAX_OTHER_ENTITIES) {
            throw new InvalidMeetingException(
                    "At most " + MAX_OTHER_ENTITIES + " other entities may be added.");
        }
        return out;
    }

    /** Comma-joins the named field across the picks, or null for an empty list rather than "". */
    private static String joinOrNull(List<Map<String, Object>> entities, String key) {
        if (entities.isEmpty()) {
            return null;
        }
        return entities.stream().map(e -> String.valueOf(e.get(key))).collect(Collectors.joining(","));
    }

    /** The inverse of {@link #joinOrNull}: pairs the two comma lists back into {@code [{id, name}, ...]}. */
    private static List<Map<String, Object>> otherEntitiesOf(String ids, String names) {
        if (ids == null || ids.isBlank()) {
            return List.of();
        }
        String[] idParts = ids.split(",");
        String[] nameParts = names == null ? new String[0] : names.split(",");
        List<Map<String, Object>> out = new ArrayList<>(idParts.length);
        for (int i = 0; i < idParts.length; i++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", Long.valueOf(idParts[i].trim()));
            entry.put("name", i < nameParts.length ? nameParts[i].trim() : null);
            out.add(entry);
        }
        return out;
    }

    private static Boolean boolValue(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Boolean b) {
            return b;
        }
        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    /** Accepts {@code yyyy-MM-dd} or a full ISO timestamp, which is what a date input round-trips as. */
    private static LocalDate parseDate(Object raw) {
        String text = raw == null ? null : raw.toString().trim();
        if (text == null || text.isEmpty()) {
            return null;
        }
        String candidate = text.length() > 10 && (text.charAt(10) == 'T' || text.charAt(10) == ' ')
                ? text.substring(0, 10) : text;
        try {
            return LocalDate.parse(candidate);
        } catch (Exception e) {
            throw new InvalidMeetingException("The meeting date could not be read: " + text);
        }
    }

    /**
     * Normalises the time to {@code HH:mm}.
     *
     * <p>Parsed rather than stored as sent so that the history panel cannot end up mixing {@code 14:30},
     * {@code 14:30:00} and {@code 2:30 PM} in one list, which is what happens when a wall-clock string is
     * taken on trust from whatever widget produced it.
     */
    private static String parseTime(Object raw) {
        String text = raw == null ? null : raw.toString().trim();
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            return java.time.LocalTime.parse(text.length() > 5 ? text.substring(0, 5) : text)
                    .format(WALL_CLOCK);
        } catch (Exception e) {
            throw new InvalidMeetingException("The meeting time could not be read: " + text);
        }
    }
}
