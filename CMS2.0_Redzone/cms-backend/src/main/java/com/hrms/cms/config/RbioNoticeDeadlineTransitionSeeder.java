package com.hrms.cms.config;

import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.repository.RbioWorkflowTransitionRepository;
import com.hrms.cms.service.RbioRoles;
import com.hrms.cms.service.RbioTransitionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Grants the 13(1) Notice to the Dealing Official at Assessment (UST779).
 *
 * <h2>Why a seeder and not an edit to the registry</h2>
 * {@code RbioWorkflowTransitionSeeder} states the convention plainly: every session adds its actions in
 * its OWN seeder at its own {@code @Order}, and nobody edits that class. The transition table is
 * authoritative for any role it already knows, so an INSERT grants the action and a deactivated row
 * revokes it — no code release, and no risk of this session silently changing another session's rows.
 *
 * <h2>What UST779 actually required</h2>
 * The story asks for the notice to be available to the Dealing Official at Assessment with NO approval
 * step and NO Deputy Ombudsman sign-off. The absence was already true and is asserted in the tests rather
 * than assumed: {@code applyTransition} has no approval check, the 13(1) row carries no
 * {@code requiresComment} and no required params, and the only approval mechanic in the service
 * ({@code FX_APPROVE_LADDER}) is attached to {@code APPROVE} alone.
 *
 * <p>What was NOT true is the availability. {@code ISSUE_NOTICE_13_1} was granted only to
 * {@code RBIO_ADJUDICATOR} and {@code RBIO_OMBUDSMAN}, and only from status {@code adjudication} — the
 * FINAL_DECISION milestone. So the officer the story names could not issue it at the stage the story
 * names. These rows close that gap.
 *
 * <h2>Why several from-statuses</h2>
 * "Assessment" is a milestone, not one status: RBIO_STATUS_MASTER maps IN_PROGRESS, INFO_REQUESTED,
 * SENT_TO_DO, SENT_BACK_DO and REOPENED to it. A notice is issued while the officer is working the file,
 * which is any of those. Granting only {@code in_progress} would make the action vanish the moment the
 * officer requested information from the entity — precisely when a 13(1) notice becomes relevant.
 *
 * <p>The statuses are the legacy lowercase values, because that is what {@code Complaint.status} holds
 * and what the from-status match compares against.
 */
@Component
@Order(32)
@RequiredArgsConstructor
@Slf4j
public class RbioNoticeDeadlineTransitionSeeder implements CommandLineRunner {

    /**
     * Assessment-era statuses a Dealing Official can issue the notice from.
     *
     * <p>Taken from RBIO_STATUS_MASTER's ASSESSMENT milestone, minus ESCALATED — an escalated complaint is
     * with a senior officer, so the Dealing Official is no longer the one corresponding with the entity.
     */
    private static final List<String> ASSESSMENT_STATUSES =
            List.of("in_progress", "info_requested", "sent_to_do", "sent_back_do", "reopened");

    private final RbioWorkflowTransitionRepository transitionRepo;

    @Override
    @Transactional
    public void run(String... args) {
        int inserted = 0;

        for (String fromStatus : ASSESSMENT_STATUSES) {
            // Insert-if-absent on the natural key, so a restart never rewrites a row an operator has
            // adjusted. Correcting live behaviour is a deliberate UPDATE in both migration directories.
            boolean exists = transitionRepo.findByActionCodeAndRoleNameAndFromStatus(
                    "ISSUE_NOTICE_13_1", RbioRoles.DEALING_OFFICIAL, fromStatus).isPresent();
            if (exists) {
                continue;
            }

            RbioWorkflowTransition row = new RbioWorkflowTransition();
            row.setActionCode("ISSUE_NOTICE_13_1");
            row.setRoleName(RbioRoles.DEALING_OFFICIAL);
            row.setFromStatus(fromStatus);

            // TO_STATUS stays NULL deliberately. Issuing a notice does not move the complaint's standing —
            // it records that the entity has been formally written to. Setting a status here would move
            // every complaint at Assessment into the FINAL_DECISION milestone on a correspondence step.
            row.setToStatus(null);
            row.setToStage("NOTICE_13_1_ISSUED");
            row.setToMilestone("ASSESSMENT");
            row.setSideEffect(RbioTransitionRegistry.FX_NOTICE_13_1);

            // No approval gate, no mandatory comment, no assignee change: UST779 requires the Dealing
            // Official to act alone. ASSIGN_STRATEGY is left null so the complaint stays with them.
            row.setRequiresComment("N");
            row.setIsTerminal("N");
            row.setIsActive("Y");
            row.setDisplayOrder(60);
            row.setOwnedBy("S3");

            transitionRepo.save(row);
            inserted++;
        }

        if (inserted > 0) {
            log.info("Granted ISSUE_NOTICE_13_1 to {} from {} assessment statuses (UST779)",
                    RbioRoles.DEALING_OFFICIAL, inserted);
        }
    }
}
