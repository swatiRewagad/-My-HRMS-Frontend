package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealOrder;
import com.hrms.cms.repository.AppealOrderRepository;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the two statutory guards on an AA final order: the award ceiling and the sub-judice block.
 *
 * <h2>Why these tests exist</h2>
 *
 * <p>Before this, {@code AaAppealOrderService} had NO test class at all, and it enforced no award cap
 * whatsoever — an Appellate Authority could record an award of any size. Separately, the appellant's
 * declared "related court trial" flag was persisted at registration and then never read by anything, so
 * an order could issue on a matter before a court with no record that the point was considered.
 *
 * <p>The E2E suite appeared to cover both. It did not: those tests drove {@code PASS_AWARD}, an action
 * that does not exist in AA, and routed the resulting failure into a swallowed catch — so they passed
 * over entirely absent functionality. See e2e/aa/backlog-traceable.spec.ts.
 *
 * <h2>Why both guards default to OFF</h2>
 *
 * <p>The ceiling VALUE and the question of whether the AA may proceed on a sub-judice matter are
 * questions of Scheme law, not engineering. Hardcoding a guess would either block lawful orders or
 * permit unlawful ones. The mechanisms are therefore config-gated and disabled by default, following
 * the existing {@code cms.aa.order.require_ed_approval} precedent, and these tests prove BOTH that the
 * guard fires when armed AND that it is genuinely inert until then — so arming it after legal sign-off
 * is a config change rather than a code change.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AaAppealOrderServiceTest {

    private static final String APPEAL = "APL-20260917-TEST01";

    @Mock private AppealOrderRepository orderRepository;
    @Mock private AppealRepository appealRepository;
    @Mock private ClosureClauseMasterRepository clauseRepository;
    @Mock private SystemConfigService systemConfigService;

    private AaAppealOrderService service;
    private Appeal appeal;

    @BeforeEach
    void setUp() {
        service = new AaAppealOrderService(orderRepository, appealRepository, clauseRepository,
                systemConfigService);

        appeal = new Appeal();
        appeal.setAppealNumber(APPEAL);
        appeal.setStatus("under_review");

        when(appealRepository.findByAppealNumber(APPEAL)).thenReturn(Optional.of(appeal));
        when(orderRepository.existsByAppealNumber(APPEAL)).thenReturn(false);
        // Both guards off unless a test arms them, matching the production default.
        when(systemConfigService.getBoolean(anyString(), any(Boolean.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(systemConfigService.getLong(anyString(), any(Long.class)))
                .thenAnswer(inv -> inv.getArgument(1));
        when(orderRepository.save(any(AppealOrder.class)))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private void armCeiling(long rupees) {
        when(systemConfigService.getLong(eq(AaAppealOrderService.CFG_MAX_AWARD_AMOUNT), any(Long.class)))
                .thenReturn(rupees);
    }

    private void armSubJudiceBlock() {
        when(systemConfigService.getBoolean(eq(AaAppealOrderService.CFG_BLOCK_SUB_JUDICE), any(Boolean.class)))
                .thenReturn(true);
    }

    private AppealOrder issue(BigDecimal award, String ground) {
        return service.issue(APPEAL, AppealOrder.OUTCOME_MODIFIED, "Reasoned order summary",
                award, null, ground, "aa_secretariat_001", "AA_SECRETARIAT");
    }

    @Nested
    @DisplayName("award ceiling")
    class AwardCeiling {

        @Test
        @DisplayName("an award above the configured ceiling is REFUSED and no order is persisted")
        void overCeilingAwardIsRefused() {
            armCeiling(3_000_000L);

            assertThatThrownBy(() -> issue(new BigDecimal("3000001"), null))
                    .isInstanceOf(AaAppealOrderService.AwardCapExceededException.class)
                    .hasMessageContaining("exceeds the maximum permitted cap");

            // An order refused on legal grounds must leave nothing behind.
            verify(orderRepository, never()).save(any(AppealOrder.class));
        }

        @Test
        @DisplayName("an award exactly AT the ceiling is allowed — the boundary is inclusive")
        void awardAtTheCeilingIsAllowed() {
            armCeiling(3_000_000L);

            AppealOrder order = issue(new BigDecimal("3000000"), null);

            assertThat(order.getAwardAmount()).isEqualByComparingTo(new BigDecimal("3000000"));
        }

        @Test
        @DisplayName("with no ceiling configured the cap is inert, so shipping it changes nothing")
        void ceilingIsInertByDefault() {
            // Deliberately NOT armed: proves the default is genuinely off rather than accidentally on,
            // which is what makes this safe to ship ahead of legal sign-off.
            AppealOrder order = issue(new BigDecimal("99999999"), null);

            assertThat(order.getAwardAmount()).isEqualByComparingTo(new BigDecimal("99999999"));
        }

        @Test
        @DisplayName("a negative award is always refused, ceiling or not")
        void negativeAwardIsAlwaysRefused() {
            assertThatThrownBy(() -> issue(new BigDecimal("-1"), null))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be negative");
        }

        @Test
        @DisplayName("an award on a non-award outcome is dropped, not recorded against a dismissal")
        void awardOnNonAwardOutcomeIsDropped() {
            armCeiling(3_000_000L);

            AppealOrder order = service.issue(APPEAL, AppealOrder.OUTCOME_DISMISSED,
                    "Dismissed on the merits", new BigDecimal("500000"), null, null,
                    "aa_secretariat_001", "AA_SECRETARIAT");

            assertThat(order.getAwardAmount()).isNull();
        }
    }

    @Nested
    @DisplayName("sub-judice block")
    class SubJudice {

        @Test
        @DisplayName("an order on a declared sub-judice appeal is REFUSED when no ground is stated")
        void subJudiceOrderIsRefusedWithoutAGround() {
            armSubJudiceBlock();
            appeal.setHasRelatedCourtTrial(true);

            assertThatThrownBy(() -> issue(null, null))
                    .isInstanceOf(AaAppealOrderService.SubJudiceException.class)
                    .hasMessageContaining("related court trial");

            verify(orderRepository, never()).save(any(AppealOrder.class));
        }

        @Test
        @DisplayName("a stated ground overrides the block and is PERSISTED on the order for audit")
        void aStatedGroundOverridesAndIsAudited() {
            armSubJudiceBlock();
            appeal.setHasRelatedCourtTrial(true);

            AppealOrder order = issue(null, "The writ concerns a different cause of action");

            // The override must be recoverable from the order itself, or "audited" is a claim with no
            // evidence behind it.
            assertThat(order.getGround()).isEqualTo("The writ concerns a different cause of action");
        }

        @Test
        @DisplayName("an appeal with no declared court trial is unaffected even when the block is armed")
        void nonSubJudiceAppealIsUnaffected() {
            armSubJudiceBlock();
            appeal.setHasRelatedCourtTrial(false);

            assertThat(issue(null, null)).isNotNull();
        }

        @Test
        @DisplayName("with the block disabled a sub-judice order still issues — the default is inert")
        void blockIsInertByDefault() {
            // Not armed. Documents the CURRENT go-live behaviour honestly: until legal sign-off arms
            // this, an order CAN be passed on a sub-judice matter.
            appeal.setHasRelatedCourtTrial(true);

            assertThat(issue(null, null)).isNotNull();
        }
    }
}
