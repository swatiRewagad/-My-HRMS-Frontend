package com.rbi.cms.assignment.engine.evaluator;

import com.rbi.cms.assignment.domain.enums.*;
import com.rbi.cms.assignment.engine.compiler.*;
import com.rbi.cms.assignment.engine.operator.ConditionValue;
import com.rbi.cms.assignment.engine.operator.OperatorRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleEvaluatorTest {

    private RuleEvaluator evaluator;
    private CompiledRuleSet ruleSet;

    @BeforeEach
    void setup() {
        evaluator = new RuleEvaluator(new OperatorRegistry());
        ruleSet = buildAppendixARuleSet();
    }

    @Test
    void veryHighValue_matchesR001() {
        Map<String, Object> ctx = Map.of("claimAmount", 3000000, "regulatedEntityType", "BANK", "complaintCategory", "LOAN", "state", "DL", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-001");
    }

    @Test
    void highValueBank_matchesR002() {
        Map<String, Object> ctx = Map.of("claimAmount", 750000, "regulatedEntityType", "BANK", "complaintCategory", "CARD_FRAUD", "state", "MH", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-002");
    }

    @Test
    void digitalFraud_lowAmount_matchesR003() {
        // Amount 100000 < 500000, BANK, UPI category, state DL — R-001 (fail), R-002 (amount in range but category doesn't matter, entity BANK => match!)
        // Actually R-002 needs BETWEEN [500000,2500000) so 100000 fails R-002. R-003 has IN(UPI,CARD_FRAUD,NET_BANKING)
        Map<String, Object> ctx = Map.of("claimAmount", 100000, "regulatedEntityType", "BANK", "complaintCategory", "UPI", "state", "DL", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-003");
    }

    @Test
    void nbfc_matchesR004() {
        Map<String, Object> ctx = Map.of("claimAmount", 200000, "regulatedEntityType", "NBFC", "complaintCategory", "LOAN", "state", "UP", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-004");
    }

    @Test
    void westZone_lowValue_matchesR005() {
        Map<String, Object> ctx = Map.of("claimAmount", 200000, "regulatedEntityType", "BANK", "complaintCategory", "DEPOSIT", "state", "MH", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-005");
    }

    @Test
    void southZone_lowValue_matchesR006() {
        Map<String, Object> ctx = Map.of("claimAmount", 200000, "regulatedEntityType", "BANK", "complaintCategory", "DEPOSIT", "state", "KA", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-006");
    }

    @Test
    void appeal_matchesR007() {
        Map<String, Object> ctx = Map.of("claimAmount", 100000, "regulatedEntityType", "BANK", "complaintCategory", "LOAN", "state", "DL", "escalationLevel", "APPEAL");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        // R-003: category LOAN not in digital fraud list. R-005: state DL not in west. R-006: DL not in south.
        // R-007: escalationLevel = APPEAL → match
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-007");
    }

    @Test
    void noMatch_fallsToDefault() {
        // High amount (not very high), entity PAYMENT_SYSTEM (not BANK/NBFC), category INSURANCE, state UP, L1
        Map<String, Object> ctx = Map.of("claimAmount", 600000, "regulatedEntityType", "PAYMENT_SYSTEM", "complaintCategory", "INSURANCE", "state", "UP", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        // R-001: 600000 < 2500000 fail. R-002: BETWEEN match but entity != BANK fail. R-003: category not digital. R-004: not NBFC. R-005/6: amount >= 500000 not in [0,500000). R-007: not APPEAL. R-008: no isSeniorCitizen.
        assertThat(result.fallbackApplied()).isTrue();
        assertThat(result.outcome().targetId()).isEqualTo("GENERAL_INTAKE_POOL");
    }

    @Test
    void nullRuleSet_returnsNoMatch() {
        EvaluationResult result = evaluator.evaluate(null, Map.of("claimAmount", 100), false);
        assertThat(result.fallbackApplied()).isTrue();
    }

    @Test
    void fullTrace_returnsAllEntries() {
        Map<String, Object> ctx = Map.of("claimAmount", 3000000);
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, true);
        assertThat(result.trace()).isNotNull();
        assertThat(result.trace().get(0).matched()).isTrue();
        assertThat(result.trace().get(0).ruleCode()).isEqualTo("R-001");
    }

    @Test
    void between_lowerBoundaryInclusive() {
        Map<String, Object> ctx = Map.of("claimAmount", 500000, "regulatedEntityType", "BANK", "complaintCategory", "DEPOSIT", "state", "UP", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-002");
    }

    @Test
    void between_upperBoundaryExclusive() {
        // 2500000 should NOT match R-002 [500000, 2500000) but SHOULD match R-001 (>= 2500000)
        Map<String, Object> ctx = Map.of("claimAmount", 2500000, "regulatedEntityType", "BANK", "complaintCategory", "DEPOSIT", "state", "UP", "escalationLevel", "L1");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-001");
    }

    @Test
    void missingAttribute_conditionFalse_wildcardTrue() {
        // No escalationLevel provided — R-007 requires EQ APPEAL so should fail
        // No isSeniorCitizen — R-008 requires IS_TRUE so should fail
        // With amount 200000, BANK, DEPOSIT, state MH → should match R-005
        Map<String, Object> ctx = Map.of("claimAmount", 200000, "regulatedEntityType", "BANK", "complaintCategory", "DEPOSIT", "state", "MH");
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-005");
    }

    @Test
    void nestedContext_resolvesValues() {
        Map<String, Object> ctx = Map.of(
                "complaint", Map.of("claimAmount", 3000000, "category", "LOAN"),
                "claimAmount", 3000000
        );
        EvaluationResult result = evaluator.evaluate(ruleSet, ctx, false);
        assertThat(result.matched()).isTrue();
        assertThat(result.matchedRule().ruleCode()).isEqualTo("R-001");
    }

    // --- Build the Appendix A ruleset in memory ---
    private CompiledRuleSet buildAppendixARuleSet() {
        CompiledRule r001 = new CompiledRule(1L, "R-001", "Very high value", 10, 10,
                List.of(cond("claimAmount", OperatorCode.GTE, ConditionValue.ofNumeric(new BigDecimal("2500000")))),
                outcome("GROUP", "SENIOR_ADJUDICATION"), 16);

        CompiledRule r002 = new CompiledRule(2L, "R-002", "High value banks", 20, 20,
                List.of(
                        cond("claimAmount", OperatorCode.BETWEEN, ConditionValue.ofRange(new BigDecimal("500000"), new BigDecimal("2500000"))),
                        cond("regulatedEntityType", OperatorCode.EQ, ConditionValue.ofText("BANK"))
                ),
                outcome("GROUP", "BANK_HIGH_VALUE"), 15);

        CompiledRule r003 = new CompiledRule(3L, "R-003", "Digital fraud fast-track", 30, 30,
                List.of(cond("complaintCategory", OperatorCode.IN, ConditionValue.ofList(List.of("UPI", "CARD_FRAUD", "NET_BANKING")))),
                outcome("GROUP", "DIGITAL_FRAUD_CELL"), 16);

        CompiledRule r004 = new CompiledRule(4L, "R-004", "NBFC cell", 40, 40,
                List.of(cond("regulatedEntityType", OperatorCode.EQ, ConditionValue.ofText("NBFC"))),
                outcome("GROUP", "NBFC_CELL"), 16);

        CompiledRule r005 = new CompiledRule(5L, "R-005", "West zone general", 50, 50,
                List.of(
                        cond("claimAmount", OperatorCode.BETWEEN, ConditionValue.ofRange(BigDecimal.ZERO, new BigDecimal("500000"))),
                        cond("state", OperatorCode.IN, ConditionValue.ofList(List.of("MH", "GJ", "GA")))
                ),
                outcome("GROUP", "ORBIO_MUMBAI"), 15);

        CompiledRule r006 = new CompiledRule(6L, "R-006", "South zone general", 60, 60,
                List.of(
                        cond("claimAmount", OperatorCode.BETWEEN, ConditionValue.ofRange(BigDecimal.ZERO, new BigDecimal("500000"))),
                        cond("state", OperatorCode.IN, ConditionValue.ofList(List.of("KA", "TN", "KL", "AP", "TS")))
                ),
                outcome("GROUP", "ORBIO_CHENNAI"), 15);

        CompiledRule r007 = new CompiledRule(7L, "R-007", "Appeal to AA", 70, 70,
                List.of(cond("escalationLevel", OperatorCode.EQ, ConditionValue.ofText("APPEAL"))),
                outcome("ROLE_IN_ORG_UNIT", "APPELLATE_AUTHORITY"), 16);

        CompiledRule r008 = new CompiledRule(8L, "R-008", "Senior citizen priority", 80, 80,
                List.of(cond("isSeniorCitizen", OperatorCode.IS_TRUE, ConditionValue.ofText(null))),
                outcome("GROUP", "PRIORITY_DESK"), 16);

        CompiledOutcome defaultOutcome = new CompiledOutcome(OutcomeType.QUEUE, "GENERAL_INTAKE_POOL", null, null, null, null);

        return new CompiledRuleSet(1L, 1L, 1, "COMPLAINT_INTAKE", "RBI-CMS", HitPolicy.FIRST,
                List.of(r001, r002, r003, r004, r005, r006, r007, r008), defaultOutcome);
    }

    private CompiledCondition cond(String attr, OperatorCode op, ConditionValue val) {
        return new CompiledCondition(attr, DataType.STRING, op, val, false);
    }

    private CompiledOutcome outcome(String type, String target) {
        return new CompiledOutcome(OutcomeType.valueOf(type), target, AssignMode.AS_GROUP, null, null, null);
    }
}
