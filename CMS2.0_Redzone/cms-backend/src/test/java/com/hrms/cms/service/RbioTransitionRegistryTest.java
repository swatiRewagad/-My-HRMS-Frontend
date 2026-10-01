package com.hrms.cms.service;

import com.hrms.cms.entity.RbioWorkflowTransition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the Wave-0 transition declarations against the mistakes that are easy to make when S1-S7 add
 * rows: dropping a required param, granting a rank an authority it must not have, or turning a
 * deliberately-null field into a literal.
 */
class RbioTransitionRegistryTest {

    @Test
    @DisplayName("every declared action has an effect, so no role can be granted an inert action")
    void everyRoleActionHasAnEffect() {
        for (String action : RbioTransitionRegistry.allActions()) {
            assertThat(RbioTransitionRegistry.effectOf(action))
                    .as("action %s has no declared effect", action)
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("ADJUDICATION_AWARD requires an amount, so a missing one can never default to a zero award")
    void awardRequiresAnAmount() {
        RbioWorkflowTransition award = RbioTransitionRegistry.resolve("ADJUDICATION_AWARD", RbioRoles.ADJUDICATOR);

        assertThat(award).isNotNull();
        assertThat(award.getRequiredParams())
                .as("the award-amount refusal is the guard against recording a citizen's compensation as 0.00")
                .isEqualTo("awardAmount|compensationAmount");
    }

    @Test
    @DisplayName("a Deputy Ombudsman may NOT issue an award — that is the Ombudsman's statutory power")
    void deputyOmbudsmanCannotIssueAnAward() {
        assertThat(RbioTransitionRegistry.actionsFor(RbioRoles.DEPUTY_OMBUDSMAN))
                .doesNotContain("ADJUDICATION_AWARD");
        assertThat(RbioTransitionRegistry.actionsFor(RbioRoles.OMBUDSMAN))
                .contains("ADJUDICATION_AWARD");
    }

    @Test
    @DisplayName("stage-only actions leave the status NULL, meaning unchanged rather than unconfigured")
    void stageOnlyActionsDeclareNoStatus() {
        for (String action : List.of("SCHEDULE_MEETING", "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY")) {
            assertThat(RbioTransitionRegistry.effectOf(action).getToStatus())
                    .as("%s must not write a status; it moves only the stage", action)
                    .isNull();
        }
    }

    @Test
    @DisplayName("the legacy role matrix is preserved exactly, negative cells included")
    void legacyRoleMatrixIsUnchanged() {
        assertThat(RbioTransitionRegistry.actionsFor(RbioRoles.CONCILIATOR)).containsExactlyInAnyOrder(
                "CONCILIATION_SUCCESS", "CONCILIATION_FAILED", "SCHEDULE_MEETING", "ESCALATE_TO_ADJUDICATION");
        assertThat(RbioTransitionRegistry.actionsFor(RbioRoles.ADJUDICATOR)).containsExactlyInAnyOrder(
                "ADJUDICATION_AWARD", "ADJUDICATION_REJECT", "ISSUE_NOTICE_13_1", "IMPLEAD_PARTY");
        assertThat(RbioTransitionRegistry.actionsFor(RbioRoles.ADMIN)).containsExactlyInAnyOrder(
                "REASSIGN", "ESCALATE", "CLOSE_COMPLAINT", "REOPEN", "BULK_ASSIGN");
    }

    @Test
    @DisplayName("CONCILIATION_FAILED moves the file to escalated, not to a status of its own name")
    void conciliationFailedYieldsEscalated() {
        assertThat(RbioTransitionRegistry.effectOf("CONCILIATION_FAILED").getToStatus()).isEqualTo("escalated");
        assertThat(RbioTransitionRegistry.effectOf("CONCILIATION_FAILED").getToStage()).isEqualTo("CONCILIATION_FAILED");
    }

    @Test
    @DisplayName("advertisement rules reproduce the old isActionValidForState, exclusions included")
    void advertisementRulesArePreserved() {
        assertThat(RbioTransitionRegistry.validForState("ACCEPT", "assigned")).isTrue();
        assertThat(RbioTransitionRegistry.validForState("ACCEPT", "closed")).isFalse();
        assertThat(RbioTransitionRegistry.validForState("ADJUDICATION_AWARD", "adjudication")).isTrue();
        assertThat(RbioTransitionRegistry.validForState("ADJUDICATION_AWARD", "in_progress")).isFalse();

        // Exclusion-based, not enumeration-based: valid from anything except the closed set.
        assertThat(RbioTransitionRegistry.validForState("REASSIGN", "in_progress")).isTrue();
        assertThat(RbioTransitionRegistry.validForState("REASSIGN", "adjudicated")).isFalse();
        assertThat(RbioTransitionRegistry.validForState("CLOSE_COMPLAINT", "in_progress")).isTrue();
        assertThat(RbioTransitionRegistry.validForState("CLOSE_COMPLAINT", "closed")).isFalse();
    }

    @Test
    @DisplayName("seedable rows cover every action/role pair and carry the owning wave")
    void allRowsAreSeedable() {
        List<RbioWorkflowTransition> rows = RbioTransitionRegistry.allRows();
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getActionCode()).isNotBlank();
            assertThat(row.getRoleName()).isNotBlank();
            assertThat(row.getOwnedBy()).isEqualTo("WAVE0");
            assertThat(row.active()).isTrue();
        });
    }

    @Test
    @DisplayName("the rank ladder is separate from the legacy ladder, and neither is re-pointed")
    void ladderIsPreservedAndExtended() {
        // Legacy ladder, pinned by RbioWorkflowServiceTest — must not be re-pointed by the new ranks.
        assertThat(RbioRoles.nextRank(RbioRoles.OFFICER)).isEqualTo(RbioRoles.SUPERVISOR);
        assertThat(RbioRoles.nextRank(RbioRoles.SUPERVISOR)).isEqualTo(RbioRoles.CONCILIATOR);

        // The corrected rank ladder.
        assertThat(RbioRoles.nextRank(RbioRoles.DEALING_OFFICIAL)).isEqualTo(RbioRoles.REVIEWER);
        assertThat(RbioRoles.nextRank(RbioRoles.REVIEWER)).isEqualTo(RbioRoles.DEPUTY_OMBUDSMAN);
        assertThat(RbioRoles.nextRank(RbioRoles.DEPUTY_OMBUDSMAN)).isEqualTo(RbioRoles.OMBUDSMAN);

        // Top of the ladder maps to itself rather than throwing.
        assertThat(RbioRoles.nextRank(RbioRoles.OMBUDSMAN)).isEqualTo(RbioRoles.OMBUDSMAN);
    }

    @Test
    @DisplayName("all nine roles are in the guard vocabulary, so no rank is 403'd at the HTTP layer")
    void guardVocabularyCoversEveryRole() {
        assertThat(Set.of(RbioRoles.ALL)).contains(
                RbioRoles.DEALING_OFFICIAL, RbioRoles.REVIEWER,
                RbioRoles.DEPUTY_OMBUDSMAN, RbioRoles.OMBUDSMAN,
                RbioRoles.OFFICER, RbioRoles.SUPERVISOR,
                RbioRoles.CONCILIATOR, RbioRoles.ADJUDICATOR, RbioRoles.ADMIN);
    }
}
