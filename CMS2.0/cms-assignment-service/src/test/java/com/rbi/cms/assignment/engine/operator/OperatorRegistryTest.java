package com.rbi.cms.assignment.engine.operator;

import com.rbi.cms.assignment.domain.enums.OperatorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OperatorRegistryTest {

    private OperatorRegistry registry;

    @BeforeEach
    void setup() {
        registry = new OperatorRegistry();
    }

    // --- EQ ---
    @Test void eq_string_match() { assertThat(op(OperatorCode.EQ).test("BANK", cv("BANK"))).isTrue(); }
    @Test void eq_string_case_insensitive() { assertThat(op(OperatorCode.EQ).test("bank", cv("BANK"))).isTrue(); }
    @Test void eq_string_mismatch() { assertThat(op(OperatorCode.EQ).test("NBFC", cv("BANK"))).isFalse(); }
    @Test void eq_null_value() { assertThat(op(OperatorCode.EQ).test(null, cv("BANK"))).isFalse(); }
    @Test void eq_numeric() { assertThat(op(OperatorCode.EQ).test(500000, cvNum(new BigDecimal("500000")))).isTrue(); }

    // --- NEQ ---
    @Test void neq_match() { assertThat(op(OperatorCode.NEQ).test("NBFC", cv("BANK"))).isTrue(); }
    @Test void neq_same() { assertThat(op(OperatorCode.NEQ).test("BANK", cv("BANK"))).isFalse(); }
    @Test void neq_null() { assertThat(op(OperatorCode.NEQ).test(null, cv("BANK"))).isFalse(); }

    // --- IN ---
    @Test void in_match() { assertThat(op(OperatorCode.IN).test("MH", cvList(List.of("MH", "GJ", "GA")))).isTrue(); }
    @Test void in_no_match() { assertThat(op(OperatorCode.IN).test("DL", cvList(List.of("MH", "GJ", "GA")))).isFalse(); }
    @Test void in_null() { assertThat(op(OperatorCode.IN).test(null, cvList(List.of("MH")))).isFalse(); }
    @Test void in_case_insensitive() { assertThat(op(OperatorCode.IN).test("mh", cvList(List.of("MH", "GJ")))).isTrue(); }

    // --- NOT_IN ---
    @Test void notIn_match() { assertThat(op(OperatorCode.NOT_IN).test("DL", cvList(List.of("MH", "GJ")))).isTrue(); }
    @Test void notIn_in_set() { assertThat(op(OperatorCode.NOT_IN).test("MH", cvList(List.of("MH", "GJ")))).isFalse(); }
    @Test void notIn_null_is_true() { assertThat(op(OperatorCode.NOT_IN).test(null, cvList(List.of("MH")))).isTrue(); }

    // --- GT / GTE / LT / LTE ---
    @Test void gt_above() { assertThat(op(OperatorCode.GT).test(600000, cvNum(new BigDecimal("500000")))).isTrue(); }
    @Test void gt_equal() { assertThat(op(OperatorCode.GT).test(500000, cvNum(new BigDecimal("500000")))).isFalse(); }
    @Test void gte_equal() { assertThat(op(OperatorCode.GTE).test(500000, cvNum(new BigDecimal("500000")))).isTrue(); }
    @Test void gte_below() { assertThat(op(OperatorCode.GTE).test(499999, cvNum(new BigDecimal("500000")))).isFalse(); }
    @Test void lt_below() { assertThat(op(OperatorCode.LT).test(499999, cvNum(new BigDecimal("500000")))).isTrue(); }
    @Test void lte_equal() { assertThat(op(OperatorCode.LTE).test(500000, cvNum(new BigDecimal("500000")))).isTrue(); }
    @Test void gt_null() { assertThat(op(OperatorCode.GT).test(null, cvNum(new BigDecimal("500000")))).isFalse(); }

    // --- BETWEEN [lo, hi) ---
    @Test void between_at_lower() { assertThat(op(OperatorCode.BETWEEN).test(500000, cvRange(new BigDecimal("500000"), new BigDecimal("2500000")))).isTrue(); }
    @Test void between_at_upper_exclusive() { assertThat(op(OperatorCode.BETWEEN).test(2500000, cvRange(new BigDecimal("500000"), new BigDecimal("2500000")))).isFalse(); }
    @Test void between_inside() { assertThat(op(OperatorCode.BETWEEN).test(750000, cvRange(new BigDecimal("500000"), new BigDecimal("2500000")))).isTrue(); }
    @Test void between_below() { assertThat(op(OperatorCode.BETWEEN).test(499999, cvRange(new BigDecimal("500000"), new BigDecimal("2500000")))).isFalse(); }
    @Test void between_null() { assertThat(op(OperatorCode.BETWEEN).test(null, cvRange(new BigDecimal("0"), new BigDecimal("500000")))).isFalse(); }
    @Test void between_zero_inclusive() { assertThat(op(OperatorCode.BETWEEN).test(0, cvRange(new BigDecimal("0"), new BigDecimal("500000")))).isTrue(); }

    // --- IS_NULL / IS_NOT_NULL ---
    @Test void isNull_null() { assertThat(op(OperatorCode.IS_NULL).test(null, cv(null))).isTrue(); }
    @Test void isNull_present() { assertThat(op(OperatorCode.IS_NULL).test("X", cv(null))).isFalse(); }
    @Test void isNotNull_present() { assertThat(op(OperatorCode.IS_NOT_NULL).test("X", cv(null))).isTrue(); }
    @Test void isNotNull_null() { assertThat(op(OperatorCode.IS_NOT_NULL).test(null, cv(null))).isFalse(); }

    // --- IS_TRUE / IS_FALSE ---
    @Test void isTrue_true() { assertThat(op(OperatorCode.IS_TRUE).test(true, cv(null))).isTrue(); }
    @Test void isTrue_false() { assertThat(op(OperatorCode.IS_TRUE).test(false, cv(null))).isFalse(); }
    @Test void isTrue_null() { assertThat(op(OperatorCode.IS_TRUE).test(null, cv(null))).isFalse(); }
    @Test void isFalse_false() { assertThat(op(OperatorCode.IS_FALSE).test(false, cv(null))).isTrue(); }
    @Test void isTrue_string_true() { assertThat(op(OperatorCode.IS_TRUE).test("true", cv(null))).isTrue(); }

    // --- STARTS_WITH / ENDS_WITH / CONTAINS ---
    @Test void startsWith() { assertThat(op(OperatorCode.STARTS_WITH).test("BR0091", cv("BR"))).isTrue(); }
    @Test void endsWith() { assertThat(op(OperatorCode.ENDS_WITH).test("BR0091", cv("91"))).isTrue(); }
    @Test void contains() { assertThat(op(OperatorCode.CONTAINS).test("HELLO WORLD", cv("lo wo"))).isTrue(); }
    @Test void startsWith_null() { assertThat(op(OperatorCode.STARTS_WITH).test(null, cv("BR"))).isFalse(); }

    // --- CONTAINS_ANY / CONTAINS_ALL / NOT_CONTAINS / IS_EMPTY ---
    @Test void containsAny_match() { assertThat(op(OperatorCode.CONTAINS_ANY).test(List.of("A", "B", "C"), cvList(List.of("B", "X")))).isTrue(); }
    @Test void containsAny_no() { assertThat(op(OperatorCode.CONTAINS_ANY).test(List.of("A", "B"), cvList(List.of("X", "Y")))).isFalse(); }
    @Test void containsAll_yes() { assertThat(op(OperatorCode.CONTAINS_ALL).test(List.of("A", "B", "C"), cvList(List.of("A", "B")))).isTrue(); }
    @Test void containsAll_no() { assertThat(op(OperatorCode.CONTAINS_ALL).test(List.of("A", "B"), cvList(List.of("A", "C")))).isFalse(); }
    @Test void notContains_yes() { assertThat(op(OperatorCode.NOT_CONTAINS).test(List.of("A", "B"), cvList(List.of("X", "Y")))).isTrue(); }
    @Test void isEmpty_null() { assertThat(op(OperatorCode.IS_EMPTY).test(null, cv(null))).isTrue(); }
    @Test void isEmpty_empty() { assertThat(op(OperatorCode.IS_EMPTY).test(List.of(), cv(null))).isTrue(); }
    @Test void isEmpty_notEmpty() { assertThat(op(OperatorCode.IS_EMPTY).test(List.of("A"), cv(null))).isFalse(); }

    // --- helpers ---
    private ConditionOperator op(OperatorCode code) { return registry.get(code); }
    private ConditionValue cv(String text) { return ConditionValue.ofText(text); }
    private ConditionValue cvNum(BigDecimal num) { return ConditionValue.ofNumeric(num); }
    private ConditionValue cvRange(BigDecimal from, BigDecimal to) { return ConditionValue.ofRange(from, to); }
    private ConditionValue cvList(List<String> list) { return ConditionValue.ofList(list); }
}
