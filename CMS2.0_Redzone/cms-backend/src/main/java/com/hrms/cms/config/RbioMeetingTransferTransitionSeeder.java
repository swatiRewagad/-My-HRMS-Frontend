package com.hrms.cms.config;

import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.repository.RbioWorkflowTransitionRepository;
import com.hrms.cms.service.RbioMeetingTransferActions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds S5's meeting and forwarding actions into RBIO_WORKFLOW_TRANSITION.
 *
 * <p>A SEPARATE seeder at its own {@code @Order}, per the convention {@code RbioWorkflowTransitionSeeder}
 * states explicitly: seven sessions editing one seeder would silently lose each other's rows.
 *
 * <p><b>One row here is an UPGRADE rather than an addition, and it needs care.</b> Wave 0 already seeds
 * {@code SCHEDULE_MEETING} with NO required params, and insert-if-absent on (action, role, from-status) means
 * those rows are never rewritten. So for the (action, role, from-status) combinations Wave 0 already seeded,
 * the permissive row stays and the mandatory date/time/participants would not be enforced by the TABLE.
 *
 * <p>That is why the mandatory-field validation does not rely on the table alone: it is enforced inside
 * {@code RbioMeetingService}, which is reached from the side effect that BOTH declarations share
 * ({@code FX_MEETING_DATE} and S5's {@code MEETING_SCHEDULED} both delegate to it). The table's
 * {@code requiredParams} is a backstop for new rows, not the control. A control that applied to some callers
 * and not others would be worse than none, because it would look enforced.
 *
 * <p>The genuinely NEW grants this seeder adds are the ones UST643 needs: {@code RBIO_REVIEWER} was never
 * granted {@code SCHEDULE_MEETING} at all (the Reviewer mirrors the Supervisor, whose set omits it), so the
 * Reviewer could not reach the meeting milestone on any path.
 */
@Component
@Order(42)
@RequiredArgsConstructor
@Slf4j
public class RbioMeetingTransferTransitionSeeder implements CommandLineRunner {

    private final RbioWorkflowTransitionRepository transitionRepo;

    @Override
    @Transactional
    public void run(String... args) {
        List<RbioWorkflowTransition> rows = RbioMeetingTransferActions.allRows();
        int inserted = 0;

        for (RbioWorkflowTransition row : rows) {
            boolean exists = transitionRepo.findByActionCodeAndRoleNameAndFromStatus(
                    row.getActionCode(), row.getRoleName(), row.getFromStatus()).isPresent();
            if (!exists) {
                transitionRepo.save(row);
                inserted++;
            }
        }

        if (inserted > 0) {
            log.info("RBIO meeting/transfer transitions seeded: {} of {} declared rows inserted",
                    inserted, rows.size());
        }
    }
}
