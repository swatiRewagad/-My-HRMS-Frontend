package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.domain.entity.AsgnRuleSet;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSetVersion;
import com.rbi.cms.assignment.dto.request.RuleBulkSaveRequest;
import com.rbi.cms.assignment.dto.request.RuleSetCreateRequest;
import com.rbi.cms.assignment.dto.request.VersionCreateRequest;
import com.rbi.cms.assignment.service.RuleSetAuthoringService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignment/rulesets")
@RequiredArgsConstructor
public class AuthoringController {

    private final RuleSetAuthoringService authoringService;

    @GetMapping
    public ResponseEntity<List<AsgnRuleSet>> listRuleSets() {
        return ResponseEntity.ok(authoringService.listRuleSets());
    }

    @PostMapping
    public ResponseEntity<AsgnRuleSet> createRuleSet(@Valid @RequestBody RuleSetCreateRequest request) {
        AsgnRuleSet created = authoringService.createRuleSet(request);
        return ResponseEntity.created(URI.create("/api/v1/assignment/rulesets/" + created.getId())).body(created);
    }

    @GetMapping("/{id}/versions")
    public ResponseEntity<List<AsgnRuleSetVersion>> listVersions(@PathVariable Long id) {
        return ResponseEntity.ok(authoringService.listVersions(id));
    }

    @PostMapping("/{id}/versions")
    public ResponseEntity<AsgnRuleSetVersion> createVersion(
            @PathVariable Long id,
            @RequestBody(required = false) VersionCreateRequest request) {
        AsgnRuleSetVersion version = authoringService.createVersion(id, request);
        return ResponseEntity.created(URI.create("/api/v1/assignment/rulesets/" + id + "/versions/" + version.getId())).body(version);
    }

    @GetMapping("/{id}/versions/{versionId}")
    public ResponseEntity<Map<String, Object>> getVersion(@PathVariable Long id, @PathVariable Long versionId) {
        return ResponseEntity.ok(authoringService.getVersionPayload(id, versionId));
    }

    @PutMapping("/{id}/versions/{versionId}/rules")
    public ResponseEntity<AsgnRuleSetVersion> bulkSaveRules(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestHeader(value = "If-Match", required = false) String etag,
            @Valid @RequestBody RuleBulkSaveRequest request) {
        AsgnRuleSetVersion updated = authoringService.bulkSaveRules(id, versionId, request, etag);
        return ResponseEntity.ok()
                .header("ETag", updated.getOptLock() != null ? updated.getOptLock().toString() : "0")
                .body(updated);
    }

    @DeleteMapping("/{id}/versions/{versionId}/rules/{ruleId}")
    public ResponseEntity<Void> deleteRule(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @PathVariable Long ruleId) {
        authoringService.deleteRule(id, versionId, ruleId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/versions/{versionId}/rules/reorder")
    public ResponseEntity<Void> reorderRules(
            @PathVariable Long id,
            @PathVariable Long versionId,
            @RequestBody List<Long> ruleIdsInOrder) {
        authoringService.reorderRules(versionId, ruleIdsInOrder);
        return ResponseEntity.ok().build();
    }
}
