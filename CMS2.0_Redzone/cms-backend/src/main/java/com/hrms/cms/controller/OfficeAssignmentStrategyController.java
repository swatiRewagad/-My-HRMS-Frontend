package com.hrms.cms.controller;

import com.hrms.cms.entity.OfficeAssignmentMapping;
import com.hrms.cms.entity.OfficeAssignmentStrategy;
import com.hrms.cms.security.RbioIdentityResolver;
import com.hrms.cms.security.RbioRoleGuard;
import com.hrms.cms.service.OfficeAssignmentStrategyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Super-Admin administration of per-office assignment logic (UST468-472).
 *
 * <h2>Why SUPER_ADMIN and not ADMIN</h2>
 * UST468 restricts this to a Super Admin. Guarding it with ADMIN would let every existing administrator
 * change which officer handles which bank nationally — a wider grant than the story allows, and one that
 * is hard to notice after the fact because the effect only shows up in later assignments.
 * {@code SUPER_ADMIN} is a new realm role (see {@code deployment/provision-super-admin.sh}); ADMIN is
 * accepted alongside it only on the READ endpoints, so an administrator can still see how offices are
 * configured without being able to change it.
 *
 * <h2>Why the reason travels in the body</h2>
 * The change reason is a body field, not a query parameter, so it does not end up in access logs and
 * proxy logs. It is mandatory: an assignment-policy change with no recorded rationale cannot be reviewed,
 * and this endpoint is precisely the one someone will ask about when complaints start landing somewhere
 * unexpected.
 *
 * <p>The actor comes from the resolved identity rather than from a request field, so a caller cannot
 * attribute their change to somebody else.
 */
@RestController
@RequestMapping("/api/v1/admin/office-assignment")
@RequiredArgsConstructor
public class OfficeAssignmentStrategyController {

    private final OfficeAssignmentStrategyService strategyService;
    private final RbioIdentityResolver identityResolver;

    @GetMapping("/strategies")
    @RbioRoleGuard(roles = {"SUPER_ADMIN", "ADMIN", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> listStrategies() {
        List<OfficeAssignmentStrategy> all = strategyService.allStrategies();
        return ResponseEntity.ok(Map.of("success", true, "data", all, "count", all.size()));
    }

    @GetMapping("/strategies/{officeId}/mappings")
    @RbioRoleGuard(roles = {"SUPER_ADMIN", "ADMIN", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> listMappings(@PathVariable String officeId) {
        List<OfficeAssignmentMapping> mappings = strategyService.mappingsFor(officeId);
        return ResponseEntity.ok(Map.of("success", true, "data", mappings, "count", mappings.size()));
    }

    /**
     * Sets the one active logic for an office.
     *
     * <p>Takes effect on the next assignment with no restart, because the resolver reads this row per
     * complaint. Complaints already assigned are untouched — UST468 requires the change not to be
     * retrospective, and re-running assignment for existing complaints would move files out from under
     * the officers currently working them.
     */
    @PutMapping("/strategies/{officeId}")
    @RbioRoleGuard(roles = {"SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> setStrategy(
            @PathVariable String officeId,
            @RequestBody Map<String, String> body) {
        try {
            OfficeAssignmentStrategy saved = strategyService.setStrategy(
                    officeId,
                    body.get("strategy"),
                    body.get("roleGroup"),
                    identityResolver.resolveActor(),
                    body.get("reason"));
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Assignment strategy updated. Applies to complaints assigned from now on.",
                    "data", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/strategies/{officeId}/mappings")
    @RbioRoleGuard(roles = {"SUPER_ADMIN"})
    public ResponseEntity<Map<String, Object>> upsertMapping(
            @PathVariable String officeId,
            @RequestBody Map<String, String> body) {
        try {
            OfficeAssignmentMapping saved = strategyService.upsertMapping(
                    officeId,
                    body.get("mappingType"),
                    body.get("subject"),
                    body.get("targetOfficerId"),
                    identityResolver.resolveActor());
            return ResponseEntity.ok(Map.of("success", true, "data", saved));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Shows which officer a hypothetical complaint would go to, without filing one.
     *
     * <p>UST469/472 require the lookup to be traceable. This is the same resolver the filing path uses, so
     * an administrator can confirm a mapping works — and see the REASON when it falls back — before a
     * citizen's complaint is the thing that reveals a misconfiguration.
     */
    @GetMapping("/strategies/{officeId}/preview")
    @RbioRoleGuard(roles = {"SUPER_ADMIN", "ADMIN", "RBIO_ADMIN"})
    public ResponseEntity<Map<String, Object>> preview(
            @PathVariable String officeId,
            @RequestParam(required = false) String entityName,
            @RequestParam(required = false) Long categoryId) {
        // dryRun: a GET must not advance the rotation pointer, or merely inspecting the configuration
        // would change who the next real complaint is assigned to.
        OfficeAssignmentStrategyService.Resolution resolution =
                strategyService.resolveOfficer(officeId, entityName, categoryId, true);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "officeId", officeId,
                "strategy", resolution.strategy(),
                "outcome", resolution.outcome(),
                "reason", resolution.reason(),
                "officerId", resolution.officerId() != null ? resolution.officerId() : "",
                "assigned", resolution.isAssigned()));
    }
}
