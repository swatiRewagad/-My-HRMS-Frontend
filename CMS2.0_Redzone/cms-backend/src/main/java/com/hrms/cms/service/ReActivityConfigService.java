package com.hrms.cms.service;

import com.hrms.cms.entity.ConfigAuditLog;
import com.hrms.cms.entity.ConfigChangeRequest;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.entity.SystemConfig;
import com.hrms.cms.repository.ConfigAuditLogRepository;
import com.hrms.cms.repository.ConfigChangeRequestRepository;
import com.hrms.cms.repository.SystemConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Maker-checker for RE Activity nudge-threshold configuration (UST851).
 *
 * Modelled on the only existing precedent in the tree — cms-rules-service's
 * {@code RuleManagementService.activateRule}, which refuses an approval whose actor equals the
 * creator. Extended here with a persisted request record, because unlike a rule activation there is
 * no draft entity to hang the proposal off.
 *
 * <p><b>Known limitation.</b> The approver identity comes from the request, and this codebase does
 * not yet enforce authentication: {@code SecurityConfig} is {@code anyRequest().permitAll()} and the
 * role-guard aspects proceed when no roles are present. A caller can therefore present an arbitrary
 * second identity and self-approve. The separation-of-duties check below is real logic, but it is
 * only a genuine control once authentication is enforced; until then treat it as defence in depth
 * rather than a barrier.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReActivityConfigService {

    static final String KEY_PREFIX = "cms.re.activity.";
    static final int MIN_NUDGE_DAYS = 1;
    static final int MAX_NUDGE_DAYS = 365;

    private final SystemConfigRepository systemConfigRepository;
    private final ConfigChangeRequestRepository changeRequestRepository;
    private final ConfigAuditLogRepository configAuditLogRepository;

    /**
     * Stages a threshold change. Nothing takes effect until a different administrator approves it.
     */
    @Transactional
    public ConfigChangeRequest requestChange(String key, String proposedValue, String reason, String requestedBy) {
        validateKey(key);
        validateNudgeValue(key, proposedValue);
        requireActor(requestedBy, "A requesting administrator must be identified");

        changeRequestRepository.findByConfigKeyAndStatus(key, "PENDING").ifPresent(existing -> {
            throw new IllegalStateException(
                    "A change to " + key + " is already awaiting approval (request " + existing.getId() + ")");
        });

        String currentValue = systemConfigRepository.findByConfigKey(key)
                .map(SystemConfig::getConfigValue)
                .orElse(null);

        if (proposedValue.trim().equals(currentValue)) {
            throw new IllegalArgumentException("Proposed value is already in force for " + key);
        }

        ConfigChangeRequest saved = changeRequestRepository.save(ConfigChangeRequest.builder()
                .configKey(key)
                .currentValue(currentValue)
                .proposedValue(proposedValue.trim())
                .reason(reason)
                .requestedBy(requestedBy.trim())
                .status("PENDING")
                .build());

        log.info("Config change requested for {} ({} → {}) by {}", key, currentValue, proposedValue, requestedBy);
        return saved;
    }

    /**
     * Applies a staged change. Rejects self-approval, which is the whole point of the control.
     */
    @Transactional
    public ConfigChangeRequest approve(Long requestId, String approvedBy, String decisionReason) {
        requireActor(approvedBy, "An approving administrator must be identified");
        ConfigChangeRequest req = requirePending(requestId);

        // Case-insensitive: Keycloak usernames are case-insensitive, so a case variant is the same
        // person and must not become a way around the control.
        if (req.getRequestedBy().equalsIgnoreCase(approvedBy.trim())) {
            throw new IllegalStateException(
                    "Maker-Checker violation: the requester cannot approve their own configuration change");
        }

        // Re-validate at apply time: the bounds could have been tightened, or the live value could
        // have moved since the request was raised, in which case the approver is not approving what
        // they think they are.
        validateNudgeValue(req.getConfigKey(), req.getProposedValue());

        SystemConfig config = systemConfigRepository.findByConfigKey(req.getConfigKey())
                .orElseGet(() -> SystemConfig.builder()
                        .configKey(req.getConfigKey())
                        .description("RE Activity nudge threshold (UST850, UST851)")
                        .build());

        String liveValue = config.getConfigValue();
        if (liveValue != null && !liveValue.equals(req.getCurrentValue())) {
            throw new IllegalStateException("Value of " + req.getConfigKey()
                    + " changed since this request was raised (now " + liveValue
                    + "). Raise a fresh request.");
        }

        config.setConfigValue(req.getProposedValue());
        config.setUpdatedBy(approvedBy.trim());
        systemConfigRepository.save(config);

        configAuditLogRepository.save(ConfigAuditLog.builder()
                .configKey(req.getConfigKey())
                .oldValue(liveValue)
                .newValue(req.getProposedValue())
                .changedBy(approvedBy.trim() + " (approved request " + req.getId()
                        + " raised by " + req.getRequestedBy() + ")")
                .build());

        req.setStatus("APPROVED");
        req.setDecidedBy(approvedBy.trim());
        req.setDecidedAt(LocalDateTime.now());
        req.setDecisionReason(decisionReason);

        log.info("Config change {} approved by {}: {} = {}", req.getId(), approvedBy,
                req.getConfigKey(), req.getProposedValue());

        // Records already in a status keep the threshold snapshotted when they entered it, so this
        // only affects transitions from here on (UST850).
        return changeRequestRepository.save(req);
    }

    @Transactional
    public ConfigChangeRequest reject(Long requestId, String rejectedBy, String decisionReason) {
        requireActor(rejectedBy, "A deciding administrator must be identified");
        if (decisionReason == null || decisionReason.isBlank()) {
            throw new IllegalArgumentException("A reason is required when rejecting a configuration change");
        }
        ConfigChangeRequest req = requirePending(requestId);

        if (req.getRequestedBy().equalsIgnoreCase(rejectedBy.trim())) {
            // Withdrawing your own request is legitimate; it just is not an independent rejection.
            req.setStatus("WITHDRAWN");
        } else {
            req.setStatus("REJECTED");
        }
        req.setDecidedBy(rejectedBy.trim());
        req.setDecidedAt(LocalDateTime.now());
        req.setDecisionReason(decisionReason);

        log.info("Config change {} {} by {}", req.getId(), req.getStatus(), rejectedBy);
        return changeRequestRepository.save(req);
    }

    @Transactional(readOnly = true)
    public List<ConfigChangeRequest> listPending() {
        return changeRequestRepository.findByStatusOrderByRequestedAtDesc("PENDING");
    }

    @Transactional(readOnly = true)
    public List<ConfigChangeRequest> historyFor(String key) {
        return changeRequestRepository.findByConfigKeyOrderByRequestedAtDesc(key);
    }

    private ConfigChangeRequest requirePending(Long requestId) {
        ConfigChangeRequest req = changeRequestRepository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Change request not found: " + requestId));
        if (!"PENDING".equals(req.getStatus())) {
            throw new IllegalStateException("Change request " + requestId + " is already " + req.getStatus());
        }
        return req;
    }

    private void validateKey(String key) {
        if (key == null || !key.startsWith(KEY_PREFIX)) {
            throw new IllegalArgumentException("Config key must start with '" + KEY_PREFIX + "'");
        }
        boolean known = key.equals(ReActivityStatusService.CONFIG_DEFAULT_KEY)
                || ReActivityStatus.nudgeableStatuses().stream()
                        .anyMatch(s -> key.equals(ReActivityStatusService.CONFIG_PREFIX + s.name().toLowerCase()))
                || key.equals(ReActivitySweepService.CONFIG_NUDGE_ENABLED)
                || key.equals(ReActivitySweepService.CONFIG_ESCALATION_ENABLED);
        if (!known) {
            throw new IllegalArgumentException("Unknown RE activity config key: " + key);
        }
    }

    private void validateNudgeValue(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("A value is required");
        }
        if (key.equals(ReActivitySweepService.CONFIG_NUDGE_ENABLED)
                || key.equals(ReActivitySweepService.CONFIG_ESCALATION_ENABLED)) {
            String v = value.trim();
            if (!"true".equalsIgnoreCase(v) && !"false".equalsIgnoreCase(v)) {
                throw new IllegalArgumentException("Value for " + key + " must be true or false");
            }
            return;
        }
        int days;
        try {
            days = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Value must be a whole number of days");
        }
        if (days < MIN_NUDGE_DAYS || days > MAX_NUDGE_DAYS) {
            throw new IllegalArgumentException(
                    "Nudge threshold must be between " + MIN_NUDGE_DAYS + " and " + MAX_NUDGE_DAYS + " days");
        }
    }

    private void requireActor(String actor, String message) {
        if (actor == null || actor.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
