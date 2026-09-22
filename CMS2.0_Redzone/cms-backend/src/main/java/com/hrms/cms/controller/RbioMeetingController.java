package com.hrms.cms.controller;

import com.hrms.cms.security.RbioIdentityResolver;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.MinutesOfMeetingLetterService;
import com.hrms.cms.service.RbioMeetingService;
import com.hrms.cms.service.RbioRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conciliation meeting reads, participants and the Minutes of Meeting letter (UST496-503, 643-651).
 *
 * <p>The meeting EVENTS themselves (schedule, reschedule, complete) are not posted here — they go through the
 * workflow action endpoint as {@code SCHEDULE_MEETING} / {@code RESCHEDULE_MEETING} / {@code COMPLETE_MEETING},
 * so they pick up the transition table's authorisation, the timeline row, the audit entry and the
 * notifications that every other RBIO action gets. A second write path would have to reproduce all of that,
 * and would drift.
 *
 * <p>What lives here is everything the meeting screen needs that is NOT a state transition: the history
 * (including superseded rows, which is the point of UST502), the participant list and its six-cap, and the
 * letter.
 *
 * <p><b>Authorisation.</b> Reads admit every RBIO role. Writes admit the four roles UST643/646/649 name —
 * Dealing Official, Reviewer, Deputy Ombudsman, Ombudsman — plus the Conciliator, whose whole function is
 * conciliation meetings, plus the legacy equivalents so live sessions keep working. Enforced by the guard,
 * not by hiding controls: the previous screen gated on {@code RBIO_CONCILIATOR} in the browser only, which
 * simultaneously locked out three roles that were authorised and protected nothing against a direct POST.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class RbioMeetingController {

    // Role lists are written out at each @RbioRoleGuard rather than held in a shared String[]: an annotation
    // member must be a constant EXPRESSION, and a reference to a static final array is not one.

    private final RbioMeetingService meetingService;
    private final MinutesOfMeetingLetterService momLetterService;
    private final RbioIdentityResolver identityResolver;

    // ═══════════════════════════ Reads ═══════════════════════════

    /**
     * The full meeting history, oldest first, INCLUDING superseded rows.
     *
     * <p>Superseded rows are returned deliberately — UST502 requires the previous meeting details to remain
     * visible after a reschedule. The frontend previously read {@code complaint.hearingHistory}, a field no
     * server response has ever set, so the history block was permanently empty.
     */
    @GetMapping("/{complaintNumber}/meetings")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> meetingHistory(@PathVariable String complaintNumber) {
        List<Map<String, Object>> history = meetingService.history(complaintNumber);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("current", meetingService.current(complaintNumber));
        meta.put("count", history.size());
        return buildResponse(true, "Meeting history retrieved", meta, history);
    }

    /** The operative meeting, or null when none is scheduled. */
    @GetMapping("/{complaintNumber}/meetings/current")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> currentMeeting(@PathVariable String complaintNumber) {
        return ResponseEntity.ok(envelope(true, "Current meeting retrieved",
                meetingService.current(complaintNumber)));
    }

    // ═══════════════════════════ Participants (UST498) ═══════════════════════════

    @GetMapping("/{complaintNumber}/meetings/participants")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> listParticipants(@PathVariable String complaintNumber) {
        List<Map<String, Object>> participants = meetingService.participants(complaintNumber);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("confirmed", meetingService.confirmedParticipantNames(complaintNumber));
        return buildResponse(true, "Participants retrieved", meta, participants);
    }

    /**
     * Adds an additional participant, refusing a seventh ENTITY.
     *
     * <p>The cap is {@code RbioAdditionalEntityService}'s, counted from persisted rows — so it holds for a
     * direct POST, not merely for the screen that disables its own button at six.
     */
    @PostMapping("/{complaintNumber}/meetings/participants")
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.CONCILIATOR, RbioRoles.OFFICER, RbioRoles.SUPERVISOR,
            RbioRoles.ADMIN})
    public ResponseEntity<Map<String, Object>> addParticipant(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> saved = meetingService.addParticipant(
                complaintNumber, request, identityResolver.resolveActor());
        return ResponseEntity.ok(envelope(true, "Participant added", saved));
    }

    /** Records whether a participant actually attended — what the MOM letter's attendee list reads. */
    @PutMapping("/{complaintNumber}/meetings/participants/{participantId}/confirm")
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.CONCILIATOR, RbioRoles.OFFICER, RbioRoles.SUPERVISOR,
            RbioRoles.ADMIN})
    public ResponseEntity<Map<String, Object>> confirmParticipant(
            @PathVariable String complaintNumber,
            @PathVariable Long participantId,
            @RequestBody(required = false) Map<String, Object> request) {
        boolean confirmed = request == null
                || !"false".equalsIgnoreCase(String.valueOf(request.getOrDefault("confirmed", "true")));
        Map<String, Object> saved = meetingService.confirmParticipant(complaintNumber, participantId, confirmed);
        return ResponseEntity.ok(envelope(true, "Participant updated", saved));
    }

    @DeleteMapping("/{complaintNumber}/meetings/participants/{participantId}")
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.CONCILIATOR, RbioRoles.OFFICER, RbioRoles.SUPERVISOR,
            RbioRoles.ADMIN})
    public ResponseEntity<Map<String, Object>> removeParticipant(
            @PathVariable String complaintNumber,
            @PathVariable Long participantId) {
        meetingService.removeParticipant(complaintNumber, participantId);
        return ResponseEntity.ok(envelope(true, "Participant removed", null));
    }

    // ═══════════════════════════ The MOM letter (UST500) ═══════════════════════════

    /**
     * The Minutes of Meeting letter, as printable HTML.
     *
     * <p>{@code TEXT_HTML} with a {@code Content-Disposition} attachment, matching the closure-letter endpoint
     * exactly — the officer prints it, signs it, scans it and uploads the scan via {@code /api/files/upload}
     * with {@code documentType=MEETING_MINUTES} (UST501).
     *
     * <p>Server-rendered rather than built in the browser, because the template is RBI-editable data. There
     * are already four jsPDF letter builders in the frontend and each is a separate place for the Scheme's
     * name to drift.
     */
    @GetMapping("/{complaintNumber}/meetings/mom-letter")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<byte[]> momLetter(
            @PathVariable String complaintNumber,
            @RequestParam(defaultValue = "RBIOS_2021") String schemeVersion) {
        byte[] letter = momLetterService.generate(complaintNumber, schemeVersion);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=mom-" + complaintNumber + ".html")
                .body(letter);
    }

    // ═══════════════════════════ Envelope ═══════════════════════════

    /** The envelope every other controller in this tree returns, and which json_field.py unwraps. */
    private static Map<String, Object> envelope(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private static ResponseEntity<Map<String, Object>> buildResponse(
            boolean success, String message, Map<String, Object> meta, Object listData) {
        Map<String, Object> response = envelope(success, message, listData);
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }
}
