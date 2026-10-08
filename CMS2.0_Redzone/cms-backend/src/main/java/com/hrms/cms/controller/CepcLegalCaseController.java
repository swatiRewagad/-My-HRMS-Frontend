package com.hrms.cms.controller;

import com.hrms.cms.dto.cepc.CepcLegalCaseRequest;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcLegalCaseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The legal-case dossier behind the Legal Case sidebar icon on the CEPC complaint detail view.
 *
 * <p><b>The complaint number travels as a query parameter, not a path segment</b> — same reasoning as
 * {@link CepcContactPersonController}: complaint numbers contain slashes, which would split a path
 * segment and match no mapping at all.
 *
 * <p><b>Not the same route as RBIO's {@code /legal-case}.</b> {@code RbioCaseFileController} already maps
 * {@code GET/POST/PUT /api/v1/complaints/{complaintNumber}/legal-case} (a path segment) for a much
 * thinner sub-judice tracker. This is a different route shape entirely — {@code
 * /api/v1/complaints/legal-case?complaintNumber=...} — so the two cannot collide, and this stays a CEPC
 * construct rather than repointing RBIO's.
 *
 * <p>Reading is open to every CEPC read role, matching {@link CepcContactPersonController}: knowing a
 * complaint is sub judice is part of reviewing it. Writing is limited to the dealing officer's roles.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints/legal-case")
@RequiredArgsConstructor
public class CepcLegalCaseController {

    private final CepcLegalCaseService legalCaseService;
    private final CepcIdentityResolver identity;

    @GetMapping
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> get(@RequestParam String complaintNumber) {
        Map<String, Object> data = legalCaseService.find(complaintNumber);
        return ResponseEntity.ok(body(true, data == null ? "No legal case recorded" : "OK", data));
    }

    @PostMapping
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> save(@RequestParam String complaintNumber,
                                                     @Valid @RequestBody CepcLegalCaseRequest request) {
        try {
            Map<String, Object> data =
                    legalCaseService.save(complaintNumber, request, identity.resolveActor());
            return ResponseEntity.ok(body(true, "Legal case saved.", data));
        } catch (CepcLegalCaseService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcLegalCaseService.InvalidRegionException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body(false, e.getMessage(), null));
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
