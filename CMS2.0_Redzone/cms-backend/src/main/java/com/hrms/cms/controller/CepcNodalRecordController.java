package com.hrms.cms.controller;

import com.hrms.cms.dto.cepc.CepcAssessmentCommentRequest;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcAssessmentCommentService;
import com.hrms.cms.service.CepcNodalRecordService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Contact Entity tab's worklist and its forward action.
 *
 * <p>Mounted under {@code /api/v1/complaints/nodal-records} rather than beside the existing
 * {@code /api/nodal-officer-records} because that one is guarded for RBIO and returns the bare entity. The
 * two serve different screens off the same table and neither DTO is usable by the other's client.
 *
 * <p>The GET is open to every CEPC read role and the POST is not: reading who the entity's nodal officer is
 * is part of reviewing a complaint, while forwarding a record commits the office to an assessment and starts
 * the Clause 13(1) clock.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints/nodal-records")
@RequiredArgsConstructor
public class CepcNodalRecordController {

    private final CepcNodalRecordService nodalRecordService;
    private final CepcAssessmentCommentService commentService;
    private final CepcIdentityResolver identity;

    @GetMapping
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> list() {
        List<Map<String, Object>> rows = nodalRecordService.list();
        return ResponseEntity.ok(body(true, "OK", rows));
    }

    @PostMapping("/{recordNumber}/forward-to-re")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> forwardToRe(
            @PathVariable String recordNumber,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> data = nodalRecordService.forwardToRe(
                    recordNumber, payload == null ? Map.of() : payload,
                    identity.resolveActor(), identity.isAdmin());
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcNodalRecordService.RecordNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcNodalRecordService.NotEditableException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(false, e.getMessage(), null));
        } catch (CepcNodalRecordService.InvalidAssessmentException e) {
            // The reason travels in `message`, which is the only text the client shows when a forward is
            // refused — the compensation caps and the date rules are enforced here, not on the form.
            return ResponseEntity.badRequest().body(body(false, e.getMessage(), null));
        }
    }

    @PutMapping("/{recordNumber}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> updateContactFields(
            @PathVariable String recordNumber,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> data = nodalRecordService.updateContactFields(
                    recordNumber, payload == null ? Map.of() : payload,
                    identity.resolveActor(), identity.isAdmin());
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcNodalRecordService.RecordNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcNodalRecordService.NotEditableException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(false, e.getMessage(), null));
        } catch (CepcNodalRecordService.ConflictException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body(false, e.getMessage(), null));
        }
    }

    /** The nodal record's own "To NO" / "To PNO" thread — newest first, matching the client's own order. */
    @GetMapping("/{recordNumber}/comments")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> listComments(@PathVariable String recordNumber) {
        return ResponseEntity.ok(body(true, "OK", commentService.listForNodalRecord(recordNumber)));
    }

    @PostMapping("/{recordNumber}/comments")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> addComment(@PathVariable String recordNumber,
                                                            @Valid @RequestBody CepcAssessmentCommentRequest request) {
        try {
            Map<String, Object> data = commentService.addForNodalRecord(recordNumber, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(body(true, "Comment saved.", data));
        } catch (CepcAssessmentCommentService.RecordNotFoundException e) {
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
