package com.hrms.cms.service;

import com.hrms.cms.entity.CommunicationTemplate;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RbioMeeting;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the Minutes of Meeting letter (UST500, 644, 647, 650).
 *
 * <p><b>Built on the shared {@link CommunicationTemplateService} pipeline, deliberately.</b> There are already
 * four browser-side letter generators in this codebase (jsPDF blocks in the task-action, file-complaint,
 * draft-assessment and complaint-tracker components) plus a hardcoded HTML "sample closure letter" with
 * {@code [Complainant Name]} placeholders that are never filled. A fifth would be a fifth place for the
 * Scheme's name to drift. This mirrors {@link ClosureLetterService} exactly, so the template is editable by
 * RBI rather than compiled in.
 *
 * <p><b>Placeholders must be double-braced.</b> {@code CommunicationTemplateService} compiles
 * {@code Pattern "\{\{(\w+)}}"}. The legacy Oracle seed rows use single braces and therefore never substitute
 * at all — a letter built that way reaches its reader with a literal {@code {complaintNumber}} in it.
 *
 * <p><b>This letter is not digitally signed</b>, for the same reason {@code ClosureLetterService} records: no
 * certificate authority or signing key exists in this system. The officer prints it, signs it by hand, scans
 * it, and uploads the scan — which is what UST501 actually describes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MinutesOfMeetingLetterService {

    /** The trigger condition of the template seeded by migration V95/V93. */
    private static final String TRIGGER = "MEETING_MINUTES";

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final ComplaintRepository complaintRepository;
    private final CommunicationTemplateService templateService;
    private final RbioMeetingService meetingService;

    /**
     * The MOM letter for a complaint's most recent COMPLETED meeting.
     *
     * <p>Refuses when no meeting has been completed. A MOM is the record of what the parties agreed; rendering
     * one from a merely SCHEDULED meeting would produce a document asserting minutes for a meeting that has
     * not happened.
     */
    public byte[] generate(String complaintNumber, String schemeVersion) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Complaint not found: " + complaintNumber));

        RbioMeeting meeting = meetingService.latestCompleted(complaintNumber)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "rbio.meeting.error.no_completed_meeting: no completed meeting exists for "
                                + complaintNumber + ", so there are no minutes to issue."));

        List<CommunicationTemplate> templates = templateService.getForScheme(TRIGGER, schemeVersion);
        if (templates.isEmpty()) {
            // Fails closed rather than falling back to a compiled-in letter body. A letter assembled from a
            // hardcoded copy of a template is exactly how the Scheme-year drift defect reached citizens.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "rbio.meeting.error.template_missing: no Minutes of Meeting template is configured for "
                            + "scheme " + schemeVersion + ". Please retry once it is configured.");
        }

        CommunicationTemplate template = templates.get(0);
        Map<String, String> variables = buildVariables(complaint, meeting);
        String body = templateService.renderBody(template, variables);
        String subject = templateService.renderSubject(template, variables);

        return render(subject, body, complaint, meeting);
    }

    private Map<String, String> buildVariables(Complaint complaint, RbioMeeting meeting) {
        Map<String, String> vars = new HashMap<>();
        vars.put("complaintNumber", nullSafe(complaint.getComplaintNumber()));
        vars.put("complainantName", nullSafe(complaint.getComplainantName()));
        vars.put("entityName", nullSafe(complaint.getEntityCode()));
        vars.put("meetingDate", meeting.getMeetingDate() != null
                ? meeting.getMeetingDate().format(DISPLAY_DATE) : "");
        vars.put("meetingTime", nullSafe(meeting.getMeetingTime()));
        vars.put("meetingMode", nullSafe(meeting.getMeetingMode()));
        vars.put("meetingVenue", nullSafe(meeting.getMeetingVenue()));
        vars.put("participants", nullSafe(meeting.getParticipants()));
        vars.put("minutesOfMeeting", nullSafe(meeting.getMinutesOfMeeting()));

        // Rendered as the words an officer wrote down, not as a raw flag. "Y" in a letter a party may read is
        // not an answer to "did the entity accept the settlement".
        String accepted = meeting.getEntityAccepted();
        vars.put("entityAccepted", "Y".equalsIgnoreCase(accepted) ? "Yes"
                : "N".equalsIgnoreCase(accepted) ? "No" : "Not recorded");

        // Only CONFIRMED attendees. An invitee list would have the letter assert attendance nobody confirmed.
        List<String> confirmed = meetingService.confirmedParticipantNames(complaint.getComplaintNumber());
        vars.put("confirmedParticipants", confirmed.isEmpty() ? "None recorded" : String.join(", ", confirmed));

        vars.put("performedBy", nullSafe(meeting.getPerformedBy()));
        vars.put("performedByRole", nullSafe(meeting.getPerformedByRole()));
        vars.put("recordedAt", meeting.getPerformedAt() != null
                ? meeting.getPerformedAt().toLocalDate().format(DISPLAY_DATE) : "");
        vars.put("currentDate", LocalDate.now().format(DISPLAY_DATE));
        return vars;
    }

    private byte[] render(String subject, String body, Complaint complaint, RbioMeeting meeting) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter writer = new PrintWriter(baos);
        writer.println("<!DOCTYPE html><html><head><meta charset='UTF-8'>");
        writer.println("<style>");
        writer.println("body { font-family: 'Times New Roman', serif; margin: 40px; line-height: 1.6; }");
        writer.println(".header { text-align: center; margin-bottom: 30px; }");
        writer.println(".header h2 { margin: 5px 0; }");
        writer.println(".ref-line { margin: 10px 0; }");
        writer.println(".body-content { margin: 20px 0; white-space: pre-wrap; }");
        writer.println(".footer { margin-top: 40px; }");
        writer.println(".sign-block { margin-top: 60px; border-top: 1px solid #000; width: 260px; padding-top: 6px; }");
        writer.println("</style></head><body>");
        writer.println("<div class='header'>");
        writer.println("<h2>OFFICE OF THE RBI OMBUDSMAN</h2>");
        writer.println("<p>Minutes of Conciliation Meeting</p></div>");
        writer.println("<div class='ref-line'><strong>Ref No:</strong> " + nullSafe(complaint.getComplaintNumber()) + "</div>");
        writer.println("<div class='ref-line'><strong>Meeting No:</strong> "
                + (meeting.getSequenceNo() == null ? "" : meeting.getSequenceNo()) + "</div>");
        writer.println("<div class='ref-line'><strong>Date of issue:</strong> "
                + LocalDate.now().format(DISPLAY_DATE) + "</div>");
        writer.println("<div class='ref-line'><strong>Subject:</strong> " + nullSafe(subject) + "</div>");
        writer.println("<div class='body-content'>" + nullSafe(body) + "</div>");

        // A hand-signature block, because UST501 has the officer print, sign, scan and upload this letter.
        writer.println("<div class='footer'>");
        writer.println("<div class='sign-block'>Signature</div>");
        writer.println("<p>" + nullSafe(meeting.getPerformedBy()) + "<br/>"
                + nullSafe(meeting.getPerformedByRole()) + "</p>");
        // Same statement ClosureLetterService makes, and for the same reason: there is no signing key in this
        // system, so claiming a digital signature would assert a guarantee it cannot provide.
        writer.println("<p style='margin-top:10px;font-size:10px;color:#888;'>"
                + "System-generated on "
                + java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss"))
                + ". This is not a digitally signed document. Print, sign and upload the scanned copy.</p>");
        writer.println("</div>");
        writer.println("</body></html>");
        writer.flush();
        return baos.toByteArray();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
