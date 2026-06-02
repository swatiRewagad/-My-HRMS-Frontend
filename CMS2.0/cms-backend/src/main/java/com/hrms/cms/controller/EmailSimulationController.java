package com.hrms.cms.controller;

import com.hrms.cms.dto.EmailReplyWithFormRequest;
import com.hrms.cms.dto.IncomingEmailRequest;
import com.hrms.cms.entity.SimulatedEmail;
import com.hrms.cms.service.EmailSimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/email-simulation")
@RequiredArgsConstructor
public class EmailSimulationController {

    private final EmailSimulationService emailService;

    @PostMapping("/receive")
    public ResponseEntity<Map<String, Object>> receiveEmail(@RequestBody IncomingEmailRequest request) {
        return ResponseEntity.ok(emailService.receiveEmail(request));
    }

    @PostMapping("/reply-with-form")
    public ResponseEntity<Map<String, Object>> replyWithForm(@RequestBody EmailReplyWithFormRequest request) {
        return ResponseEntity.ok(emailService.receiveFormReply(request));
    }

    @GetMapping("/threads")
    public ResponseEntity<List<Map<String, Object>>> getAllThreads() {
        return ResponseEntity.ok(emailService.getAllThreads());
    }

    @GetMapping("/threads/{threadId}")
    public ResponseEntity<Map<String, Object>> getThread(@PathVariable String threadId) {
        return ResponseEntity.ok(emailService.getThread(threadId));
    }

    @GetMapping("/inbox")
    public ResponseEntity<List<SimulatedEmail>> getInbox() {
        return ResponseEntity.ok(emailService.getInbox());
    }

    @GetMapping("/sent")
    public ResponseEntity<List<SimulatedEmail>> getSent() {
        return ResponseEntity.ok(emailService.getSent());
    }

    @GetMapping("/form-template/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> getFormTemplate(@PathVariable String complaintNumber) {
        return ResponseEntity.ok(emailService.getFormTemplate(complaintNumber));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(emailService.getStats());
    }
}
