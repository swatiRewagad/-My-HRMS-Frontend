package com.hrms.cms.controller;

import com.hrms.cms.dto.cepc.CepcAssessmentCommentRequest;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcAssessmentCommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The "Comments" box on the complaint detail view's Assessment, Forward and Final Decision tabs.
 *
 * <p>A SEPARATE CONTROLLER rather than extra routes on {@link ComplaintApiV1Controller}, matching why
 * {@link ComplaintCommentController} is its own file: that controller is heavily edited by concurrent CEPC
 * and merge work, and a new file keeps this route from colliding with it.
 *
 * <p><b>Not the same feature as {@link ComplaintCommentController}'s threaded, visibility-scoped
 * comments at {@code /api/v1/complaint-comments}.</b> That system is correctly wired and used today by the
 * RE portal; this one is the CEPC screen's own flat, no-visibility feed, matching the shape its client has
 * always sent — the fix here is to serve the route the client already calls, not to migrate the screen
 * onto the other system.
 *
 * <p>Reading and writing are both open to every CEPC role that can view a complaint: a comment is a shared
 * note across the assessment, not a scoped action.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class CepcAssessmentCommentController {

    private final CepcAssessmentCommentService commentService;

    @GetMapping("/{complaintNumber}/comments")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> list(@PathVariable String complaintNumber) {
        List<Map<String, Object>> rows = commentService.listForComplaint(complaintNumber);
        return ResponseEntity.ok(body(true, "OK", rows));
    }

    @PostMapping("/{complaintNumber}/comments")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> add(@PathVariable String complaintNumber,
                                                     @Valid @RequestBody CepcAssessmentCommentRequest request) {
        try {
            Map<String, Object> data = commentService.addForComplaint(complaintNumber, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(body(true, "Comment saved.", data));
        } catch (CepcAssessmentCommentService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcAssessmentCommentService.InvalidCommentException e) {
            return ResponseEntity.badRequest().body(body(false, e.getMessage(), null));
        }
    }

    private static Map<String, Object> body(boolean success, String message, Object data) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("success", success);
        out.put("message", message);
        out.put("data", data);
        out.put("timestamp", LocalDateTime.now().toString());
        return out;
    }
}
