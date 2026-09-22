package com.hrms.cms.controller;

import com.hrms.cms.entity.StaffDraft;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.StaffDraftService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Staff draft save/resume, the auto-save interval, and edit presence (UST673-675).
 *
 * <h2>The owner is never a request parameter</h2>
 * Every endpoint resolves the caller from the SSO token. None accepts a username, and there is no
 * "list all drafts" route. That is a direct response to the existing draft surface, where the owner is a
 * query parameter the caller chooses and omitting it returns every draft in the system, and where
 * {@code PUT /drafts/{id}} has no ownership check at all — anyone who knows a draft id can overwrite it.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/staff-drafts")
@RequiredArgsConstructor
public class StaffDraftController {

    private final StaffDraftService draftService;
    private final RequestIdentityResolver identityResolver;

    /**
     * The auto-save configuration the client should use (UST673).
     *
     * <p>Exposed because the interval lives in SYSTEM_CONFIG and the Angular timer needs it; hardcoding
     * 120 in TypeScript would reintroduce exactly the duplication the config row exists to prevent. The
     * only other autosave in the product (the citizen wizard) hardcodes its 30s literal, which is why
     * changing it needs a release.
     */
    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        int interval = draftService.autosaveIntervalSeconds();
        body.put("autosaveIntervalSeconds", interval);
        body.put("autosaveEnabled", interval > 0);
        body.put("editPresenceStaleSeconds", draftService.presenceStaleSeconds());
        return ResponseEntity.ok(body);
    }

    /** The caller's own drafts. There is no endpoint that returns anybody else's. */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> myDrafts(HttpServletRequest request) {
        return ResponseEntity.ok(draftService.listOwn(resolveUserId(request)));
    }

    /**
     * One specific draft of the caller's, for resume.
     *
     * <p>Returns 204 rather than 404 when absent: "you have no draft here" is a normal state on first
     * visit, and a 404 would show as an error in the browser console on every clean form load.
     */
    @GetMapping("/{milestone}")
    public ResponseEntity<Map<String, Object>> myDraft(
            @PathVariable String milestone,
            @RequestParam(required = false) String complaintNumber,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        StaffDraft.Milestone parsed = parseMilestone(milestone);
        String normalised = (complaintNumber == null || complaintNumber.isBlank())
                ? null : complaintNumber.trim();

        return draftService.findOwn(parsed, userId, normalised)
                .map(draft -> ResponseEntity.ok(draftService.toDto(draft)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Saves the caller's draft.
     *
     * <p>A failure here MUST reach the client as a failure. The bug this replaces is
     * {@code rbio-create-complaint.component.ts}, which set {@code draftSaved(true)} in both the success
     * AND error handlers — and because that flag swaps the form for a saved-summary view, a rejected save
     * showed the officer a confirmation page for a complaint that did not exist.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> save(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String userId = resolveUserId(request);
        String milestone = asText(body.get("milestone"));
        String complaintNumber = asText(body.get("complaintNumber"));
        boolean autosave = Boolean.parseBoolean(String.valueOf(body.getOrDefault("autosave", false)));

        @SuppressWarnings("unchecked")
        Map<String, Object> formData = body.get("formData") instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : null;

        StaffDraft saved = draftService.save(milestone, userId, complaintNumber, formData, autosave);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("id", saved.getId());
        response.put("milestone", saved.getMilestone());
        response.put("draftStatus", saved.getDraftStatus());
        response.put("saveSource", saved.getSaveSource());
        // The server's timestamp, so the client's "last saved at" cannot claim a save the server never
        // performed. The citizen-side equivalent of this bug was fixed under UST82; the staff side kept it.
        response.put("savedAt", saved.getUpdatedAt() == null ? null : saved.getUpdatedAt().toString());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        boolean removed = draftService.deleteOwn(id, resolveUserId(request));
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /** UST673: drops the auto-saved draft after the user successfully saved and proceeded. */
    @PostMapping("/discard-autosave")
    public ResponseEntity<Map<String, Object>> discardAutosave(
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        String complaintNumber = body == null ? null : asText(body.get("complaintNumber"));
        int discarded = draftService.discardAutosave(resolveUserId(request), complaintNumber);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("discarded", discarded);
        return ResponseEntity.ok(response);
    }

    // ── Edit presence (UST675) ───────────────────────────────────────────────────────────────────

    /**
     * Registers the caller as editing a complaint and reports who else is.
     *
     * <p>Advisory only — see {@code ComplaintEditPresence}. This cannot and does not prevent a save; the
     * {@code @Version} check on {@code Complaint} does that, returning 409. What this adds is the WARNING
     * the version check structurally cannot give, because optimistic locking only detects a conflict at
     * write time, by which point the user has already done the work.
     */
    @PostMapping("/presence/{complaintNumber}")
    public ResponseEntity<Map<String, Object>> heartbeat(
            @PathVariable String complaintNumber,
            HttpServletRequest request) {

        RequestIdentity identity = resolveIdentity(request);
        List<Map<String, Object>> others = draftService.heartbeatAndListOthers(
                complaintNumber, identity.getUserId(), identity.getDisplayName());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("complaintNumber", complaintNumber);
        response.put("otherEditors", others);
        response.put("concurrentEdit", !others.isEmpty());
        response.put("heartbeatIntervalSeconds", Math.max(15, draftService.presenceStaleSeconds() / 3));
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/presence/{complaintNumber}")
    public ResponseEntity<Void> releasePresence(
            @PathVariable String complaintNumber,
            HttpServletRequest request) {
        draftService.releasePresence(complaintNumber, resolveUserId(request));
        return ResponseEntity.noContent().build();
    }

    private RequestIdentity resolveIdentity(HttpServletRequest request) {
        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null || identity.getUserId() == null) {
            // Fails closed. A draft whose owner cannot be established is a draft that cannot be kept
            // private, so it is refused rather than attributed to a placeholder.
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Your identity could not be established, so the draft action was refused.");
        }
        return identity;
    }

    private String resolveUserId(HttpServletRequest request) {
        return resolveIdentity(request).getUserId();
    }

    private StaffDraft.Milestone parseMilestone(String raw) {
        try {
            return StaffDraft.Milestone.valueOf(raw.trim().toUpperCase());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown draft milestone '" + raw + "'.");
        }
    }

    private String asText(Object value) {
        return value == null ? null : value.toString();
    }
}
