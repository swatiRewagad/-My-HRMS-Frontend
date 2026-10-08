package com.hrms.cms.controller;

import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcComplaintSummaryService;
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
 * The Summary tab of the CEPC complaint detail view.
 *
 * <p>The GET is the screen's bootstrap call: the client blocks on it, takes the complaint number every
 * other tab addresses from its {@code navBarDto}, and locks itself read-only if it fails. So a failure here
 * empties all six tabs regardless of their own health, which is why the responses below distinguish "no
 * such complaint" from "not yours to edit" rather than collapsing both into an empty body.
 */
@Slf4j
@RestController
@RequestMapping("/api/complaints/cepc")
@RequiredArgsConstructor
public class CepcComplaintSummaryController {

    private final CepcComplaintSummaryService summaryService;
    private final CepcIdentityResolver identity;

    @GetMapping("/{id}/summary")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> get(@PathVariable String id) {
        try {
            Map<String, Object> data = summaryService.read(id, identity.resolveActor(), identity.isAdmin());
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcComplaintSummaryService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        }
    }

    /**
     * Applies a partial summary and answers with the re-read result.
     *
     * <p>Two buttons on the screen PUT here with differently shaped bodies — see
     * {@link CepcComplaintSummaryService#update} for why the body is taken as a map and why that matters.
     * The response carries the stored summary rather than an acknowledgement, because the client re-renders
     * the form from it so that normalised dates and amounts are what the officer ends up looking at.
     */
    @PutMapping("/{id}/summary")
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> put(@PathVariable String id,
                                                   @RequestBody(required = false) Map<String, Object> patch) {
        try {
            Map<String, Object> data = summaryService.update(
                    id, patch, identity.resolveActor(), identity.isAdmin());
            return ResponseEntity.ok(body(true, "OK", data));
        } catch (CepcComplaintSummaryService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcComplaintSummaryService.NotEditableException e) {
            // 403 rather than 409: nothing the officer can retry changes the answer, and the client surfaces
            // the message verbatim so it has to say why the save was refused.
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body(false, e.getMessage(), null));
        } catch (CepcComplaintSummaryService.InvalidFieldException e) {
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
