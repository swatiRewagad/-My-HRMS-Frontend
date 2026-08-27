package com.rbi.cms.assignment.web;

import com.rbi.cms.assignment.domain.entity.AsgnAuditEvent;
import com.rbi.cms.assignment.domain.entity.AsgnRuleSetVersion;
import com.rbi.cms.assignment.service.GovernanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignment/rulesets/{ruleSetId}/versions/{versionId}")
@RequiredArgsConstructor
public class GovernanceController {

    private final GovernanceService governanceService;

    @PostMapping("/submit")
    public ResponseEntity<AsgnRuleSetVersion> submit(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = extractActorId(jwt);
        String remarks = body != null ? body.get("remarks") : null;
        return ResponseEntity.ok(governanceService.submit(ruleSetId, versionId, actorId, remarks));
    }

    @PostMapping("/approve")
    public ResponseEntity<AsgnRuleSetVersion> approve(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = extractActorId(jwt);
        String remarks = body != null ? body.get("remarks") : null;
        return ResponseEntity.ok(governanceService.approve(ruleSetId, versionId, actorId, remarks));
    }

    @PostMapping("/reject")
    public ResponseEntity<AsgnRuleSetVersion> reject(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = extractActorId(jwt);
        String remarks = body != null ? body.get("remarks") : null;
        return ResponseEntity.ok(governanceService.reject(ruleSetId, versionId, actorId, remarks));
    }

    @PostMapping("/publish")
    public ResponseEntity<AsgnRuleSetVersion> publish(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = extractActorId(jwt);
        String effectiveFromStr = body != null ? body.get("effectiveFrom") : null;
        Instant effectiveFrom = effectiveFromStr != null ? Instant.parse(effectiveFromStr) : null;
        return ResponseEntity.ok(governanceService.publish(ruleSetId, versionId, actorId, effectiveFrom));
    }

    @PostMapping("/archive")
    public ResponseEntity<AsgnRuleSetVersion> archive(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId,
            @AuthenticationPrincipal Jwt jwt) {
        String actorId = extractActorId(jwt);
        return ResponseEntity.ok(governanceService.archive(ruleSetId, versionId, actorId));
    }

    @GetMapping("/audit-trail")
    public ResponseEntity<List<Map<String, Object>>> getAuditTrail(
            @PathVariable Long ruleSetId,
            @PathVariable Long versionId) {
        List<AsgnAuditEvent> events = governanceService.getAuditTrail(versionId);
        List<Map<String, Object>> result = events.stream().map(e -> {
            Map<String, Object> dto = new java.util.LinkedHashMap<>();
            dto.put("id", e.getId());
            dto.put("eventType", e.getAction());
            dto.put("actorId", e.getActor());
            dto.put("remarks", e.getAfterJson());
            dto.put("createdAt", e.getCreatedAt());
            return dto;
        }).toList();
        return ResponseEntity.ok(result);
    }

    private String extractActorId(Jwt jwt) {
        if (jwt == null) return "SYSTEM";
        String preferred = jwt.getClaimAsString("preferred_username");
        return preferred != null ? preferred : jwt.getSubject();
    }
}
