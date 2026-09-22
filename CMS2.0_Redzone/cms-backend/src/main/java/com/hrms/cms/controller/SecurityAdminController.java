package com.hrms.cms.controller;

import com.hrms.cms.entity.DeletionLog;
import com.hrms.cms.entity.RetentionPolicy;
import com.hrms.cms.entity.RevealAuditEntry;
import com.hrms.cms.entity.SecurityAlert;
import com.hrms.cms.repository.*;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.CredentialRevocationService;
import com.hrms.cms.service.RetentionService;
import com.hrms.cms.service.SystemConfigService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Security console for administrators (UST873, UST875, UST877, UST890).
 *
 * Sits under /api/v1/admin, which the security chain restricts to ADMIN. The realm has no
 * SECURITY_ADMIN role — ADMIN is the only role not tied to a workflow office — so that is what gates
 * this. Introducing a dedicated role is a realm change and is flagged rather than assumed.
 *
 * The URL-level rule is the actual control; the checks here are a second layer so a future change to
 * the path patterns cannot silently expose these endpoints.
 */
@RestController
@RequestMapping("/api/v1/admin/security")
@RequiredArgsConstructor
public class SecurityAdminController {

    private static final String ADMIN_ROLE = "ADMIN";

    private final SecurityAlertRepository securityAlertRepository;
    private final SecurityEventRepository securityEventRepository;
    private final RevealAuditEntryRepository revealAuditEntryRepository;
    private final CredentialRevocationRepository credentialRevocationRepository;
    private final RetentionPolicyRepository retentionPolicyRepository;
    private final DeletionLogRepository deletionLogRepository;
    private final CredentialRevocationService credentialRevocationService;
    private final RetentionService retentionService;
    private final SystemConfigService systemConfigService;
    private final RequestIdentityResolver requestIdentityResolver;

    // ─────────────────────────── Alerts (UST873) ───────────────────────────

    @GetMapping("/alerts")
    public ResponseEntity<?> alerts(@RequestParam(required = false) String status,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size,
                                    HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, 100));
        Page<SecurityAlert> alerts = (status == null || status.isBlank())
                ? securityAlertRepository.findByOrderByRaisedAtDesc(pageable)
                : securityAlertRepository.findByStatusOrderByRaisedAtDesc(status, pageable);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("data", alerts.getContent());
        body.put("totalElements", alerts.getTotalElements());
        body.put("openCount", securityAlertRepository.countByStatus("OPEN"));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/alerts/{id}/acknowledge")
    public ResponseEntity<?> acknowledge(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, String> body,
                                         HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        return securityAlertRepository.findById(id)
                .map(alert -> {
                    alert.setStatus("ACKNOWLEDGED");
                    alert.setAcknowledgedBy(identity.getUserId());
                    alert.setAcknowledgedAt(LocalDateTime.now());
                    alert.setAcknowledgementNote(body == null ? null : body.get("note"));
                    securityAlertRepository.save(alert);
                    return ResponseEntity.ok(Map.of("success", true, "message", "Alert acknowledged"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "Alert not found")));
    }

    @GetMapping("/events")
    public ResponseEntity<?> events(@RequestParam(required = false) String subject,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "50") int size,
                                    HttpServletRequest request) {
        if (requireAdmin(request) == null) {
            return forbidden();
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
        var events = (subject == null || subject.isBlank())
                ? securityEventRepository.findByOrderByOccurredAtDesc(pageable)
                : securityEventRepository.findBySubjectOrderByOccurredAtDesc(subject, pageable);
        return ResponseEntity.ok(Map.of("success", true, "data", events.getContent(),
                "totalElements", events.getTotalElements()));
    }

    // ─────────────────────── PII reveal audit (UST875) ───────────────────────

    @GetMapping("/pii-reveals")
    public ResponseEntity<?> reveals(@RequestParam(required = false) String userId,
                                     @RequestParam(required = false) String complaintNumber,
                                     @RequestParam(defaultValue = "0") int page,
                                     @RequestParam(defaultValue = "50") int size,
                                     HttpServletRequest request) {
        if (requireAdmin(request) == null) {
            return forbidden();
        }
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
        Page<RevealAuditEntry> entries;
        if (userId != null && !userId.isBlank()) {
            entries = revealAuditEntryRepository.findByUserIdOrderByRevealedAtDesc(userId, pageable);
        } else if (complaintNumber != null && !complaintNumber.isBlank()) {
            entries = revealAuditEntryRepository.findByComplaintNumberOrderByRevealedAtDesc(complaintNumber, pageable);
        } else {
            entries = revealAuditEntryRepository.findAll(pageable);
        }
        return ResponseEntity.ok(Map.of("success", true, "data", entries.getContent(),
                "totalElements", entries.getTotalElements()));
    }

    // ─────────────────── Credential revocation (UST877) ───────────────────

    @PostMapping("/revocations")
    public ResponseEntity<?> revoke(@RequestBody Map<String, String> body, HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        String username = body.get("username");
        if (username == null || username.isBlank()) {
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "A username is required."));
        }
        if (username.trim().equals(identity.getUserId())) {
            // Self-revocation would lock the acting administrator out mid-session.
            return ResponseEntity.badRequest().body(
                    Map.of("success", false, "message", "You cannot revoke your own access."));
        }

        CredentialRevocationService.RevocationResult result = credentialRevocationService.revoke(
                username, body.getOrDefault("reason", CredentialRevocationService.REASON_ADMIN),
                body.get("notes"), identity.getUserId());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("username", username);
        payload.put("accountDisabled", result.accountDisabled());
        payload.put("sessionsTerminated", result.sessionsTerminated());
        payload.put("sessionsTerminatedCount", result.sessionsTerminatedCount());
        payload.put("fullySuccessful", result.fullySuccessful());
        payload.put("detail", result.detail());

        // 207 when the local block succeeded but Keycloak did not fully comply, so the caller knows
        // the state is partial rather than assuming a clean revocation.
        HttpStatus status = result.fullySuccessful() ? HttpStatus.OK : HttpStatus.MULTI_STATUS;
        return ResponseEntity.status(status).body(Map.of(
                "success", true,
                "message", result.fullySuccessful()
                        ? "Access revoked and all sessions terminated."
                        : "Access blocked locally, but Keycloak did not fully confirm. Review the detail.",
                "data", payload));
    }

    @DeleteMapping("/revocations/{username}")
    public ResponseEntity<?> restore(@PathVariable String username, HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        credentialRevocationService.restore(username, identity.getUserId());
        return ResponseEntity.ok(Map.of("success", true, "message", "Access restored for " + username));
    }

    @GetMapping("/revocations")
    public ResponseEntity<?> revocations(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size,
                                         HttpServletRequest request) {
        if (requireAdmin(request) == null) {
            return forbidden();
        }
        var revocations = credentialRevocationRepository.findByOrderByRevokedAtDesc(
                PageRequest.of(page, Math.min(size, 200)));
        return ResponseEntity.ok(Map.of("success", true, "data", revocations.getContent(),
                "totalElements", revocations.getTotalElements()));
    }

    // ─────────────────────── Retention (UST890) ───────────────────────

    @GetMapping("/retention/policies")
    public ResponseEntity<?> policies(HttpServletRequest request) {
        if (requireAdmin(request) == null) {
            return forbidden();
        }
        List<RetentionPolicy> policies = retentionPolicyRepository.findAll();
        List<Map<String, Object>> enriched = policies.stream().map(p -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", p.getId());
            row.put("category", p.getCategory());
            row.put("targetTable", p.getTargetTable());
            row.put("retentionDays", p.getRetentionDays());
            row.put("auditCategory", p.isAuditCategory());
            row.put("redactInsteadOfDelete", p.isRedactInsteadOfDelete());
            row.put("enabled", p.isEnabled());
            row.put("description", p.getDescription());
            try {
                row.put("rowsPastRetention", retentionService.preview(p));
            } catch (Exception e) {
                row.put("rowsPastRetention", null);
                row.put("previewError", e.getMessage());
            }
            return row;
        }).toList();

        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", enriched,
                "destructiveEnabled",
                        systemConfigService.getBoolean(RetentionService.CFG_DESTRUCTIVE, false)));
    }

    @PutMapping("/retention/policies/{category}")
    public ResponseEntity<?> updatePolicy(@PathVariable String category,
                                          @RequestBody Map<String, Object> body,
                                          HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        return retentionPolicyRepository.findByCategory(category)
                .map(policy -> {
                    if (body.get("retentionDays") instanceof Number days) {
                        int value = days.intValue();
                        if (value <= 0) {
                            return ResponseEntity.badRequest().body(
                                    Map.of("success", false, "message", "Retention days must be positive."));
                        }
                        policy.setRetentionDays(value);
                    }
                    if (body.get("enabled") instanceof Boolean enabled) {
                        policy.setEnabled(enabled);
                    }
                    policy.setUpdatedBy(identity.getUserId());
                    retentionPolicyRepository.save(policy);
                    return ResponseEntity.ok(Map.of("success", true, "message", "Policy updated"));
                })
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "No policy for category " + category)));
    }

    @PostMapping("/retention/run")
    public ResponseEntity<?> runRetention(@RequestParam(required = false) String category,
                                          HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        Object data = (category == null || category.isBlank())
                ? retentionService.runAll(identity.getUserId())
                : retentionService.runCategory(category, identity.getUserId());

        boolean destructive = systemConfigService.getBoolean(RetentionService.CFG_DESTRUCTIVE, false);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "dryRun", !destructive,
                "message", destructive
                        ? "Retention executed."
                        : "Dry run only — no data was deleted. Enable "
                          + RetentionService.CFG_DESTRUCTIVE + " to purge for real.",
                "data", data));
    }

    @GetMapping("/retention/deletion-log")
    public ResponseEntity<?> deletionLog(@RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "50") int size,
                                         HttpServletRequest request) {
        if (requireAdmin(request) == null) {
            return forbidden();
        }
        Page<DeletionLog> logs = deletionLogRepository.findByOrderByExecutedAtDesc(
                PageRequest.of(page, Math.min(size, 200)));
        return ResponseEntity.ok(Map.of("success", true, "data", logs.getContent(),
                "totalElements", logs.getTotalElements()));
    }

    // ───────────────────── Tunable thresholds ─────────────────────

    @GetMapping("/config")
    public ResponseEntity<?> config(HttpServletRequest request) {
        if (requireAdmin(request) == null) {
            return forbidden();
        }
        return ResponseEntity.ok(Map.of("success", true,
                "data", systemConfigService.findByPrefix("cms.security.")));
    }

    @PutMapping("/config/{key}")
    public ResponseEntity<?> updateConfig(@PathVariable String key,
                                          @RequestBody Map<String, String> body,
                                          HttpServletRequest request) {
        RequestIdentity identity = requireAdmin(request);
        if (identity == null) {
            return forbidden();
        }
        // Restricting the prefix keeps this endpoint from becoming a general-purpose editor for
        // unrelated application config.
        if (!key.startsWith("cms.security.")) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false, "message", "Only cms.security.* keys can be changed here."));
        }
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "A value is required."));
        }
        systemConfigService.upsert(key, value, identity.getUserId());
        return ResponseEntity.ok(Map.of("success", true, "message", key + " updated"));
    }

    private RequestIdentity requireAdmin(HttpServletRequest request) {
        RequestIdentity identity = requestIdentityResolver.resolve(request);
        if (identity == null || !identity.hasAnyRole(ADMIN_ROLE)) {
            return null;
        }
        return identity;
    }

    private ResponseEntity<Map<String, Object>> forbidden() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("error", "FORBIDDEN");
        body.put("message", "Administrator access is required.");
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }
}
