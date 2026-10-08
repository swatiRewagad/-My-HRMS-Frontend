package com.hrms.cms.controller;

import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcConciliationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Conciliation tab of the CEPC complaint detail view.
 *
 * <p>The two verbs are guarded differently on purpose. Reviewers and the in-charge need to READ the
 * conciliation record to review a complaint, and hiding it from them would mean reviewing a conciliated
 * complaint without its conciliation. Only the dealing officer conducts the meeting, so only the dealing
 * officer may WRITE one — which is the same restriction the tab applies when it enables its save button.
 */
@Slf4j
@RestController
@RequestMapping("/api/complaints/cepc")
@RequiredArgsConstructor
public class CepcConciliationController {

    private final CepcConciliationService conciliationService;
    private final CepcIdentityResolver identity;

    @GetMapping("/{id}/conciliation")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        try {
            return ResponseEntity.ok(body(true, "OK", conciliationService.read(id)));
        } catch (CepcConciliationService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        }
    }

    @PutMapping("/{id}/conciliation")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONCILIATOR", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> put(@PathVariable String id,
                                                   @RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> data = conciliationService.update(
                    id, payload == null ? Map.of() : payload, identity.resolveActor(), identity.isAdmin());
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcConciliationService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcConciliationService.NotEditableException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(false, e.getMessage(), null));
        } catch (CepcConciliationService.InvalidMeetingException e) {
            // 400 with the reason in `message`, which is the key the dialog surfaces verbatim above the
            // fields. A bare 400 would leave the officer looking at a form that refuses to save silently.
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
