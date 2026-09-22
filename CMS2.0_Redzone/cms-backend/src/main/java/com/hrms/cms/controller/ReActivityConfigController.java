package com.hrms.cms.controller;

import com.hrms.cms.entity.ConfigChangeRequest;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ReActivityConfigService;
import jakarta.servlet.http.HttpServletRequest;
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
 * Maker-checker endpoints for RE Activity nudge thresholds (UST851).
 *
 * Identity comes from {@link RequestIdentityResolver} rather than a request-body field, so the
 * approver cannot simply name themselves in the payload. Note the limitation documented on
 * {@link ReActivityConfigService}: authentication is not yet enforced globally, so this raises the
 * bar without being an absolute control.
 */
@RestController
@RequestMapping("/api/v1/re-activity-config")
@RequiredArgsConstructor
@Slf4j
public class ReActivityConfigController {

    private final ReActivityConfigService configService;
    private final RequestIdentityResolver identityResolver;

    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> requestChange(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }

        try {
            ConfigChangeRequest saved = configService.requestChange(
                    (String) body.get("configKey"),
                    stringOf(body.get("proposedValue")),
                    (String) body.get("reason"),
                    identity.getUserId());
            return ResponseEntity.status(HttpStatus.CREATED).body(ok(Map.of(
                    "request", toMap(saved),
                    "message", "Change staged and awaiting approval by a different administrator")));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        }
    }

    @PostMapping("/requests/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }

        String reason = body == null ? null : (String) body.get("decisionReason");
        try {
            ConfigChangeRequest decided = configService.approve(id, identity.getUserId(), reason);
            return ResponseEntity.ok(ok(Map.of("request", toMap(decided))));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        }
    }

    @PostMapping("/requests/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }

        String reason = body == null ? null : (String) body.get("decisionReason");
        try {
            ConfigChangeRequest decided = configService.reject(id, identity.getUserId(), reason);
            return ResponseEntity.ok(ok(Map.of("request", toMap(decided))));
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (IllegalStateException e) {
            return conflict(e.getMessage());
        }
    }

    @GetMapping("/requests/pending")
    public ResponseEntity<Map<String, Object>> listPending() {
        List<Map<String, Object>> items = configService.listPending().stream().map(this::toMap).toList();
        return ResponseEntity.ok(ok(Map.of("requests", items)));
    }

    @GetMapping("/requests")
    public ResponseEntity<Map<String, Object>> historyFor(@RequestParam String configKey) {
        List<Map<String, Object>> items = configService.historyFor(configKey).stream().map(this::toMap).toList();
        return ResponseEntity.ok(ok(Map.of("requests", items)));
    }

    private Map<String, Object> toMap(ConfigChangeRequest req) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", req.getId());
        map.put("configKey", req.getConfigKey());
        map.put("currentValue", req.getCurrentValue());
        map.put("proposedValue", req.getProposedValue());
        map.put("reason", req.getReason());
        map.put("requestedBy", req.getRequestedBy());
        map.put("requestedAt", req.getRequestedAt() != null ? req.getRequestedAt().toString() : null);
        map.put("status", req.getStatus());
        map.put("decidedBy", req.getDecidedBy());
        map.put("decidedAt", req.getDecidedAt() != null ? req.getDecidedAt().toString() : null);
        map.put("decisionReason", req.getDecisionReason());
        return map;
    }

    private String stringOf(Object value) {
        return value == null ? null : value.toString();
    }

    private Map<String, Object> ok(Map<String, Object> payload) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.putAll(payload);
        response.put("timestamp", LocalDateTime.now().toString());
        return response;
    }

    private ResponseEntity<Map<String, Object>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "success", false,
                "message", "An identified administrator is required for configuration changes."));
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("success", false, "message", message));
    }

    private ResponseEntity<Map<String, Object>> conflict(String message) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("success", false, "message", message));
    }
}
