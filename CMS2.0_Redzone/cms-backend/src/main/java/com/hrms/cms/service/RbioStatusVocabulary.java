package com.hrms.cms.service;

import com.hrms.cms.repository.RbioStatusMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * THE closed-status vocabulary, read from RBIO_STATUS_MASTER.
 *
 * <p>Replaces the hardcoded {@code CLOSED_STATUSES} copies. Two of them genuinely disagreed:
 * {@code WorkflowController} held six values while {@code NotificationScheduledTasks} held four, omitting
 * {@code adjudicated} and {@code conciliated} — so a complaint closed by an award or a successful
 * conciliation counted as OPEN to the reminder scheduler, which kept nudging officers about cases that
 * were already decided.
 *
 * <p>Deliberately its OWN service rather than a method on {@code RbioComplaintListService}: the callers
 * that need the vocabulary (a controller and a scheduler) have no interest in list paging, and coupling
 * them to the search service would drag its repositories into every context that wants to know what
 * "closed" means.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioStatusVocabulary {

    /**
     * Exactly {@code WorkflowController}'s original six values, so falling back is behaviour-preserving
     * for that caller.
     *
     * <p>Used when the status master is unseeded or unreachable. An empty list would mean "nothing is
     * closed", which would put every closed complaint back into every officer's open work queue — a far
     * worse failure than a slightly stale vocabulary.
     */
    static final List<String> LEGACY_CLOSED =
            List.of("resolved", "closed", "rejected", "withdrawn", "adjudicated", "conciliated");

    private final RbioStatusMasterRepository statusRepo;

    /**
     * The legacy status strings that mean "the file is shut".
     *
     * <p>Resolved per call rather than cached: the table is operator-editable, and a static would freeze
     * the vocabulary at class-load and need a redeploy to correct.
     */
    public List<String> closedStatuses() {
        try {
            List<String> fromTable = statusRepo.findClosedLegacyValues();
            if (!fromTable.isEmpty()) {
                return fromTable;
            }
            log.debug("RBIO_STATUS_MASTER has no closed statuses — using the legacy list");
        } catch (Exception e) {
            log.debug("Status master unavailable, using legacy closed list: {}", e.getMessage());
        }
        return LEGACY_CLOSED;
    }

    /** The fallback list, for callers that cannot inject this service (see WorkflowController). */
    public static List<String> legacyClosedStatuses() {
        return LEGACY_CLOSED;
    }
}
