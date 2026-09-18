package com.hrms.cms.service;

import com.hrms.cms.entity.OfficeThresholdConfig;
import com.hrms.cms.repository.OfficeThresholdConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Routes a complaint to an ombudsman office, respecting per-office capacity and the configured
 * overflow chain.
 *
 * <p>OFFICE IDENTITY. {@code officeId} is an OFFICE_CODE_MASTER.OFFICE_CODE (e.g. "013" for
 * Mumbai-I), which is the same value carried on COMPLAINTS.rbio_office_code. It is NOT an office
 * name and NOT one of the retired synthetic 'RBIO-MUM'-style ids — those are deactivated by
 * migration V73/V71 because they covered only four of the twenty-four real offices and had no join
 * key to any office master.
 *
 * <p>WHY CAPACITY IS CLAIMED IN SQL. Every counter mutation goes through a single atomic UPDATE with
 * the capacity test in the WHERE clause. The previous implementation read the count into Java, added
 * one and saved, which let two concurrent filings both pass a full office's threshold and also lost
 * one of the two increments. There is no {@code @Version} on the entity, so nothing would have
 * detected it.
 *
 * <p>FAIL-CLOSED ON UNKNOWN OFFICE. An unknown or inactive officeId returns NOT_FOUND and assigns
 * nothing. Territorial jurisdiction decides which Ombudsman may lawfully hear a complaint and its
 * appeal, so guessing an office is worse than refusing to route: the caller must surface the failure
 * rather than silently place the case somewhere plausible.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeRoutingService {

    /** Outcome status values. Callers branch on these, so they are part of the contract. */
    public static final String STATUS_ASSIGNED = "ASSIGNED";
    public static final String STATUS_VERNACULAR = "ASSIGNED_VERNACULAR_OVERRIDE";
    public static final String STATUS_OVERFLOW = "OVERFLOW_ASSIGNED";
    public static final String STATUS_AT_CAPACITY = "ALL_OFFICES_AT_CAPACITY";
    public static final String STATUS_NOT_FOUND = "NOT_FOUND";

    private static final String CFG_ENFORCEMENT = "office.threshold.enforcement_enabled";

    private final OfficeThresholdConfigRepository thresholdRepo;
    private final SystemConfigService systemConfigService;

    /**
     * Claims capacity for a complaint at {@code targetOfficeId}, walking the configured overflow
     * chain if that office is full.
     *
     * @param targetOfficeId  OFFICE_CODE of the territorially correct office
     * @param isVernacularOverride true when the complaint's language forces this specific office to
     *                             retain it (UST467). Bypasses the capacity test by design: a
     *                             language-capable office keeps the case even when loaded, because
     *                             diverting it would leave the citizen without an office that can
     *                             read their complaint.
     * @return an immutable result map carrying at minimum {@code officeId} and {@code status}
     */
    @Transactional
    public Map<String, Object> routeToOffice(String targetOfficeId, boolean isVernacularOverride) {
        if (targetOfficeId == null || targetOfficeId.isBlank()) {
            log.warn("routeToOffice called with no target office — refusing to route");
            return Map.of("officeId", "", "status", STATUS_NOT_FOUND,
                    "reason", "No target office supplied");
        }

        OfficeThresholdConfig target = thresholdRepo.findByOfficeId(targetOfficeId).orElse(null);
        if (target == null || !target.isActive()) {
            log.warn("No active OFFICE_THRESHOLD_CONFIG row for office '{}' — refusing to route", targetOfficeId);
            return Map.of("officeId", targetOfficeId, "status", STATUS_NOT_FOUND,
                    "reason", "Office not configured or inactive");
        }

        // UST467: language-capable office retains the case regardless of load.
        if (isVernacularOverride) {
            thresholdRepo.incrementUnconditionally(targetOfficeId);
            int count = currentCountOf(targetOfficeId);
            log.info("Vernacular override — complaint retained at {} (count now {})", targetOfficeId, count);
            return Map.of("officeId", targetOfficeId, "status", STATUS_VERNACULAR,
                    "currentCount", count);
        }

        // When enforcement is off, record the office but never divert. Lets RBI run the territorial
        // routing without capacity diversion until real capacity figures are configured.
        if (!systemConfigService.getBoolean(CFG_ENFORCEMENT, true)) {
            thresholdRepo.incrementUnconditionally(targetOfficeId);
            return Map.of("officeId", targetOfficeId, "status", STATUS_ASSIGNED,
                    "currentCount", currentCountOf(targetOfficeId),
                    "reason", "Threshold enforcement disabled by configuration");
        }

        if (thresholdRepo.claimCapacity(targetOfficeId) == 1) {
            return Map.of("officeId", targetOfficeId, "status", STATUS_ASSIGNED,
                    "currentCount", currentCountOf(targetOfficeId));
        }

        // Primary office full — follow the configured overflow chain.
        return followOverflowChain(target);
    }

    /**
     * Walks {@code overflowTargetOffice} hop by hop until an office grants capacity.
     *
     * <p>The chain column is honoured deliberately: the previous implementation ignored it and
     * instead re-scanned every office in the department by sequence order, so the configured overflow
     * topology (seeded with an explicit chain) was silently unenforced and load spilled in an order
     * nobody had approved.
     *
     * <p>Visited offices are tracked so a mis-configured cycle terminates instead of spinning.
     */
    private Map<String, Object> followOverflowChain(OfficeThresholdConfig primary) {
        Set<String> visited = new LinkedHashSet<>();
        visited.add(primary.getOfficeId());

        String hop = primary.getOverflowTargetOffice();
        while (hop != null && !hop.isBlank() && visited.add(hop)) {
            OfficeThresholdConfig candidate = thresholdRepo.findByOfficeId(hop).orElse(null);
            if (candidate == null || !candidate.isActive()) {
                log.warn("Overflow chain from {} points at unknown/inactive office '{}' — chain ends here",
                        primary.getOfficeId(), hop);
                break;
            }
            if (thresholdRepo.claimCapacity(hop) == 1) {
                log.info("Office {} at capacity — overflowed to {}", primary.getOfficeId(), hop);
                return Map.of("officeId", hop, "status", STATUS_OVERFLOW,
                        "reason", "Primary office " + primary.getOfficeId() + " at capacity",
                        "currentCount", currentCountOf(hop),
                        "primaryOfficeId", primary.getOfficeId());
            }
            hop = candidate.getOverflowTargetOffice();
        }

        // Every office in the chain is full.
        //
        // The previous behaviour here zeroed EVERY counter in the department and then assigned to the
        // primary office anyway. That silently discarded the load of every other office (each still
        // holding maxThreshold live complaints while reporting 0) and admitted the complaint past a
        // capacity limit that had just been declared breached. The counter stopped measuring load and
        // became a rolling batch marker, so any dashboard reading it was wrong.
        //
        // Instead the complaint is placed at its territorially correct office and the saturation is
        // reported. Capacity is an operational signal, not a reason to move a citizen's complaint out
        // of its lawful jurisdiction — but it must be visible, not erased.
        thresholdRepo.incrementUnconditionally(primary.getOfficeId());
        int count = currentCountOf(primary.getOfficeId());
        log.warn("All {} offices in the overflow chain from {} are at capacity. Complaint retained at {} "
                        + "(count now {}, threshold {}). Counters NOT reset.",
                primary.getDepartment(), primary.getOfficeId(), primary.getOfficeId(),
                count, primary.getMaxThreshold());
        return Map.of("officeId", primary.getOfficeId(), "status", STATUS_AT_CAPACITY,
                "reason", "All offices in department " + primary.getDepartment() + " at capacity",
                "currentCount", count,
                "chainVisited", List.copyOf(visited));
    }

    private int currentCountOf(String officeId) {
        return thresholdRepo.findByOfficeId(officeId)
                .map(OfficeThresholdConfig::getCurrentCount)
                .orElse(0);
    }

    /** Releases capacity when a complaint leaves an office (closure, transfer out). */
    @Transactional
    public void decrementOffice(String officeId) {
        if (officeId == null || officeId.isBlank()) return;
        thresholdRepo.releaseCapacity(officeId);
    }

    /**
     * Claims capacity for an administrative transfer INTO an office.
     *
     * @return true when capacity was claimed; false when the office is already at its threshold, so
     *         the caller can refuse the transfer rather than silently overfilling the office. The
     *         previous version incremented unconditionally with no return value, which made this the
     *         one live write path able to push any office past its declared capacity.
     */
    @Transactional
    public boolean incrementOffice(String officeId) {
        if (officeId == null || officeId.isBlank()) return false;
        if (thresholdRepo.claimCapacity(officeId) == 1) {
            return true;
        }
        log.warn("Office {} is at capacity — transfer in was not counted", officeId);
        return false;
    }

    @Transactional
    public void resetAllCounters(String department) {
        int updated = thresholdRepo.resetCountersForDepartment(department);
        log.info("Reset {} office counters for department {}", updated, department);
    }

    @Transactional
    public void updateThreshold(String officeId, int newThreshold, String updatedBy) {
        if (newThreshold < 1) {
            throw new IllegalArgumentException("Office threshold must be at least 1, got: " + newThreshold);
        }
        OfficeThresholdConfig config = thresholdRepo.findByOfficeId(officeId)
                .orElseThrow(() -> new IllegalArgumentException("Office not found: " + officeId));
        config.setMaxThreshold(newThreshold);
        config.setUpdatedBy(updatedBy);
        thresholdRepo.save(config);
    }

    public List<OfficeThresholdConfig> getAllOfficeConfigs() {
        return thresholdRepo.findByActiveTrueOrderByOverflowSequenceOrderAsc();
    }
}
