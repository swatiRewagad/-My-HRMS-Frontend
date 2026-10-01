package com.hrms.cms.controller;

import com.hrms.cms.entity.RbioCaseAssignmentHistory;
import com.hrms.cms.security.RbioIdentityResolver;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.RbioActionOverrideService;
import com.hrms.cms.service.RbioAdditionalEntityService;
import com.hrms.cms.service.RbioCaseAssignmentHistoryService;
import com.hrms.cms.service.RbioLegalCaseService;
import com.hrms.cms.service.RbioRoles;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The four case-file endpoints the RBIO frontend has always called and no controller ever served.
 *
 * <p>Each of these was a PHANTOM: {@code additional-entities}, {@code action-override},
 * {@code legal-case} and {@code last-active-officer} returned 404 into a
 * {@code catchError(() => of(default))}, so every screen rendered its empty default and every write was
 * discarded. Nothing was broken visibly — the add-entity form accepted six entities, the History tab
 * showed no overrides, and the legal-case form reopened blank — which is why these went unnoticed.
 *
 * <p>Mounted under {@code /api/v1/complaints} to match the URLs the frontend already builds
 * ({@code rbio-workflow.service.ts:65} sets that base), but kept in a SEPARATE controller from
 * {@code ComplaintApiV1Controller}: that class is the citizen-facing complaint API, and these are
 * staff-only, role-guarded case-file operations.
 *
 * <p><b>Authorisation.</b> Reads admit any RBIO role. Writes are restricted to the three roles UST487-495
 * name — Dealing Official, Reviewer and Deputy Ombudsman — plus the Ombudsman, who can do anything a
 * Deputy can, and the legacy equivalents so live sessions keep working. Enforced by the guard, not by
 * hiding controls.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/complaints")
@RequiredArgsConstructor
public class RbioCaseFileController {

    // Role lists are written out at each @RbioRoleGuard rather than held in a shared String[] constant:
    // an annotation member must be a constant EXPRESSION, and a reference to a static final array is not
    // one, however final the array is. RbioRoles' String constants are, so the literals stay centralised.
    //
    // READ  = every RBIO role.
    // WRITE = the three roles UST487-495 name (Dealing Official, Reviewer, Deputy Ombudsman), plus the
    //         Ombudsman, who holds every power the rank below does, plus the two legacy names for the
    //         first two ranks so live sessions keep working, plus ADMIN.

    private final RbioAdditionalEntityService additionalEntityService;
    private final RbioActionOverrideService actionOverrideService;
    private final RbioLegalCaseService legalCaseService;
    private final RbioCaseAssignmentHistoryService assignmentHistoryService;
    private final RbioIdentityResolver identityResolver;

    // ═══════════════════════════ Additional entities (UST487-495) ═══════════════════════════

    @GetMapping("/{complaintNumber}/additional-entities")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> listAdditionalEntities(@PathVariable String complaintNumber) {
        List<Map<String, Object>> entities = additionalEntityService.list(complaintNumber);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("entities", entities);
        data.putAll(additionalEntityService.capState(complaintNumber));
        return buildResponse(true, "Additional entities retrieved", data, entities);
    }

    /**
     * Adds an additional entity, refusing a seventh.
     *
     * <p>The cap is checked in the service against a COUNT of persisted rows, so it holds for a caller
     * who never loaded the screen.
     */
    @PostMapping("/{complaintNumber}/additional-entities")
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.ADMIN})
    public ResponseEntity<Map<String, Object>> addAdditionalEntity(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> saved = additionalEntityService.add(
                complaintNumber, request, identityResolver.resolveActor(), identityResolver.resolveRbioRole());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(envelope(true, "Additional entity added", saved));
    }

    @DeleteMapping("/{complaintNumber}/additional-entities/{id}")
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.ADMIN})
    public ResponseEntity<Map<String, Object>> deleteAdditionalEntity(
            @PathVariable String complaintNumber, @PathVariable Long id) {
        additionalEntityService.remove(complaintNumber, id);
        return ResponseEntity.ok(envelope(true, "Additional entity removed",
                additionalEntityService.capState(complaintNumber)));
    }

    // ═══════════════════════════ Field-level override history ═══════════════════════════

    @GetMapping("/{complaintNumber}/action-override")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> listOverrides(@PathVariable String complaintNumber) {
        List<Map<String, Object>> overrides = actionOverrideService.list(complaintNumber);
        // Returned as a bare list in `data` because the component reads `res.data || res || []`; wrapping
        // it in an object would leave the History tab iterating a non-array and rendering nothing.
        return ResponseEntity.ok(envelope(true, "Action override history retrieved", overrides));
    }

    @PostMapping("/{complaintNumber}/action-override")
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.ADMIN})
    public ResponseEntity<Map<String, Object>> recordOverride(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {

        Map<String, Object> saved = actionOverrideService.record(
                complaintNumber, request, identityResolver.resolveActor(), identityResolver.resolveRbioRole());

        // A no-change edit is accepted and not stored — see RbioActionOverrideService.record.
        if (saved == null) {
            return ResponseEntity.ok(envelope(true, "No change to record", null));
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(envelope(true, "Override recorded", saved));
    }

    // ═══════════════════════════ Legal case (UST553) ═══════════════════════════

    // CEPC roles are admitted alongside RBIO ones: a complaint can be sub judice while it still sits
    // with CEPC, and the officer handling it then needs the court reference visible and recordable.
    // The guard only matches role strings, so the RBIO-named annotation is reused rather than adding a
    // second aspect that would have to agree with this one.
    @GetMapping("/{complaintNumber}/legal-case")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN,
            "CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN",
            "CEPC_CONTACT_PERSON"})
    public ResponseEntity<Map<String, Object>> getLegalCase(@PathVariable String complaintNumber) {
        Map<String, Object> legalCase = legalCaseService.find(complaintNumber);
        return ResponseEntity.ok(envelope(true,
                legalCase == null ? "No legal case recorded" : "Legal case retrieved", legalCase));
    }

    @PostMapping("/{complaintNumber}/legal-case")
    // CEPC_CONTACT_PERSON is deliberately absent: it reads the case file, it does not write to it.
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.ADMIN,
            "CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN"})
    public ResponseEntity<Map<String, Object>> saveLegalCase(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {
        Map<String, Object> saved = legalCaseService.save(
                complaintNumber, request, identityResolver.resolveActor());
        return ResponseEntity.ok(envelope(true, "Legal case saved", saved));
    }

    /** PUT and POST are the same operation; see {@code RbioLegalCaseService.save}. */
    @PutMapping("/{complaintNumber}/legal-case")
    // CEPC_CONTACT_PERSON is deliberately absent: it reads the case file, it does not write to it.
    @RbioRoleGuard(roles = {RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER, RbioRoles.DEPUTY_OMBUDSMAN,
            RbioRoles.OMBUDSMAN, RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.ADMIN,
            "CEPC_DO", "CEPC_REVIEWER", "CEPC_INCHARGE", "CEPC_CLOSING_AUTHORITY", "CEPC_ADMIN"})
    public ResponseEntity<Map<String, Object>> updateLegalCase(
            @PathVariable String complaintNumber,
            @RequestBody Map<String, Object> request) {
        return saveLegalCase(complaintNumber, request);
    }

    // ═══════════════════════════ Send-back target (UST516-517, 531, 759) ═══════════════════════════

    /**
     * The officer a send-back would return this complaint to, and whether they can still receive it.
     *
     * <p>{@code role} names the rank the file would go BACK to. It defaults to one step below the
     * complaint's current holder rather than to a literal, so the caller does not have to know the ladder.
     *
     * <p>This is the contract S5's UST759 consumes. {@code requiresManualSelection} is the field to branch
     * on: true means the client must show the picker, whatever the reason.
     */
    @GetMapping("/{complaintNumber}/last-active-officer")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> lastActiveOfficer(
            @PathVariable String complaintNumber,
            @RequestParam(required = false) String role) {

        String targetRole = (role != null && !role.isBlank())
                ? role.trim()
                : assignmentHistoryService.currentHolder(complaintNumber)
                        .map(h -> RbioRoles.previousRank(h.getRoleName()))
                        .orElse(null);

        if (targetRole == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "This complaint has no recorded assignment history, so no send-back target can be "
                            + "determined. Name the target role explicitly.");
        }

        return ResponseEntity.ok(envelope(true, "Send-back target resolved",
                assignmentHistoryService.lastActiveOfficerPayload(complaintNumber, targetRole)));
    }

    /** The full custody trail. Feeds the History tab alongside S6's status-change timeline. */
    @GetMapping("/{complaintNumber}/assignment-history")
    @RbioRoleGuard(roles = {RbioRoles.OFFICER, RbioRoles.SUPERVISOR, RbioRoles.CONCILIATOR,
            RbioRoles.ADJUDICATOR, RbioRoles.ADMIN, RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
            RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN})
    public ResponseEntity<Map<String, Object>> assignmentHistory(@PathVariable String complaintNumber) {
        List<Map<String, Object>> trail = assignmentHistoryService.trailFor(complaintNumber).stream()
                .map(RbioCaseFileController::toTrailPayload)
                .toList();
        return ResponseEntity.ok(envelope(true, "Assignment history retrieved", trail));
    }

    private static Map<String, Object> toTrailPayload(RbioCaseAssignmentHistory row) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", row.getId());
        payload.put("role", row.getRoleName());
        payload.put("officerId", row.getOfficerId());
        payload.put("assignedByAction", row.getAssignedByAction());
        payload.put("assignedBy", row.getAssignedBy());
        payload.put("assignedAt", row.getAssignedAt() != null ? row.getAssignedAt().toString() : null);
        payload.put("releasedAt", row.getReleasedAt() != null ? row.getReleasedAt().toString() : null);
        payload.put("current", row.current());
        return payload;
    }

    /**
     * The envelope every other controller in this tree returns, and which
     * {@code scripts/qa/json_field.py} unwraps.
     */
    private static Map<String, Object> envelope(boolean success, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("message", message);
        response.put("data", data);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    /**
     * The list endpoints return the ARRAY in {@code data} for the components that read
     * {@code res.data || res || []}, while still exposing the cap alongside it for callers that want it.
     */
    private static ResponseEntity<Map<String, Object>> buildResponse(
            boolean success, String message, Map<String, Object> meta, Object listData) {
        Map<String, Object> response = envelope(success, message, listData);
        response.put("meta", meta);
        return ResponseEntity.ok(response);
    }
}
