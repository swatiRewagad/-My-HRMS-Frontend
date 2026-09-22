package com.hrms.cms.config;

import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.repository.RbioWorkflowTransitionRepository;
import com.hrms.cms.service.RbioTransitionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Writes the Wave-0 transition rows into RBIO_WORKFLOW_TRANSITION.
 *
 * <p>Insert-if-absent on the natural key (action, role, from-status), per the established seeder
 * convention: editing a declaration in {@link RbioTransitionRegistry} does NOT update an already-seeded
 * row, so a correction to live behaviour needs a code-scoped UPDATE in both migration directories. That
 * is deliberate — silently rewriting workflow rules on restart would change how in-flight complaints
 * route, with no record of when it happened.
 *
 * <p>Sessions S1-S7 add their actions in their OWN seeder class at their own {@code @Order}. Nobody
 * edits this one.
 */
@Component
@Order(21)
@RequiredArgsConstructor
@Slf4j
public class RbioWorkflowTransitionSeeder implements CommandLineRunner {

    private final RbioWorkflowTransitionRepository transitionRepo;

    @Override
    @Transactional
    public void run(String... args) {
        List<RbioWorkflowTransition> rows = RbioTransitionRegistry.allRows();
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
            log.info("RBIO workflow transitions seeded: {} of {} declared rows inserted", inserted, rows.size());
        }
    }
}
