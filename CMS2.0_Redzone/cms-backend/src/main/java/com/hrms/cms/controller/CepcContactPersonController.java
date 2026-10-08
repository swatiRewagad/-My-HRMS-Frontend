package com.hrms.cms.controller;

import com.hrms.cms.dto.cepc.CepcContactPersonRequest;
import com.hrms.cms.security.CepcIdentityResolver;
import com.hrms.cms.security.CepcRoleGuard;
import com.hrms.cms.service.CepcContactPersonService;
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
 * Contact persons for one complaint, shown on the Contact Entity tab of the CEPC complaint detail view.
 *
 * <p><b>The complaint number travels as a query parameter, not a path segment.</b> Complaint numbers contain
 * slashes, which would split a path segment and match no mapping at all — the same hazard that made
 * {@code NodalOfficerRecord.recordNumber} replace its slashes. A query parameter carries them unharmed, so
 * no second identifier has to be invented for this table.
 *
 * <p>Reading is open to every CEPC read role, as the nodal-record list is: knowing who the office has been
 * speaking to is part of reviewing a complaint. Writing is limited to the dealing officer's roles, matching
 * the guard on {@code forward-to-re} — a contact is part of working the complaint, not of reviewing it.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints/contact-persons")
@RequiredArgsConstructor
public class CepcContactPersonController {

    private final CepcContactPersonService contactPersonService;
    private final CepcIdentityResolver identity;

    @GetMapping
    @CepcRoleGuard(roles = {
            "CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_CONCILIATOR", "CEPC_ADJUDICATOR",
            "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_SUPERVISOR", "CEPC_ADMIN",
            "ADMIN"})
    public ResponseEntity<Map<String, Object>> list(@RequestParam String complaintNumber) {
        List<Map<String, Object>> rows = contactPersonService.list(complaintNumber);
        return ResponseEntity.ok(body(true, "OK", rows));
    }

    @PostMapping
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> add(@RequestParam String complaintNumber,
                                                   @Valid @RequestBody CepcContactPersonRequest request) {
        try {
            Map<String, Object> data =
                    contactPersonService.add(complaintNumber, request, identity.resolveActor());
            return ResponseEntity.ok(body(true, "Contact person added.", data));
        } catch (CepcContactPersonService.ComplaintNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcContactPersonService.InvalidContactException e) {
            return ResponseEntity.badRequest().body(body(false, e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @CepcRoleGuard(roles = {"CEPC_DO", "CEPC_OFFICER", "CEPC_CONTACT_PERSON", "CEPC_ADMIN", "ADMIN"})
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                      @RequestParam String complaintNumber,
                                                      @Valid @RequestBody CepcContactPersonRequest request) {
        try {
            Map<String, Object> data =
                    contactPersonService.update(complaintNumber, id, request, identity.resolveActor());
            return ResponseEntity.ok(body(true, "Contact person updated.", data));
        } catch (CepcContactPersonService.ContactNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body(false, e.getMessage(), null));
        } catch (CepcContactPersonService.InvalidContactException e) {
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
