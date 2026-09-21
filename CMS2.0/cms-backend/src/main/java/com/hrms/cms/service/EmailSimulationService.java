package com.hrms.cms.service;

import com.hrms.cms.dto.EmailReplyWithFormRequest;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.dto.simulation.EmailStatsResponse;
import com.hrms.cms.dto.simulation.EmailThreadResponse;
import com.hrms.cms.dto.simulation.EmailThreadSummary;
import com.hrms.cms.dto.simulation.FormFieldDescriptor;
import com.hrms.cms.dto.simulation.FormTemplateResponse;
import com.hrms.cms.dto.simulation.SimulatedEmailResponse;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.ComplaintTimelineRepository;
import com.hrms.cms.entity.ComplaintTimeline;
import com.hrms.cms.repository.SimulatedEmailRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmailSimulationService {

    private static final String CMS_EMAIL = "complaints@cms.rbi.org.in";

    private final SimulatedEmailRepository emailRepository;
    private final ComplaintRepository complaintRepository;
    private final ComplaintTimelineRepository timelineRepository;

    @CacheEvict(value = "email-stats", allEntries = true)
    @Transactional
    public EmailThreadResponse receiveEmail(IncomingEmailRequest request) {
        String threadId = UUID.randomUUID().toString();
        String complaintNumber = generateComplaintNumber();

        SimulatedEmail inbound = SimulatedEmail.builder()
                .messageId(UUID.randomUUID().toString())
                .threadId(threadId)
                .fromEmail(request.getFromEmail())
                .toEmail(CMS_EMAIL)
                .subject(request.getSubject())
                .body(request.getBody())
                .direction("INBOUND")
                .status("PROCESSED")
                .complaintNumber(complaintNumber)
                .processedAt(LocalDateTime.now())
                .build();

        Complaint complaint = Complaint.builder()
                .complaintNumber(complaintNumber)
                .complainantName(request.getFromName() != null ? request.getFromName() : extractNameFromEmail(request.getFromEmail()))
                .complainantEmail(request.getFromEmail())
                .subject(request.getSubject())
                .description(request.getBody())
                .status("awaiting_details")
                .priority("medium")
                .filingType("email")
                .build();

        Complaint savedComplaint = complaintRepository.save(complaint);
        inbound.setComplaintId(savedComplaint.getId());
        emailRepository.save(inbound);

        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(savedComplaint.getId())
                .action("filed")
                .performedBy("Email System")
                .remarks("Complaint initiated via email from " + request.getFromEmail())
                .toStatus("awaiting_details")
                .build());

        String replyBody = buildAutoReplyBody(request.getFromName(), complaintNumber);
        SimulatedEmail outbound = SimulatedEmail.builder()
                .messageId(UUID.randomUUID().toString())
                .threadId(threadId)
                .fromEmail(CMS_EMAIL)
                .toEmail(request.getFromEmail())
                .subject("Re: " + request.getSubject() + " | Complaint #" + complaintNumber)
                .body(replyBody)
                .direction("OUTBOUND")
                .status("SENT")
                .complaintId(savedComplaint.getId())
                .complaintNumber(complaintNumber)
                .attachmentUrl("/api/email-simulation/form-template/" + complaintNumber)
                .build();
        emailRepository.save(outbound);

        return EmailThreadResponse.builder()
                .threadId(threadId)
                .complaintNumber(complaintNumber)
                .fromEmail(inbound.getFromEmail())
                .subject(inbound.getSubject())
                .status("AWAITING_FORM")
                .emails(List.of(toEmailResponse(inbound), toEmailResponse(outbound)))
                .build();
    }

    @CacheEvict(value = "email-stats", allEntries = true)
    @Transactional
    public EmailThreadResponse receiveFormReply(EmailReplyWithFormRequest request) {
        List<SimulatedEmail> threadEmails = emailRepository.findByThreadIdOrderBySentAtAsc(request.getThreadId());
        if (threadEmails.isEmpty()) {
            throw new RuntimeException("Thread not found: " + request.getThreadId());
        }

        SimulatedEmail firstEmail = threadEmails.get(0);
        String complaintNumber = firstEmail.getComplaintNumber();
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new RuntimeException("Complaint not found: " + complaintNumber));

        complaint.setComplainantName(request.getComplainantName());
        complaint.setComplainantPhone(request.getComplainantPhone());
        complaint.setComplainantAddress(request.getComplainantAddress());
        complaint.setBankId(request.getBankId());
        complaint.setBankBranch(request.getBankBranch());
        complaint.setAccountNumber(request.getAccountNumber());
        complaint.setCategoryId(request.getCategoryId());
        if (request.getDescription() != null) complaint.setDescription(request.getDescription());
        complaint.setReliefSought(request.getReliefSought());
        complaint.setStatus("pending");
        complaintRepository.save(complaint);

        timelineRepository.save(ComplaintTimeline.builder()
                .complaintId(complaint.getId())
                .action("details_received")
                .performedBy("Email System")
                .remarks("Complaint details received via email reply")
                .fromStatus("awaiting_details")
                .toStatus("pending")
                .build());

        SimulatedEmail formReply = SimulatedEmail.builder()
                .messageId(UUID.randomUUID().toString())
                .threadId(request.getThreadId())
                .fromEmail(request.getFromEmail())
                .toEmail(CMS_EMAIL)
                .subject(request.getSubject())
                .body(buildFormReplyBody(request))
                .direction("INBOUND")
                .status("PROCESSED")
                .complaintId(complaint.getId())
                .complaintNumber(complaintNumber)
                .processedAt(LocalDateTime.now())
                .build();
        emailRepository.save(formReply);

        SimulatedEmail confirmation = SimulatedEmail.builder()
                .messageId(UUID.randomUUID().toString())
                .threadId(request.getThreadId())
                .fromEmail(CMS_EMAIL)
                .toEmail(request.getFromEmail())
                .subject("Re: " + request.getSubject())
                .body(buildConfirmationBody(request.getComplainantName(), complaintNumber))
                .direction("OUTBOUND")
                .status("SENT")
                .complaintId(complaint.getId())
                .complaintNumber(complaintNumber)
                .build();
        emailRepository.save(confirmation);

        List<SimulatedEmail> allEmails = emailRepository.findByThreadIdOrderBySentAtAsc(request.getThreadId());
        return EmailThreadResponse.builder()
                .threadId(request.getThreadId())
                .complaintNumber(complaintNumber)
                .fromEmail(firstEmail.getFromEmail())
                .subject(firstEmail.getSubject())
                .status("COMPLETED")
                .emails(allEmails.stream().map(this::toEmailResponse).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<EmailThreadSummary> getAllThreads() {
        List<SimulatedEmail> allEmails = emailRepository.findByDirectionOrderBySentAtDesc("INBOUND");
        Map<String, List<SimulatedEmail>> grouped = allEmails.stream()
                .collect(Collectors.groupingBy(SimulatedEmail::getThreadId, LinkedHashMap::new, Collectors.toList()));

        List<EmailThreadSummary> threads = new ArrayList<>();
        for (Map.Entry<String, List<SimulatedEmail>> entry : grouped.entrySet()) {
            SimulatedEmail first = entry.getValue().get(0);
            List<SimulatedEmail> threadEmails = emailRepository.findByThreadIdOrderBySentAtAsc(entry.getKey());

            threads.add(toThreadSummary(entry.getKey(), first, threadEmails.size(),
                    deriveThreadStatus(threadEmails)));
        }
        return threads;
    }

    @Transactional(readOnly = true)
    public List<EmailThreadSummary> getThreadsByComplaintNumber(String complaintNumber) {
        List<SimulatedEmail> emails = emailRepository.findByComplaintNumberOrderBySentAtAsc(complaintNumber);

        Map<String, List<SimulatedEmail>> grouped = emails.stream()
                .collect(Collectors.groupingBy(SimulatedEmail::getThreadId, LinkedHashMap::new, Collectors.toList()));

        List<EmailThreadSummary> threads = new ArrayList<>();
        for (Map.Entry<String, List<SimulatedEmail>> entry : grouped.entrySet()) {
            List<SimulatedEmail> threadEmails = entry.getValue();
            SimulatedEmail first = threadEmails.stream()
                    .min(Comparator.comparing(SimulatedEmail::getSentAt))
                    .orElse(threadEmails.get(0));

            // first.getStatus() rather than deriveThreadStatus: the complaint details screen buckets
            // its email activity on the message status SENT. See EmailThreadSummary#status.
            threads.add(toThreadSummary(entry.getKey(), first, threadEmails.size(), first.getStatus()));
        }
        return threads;
    }

    @Transactional(readOnly = true)
    public EmailThreadResponse getThread(String threadId) {
        List<SimulatedEmail> emails = emailRepository.findByThreadIdOrderBySentAtAsc(threadId);
        if (emails.isEmpty()) throw new RuntimeException("Thread not found");

        SimulatedEmail first = emails.get(0);
        return EmailThreadResponse.builder()
                .threadId(threadId)
                .complaintNumber(first.getComplaintNumber())
                .fromEmail(first.getFromEmail())
                .subject(first.getSubject())
                .status(deriveThreadStatus(emails))
                .emails(emails.stream().map(this::toEmailResponse).collect(Collectors.toList()))
                .build();
    }

    @Transactional(readOnly = true)
    public List<SimulatedEmailResponse> getInbox() {
        return mapEmails("INBOUND");
    }

    @Transactional(readOnly = true)
    public List<SimulatedEmailResponse> getSent() {
        return mapEmails("OUTBOUND");
    }

    private List<SimulatedEmailResponse> mapEmails(String direction) {
        return emailRepository.findByDirectionOrderBySentAtDesc(direction).stream()
                .map(this::toEmailResponse)
                .collect(Collectors.toList());
    }

    @Cacheable(value = "email-stats")
    @Transactional(readOnly = true)
    public EmailStatsResponse getStats() {
        List<EmailThreadSummary> threads = getAllThreads();
        return EmailStatsResponse.builder()
                .totalThreads(threads.size())
                .awaitingForm(threads.stream().filter(t -> "AWAITING_FORM".equals(t.getStatus())).count())
                .completed(threads.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count())
                .build();
    }

    @Transactional(readOnly = true)
    public FormTemplateResponse getFormTemplate(String complaintNumber) {
        Complaint complaint = complaintRepository.findByComplaintNumber(complaintNumber)
                .orElseThrow(() -> new RuntimeException("Complaint not found"));

        return FormTemplateResponse.builder()
                .complaintNumber(complaintNumber)
                .complainantEmail(complaint.getComplainantEmail())
                .fields(FORM_FIELDS)
                .build();
    }

    private static final List<FormFieldDescriptor> FORM_FIELDS = List.of(
            field("complainantName", "Full Name", "text", true),
            field("complainantPhone", "Mobile Number", "tel", true),
            field("complainantAddress", "Address", "textarea", true),
            field("bankId", "Bank / Financial Institution", "select", true),
            field("bankBranch", "Branch", "text", false),
            field("accountNumber", "Account Number", "text", false),
            field("categoryId", "Complaint Category", "select", true),
            field("description", "Detailed Description", "textarea", true),
            field("reliefSought", "Relief Sought", "textarea", false)
    );

    private static FormFieldDescriptor field(String key, String label, String type, boolean required) {
        return FormFieldDescriptor.builder().key(key).label(label).type(type).required(required).build();
    }

    private String deriveThreadStatus(List<SimulatedEmail> emails) {
        long inboundCount = emails.stream().filter(e -> "INBOUND".equals(e.getDirection())).count();
        if (inboundCount >= 2) return "COMPLETED";
        return "AWAITING_FORM";
    }

    private String generateComplaintNumber() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String uuid = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        return "CMS-" + date + "-" + uuid;
    }

    private String extractNameFromEmail(String email) {
        if (email == null) return "Unknown";
        String local = email.split("@")[0];
        return local.substring(0, 1).toUpperCase() + local.substring(1).replace(".", " ");
    }

    private String buildAutoReplyBody(String name, String complaintNumber) {
        String greeting = (name != null && !name.isEmpty()) ? name : "Citizen";
        return "Dear " + greeting + ",\n\n" +
                "Thank you for reaching out to the RBI Complaint Management System.\n\n" +
                "Your complaint has been registered with reference number: " + complaintNumber + "\n\n" +
                "To complete your complaint, please fill out the required details and reply to this email " +
                "WITHOUT changing the subject line.\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n" +
                "REQUIRED INFORMATION:\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "• Full Name\n" +
                "• Mobile Number\n" +
                "• Complete Address\n" +
                "• Bank / Financial Institution Name\n" +
                "• Branch (if applicable)\n" +
                "• Account Number (if applicable)\n" +
                "• Complaint Category\n" +
                "• Detailed Description of the Issue\n" +
                "• Relief Sought\n\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "You can also download and fill the complaint form from the link below:\n" +
                "Form: /api/email-simulation/form-template/" + complaintNumber + "\n\n" +
                "IMPORTANT: Please reply to this email without changing the subject line.\n\n" +
                "Regards,\n" +
                "CMS - Complaint Management System\n" +
                "Reserve Bank of India";
    }

    private String buildFormReplyBody(EmailReplyWithFormRequest req) {
        return "COMPLAINT FORM DETAILS\n" +
                "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n" +
                "Name: " + req.getComplainantName() + "\n" +
                "Phone: " + req.getComplainantPhone() + "\n" +
                "Address: " + req.getComplainantAddress() + "\n" +
                "Bank ID: " + req.getBankId() + "\n" +
                "Branch: " + (req.getBankBranch() != null ? req.getBankBranch() : "N/A") + "\n" +
                "Account: " + (req.getAccountNumber() != null ? req.getAccountNumber() : "N/A") + "\n" +
                "Category ID: " + req.getCategoryId() + "\n" +
                "Description: " + req.getDescription() + "\n" +
                "Relief Sought: " + (req.getReliefSought() != null ? req.getReliefSought() : "N/A") + "\n";
    }

    private String buildConfirmationBody(String name, String complaintNumber) {
        return "Dear " + (name != null ? name : "Citizen") + ",\n\n" +
                "Thank you for submitting the complaint details.\n\n" +
                "Your complaint #" + complaintNumber + " is now COMPLETE and has been assigned for review.\n\n" +
                "You can track the status of your complaint at:\n" +
                "https://cms.rbi.org.in/track-complaint?ref=" + complaintNumber + "\n\n" +
                "What happens next:\n" +
                "• Your complaint will be reviewed by the assigned officer\n" +
                "• You will receive updates on any status changes\n" +
                "• Expected resolution time: 30 working days\n\n" +
                "Regards,\n" +
                "CMS - Complaint Management System\n" +
                "Reserve Bank of India";
    }

    private SimulatedEmailResponse toEmailResponse(SimulatedEmail email) {
        return SimulatedEmailResponse.builder()
                .id(email.getId())
                .messageId(email.getMessageId())
                .threadId(email.getThreadId())
                .fromEmail(email.getFromEmail())
                .toEmail(email.getToEmail())
                .subject(email.getSubject())
                .body(email.getBody())
                .direction(email.getDirection())
                .status(email.getStatus())
                .complaintNumber(email.getComplaintNumber())
                .attachmentUrl(email.getAttachmentUrl())
                .sentAt(email.getSentAt())
                .build();
    }

    private EmailThreadSummary toThreadSummary(String threadId, SimulatedEmail first, int emailCount, String status) {
        return EmailThreadSummary.builder()
                .threadId(threadId)
                .complaintNumber(first.getComplaintNumber())
                .fromEmail(first.getFromEmail())
                .subject(first.getSubject())
                .sentAt(first.getSentAt())
                .emailCount(emailCount)
                .status(status)
                .build();
    }
}
