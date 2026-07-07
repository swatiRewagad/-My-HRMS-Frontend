package com.hrms.cms.service.mre;

import com.hrms.cms.service.BusinessHoursService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MaintainabilityRulesEngineTest {

    @Mock
    private BusinessHoursService businessHoursService;

    @Mock
    private MreEntityCoverageService entityCoverageService;

    private MreProperties config;
    private MaintainabilityRulesEngine engine;

    @BeforeEach
    void setUp() {
        config = new MreProperties();
        config.setVersion(1);
        config.setReWindowDays(30);
        config.setNpciWindowDays(30);
        config.setCardNetworkWindowDays(60);
        config.setFilingDeadlineDays(90);
        config.setLimitationPeriodYears(3);
        config.setWindowBasis(MreProperties.WindowBasis.CALENDAR);

        engine = new MaintainabilityRulesEngine(config, businessHoursService, entityCoverageService);
    }

    private void setupBusinessDaysMode() {
        config.setWindowBasis(MreProperties.WindowBasis.BUSINESS);
        when(businessHoursService.isBusinessDay(any(LocalDate.class))).thenAnswer(inv -> {
            LocalDate d = inv.getArgument(0);
            int dow = d.getDayOfWeek().getValue();
            return dow <= 5;
        });
    }

    @Nested
    @DisplayName("Ground: Entity not covered (Q13)")
    class EntityCoverageTests {

        @Test
        @DisplayName("PASS: Entity is in regulated entity list")
        void entityCovered() {
            when(entityCoverageService.isEntityCovered("SBI", null)).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));
            GroundVerdict ground = findGround(verdict, MreGround.ENTITY_NOT_COVERED);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }

        @Test
        @DisplayName("FAIL: Entity is NOT in regulated entity list")
        void entityNotCovered() {
            when(entityCoverageService.isEntityCovered("UNKNOWN_ENTITY", null)).thenReturn(false);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("UNKNOWN_ENTITY")
                    .priorReComplaint(true)
                    .reComplaintDate(LocalDate.of(2026, 5, 1))
                    .reComplaintReference("REF-001")
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            GroundVerdict ground = findGround(verdict, MreGround.ENTITY_NOT_COVERED);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
            assertThat(ground.getClause()).isEqualTo("Q13");
        }

        @Test
        @DisplayName("NEEDS_REVIEW: No entity code provided")
        void noEntityCode() {
            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("")
                    .priorReComplaint(true)
                    .reComplaintDate(LocalDate.of(2026, 5, 1))
                    .reComplaintReference("REF-001")
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            GroundVerdict ground = findGround(verdict, MreGround.ENTITY_NOT_COVERED);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.NEEDS_REVIEW);
        }
    }

    @Nested
    @DisplayName("Ground: No prior RE complaint (Q16)")
    class NoPriorReComplaintTests {

        @Test
        @DisplayName("PASS: Complainant has prior RE complaint")
        void hasPriorComplaint() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true, LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));
            GroundVerdict ground = findGround(verdict, MreGround.NO_PRIOR_RE_COMPLAINT);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }

        @Test
        @DisplayName("FAIL: No prior complaint to RE")
        void noPriorComplaint() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("SBI")
                    .priorReComplaint(false)
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            GroundVerdict ground = findGround(verdict, MreGround.NO_PRIOR_RE_COMPLAINT);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
            assertThat(ground.getClause()).isEqualTo("Q16");
        }
    }

    @Nested
    @DisplayName("Ground: Filed before RE window (Q17)")
    class FiledBeforeWindowTests {

        @Test
        @DisplayName("PASS: Filed after 30-day window")
        void filedAfterWindow() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }

        @Test
        @DisplayName("FAIL: Filed on day 20 — before 30-day window")
        void filedBeforeWindow() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 20)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
            assertThat(ground.getReason()).contains("19 days");
        }

        @Test
        @DisplayName("PASS: Filed before window BUT RE replied and dissatisfied — waived")
        void repliedAndDissatisfied() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("SBI")
                    .priorReComplaint(true)
                    .reComplaintDate(LocalDate.of(2026, 6, 1))
                    .reComplaintReference("REF-001")
                    .reRepliedAndDissatisfied(true)
                    .filingDate(LocalDate.of(2026, 6, 10))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
            assertThat(ground.getReason()).contains("waived");
        }

        @Test
        @DisplayName("FAIL: Card complaint filed on day 50 — card window is 60 days")
        void cardNetworkExtendedWindow() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("SBI")
                    .categoryCode("CREDIT_CARD")
                    .priorReComplaint(true)
                    .reComplaintDate(LocalDate.of(2026, 4, 1))
                    .reComplaintReference("REF-001")
                    .filingDate(LocalDate.of(2026, 5, 20))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
            assertThat(ground.getReason()).contains("60-day");
        }

        @Test
        @DisplayName("PASS: Exact boundary — filed on day 30")
        void exactBoundary() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 31)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }
    }

    @Nested
    @DisplayName("Ground: Filed beyond 90-day deadline (Q16/Q17)")
    class FiledBeyondDeadlineTests {

        @Test
        @DisplayName("PASS: Filed within 90 days of window expiry")
        void withinDeadline() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 3, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEYOND_DEADLINE);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }

        @Test
        @DisplayName("FAIL: Filed 150 days after window expiry")
        void beyondDeadline() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2025, 10, 1), LocalDate.of(2026, 6, 1)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEYOND_DEADLINE);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
        }
    }

    @Nested
    @DisplayName("Ground: Limitation Act period (Q16)")
    class LimitationPeriodTests {

        @Test
        @DisplayName("PASS: RE complaint within 3 years of filing")
        void withinLimitation() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2024, 1, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.RE_COMPLAINT_BEYOND_LIMITATION);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }

        @Test
        @DisplayName("FAIL: RE complaint more than 3 years before filing")
        void beyondLimitation() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2022, 1, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.RE_COMPLAINT_BEYOND_LIMITATION);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
            assertThat(ground.getClause()).isEqualTo("Q16");
        }
    }

    @Nested
    @DisplayName("Ground: Same grievance pending (Q16)")
    class SameGrievanceTests {

        @Test
        @DisplayName("PASS: No duplicate grievance")
        void noDuplicate() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.SAME_GRIEVANCE_PENDING);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }

        @Test
        @DisplayName("FAIL: Same grievance already pending")
        void duplicateExists() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("SBI")
                    .priorReComplaint(true)
                    .reComplaintDate(LocalDate.of(2026, 5, 1))
                    .reComplaintReference("REF-001")
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .sameGrievancePendingOrDecided(true)
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            GroundVerdict ground = findGround(verdict, MreGround.SAME_GRIEVANCE_PENDING);

            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
        }
    }

    @Nested
    @DisplayName("Overall signal computation")
    class OverallSignalTests {

        @Test
        @DisplayName("GREEN: All grounds pass")
        void allPass() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));

            assertThat(verdict.getOverallSignal()).isEqualTo(MreVerdict.OverallSignal.OBJECTIVELY_CLEAR);
            assertThat(verdict.getTriageSignal()).isEqualTo("GREEN");
        }

        @Test
        @DisplayName("RED: One ground fails")
        void oneFail() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("SBI")
                    .priorReComplaint(false)
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            assertThat(verdict.getOverallSignal()).isEqualTo(MreVerdict.OverallSignal.OBJECTIVELY_NON_MAINTAINABLE);
            assertThat(verdict.getTriageSignal()).isEqualTo("RED");
        }

        @Test
        @DisplayName("AMBER: Needs review (no entity code)")
        void needsReview() {
            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("")
                    .priorReComplaint(true)
                    .reComplaintDate(LocalDate.of(2026, 5, 1))
                    .reComplaintReference("REF-001")
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            assertThat(verdict.getOverallSignal()).isEqualTo(MreVerdict.OverallSignal.NEEDS_HUMAN_REVIEW);
            assertThat(verdict.getTriageSignal()).isEqualTo("AMBER");
        }
    }

    @Nested
    @DisplayName("Business days mode")
    class BusinessDaysModeTests {

        @Test
        @DisplayName("Business days: weekend days not counted in window")
        void businessDaysWindow() {
            setupBusinessDaysMode();
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            // May 1 (Thu) + 30 business days = Jun 12 (Thu)
            // Filing on Jun 10 (Tue) = only ~28 business days, should FAIL
            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 10)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
        }

        @Test
        @DisplayName("Business days: filing after 30 business days passes")
        void businessDaysWindowPass() {
            setupBusinessDaysMode();
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            // May 1 (Thu) + 30 business days ≈ Jun 12 (Thu)
            // Filing on Jun 15 (Mon) should PASS
            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEFORE_WINDOW);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.PASS);
        }
    }

    @Nested
    @DisplayName("Timeline computation")
    class TimelineTests {

        @Test
        @DisplayName("Timeline includes all key dates")
        void timelineHasKeyDates() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));

            assertThat(verdict.getTimeline()).containsKey("reComplaintDate");
            assertThat(verdict.getTimeline()).containsKey("windowOpens");
            assertThat(verdict.getTimeline()).containsKey("filingDeadline");
            assertThat(verdict.getTimeline()).containsKey("windowDays");
            assertThat(verdict.getTimeline().get("windowDays")).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("Adversarial scenarios")
    class AdversarialTests {

        @Test
        @DisplayName("Null dates handled gracefully")
        void nullDates() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("SBI")
                    .priorReComplaint(true)
                    .reComplaintDate(null)
                    .reComplaintReference("REF-001")
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            assertThat(verdict).isNotNull();
            assertThat(verdict.getGroundVerdicts()).hasSize(6);
        }

        @Test
        @DisplayName("Future filing date still evaluates correctly")
        void futureFilingDate() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2030, 1, 1)));

            assertThat(verdict).isNotNull();
            GroundVerdict ground = findGround(verdict, MreGround.FILED_BEYOND_DEADLINE);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
        }

        @Test
        @DisplayName("Very old RE complaint date — limitation period catches it")
        void veryOldComplaint() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2010, 1, 1), LocalDate.of(2026, 6, 15)));

            GroundVerdict ground = findGround(verdict, MreGround.RE_COMPLAINT_BEYOND_LIMITATION);
            assertThat(ground.getStatus()).isEqualTo(GroundVerdict.Status.FAIL);
        }

        @Test
        @DisplayName("Multiple grounds fail simultaneously")
        void multipleFailures() {
            when(entityCoverageService.isEntityCovered("UNKNOWN", null)).thenReturn(false);

            ComplaintFacts facts = ComplaintFacts.builder()
                    .entityCode("UNKNOWN")
                    .priorReComplaint(false)
                    .filingDate(LocalDate.of(2026, 6, 15))
                    .sameGrievancePendingOrDecided(true)
                    .build();

            MreVerdict verdict = engine.evaluate(facts);
            assertThat(verdict.getOverallSignal()).isEqualTo(MreVerdict.OverallSignal.OBJECTIVELY_NON_MAINTAINABLE);
            assertThat(verdict.getFailedGrounds().size()).isGreaterThanOrEqualTo(3);
        }

        @Test
        @DisplayName("Rule version is included in verdict")
        void ruleVersionIncluded() {
            when(entityCoverageService.isEntityCovered(any(), any())).thenReturn(true);

            MreVerdict verdict = engine.evaluate(buildFacts("SBI", true,
                    LocalDate.of(2026, 5, 1), LocalDate.of(2026, 6, 15)));

            assertThat(verdict.getRuleVersion()).isEqualTo(1);
        }
    }

    // Helper methods

    private ComplaintFacts buildFacts(String entity, boolean priorRe, LocalDate reDate, LocalDate filingDate) {
        return ComplaintFacts.builder()
                .entityCode(entity)
                .priorReComplaint(priorRe)
                .reComplaintDate(reDate)
                .reComplaintReference("REF-001")
                .filingDate(filingDate)
                .build();
    }

    private GroundVerdict findGround(MreVerdict verdict, MreGround ground) {
        return verdict.getGroundVerdicts().stream()
                .filter(g -> g.getGround() == ground)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Ground " + ground + " not found in verdict"));
    }
}
