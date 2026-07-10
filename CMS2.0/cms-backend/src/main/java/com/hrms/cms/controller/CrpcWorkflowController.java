package com.hrms.cms.controller;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EmailDraft;
import com.hrms.cms.service.ClosureLetterService;
import com.hrms.cms.service.CrpcWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/crpc/workflow")
@RequiredArgsConstructor
public class CrpcWorkflowController {

    private final CrpcWorkflowService workflowService;
    private final ClosureLetterService closureLetterService;

    @PostMapping("/send-for-approval")
    @PreAuthorize("hasAnyRole('DEO', 'CRPC_DEO')")
    public ResponseEntity<EmailDraft> sendForApproval(
            @RequestParam String draftId,
            @RequestParam(required = false) String remarks,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workflowService.sendForApproval(draftId, jwt.getSubject(), remarks));
    }

    @PostMapping("/not-a-complaint")
    @PreAuthorize("hasAnyRole('DEO', 'CRPC_DEO', 'REVIEWER', 'CRPC_REVIEWER')")
    public ResponseEntity<EmailDraft> markNotAComplaint(
            @RequestParam String draftId,
            @RequestParam String reason,
            @RequestParam(required = false) String remarks,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workflowService.markNotAComplaint(draftId, jwt.getSubject(), reason, remarks));
    }

    @PostMapping("/bulk-not-a-complaint")
    @PreAuthorize("hasAnyRole('DEO', 'CRPC_DEO', 'REVIEWER', 'CRPC_REVIEWER')")
    public ResponseEntity<List<EmailDraft>> bulkMarkNotAComplaint(
            @RequestBody Map<String, Object> request,
            @AuthenticationPrincipal Jwt jwt) {
        @SuppressWarnings("unchecked")
        List<String> draftIds = (List<String>) request.get("draftIds");
        String reason = (String) request.get("reason");
        String remarks = (String) request.get("remarks");
        return ResponseEntity.ok(workflowService.bulkMarkNotAComplaint(draftIds, jwt.getSubject(), reason, remarks));
    }

    @PostMapping("/approve")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD')")
    public ResponseEntity<EmailDraft> approve(
            @RequestParam String draftId,
            @RequestParam(required = false) String remarks,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workflowService.approve(draftId, jwt.getSubject(), remarks));
    }

    @PostMapping("/send-back")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD')")
    public ResponseEntity<EmailDraft> sendBack(
            @RequestParam String draftId,
            @RequestParam String remarks,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workflowService.sendBack(draftId, jwt.getSubject(), remarks));
    }

    @PostMapping("/convert-to-complaint")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD')")
    public ResponseEntity<Complaint> convertToComplaint(
            @RequestParam String draftId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(workflowService.convertToComplaint(draftId, jwt.getSubject()));
    }

    @GetMapping("/not-a-complaint-reasons")
    public ResponseEntity<List<String>> getNotAComplaintReasons() {
        return ResponseEntity.ok(workflowService.getNotAComplaintReasons());
    }

    @GetMapping("/closure-letter")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD', 'ADMIN')")
    public ResponseEntity<byte[]> generateClosureLetter(
            @RequestParam String complaintNumber,
            @RequestParam(defaultValue = "RBIOS_2026") String schemeVersion) {
        byte[] pdf = closureLetterService.generateClosureLetter(complaintNumber, schemeVersion);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=closure-" + complaintNumber + ".html")
                .contentType(MediaType.TEXT_HTML)
                .body(pdf);
    }

    @GetMapping("/closure-letter/preview")
    @PreAuthorize("hasAnyRole('REVIEWER', 'CRPC_REVIEWER', 'CRPC_HEAD', 'ADMIN')")
    public ResponseEntity<byte[]> previewClosureLetter(
            @RequestParam String complaintNumber,
            @RequestParam Long templateId) {
        byte[] pdf = closureLetterService.generateClosureLetterPreview(complaintNumber, templateId);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(pdf);
    }
}
