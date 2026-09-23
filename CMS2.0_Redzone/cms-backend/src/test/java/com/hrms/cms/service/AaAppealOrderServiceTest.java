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
import static org.assertj.core.api.Assertions.assertThatCode;
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

        /**
         * Replaces an earlier test that asserted the ceiling was inert by default. That behaviour was
         * overruled by the product owner: shipping 0 meant no statutory ceiling was enforced at all, so
         * an AA order could exceed the limit. The cap is now armed at 33,00,000 out of the box.
         */
        @Test
        @DisplayName("with nothing configured the cap is ARMED at the combined statutory ceiling")
        void ceilingIsArmedByDefault() {
            assertThatThrownBy(() -> issue(new BigDecimal("99999999"), null))
                    .isInstanceOf(AaAppealOrderService.AwardCapExceededException.class)
                    .hasMessageContaining("3300000");

            verify(orderRepository, never()).save(any(AppealOrder.class));
        }

        @Test
        @DisplayName("an award at the default combined ceiling still issues")
        void awardAtDefaultCombinedCeilingIssues() {
            AppealOrder order = issue(new BigDecimal("3300000"), null);

            assertThat(order.getAwardAmount()).isEqualByComparingTo(new BigDecimal("3300000"));
        }

        @Test
        @DisplayName("an explicit ceiling of 0 still disables the check, preserving the escape hatch")
        void explicitZeroDisablesTheCheck() {
            armCeiling(0L);

            AppealOrder order = issue(new BigDecimal("99999999"), null);

            assertThat(order.getAwardAmount()).isEqualByComparingTo(new BigDecimal("99999999"));
        }
    }

    @Nested
    @DisplayName("component compensation ceilings")
    class ComponentCeilings {

        /**
         * The AA ceiling mirrors RBIO by ruling. Asserting the identity rather than the literals means a
         * later amendment to one component cannot silently leave the combined figure inconsistent.
         */
        @Test
        @DisplayName("combined ceiling equals financial + harassment")
        void combinedEqualsSumOfComponents() {
            BigDecimal financial = service.maxCompensation(AaAppealOrderService.COMPENSATION_FINANCIAL);
            BigDecimal harassment = service.maxCompensation(AaAppealOrderService.COMPENSATION_HARASSMENT);

            assertThat(service.maxCompensation(AaAppealOrderService.COMPENSATION_COMBINED))
                    .isEqualByComparingTo(financial.add(harassment));
        }

        @Test
        @DisplayName("the components default to the RBIO figures of 30 Lakh and 3 Lakh")
        void componentsMirrorRbio() {
            assertThat(service.maxCompensation(AaAppealOrderService.COMPENSATION_FINANCIAL))
                    .isEqualByComparingTo(new BigDecimal("3000000"));
            assertThat(service.maxCompensation(AaAppealOrderService.COMPENSATION_HARASSMENT))
                    .isEqualByComparingTo(new BigDecimal("300000"));
        }

        @Test
        @DisplayName("an amount above the financial ceiling is refused")
        void overFinancialCeilingIsRefused() {
            assertThatThrownBy(() -> service.validateAwardComponent(
                    new BigDecimal("3000001"), AaAppealOrderService.COMPENSATION_FINANCIAL))
                    .isInstanceOf(AaAppealOrderService.AwardCapExceededException.class)
                    .hasMessageContaining("3000000");
        }

        @Test
        @DisplayName("an amount above the harassment ceiling is refused")
        void overHarassmentCeilingIsRefused() {
            assertThatThrownBy(() -> service.validateAwardComponent(
                    new BigDecimal("300001"), AaAppealOrderService.COMPENSATION_HARASSMENT))
                    .isInstanceOf(AaAppealOrderService.AwardCapExceededException.class)
                    .hasMessageContaining("300000");
        }

        @Test
        @DisplayName("an amount at either component ceiling is allowed — the boundary is inclusive")
        void componentBoundariesAreInclusive() {
            assertThatCode(() -> {
                service.validateAwardComponent(
                        new BigDecimal("3000000"), AaAppealOrderService.COMPENSATION_FINANCIAL);
                service.validateAwardComponent(
                        new BigDecimal("300000"), AaAppealOrderService.COMPENSATION_HARASSMENT);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("an unknown component is rejected rather than silently uncapped")
        void unknownComponentIsRejected() {
            assertThatThrownBy(() -> service.maxCompensation("MYSTERY"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown compensation component");
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
