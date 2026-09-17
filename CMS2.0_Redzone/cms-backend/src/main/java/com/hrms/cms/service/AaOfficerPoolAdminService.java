package com.hrms.cms.service;

import com.hrms.cms.entity.AaAssignmentAudit;
import com.hrms.cms.entity.AaAssignmentRecord;
import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.repository.AaAssignmentAuditRepository;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.AaWorkloadRepository;
import com.hrms.cms.security.AaIdentityResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AA Admin operations on the officer pool: thresholds, activation, skills, rebalancing.
 *
 * Every method here mutates state that decides who receives citizen work, so each one writes an audit
 * row carrying the acting admin, a timestamp and a reason, inside the same transaction as the change.
 * The actor is always resolved from the JWT — accepting one from the caller is what made attribution
 * spoofable before.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaOfficerPoolAdminService {

    private final AaOfficerPoolRepository poolRepository;
    private final AaWorkloadRepository workloadRepository;
    private final AaAssignmentAuditRepository auditRepository;
    private final AaAssignmentEngine assignmentEngine;
    private final SystemConfigService systemConfigService;
    private final AaIdentityResolver identityResolver;
    private final NotificationService notificationService;

    /** Pool listing with LIVE workload, not the drifting stored counter. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPool(String roleGroup) {
        List<AaOfficerPool> officers = roleGroup == null || roleGroup.isBlank()
                ? poolRepository.findAll()
                : poolRepository.findByRoleGroupOrderByUserIdAsc(roleGroup.trim());

        List<Map<String, Object>> out = new ArrayList<>();
        for (AaOfficerPool officer : officers) {
            int live = assignmentEngine.currentWorkload(officer.getUserId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", officer.getId());
            row.put("userId", officer.getUserId());
            row.put("displayName", officer.getDisplayName());
            row.put("roleGroup", officer.getRoleGroup());
            row.put("regionalOffice", officer.getRegionalOffice());
            row.put("active", officer.isActive());
            row.put("onLeave", officer.isOnLeave());
            row.put("threshold", officer.thresholdOrZero());
            row.put("unlimited", officer.hasUnlimitedThreshold());
            row.put("currentWorkload", live);
            row.put("skillLanguages", officer.getSkillLanguages());
            // Eligibility is reported, not inferred by the client: the same three conditions the engine
            // applies, so the console cannot disagree with what assignment will actually do.
            row.put("eligible", officer.isActive() && !officer.isOnLeave()
                    && (officer.hasUnlimitedThreshold() || live < officer.thresholdOrZero()));
            row.put("atThreshold", !officer.hasUnlimitedThreshold() && live >= officer.thresholdOrZero());
            out.add(row);
        }
        return out;
    }

    /**
     * Stories 2 and 6: change one officer's threshold. Takes effect on the next assignment because
     * eligibility is evaluated per assignment, never cached.
     */
    @Transactional
    public Map<String, Object> updateThreshold(String userId, int newThreshold, String reason,
                                               boolean unlimited) {
        if (newThreshold < 0) {
            throw new IllegalArgumentException("aa.pool.error_threshold_negative");
        }
        // 0 means UNLIMITED in this table, which is the opposite of what an admin typing 0 to stop
        // giving someone work would expect. Requiring an explicit flag makes the dangerous reading
        // impossible to reach by accident; for a regulator, silently granting unbounded caseload is the
        // wrong way to fail.
        if (newThreshold == 0 && !unlimited) {
            throw new IllegalArgumentException("aa.pool.error_threshold_zero_ambiguous");
        }
        AaOfficerPool officer = poolRepository.findByUserId(requireUserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("aa.pool.error_officer_not_found"));

        int oldThreshold = officer.thresholdOrZero();
        officer.setMaxWorkload(newThreshold);
        poolRepository.save(officer);

        audit(AaAssignmentAudit.ACTION_THRESHOLD_CHANGED, officer.getUserId(), officer.getRoleGroup(),
                null, "maxWorkload", String.valueOf(oldThreshold), String.valueOf(newThreshold), reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", officer.getUserId());
        result.put("previousThreshold", oldThreshold);
        result.put("threshold", newThreshold);
        result.put("currentWorkload", assignmentEngine.currentWorkload(officer.getUserId()));
        result.put("messageKey", "aa.pool.threshold_updated");

        // Story 7: lowering a threshold can leave an officer over it. Whether that rebalances
        // immediately or waits for the admin is configurable, because auto-moving citizen work off an
        // officer without warning is not always wanted.
        boolean autoRebalance = systemConfigService.getBoolean(
                AaAssignmentEngineImpl.CFG_AUTO_REBALANCE, false);
        int over = overThresholdCount(officer);
        result.put("overThreshold", over);
        result.put("rebalanced", false);
        if (over > 0 && autoRebalance) {
            int moved = rebalance(officer.getRoleGroup(), "threshold lowered for " + officer.getUserId());
            result.put("rebalanced", true);
            result.put("recordsMoved", moved);
        } else if (over > 0) {
            result.put("messageKey", "aa.pool.threshold_updated_rebalance_pending");
        }
        return result;
    }

    /** Story 8 support: set which languages an officer can handle. */
    @Transactional
    public Map<String, Object> updateSkills(String userId, String skillLanguages, String reason) {
        AaOfficerPool officer = poolRepository.findByUserId(requireUserId(userId))
                .orElseThrow(() -> new IllegalArgumentException("aa.pool.error_officer_not_found"));

        String normalised = normaliseLanguages(skillLanguages);
        String previous = officer.getSkillLanguages();
        officer.setSkillLanguages(normalised);
        poolRepository.save(officer);

        audit(AaAssignmentAudit.ACTION_SKILL_CHANGED, officer.getUserId(), officer.getRoleGroup(),
                null, "skillLanguages", previous, normalised, reason);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", officer.getUserId());
        result.put("skillLanguages", normalised);
        result.put("messageKey", "aa.pool.skills_updated");
        return result;
    }

    /**
     * Story 10: what an admin must be told BEFORE an officer is made inactive.
     *
     * Read-only on purpose. Deactivating in the same call as the warning is what removes the admin's
     * chance to cancel — the existing officer-deactivation service forces reassignment instead of
     * offering a choice, which is the behaviour this replaces for AA.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> previewDeactivation(List<String> userIds) {
        List<Map<String, Object>> impacted = new ArrayList<>();
        int totalPending = 0;

        for (String rawUserId : userIds) {
            // A null or blank entry is skipped rather than dereferenced: a malformed list must not 500.
            if (rawUserId == null || rawUserId.isBlank()) {
                continue;
            }
            AaOfficerPool officer = poolRepository.findByUserId(rawUserId.trim()).orElse(null);
            if (officer == null) {
                continue;
            }
            List<AaAssignmentRecord> held =
                    workloadRepository.findByAssignedUserIdAndReleasedAtIsNullOrderByAssignedAtAsc(
                            officer.getUserId());
            totalPending += held.size();

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", officer.getUserId());
            row.put("displayName", officer.getDisplayName());
            row.put("roleGroup", officer.getRoleGroup());
            row.put("pendingCount", held.size());
            row.put("pendingAppeals", held.stream().map(AaAssignmentRecord::getAppealNumber).toList());
            impacted.add(row);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("officers", impacted);
        result.put("totalPending", totalPending);
        result.put("requiresConfirmation", totalPending > 0);
        result.put("messageKey", totalPending > 0
                ? "aa.pool.deactivate_warning_pending_work"
                : "aa.pool.deactivate_no_pending_work");
        return result;
    }

    /**
     * Story 5: bulk activate or deactivate.
     *
     * Per-officer isolation: one bad user id must not abort the rest of the batch, because a partially
     * applied bulk action the admin cannot see is worse than a reported failure.
     *
     * {@code confirmed} is the story-10 gate. Deactivating an officer holding pending work requires it,
     * so the alert cannot be bypassed by calling the API directly — a browser-only confirmation is not
     * a control.
     */
    @Transactional
    public Map<String, Object> bulkSetActive(List<String> userIds, boolean active, boolean confirmed,
                                             String reason) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("aa.pool.error_no_officers_selected");
        }
        List<Map<String, Object>> succeeded = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();

        for (String rawUserId : userIds) {
            String userId = rawUserId == null ? "" : rawUserId.trim();
            try {
                AaOfficerPool officer = poolRepository.findByUserId(userId)
                        .orElseThrow(() -> new IllegalArgumentException("aa.pool.error_officer_not_found"));

                long pending = workloadRepository.countByAssignedUserIdAndReleasedAtIsNull(userId);
                if (!active && pending > 0 && !confirmed) {
                    failed.add(failure(userId, "aa.pool.error_confirmation_required", pending));
                    continue;
                }

                if (officer.isActive() == active) {
                    // Not an error, but reporting it as changed would misrepresent what happened.
                    succeeded.add(outcome(userId, active, pending, "aa.pool.activation_unchanged"));
                    continue;
                }

                officer.setActive(active);
                poolRepository.save(officer);

                audit(AaAssignmentAudit.ACTION_ACTIVATION_CHANGED, userId, officer.getRoleGroup(),
                        null, "active", String.valueOf(!active), String.valueOf(active), reason);

                if (!active && pending > 0) {
                    // The work stays with them: silently moving it would hide from the admin that a
                    // deactivated officer still holds citizen records. The console surfaces the count
                    // so it can be reassigned deliberately.
                    log.warn("AA pool: {} deactivated while still holding {} record(s)", userId, pending);
                    notifyAdmins("aa.pool.notify_deactivated_with_pending", userId, pending);
                }
                succeeded.add(outcome(userId, active, pending, "aa.pool.activation_updated"));
            } catch (RuntimeException e) {
                failed.add(failure(userId, e.getMessage(), 0));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("requested", userIds.size());
        result.put("succeeded", succeeded);
        result.put("failed", failed);
        result.put("messageKey", failed.isEmpty()
                ? "aa.pool.bulk_activation_complete"
                : "aa.pool.bulk_activation_partial");
        return result;
    }

    /**
     * Story 7: move records off officers who are over threshold, back through the engine.
     *
     * Newest placements move first: the oldest have been queued longest and are closest to their
     * deadline, so disturbing them is the worse outcome.
     */
    @Transactional
    public int rebalance(String roleGroup, String reason) {
        List<AaAssignmentRecord> held = workloadRepository.findHeldInRoleGroup(roleGroup);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (AaAssignmentRecord record : held) {
            counts.merge(record.getAssignedUserId(), 1, Integer::sum);
        }

        int moved = 0;
        for (AaAssignmentRecord record : held) {
            AaOfficerPool holder = poolRepository.findByUserId(record.getAssignedUserId()).orElse(null);
            if (holder == null || holder.hasUnlimitedThreshold()) {
                continue;
            }
            int load = counts.getOrDefault(holder.getUserId(), 0);
            if (load <= holder.thresholdOrZero()) {
                continue;
            }

            var reassigned = assignmentEngine.assign(com.hrms.cms.dto.AaAssignmentRequest.builder()
                    .appealNumber(record.getAppealNumber())
                    .roleGroup(roleGroup)
                    .build());

            // Only count it if the record actually changed hands. An exhausted pool can legitimately
            // hand it straight back, and reporting that as a move would overstate what happened.
            if (reassigned.isAssigned()
                    && !reassigned.getAssignedUserId().equals(holder.getUserId())) {
                counts.merge(holder.getUserId(), -1, Integer::sum);
                counts.merge(reassigned.getAssignedUserId(), 1, Integer::sum);
                moved++;
            }
        }

        audit(AaAssignmentAudit.ACTION_REBALANCE, null, roleGroup, null, "recordsMoved", null,
                String.valueOf(moved), reason);
        return moved;
    }

    @Transactional(readOnly = true)
    public List<AaAssignmentAudit> auditFor(String userId) {
        return auditRepository.findBySubjectUserIdOrderByPerformedAtDescIdDesc(userId);
    }

    private int overThresholdCount(AaOfficerPool officer) {
        if (officer.hasUnlimitedThreshold()) {
            return 0;
        }
        int live = assignmentEngine.currentWorkload(officer.getUserId());
        return Math.max(0, live - officer.thresholdOrZero());
    }

    private String normaliseLanguages(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        List<String> codes = new ArrayList<>();
        for (String part : raw.split(",")) {
            String code = part.trim().toLowerCase();
            if (!code.isEmpty() && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes.isEmpty() ? null : String.join(",", codes);
    }

    /** Story 10 tail: the admin who needs to act is told through the same seam as every other alert. */
    private void notifyAdmins(String messageKey, String subjectUserId, long pending) {
        try {
            notificationService.send("AA_ADMIN", "CONFIGURATION_ALERT", messageKey,
                    subjectUserId + ":" + pending, subjectUserId, "OFFICER_POOL", "/aa/admin");
        } catch (Exception e) {
            log.warn("AA pool: could not alert AA_ADMIN about {}: {}", subjectUserId, e.getMessage());
        }
    }

    private Map<String, Object> outcome(String userId, boolean active, long pending, String messageKey) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("active", active);
        row.put("pendingCount", pending);
        row.put("messageKey", messageKey);
        return row;
    }

    private Map<String, Object> failure(String userId, String messageKey, long pending) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userId", userId);
        row.put("messageKey", messageKey == null ? "aa.pool.error_unknown" : messageKey);
        row.put("pendingCount", pending);
        return row;
    }

    private String requireUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("aa.pool.error_officer_not_found");
        }
        return userId.trim();
    }

    private void audit(String action, String subjectUserId, String roleGroup, String appealNumber,
                       String fieldName, String oldValue, String newValue, String reason) {
        auditRepository.save(AaAssignmentAudit.builder()
                .action(action)
                .subjectUserId(subjectUserId)
                .roleGroup(roleGroup)
                .appealNumber(appealNumber)
                .fieldName(fieldName)
                .oldValue(oldValue)
                .newValue(newValue)
                .reason(reason)
                .performedBy(orSystem(identityResolver.resolveActor()))
                .performedByRole(identityResolver.resolveAaRole())
                .performedAt(LocalDateTime.now())
                .build());
    }

    private String orSystem(String actor) {
        return actor == null || actor.isBlank() ? "SYSTEM" : actor;
    }
}
