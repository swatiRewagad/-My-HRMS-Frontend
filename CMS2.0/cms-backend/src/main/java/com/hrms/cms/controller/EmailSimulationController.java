package com.hrms.cms.controller;

import com.hrms.cms.dto.EmailReplyWithFormRequest;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.dto.simulation.EmailStatsResponse;
import com.hrms.cms.dto.simulation.EmailThreadResponse;
import com.hrms.cms.dto.simulation.EmailThreadSummary;
import com.hrms.cms.dto.simulation.FormTemplateResponse;
import com.hrms.cms.dto.simulation.SimulatedEmailResponse;
import com.hrms.cms.service.EmailSimulationService;
import com.rbi.cms.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/email-simulation")
@RequiredArgsConstructor
public class EmailSimulationController {

    private final EmailSimulationService emailService;

    @PostMapping("/receive")
    public ResponseEntity<ApiResponse<EmailThreadResponse>> receiveEmail(
            @RequestBody IncomingEmailRequest request) {
        return ResponseEntity.ok(ApiResponse.success(emailService.receiveEmail(request)));
    }

    @PostMapping("/reply-with-form")
    public ResponseEntity<ApiResponse<EmailThreadResponse>> replyWithForm(
            @RequestBody EmailReplyWithFormRequest request) {
        return ResponseEntity.ok(ApiResponse.success(emailService.receiveFormReply(request)));
    }

    @GetMapping("/threads")
    public ResponseEntity<ApiResponse<List<EmailThreadSummary>>> getAllThreads() {
        return ResponseEntity.ok(ApiResponse.success(emailService.getAllThreads()));
    }

    @GetMapping("/threads/{threadId}")
    public ResponseEntity<ApiResponse<EmailThreadResponse>> getThread(@PathVariable String threadId) {
        return ResponseEntity.ok(ApiResponse.success(emailService.getThread(threadId)));
    }

    @GetMapping("/inbox")
    public ResponseEntity<ApiResponse<List<SimulatedEmailResponse>>> getInbox() {
        return ResponseEntity.ok(ApiResponse.success(emailService.getInbox()));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<SimulatedEmailResponse>>> getSent() {
        return ResponseEntity.ok(ApiResponse.success(emailService.getSent()));
    }

    @GetMapping("/form-template/{complaintNumber}")
    public ResponseEntity<ApiResponse<FormTemplateResponse>> getFormTemplate(
            @PathVariable String complaintNumber) {
        return ResponseEntity.ok(ApiResponse.success(emailService.getFormTemplate(complaintNumber)));
    }

    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<EmailStatsResponse>> getStats() {
        return ResponseEntity.ok(ApiResponse.success(emailService.getStats()));
    }

    @GetMapping("/complaints/{complaintNumber}/threads")
    public ResponseEntity<ApiResponse<List<EmailThreadSummary>>> getEmailThreadsByComplaint(
            @PathVariable String complaintNumber) {
        return ResponseEntity.ok(ApiResponse.success(emailService.getThreadsByComplaintNumber(complaintNumber)));
    }
}
