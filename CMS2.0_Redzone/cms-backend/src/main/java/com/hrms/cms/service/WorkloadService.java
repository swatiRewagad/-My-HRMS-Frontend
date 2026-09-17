package com.hrms.cms.service;

import com.hrms.cms.entity.EntityUser;
import com.hrms.cms.repository.EntityUserRepository;
import com.hrms.cms.repository.NodalOfficerRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one place RE nodal-officer workload is calculated (UST838).
 *
 * <p>UST838 requires the number in the reassignment popup and the number on the PNO dashboard to
 * agree. The reliable way to guarantee that is for there to be only one implementation, so both
 * callers enter here and neither counts anything itself. If a third surface needs the figure later,
 * it calls {@link #workloadFor} or {@link #workloadForEntity} — it does not write its own query.
 *
 * <p><b>Definition:</b> the count of records assigned to the officer, excluding drafts and closed
 * work. Two subtleties in that sentence:
 *
 * <ul>
 *   <li>"Assigned to me" on the RE side means {@code NODAL_OFFICER_RECORDS.assigned_to}. It is not
 *       {@code COMPLAINTS.assigned_officer}, which holds the <em>RBI</em>-side owner — counting that
 *       would report an RBI officer's caseload as if it were the entity's.</li>
 *   <li>The excluded status list comes from SYSTEM_CONFIG, not from Java. This codebase already
 *       contains four disagreeing hardcoded CLOSED_STATUSES lists (WorkflowController,
 *       NotificationScheduledTasks, AppealWorkflowService, AppealController); adding a fifth would
 *       mean the workload figure and the rest of the system disagree about what "closed" is, and
 *       operations could not correct it without a redeploy.</li>
 * </ul>
 *
 * <p>Comparison is upper-cased on both sides because this column has been written in mixed case by
 * different callers over time; a case-sensitive NOT IN would silently count closed records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkloadService {

    static final String CFG_EXCLUDED_STATUSES = "cms.reassign.workload.excluded_statuses";

    /**
     * Fallback only, used when the SYSTEM_CONFIG row is missing. Deliberately the union of the
     * closed-ish vocabularies plus the draft states, so an absent config row under-counts rather
     * than over-counts: showing a PNO less work than exists is a visible surprise, whereas silently
     * counting closed records inflates every workload figure and would go unnoticed.
     */
    private static final Set<String> DEFAULT_EXCLUDED = Set.of(
            "DRAFT", "CLOSED", "RESOLVED", "REJECTED", "WITHDRAWN",
            "ADJUDICATED", "CONCILIATED", "ORDER_PASSED", "NON_MAINTAINABLE");

    private final NodalOfficerRecordRepository recordRepository;
    private final EntityUserRepository entityUserRepository;
    private final SystemConfigService systemConfigService;

    /** One officer's workload. The reassignment popup calls this per candidate it displays. */
    @Transactional(readOnly = true)
    public int workloadFor(String entityCode, String userId) {
        if (entityCode == null || entityCode.isBlank() || userId == null || userId.isBlank()) {
            return 0;
        }
        return (int) recordRepository.countActiveForOfficer(entityCode, userId, excludedStatuses());
    }

    /**
     * Every active officer in the entity with their workload, ordered by display name.
     *
     * <p>Officers with no work appear with zero rather than being omitted: the dashboard has to show
     * an idle officer, and a GROUP BY alone cannot produce a row for someone who has no records.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> workloadForEntity(String entityCode) {
        Map<String, Integer> workloads = new LinkedHashMap<>();
        if (entityCode == null || entityCode.isBlank()) {
            return workloads;
        }

        for (EntityUser user : entityUserRepository
                .findByEntityCodeAndActiveTrueOrderByDisplayNameAsc(entityCode)) {
            workloads.put(user.getUserId(), 0);
        }

        for (Object[] row : recordRepository.countActiveGroupedByOfficer(entityCode, excludedStatuses())) {
            String userId = row[0] == null ? null : row[0].toString();
            if (userId == null) {
                continue;
            }
            int count = row[1] == null ? 0 : ((Number) row[1]).intValue();
            // Records may be assigned to a user id that is not (or is no longer) in the directory.
            // Those are still real work, so they are reported rather than dropped.
            workloads.merge(userId, count, Integer::sum);
        }
        return workloads;
    }

    /**
     * The excluded-status set, upper-cased so the comparison is case-insensitive on both sides.
     * Read per call — SystemConfigService already caches with a short TTL, so an operator's change
     * takes effect promptly without this class holding its own copy.
     */
    Set<String> excludedStatuses() {
        Set<String> configured = systemConfigService.getSet(CFG_EXCLUDED_STATUSES, DEFAULT_EXCLUDED);
        Set<String> normalised = new LinkedHashSet<>();
        for (String status : configured) {
            if (status != null && !status.isBlank()) {
                normalised.add(status.trim().toUpperCase());
            }
        }
        // An empty IN list is invalid in JPQL and would make every workload query fail, so an
        // operator blanking the config row must not be able to break the endpoint.
        return normalised.isEmpty() ? DEFAULT_EXCLUDED : normalised;
    }

    /**
     * Workload for a specific list of user ids, in the order given — what the popup needs once it
     * has filtered its candidates. Reuses {@link #workloadForEntity} so it cannot drift from the
     * dashboard figure.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> workloadForUsers(String entityCode, List<String> userIds) {
        Map<String, Integer> all = workloadForEntity(entityCode);
        Map<String, Integer> subset = new LinkedHashMap<>();
        for (String userId : userIds) {
            subset.put(userId, all.getOrDefault(userId, 0));
        }
        return subset;
    }
}
