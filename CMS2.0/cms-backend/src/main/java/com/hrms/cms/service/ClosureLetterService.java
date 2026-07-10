package com.hrms.cms.service;

import com.hrms.cms.entity.CommunicationTemplate;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClosureLetterService {

    private final ComplaintRepository complaintRepository;
    private final CommunicationTemplateService templateService;

    public byte[] generateClosureLetter(String complaintNumber, String schemeVersion) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintNumber));

        List<CommunicationTemplate> templates = templateService.getForScheme("CLOSURE", schemeVersion);
        if (templates.isEmpty()) {
            throw new IllegalStateException("No closure template found for scheme: " + schemeVersion);
        }

        CommunicationTemplate template = templates.get(0);
        Map<String, String> variables = buildVariables(complaint);
        String renderedBody = templateService.renderBody(template, variables);
        String renderedSubject = templateService.renderSubject(template, variables);

        return generatePdf(renderedSubject, renderedBody, complaint);
    }

    public byte[] generateClosureLetterPreview(String complaintNumber, Long templateId) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new IllegalArgumentException("Complaint not found: " + complaintNumber));
        CommunicationTemplate template = templateService.getById(templateId);
        Map<String, String> variables = buildVariables(complaint);
        String renderedBody = templateService.renderBody(template, variables);
        String renderedSubject = templateService.renderSubject(template, variables);
        return generatePdf(renderedSubject, renderedBody, complaint);
    }

    private Map<String, String> buildVariables(Complaint complaint) {
        Map<String, String> vars = new HashMap<>();
        vars.put("complaintNumber", nullSafe(complaint.getComplaintNumber()));
        vars.put("complainantName", nullSafe(complaint.getComplainantName()));
        vars.put("complainantAddress", nullSafe(complaint.getComplainantAddress()));
        vars.put("subject", nullSafe(complaint.getSubject()));
        vars.put("bankName", complaint.getBankId() != null ? String.valueOf(complaint.getBankId()) : "N/A");
        vars.put("currentDate", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        vars.put("closureDate", LocalDate.now().format(DateTimeFormatter.ofPattern("dd-MM-yyyy")));
        vars.put("status", nullSafe(complaint.getStatus()));
        return vars;
    }

    private byte[] generatePdf(String subject, String body, Complaint complaint) {
        // HTML-based PDF generation (can be replaced with iText/OpenPDF later)
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
        writer.println("</style></head><body>");
        writer.println("<div class='header'>");
        writer.println("<h2>COMPLAINT RESOLUTION &amp; PROCESS CELL</h2>");
        writer.println("<p>Closure Communication</p></div>");
        writer.println("<div class='ref-line'><strong>Ref No:</strong> " + complaint.getComplaintNumber() + "</div>");
        writer.println("<div class='ref-line'><strong>Date:</strong> " + LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "</div>");
        writer.println("<div class='ref-line'><strong>To:</strong> " + nullSafe(complaint.getComplainantName()) + "</div>");
        writer.println("<div class='ref-line'><strong>Subject:</strong> " + subject + "</div>");
        writer.println("<div class='body-content'>" + body + "</div>");
        writer.println("<div class='footer'><p>Yours faithfully,</p><p>CRPC Cell</p></div>");
        writer.println("</body></html>");
        writer.flush();
        return baos.toByteArray();
    }

    private String nullSafe(String value) {
        return value != null ? value : "";
    }
}
