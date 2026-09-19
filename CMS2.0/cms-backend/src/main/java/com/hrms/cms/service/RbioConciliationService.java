package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ConciliationMeeting;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ConciliationMeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Reads and writes the RBIO Conciliation tab.
 * <p>
 * A complaint accumulates CONCILIATION_MEETINGS rows: the newest is the live meeting, the earlier
 * ones are the reschedule trail. A save updates the live row in place unless that row is already
 * closed, or the officer picked RESCHEDULED - either of which opens a new row so the previous
 * meeting's date and minutes survive.
 * <p>
 * COMPLAINTS.CONCILIATION_DATE and WORKFLOW_STAGE are mirrored on every live save because
 * {@code RbioWorkflowService} and the dashboard read those and not this table. The outcome columns
 * are left alone: CONCILIATION_SUCCESS/FAILED are workflow transitions, not tab edits.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioConciliationService {

    private final ComplaintRepository complaintRepository;
    private final ConciliationMeetingRepository meetingRepository;
    private final ComplaintService complaintService;
    private final CepcAuditService auditService;

    private static final Set<String> FIELD_KEYS = Set.of(
            "meetingStatus", "meetingDate", "meetingTime", "acceptedByComplainant",
            "acceptedByEntity", "conductedThroughVc", "meetingComments", "comments");

    /** A live row is still editable in place. */
    private static final Set<String> LIVE_STATUSES = Set.of("SCHEDULED", "RESCHEDULED");

    /** A closed row is frozen; the next save opens a new meeting. */
    private static final Set<String> CLOSED_STATUSES = Set.of("COMPLETED", "CANCELLED");

    private static final Pattern HH_MM = Pattern.compile("([01]\\d|2[0-3]):[0-5]\\d");

    private static final int COMMENT_MAX = 4000;

    // ---------------------------------------------------------------- read

    @Transactional(readOnly = true)
    public Map<String, Object> getConciliation(Long complaintId) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        List<ConciliationMeeting> history = meetingRepository.findByComplaintIdOrderByIdAsc(complaintId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("complaintId", c.getId());
        out.put("complaintNumber", c.getComplaintNumber());
        out.put("current", history.isEmpty() ? null : toMap(history.get(history.size() - 1)));
        out.put("history", history.stream().map(RbioConciliationService::toMap).toList());
        return out;
    }

    private static Map<String, Object> toMap(ConciliationMeeting m) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", m.getId());
        out.put("meetingStatus", m.getMeetingStatus());
        out.put("meetingDate", m.getMeetingDate() != null ? m.getMeetingDate().toString() : null);
        out.put("meetingTime", m.getMeetingTime());
        out.put("acceptedByComplainant", RbioComplaintSummaryService.yesNo(m.getAcceptedByComplainant()));
        out.put("acceptedByEntity", RbioComplaintSummaryService.yesNo(m.getAcceptedByEntity()));
        out.put("conductedThroughVc", RbioComplaintSummaryService.yesNo(m.getConductedThroughVc()));
        out.put("meetingComments", m.getMeetingComments());
        out.put("comments", m.getComments());
        out.put("createdBy", m.getCreatedBy());
        out.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : null);
        out.put("updatedBy", m.getUpdatedBy());
        out.put("updatedAt", m.getUpdatedAt() != null ? m.getUpdatedAt().toString() : null);
        return out;
    }

    // ---------------------------------------------------------------- write

    /**
     * Apply the Conciliation panel's edits. Takes the flat shape {@code current} is returned in; a
     * field that is absent is left untouched, while a field present with a {@code null} value is
     * cleared. That distinction is why this takes a Map rather than a typed DTO.
     */
    @Transactional
    @CacheEvict(value = "dashboard", allEntries = true)
    public Map<String, Object> saveMeeting(Long complaintId, Map<String, Object> payload, String actor) {
        Complaint c = complaintRepository.findById(complaintId)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintId));

        validateKeys(payload);

        // Resolved up front so an unusable status fails before anything is written.
        String incomingStatus = payload.containsKey("meetingStatus") ? status(payload.get("meetingStatus")) : null;

        ConciliationMeeting latest = meetingRepository.findFirstByComplaintIdOrderByIdDesc(complaintId).orElse(null);
        boolean openNewRow = latest == null
                || CLOSED_STATUSES.contains(latest.getMeetingStatus())
                || "RESCHEDULED".equals(incomingStatus);

        ConciliationMeeting target = openNewRow
                ? ConciliationMeeting.builder().complaintId(complaintId).createdBy(actor).build()
                : latest;

        setIfPresent(payload, "meetingStatus", v -> target.setMeetingStatus(status(v)));
        setIfPresent(payload, "meetingDate", v -> target.setMeetingDate(date(v)));
        setIfPresent(payload, "meetingTime", v -> target.setMeetingTime(time(v)));
        setIfPresent(payload, "acceptedByComplainant",
                v -> target.setAcceptedByComplainant(RbioComplaintSummaryService.toYesNo(v, "acceptedByComplainant")));
        setIfPresent(payload, "acceptedByEntity",
                v -> target.setAcceptedByEntity(RbioComplaintSummaryService.toYesNo(v, "acceptedByEntity")));
        setIfPresent(payload, "conductedThroughVc",
                v -> target.setConductedThroughVc(RbioComplaintSummaryService.toYesNo(v, "conductedThroughVc")));
        setIfPresent(payload, "meetingComments", v -> target.setMeetingComments(text(v, "meetingComments")));
        setIfPresent(payload, "comments", v -> target.setComments(text(v, "comments")));

        if (target.getMeetingStatus() == null) target.setMeetingStatus("SCHEDULED");

        boolean live = LIVE_STATUSES.contains(target.getMeetingStatus());
        if (live && (target.getMeetingDate() == null || target.getMeetingTime() == null)) {
            throw new IllegalArgumentException(
                    "Fields 'meetingDate' and 'meetingTime' are required while the meeting is "
                            + target.getMeetingStatus());
        }

        target.setUpdatedBy(actor);
        ConciliationMeeting saved = meetingRepository.save(target);

        if (live) {
            c.setConciliationDate(LocalDateTime.of(saved.getMeetingDate(), LocalTime.parse(saved.getMeetingTime())));
            c.setWorkflowStage("MEETING_SCHEDULED");
            complaintRepository.save(c);
        }

        String action = "rbio_conciliation_" + saved.getMeetingStatus().toLowerCase();
        complaintService.addTimeline(complaintId, action, actor,
                "Conciliation meeting " + saved.getMeetingStatus().toLowerCase()
                        + (saved.getMeetingDate() != null ? " for " + saved.getMeetingDate() : ""),
                c.getStatus(), c.getStatus());
        auditService.logAction(c.getComplaintNumber(), action.toUpperCase(), actor, "RBIO",
                "Conciliation meeting details saved from the RBIO portal",
                Map.of("complaintId", complaintId, "meetingId", saved.getId(),
                        "fields", payload.keySet(), "newMeeting", openNewRow));

        log.info("Conciliation meeting {} on complaint {} is now {} (newRow={})",
                saved.getId(), complaintId, saved.getMeetingStatus(), openNewRow);

        return getConciliation(complaintId);
    }

    private static void validateKeys(Map<String, Object> payload) {
        for (String key : payload.keySet()) {
            if (!FIELD_KEYS.contains(key) && !"id".equals(key) && !"complaintId".equals(key)) {
                throw new IllegalArgumentException("Unknown field: " + key);
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Absent key = leave alone; present key with a null value = clear. */
    private static void setIfPresent(Map<String, Object> src, String key, Consumer<Object> setter) {
        if (src.containsKey(key)) setter.accept(src.get(key));
    }

    private static String status(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim().toUpperCase();
        if (s.isEmpty()) return null;
        if (!LIVE_STATUSES.contains(s) && !CLOSED_STATUSES.contains(s)) {
            throw new IllegalArgumentException(
                    "Field 'meetingStatus' must be one of SCHEDULED, RESCHEDULED, COMPLETED, CANCELLED");
        }
        return s;
    }

    private static LocalDate date(Object v) {
        if (v == null) return null;
        try {
            return LocalDate.parse(String.valueOf(v).substring(0, 10));
        } catch (DateTimeParseException | StringIndexOutOfBoundsException ex) {
            throw new IllegalArgumentException("Field 'meetingDate' must be an ISO date (yyyy-MM-dd)");
        }
    }

    /** Stored as HH:mm because Oracle has no TIME type; seconds, when sent, are dropped. */
    private static String time(Object v) {
        if (v == null) return null;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return null;
        if (s.length() > 5) s = s.substring(0, 5);
        if (!HH_MM.matcher(s).matches()) {
            throw new IllegalArgumentException("Field 'meetingTime' must be a 24-hour time (HH:mm)");
        }
        return s;
    }

    private static String text(Object v, String field) {
        if (v == null) return null;
        if (!(v instanceof String s)) {
            throw new IllegalArgumentException("Field '" + field + "' must be a string");
        }
        if (s.length() > COMMENT_MAX) {
            throw new IllegalArgumentException("Field '" + field + "' must be at most " + COMMENT_MAX + " characters");
        }
        return s;
    }
}
