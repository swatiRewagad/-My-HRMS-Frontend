package com.rbi.cms.assignment.engine.operator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.List;

public record ConditionValue(
    String textValue,
    BigDecimal numericFrom,
    BigDecimal numericTo,
    LocalDate dateFrom,
    LocalDate dateTo,
    List<String> listValues
) {

    public static ConditionValue ofText(String value) {
        return new ConditionValue(value, null, null, null, null, null);
    }

    public static ConditionValue ofList(List<String> values) {
        return new ConditionValue(null, null, null, null, null, values);
    }

    public static ConditionValue ofRange(BigDecimal from, BigDecimal to) {
        return new ConditionValue(null, from, to, null, null, null);
    }

    public static ConditionValue ofNumeric(BigDecimal value) {
        return new ConditionValue(null, value, null, null, null, null);
    }
}
