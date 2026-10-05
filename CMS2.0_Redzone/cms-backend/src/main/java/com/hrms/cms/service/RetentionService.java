package com.hrms.cms.service;

import com.hrms.cms.entity.DeletionLog;
import com.hrms.cms.entity.RetentionPolicy;
import com.hrms.cms.repository.DeletionLogRepository;
import com.hrms.cms.repository.RetentionPolicyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Applies retention policies and records every run (UST890).
 *
 * Ships in dry-run mode. The purge reports what it would remove and deletes nothing until
 * {@code cms.security.retention.destructive_enabled} is turned on deliberately, because the
 * database is shared and an over-broad policy would destroy real complaints irreversibly.
 *
 * Audit categories carry their own longer retention and are evaluated independently, so purging
 * operational data never removes the record of what was done to it.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RetentionService {

    public static final String CFG_ENABLED = "cms.security.retention.enabled";
    public static final String CFG_DESTRUCTIVE = "cms.security.retention.destructive_enabled";
    public static final String CFG_BATCH_LIMIT = "cms.security.retention.batch_limit";

    /**
     * How many days ahead of expiry a row is surfaced for human review.
     *
     * Configurable, because how much notice a reviewer needs is an operational decision, not a
     * constant. The default is 90 days: long enough that a quarterly review cycle sees every record
     * before it expires.
     */
    public static final String CFG_REVIEW_WINDOW_DAYS = "cms.security.retention.review_window_days";

    private static final int DEFAULT_REVIEW_WINDOW_DAYS = 90;

    /**
     * Tables a policy is permitted to touch.
     *
     * Policies name their own table, and those rows are editable by an admin. Without a compiled-in
     * allowlist a mistyped or malicious policy row could target any table in the schema, so an
     * unrecognised name is refused rather than executed.
     */
    private static final Set<String> ALLOWED_TABLES = Set.of(
            "AUDIT_LOG", "CONFIG_AUDIT_LOG", "PII_REVEAL_AUDIT", "SECURITY_EVENT", "SECURITY_ALERT",
            "DELETION_LOG", "IN_APP_NOTIFICATIONS", "COMPLAINT_HISTORY", "COMPLAINTS",
            "COMPLAINT_QUERY", "COMPLAINT_QUERY_MESSAGE", "OUTBOX_EVENT", "ATTACHMENT_METADATA",
            // COMPLAINT_TIMELINE is the table that actually exists for complaint history in this
            // schema; COMPLAINT_HISTORY above was whitelisted but has no table behind it. Without
            // this entry the 7-year history policy seeded by V114 would be refused at run time with
            // "targets a table that is not permitted" — i.e. the obligation would be declared and
            // then be unenforceable, which is worse than not declaring it.
            "COMPLAINT_TIMELINE",
            "COMPLAINT_ATTACHMENTS", "ELIGIBILITY_AUDIT", "COMPLAINT_DRAFT");

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private final RetentionPolicyRepository retentionPolicyRepository;
    private final DeletionLogRepository deletionLogRepository;
    private final SystemConfigService systemConfigService;
    private final JdbcTemplate jdbcTemplate;

    public record RetentionRunResult(String category,
                                     String targetTable,
                                     int rowsAffected,
                                     boolean dryRun,
                                     String action,
                                     boolean succeeded,
                                     String detail) {}

    /**
     * Nightly sweep.
     *
     * Note: cms-backend has no distributed lock, so if this ever runs multi-instance each node would
     * evaluate the same policies. That is harmless while dry-run is the default, but a real purge
     * needs ShedLock (the SHEDLOCK table already exists) before it is switched on in a clustered
     * deployment.
     */
    @Scheduled(cron = "${cms.security.retention.cron:0 30 2 * * *}")
    public void scheduledRun() {
        if (!systemConfigService.getBoolean(CFG_ENABLED, true)) {
            log.debug("Retention sweep skipped: disabled by configuration");
            return;
        }
        List<RetentionRunResult> results = runAll("SCHEDULER");
        log.info("Retention sweep finished: {} policy(ies) evaluated", results.size());
    }

    public List<RetentionRunResult> runAll(String executedBy) {
        List<RetentionRunResult> results = new ArrayList<>();
        for (RetentionPolicy policy : retentionPolicyRepository.findByEnabledTrue()) {
            results.add(run(policy, executedBy));
        }
        return results;
    }

    public RetentionRunResult runCategory(String category, String executedBy) {
        RetentionPolicy policy = retentionPolicyRepository.findByCategory(category)
                .orElseThrow(() -> new IllegalArgumentException("No retention policy for category " + category));
        return run(policy, executedBy);
    }

    /** Counts what the policy would affect without changing anything. */
    public int preview(RetentionPolicy policy) {
        validate(policy);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.getRetentionDays());
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + policy.getTargetTable()
                        + " WHERE " + policy.getTimestampColumn() + " < ?",
                Integer.class, cutoff);
        return count == null ? 0 : count;
    }

    /** The review window currently in force, in days. */
    public int reviewWindowDays() {
        int days = systemConfigService.getInt(CFG_REVIEW_WINDOW_DAYS, DEFAULT_REVIEW_WINDOW_DAYS);
        return days > 0 ? days : DEFAULT_REVIEW_WINDOW_DAYS;
    }

    /**
     * Counts rows that are APPROACHING the end of the retention period but have not reached it —
     * the forward-looking signal a human review has to act on.
     *
     * <p>WHY THIS EXISTS: {@link #preview} is backward-looking. It counts rows that are ALREADY past
     * retention, which is exactly too late to review: by the time a record appears in that number
     * the only remaining decision is whether to purge it, not whether purging it is correct. So
     * nothing anywhere surfaced "these records are about to expire, look at them first", and a purge
     * would run with no prior human review of anything.
     *
     * <p>The band is half-open on both sides and deliberately EXCLUDES the already-expired rows
     * {@code preview} counts, so the two numbers never double-count the same record and
     * "approaching" cannot be satisfied by a backlog of overdue ones.
     */
    public int approachingRetention(RetentionPolicy policy) {
        validate(policy);
        LocalDateTime now = LocalDateTime.now();
        // Older than this -> inside the review window.
        LocalDateTime reviewFrom = now.minusDays(policy.getRetentionDays() - (long) reviewWindowDays());
        // Older than this -> already expired, counted by preview() instead.
        LocalDateTime expired = now.minusDays(policy.getRetentionDays());

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + policy.getTargetTable()
                        + " WHERE " + policy.getTimestampColumn() + " < ?"
                        + "   AND " + policy.getTimestampColumn() + " >= ?",
                Integer.class, reviewFrom, expired);
        return count == null ? 0 : count;
    }

    @Transactional
    public RetentionRunResult run(RetentionPolicy policy, String executedBy) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(policy.getRetentionDays());
        boolean destructive = systemConfigService.getBoolean(CFG_DESTRUCTIVE, false);
        String action = policy.isRedactInsteadOfDelete() ? "REDACT" : "DELETE";

        try {
            validate(policy);
            int candidates = preview(policy);

            if (!destructive) {
                // Dry run still writes a log row: the point is an auditable record of what the policy
                // would do, reviewable before anyone enables destruction.
                return record(policy, cutoff, candidates, action, true, true, executedBy,
                        "Dry run — " + candidates + " row(s) are past retention and would be "
                                + action.toLowerCase() + "d. Nothing was changed.", null);
            }

            int limit = systemConfigService.getInt(CFG_BATCH_LIMIT, 5000);
            int affected = policy.isRedactInsteadOfDelete()
                    ? redact(policy, cutoff, limit)
                    : delete(policy, cutoff, limit);

            return record(policy, cutoff, affected, action, false, true, executedBy,
                    affected + " row(s) " + action.toLowerCase() + "d past a "
                            + policy.getRetentionDays() + "-day retention.", null);

        } catch (Exception e) {
            log.error("Retention run for {} failed: {}", policy.getCategory(), e.getMessage());
            return record(policy, cutoff, 0, action, !destructive, false, executedBy, null, e.getMessage());
        }
    }

    private int delete(RetentionPolicy policy, LocalDateTime cutoff, int limit) {
        return jdbcTemplate.update(
                "DELETE FROM " + policy.getTargetTable()
                        + " WHERE " + policy.getTimestampColumn() + " < ? LIMIT " + limit,
                cutoff);
    }

    private int redact(RetentionPolicy policy, LocalDateTime cutoff, int limit) {
        Set<String> columns = redactColumns(policy);
        if (columns.isEmpty()) {
            throw new IllegalStateException(
                    "Policy " + policy.getCategory() + " redacts but names no columns.");
        }
        String setClause = String.join(", ", columns.stream().map(c -> c + " = NULL").toList());
        return jdbcTemplate.update(
                "UPDATE " + policy.getTargetTable() + " SET " + setClause
                        + " WHERE " + policy.getTimestampColumn() + " < ? LIMIT " + limit,
                cutoff);
    }

    private Set<String> redactColumns(RetentionPolicy policy) {
        if (policy.getRedactColumns() == null || policy.getRedactColumns().isBlank()) {
            return Set.of();
        }
        Set<String> columns = new LinkedHashSet<>(Arrays.stream(policy.getRedactColumns().split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList());
        columns.forEach(this::requireSafeIdentifier);
        return columns;
    }

    /**
     * Table and column names are interpolated into SQL because they cannot be bound as parameters,
     * so each one is checked against the allowlist and an identifier pattern first.
     */
    private void validate(RetentionPolicy policy) {
        String table = policy.getTargetTable();
        requireSafeIdentifier(table);
        requireSafeIdentifier(policy.getTimestampColumn());

        if (!ALLOWED_TABLES.contains(table.toUpperCase())) {
            throw new IllegalStateException("Retention policy targets a table that is not permitted: " + table);
        }
        if (policy.getRetentionDays() <= 0) {
            throw new IllegalStateException("Retention days must be positive for " + policy.getCategory());
        }
    }

    private void requireSafeIdentifier(String identifier) {
        if (identifier == null || !SAFE_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalStateException("Unsafe SQL identifier in retention policy: " + identifier);
        }
    }

    private RetentionRunResult record(RetentionPolicy policy, LocalDateTime cutoff, int rows,
                                      String action, boolean dryRun, boolean succeeded,
                                      String executedBy, String details, String error) {
        deletionLogRepository.save(DeletionLog.builder()
                .category(policy.getCategory())
                .targetTable(policy.getTargetTable())
                .retentionDays(policy.getRetentionDays())
                .cutoffDate(cutoff)
                .rowsAffected(rows)
                .action(action)
                .dryRun(dryRun)
                .executedBy(executedBy == null ? "SYSTEM" : executedBy)
                .executedAt(LocalDateTime.now())
                .details(details)
                .succeeded(succeeded)
                .errorDetail(error)
                .build());

        return new RetentionRunResult(policy.getCategory(), policy.getTargetTable(), rows,
                dryRun, action, succeeded, succeeded ? details : error);
    }
}
