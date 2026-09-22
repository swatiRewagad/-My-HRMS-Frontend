package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Marks complaints whose Regulated Entity has missed its response deadline (UST637).
 *
 * <h2>Why the flag is persisted rather than computed per request</h2>
 * UST637 requires the comparison against the deadline to be a SERVER-side control. Two things follow that
 * a per-page-load computation cannot give:
 *
 * <ul>
 *   <li>The grid can SORT and FILTER on overdue. {@code RbioComplaintListService} whitelists sortable
 *       columns, and a value computed in Java after the page is fetched cannot be ordered by the database —
 *       so "show me the overdue ones first" would silently not work.</li>
 *   <li>Every client agrees. A browser comparing two dates can be wrong about the timezone and can be
 *       working from a stale page; a statutory window must not depend on either.</li>
 * </ul>
 *
 * <p>The flag is therefore a column the sweep maintains, and {@link ReResponseDeadlineService#isOverdue} is
 * the single definition both this job and the API response use — so the list, the detail page and the
 * reminder can never disagree about whether an entity is late.
 *
 * <h2>The highlight clears itself</h2>
 * The sweep CLEARS the flag as well as setting it. UST637 requires the highlight to disappear once the RE
 * responds, and doing that here means no separate un-highlight step can be forgotten: a complaint that has
 * been answered, resolved or closed has its flag removed on the next pass. Clearing is also why the sweep
 * examines complaints that already carry the flag rather than only unflagged ones.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReDeadlineSweepService {

    private final ComplaintRepository complaintRepository;
    private final ReResponseDeadlineService deadlineService;

    /**
     * Brings every complaint's overdue flag into line with its deadline and response state.
     *
     * <p>Deliberately idempotent and silent when nothing changes: it runs as often as an administrator
     * configures, so a pass that finds nothing must cost one indexed query and write nothing.
     */
    @Transactional
    public int sweepOverdueDeadlines() {
        List<Complaint> candidates = complaintRepository.findWithReResponseDeadline();
        if (candidates.isEmpty()) {
            return 0;
        }

        int changed = 0;
        for (Complaint complaint : candidates) {
            boolean overdue = deadlineService.isOverdue(complaint);
            boolean flagged = Boolean.TRUE.equals(complaint.getReResponseOverdue());

            if (overdue == flagged) {
                continue;
            }

            complaint.setReResponseOverdue(overdue);
            complaintRepository.save(complaint);
            changed++;

            if (overdue) {
                log.info("Complaint {} is overdue: the entity's response was due {} ({} days ago)",
                        complaint.getComplaintNumber(), complaint.getReResponseDeadline(),
                        deadlineService.daysOverdue(complaint));
            } else {
                // Worth logging: this is the highlight clearing, and an operator watching a complaint
                // disappear from the overdue list should be able to see why.
                log.info("Complaint {} is no longer overdue (status {})",
                        complaint.getComplaintNumber(), complaint.getStatus());
            }
        }

        if (changed > 0) {
            log.info("RE-deadline sweep updated {} of {} complaints carrying a deadline",
                    changed, candidates.size());
        }
        return changed;
    }

    /** Today's overdue complaints, for a dashboard count that does not re-derive the rule. */
    @Transactional(readOnly = true)
    public List<Complaint> currentlyOverdue() {
        return complaintRepository.findOverdueReResponses(LocalDate.now());
    }
}
