package com.hrms.cms.config;

import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.repository.RbioWorkflowTransitionRepository;
import com.hrms.cms.service.RbioLadderActions;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Seeds the S3 ladder and milestone actions into RBIO_WORKFLOW_TRANSITION.
 *
 * <p>A SEPARATE seeder from {@code RbioWorkflowTransitionSeeder} (@Order 21) per the convention that file
 * states explicitly: sessions add their actions in their OWN class at their own {@code @Order}, because
 * seven sessions editing one seeder would silently lose each other's rows.
 *
 * <p>Insert-if-absent on the natural key (action, role, from-status), matching the Wave-0 seeder. So
 * editing a declaration in {@link RbioLadderActions} does NOT rewrite an already-seeded row — a
 * correction to live routing needs a code-scoped UPDATE in both migration directories. That is
 * deliberate: silently re-pointing a workflow arrow on restart would change where in-flight complaints
 * go, with no record of when it happened.
 */
@Component
@Order(32)
@RequiredArgsConstructor
@Slf4j
public class RbioLadderTransitionSeeder implements CommandLineRunner {

    private final RbioWorkflowTransitionRepository transitionRepo;

    @Override
    @Transactional
    public void run(String... args) {
        List<RbioWorkflowTransition> rows = RbioLadderActions.allRows();
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
            log.info("RBIO ladder transitions seeded: {} of {} declared rows inserted", inserted, rows.size());
        }
    }
}
