package com.rbi.cms.assignment.engine.operator;

import com.rbi.cms.assignment.domain.enums.DataType;
import com.rbi.cms.assignment.domain.enums.OperatorCode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class OperatorRegistry {

    private final Map<OperatorCode, ConditionOperator> operators = new EnumMap<>(OperatorCode.class);

    public OperatorRegistry() {
        register(new EqOperator());
        register(new NeqOperator());
        register(new InOperator());
        register(new NotInOperator());
        register(new StartsWithOperator());
        register(new EndsWithOperator());
        register(new ContainsOperator());
        register(new IsNullOperator());
        register(new IsNotNullOperator());
        register(new GtOperator());
        register(new GteOperator());
        register(new LtOperator());
        register(new LteOperator());
        register(new BetweenOperator());
        register(new BeforeOperator());
        register(new OnOrBeforeOperator());
        register(new AfterOperator());
        register(new OnOrAfterOperator());
        register(new OlderThanDaysOperator());
        register(new WithinLastDaysOperator());
        register(new IsTrueOperator());
        register(new IsFalseOperator());
        register(new ContainsAnyOperator());
        register(new ContainsAllOperator());
        register(new NotContainsOperator());
        register(new IsEmptyOperator());
    }

    private void register(ConditionOperator op) {
        operators.put(op.code(), op);
    }

    public ConditionOperator get(OperatorCode code) {
        ConditionOperator op = operators.get(code);
        if (op == null) throw new IllegalArgumentException("Unknown operator: " + code);
        return op;
    }

    public Set<OperatorCode> getOperatorsFor(DataType dataType) {
        return operators.values().stream()
                .filter(op -> op.supportedTypes().contains(dataType))
                .map(ConditionOperator::code)
                .collect(Collectors.toSet());
    }

    // --- Operator implementations ---

    static class EqOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.EQ; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING, DataType.ENUM, DataType.NUMBER, DataType.MONEY, DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            if (cv.numericFrom() != null) return toDecimal(ctxValue).compareTo(cv.numericFrom()) == 0;
            return normalize(ctxValue.toString()).equals(normalize(cv.textValue()));
        }
    }

    static class NeqOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.NEQ; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING, DataType.ENUM, DataType.NUMBER, DataType.MONEY, DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            if (cv.numericFrom() != null) return toDecimal(ctxValue).compareTo(cv.numericFrom()) != 0;
            return !normalize(ctxValue.toString()).equals(normalize(cv.textValue()));
        }
    }

    static class InOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.IN; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING, DataType.ENUM, DataType.NUMBER, DataType.MONEY); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            List<String> list = cv.listValues();
            if (list == null || list.isEmpty()) return false;
            String normalized = normalize(ctxValue.toString());
            return list.stream().anyMatch(v -> normalize(v).equals(normalized));
        }
    }

    static class NotInOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.NOT_IN; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING, DataType.ENUM, DataType.NUMBER, DataType.MONEY); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return true; // null is not in any set
            List<String> list = cv.listValues();
            if (list == null || list.isEmpty()) return true;
            String normalized = normalize(ctxValue.toString());
            return list.stream().noneMatch(v -> normalize(v).equals(normalized));
        }
    }

    static class StartsWithOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.STARTS_WITH; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return normalize(ctxValue.toString()).startsWith(normalize(cv.textValue()));
        }
    }

    static class EndsWithOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.ENDS_WITH; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return normalize(ctxValue.toString()).endsWith(normalize(cv.textValue()));
        }
    }

    static class ContainsOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.CONTAINS; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return normalize(ctxValue.toString()).contains(normalize(cv.textValue()));
        }
    }

    static class IsNullOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.IS_NULL; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.values()); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            return ctxValue == null;
        }
    }

    static class IsNotNullOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.IS_NOT_NULL; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.values()); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            return ctxValue != null;
        }
    }

    static class GtOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.GT; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.NUMBER, DataType.MONEY); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toDecimal(ctxValue).compareTo(cv.numericFrom()) > 0;
        }
    }

    static class GteOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.GTE; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.NUMBER, DataType.MONEY); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toDecimal(ctxValue).compareTo(cv.numericFrom()) >= 0;
        }
    }

    static class LtOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.LT; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.NUMBER, DataType.MONEY); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toDecimal(ctxValue).compareTo(cv.numericFrom()) < 0;
        }
    }

    static class LteOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.LTE; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.NUMBER, DataType.MONEY); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toDecimal(ctxValue).compareTo(cv.numericFrom()) <= 0;
        }
    }

    static class BetweenOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.BETWEEN; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.NUMBER, DataType.MONEY, DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            // [lo, hi) — inclusive lower, exclusive upper
            if (cv.numericFrom() != null && cv.numericTo() != null) {
                BigDecimal val = toDecimal(ctxValue);
                return val.compareTo(cv.numericFrom()) >= 0 && val.compareTo(cv.numericTo()) < 0;
            }
            if (cv.dateFrom() != null && cv.dateTo() != null) {
                LocalDate val = toDate(ctxValue);
                return !val.isBefore(cv.dateFrom()) && val.isBefore(cv.dateTo());
            }
            return false;
        }
    }

    static class BeforeOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.BEFORE; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toDate(ctxValue).isBefore(cv.dateFrom());
        }
    }

    static class OnOrBeforeOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.ON_OR_BEFORE; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return !toDate(ctxValue).isAfter(cv.dateFrom());
        }
    }

    static class AfterOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.AFTER; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toDate(ctxValue).isAfter(cv.dateFrom());
        }
    }

    static class OnOrAfterOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.ON_OR_AFTER; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return !toDate(ctxValue).isBefore(cv.dateFrom());
        }
    }

    static class OlderThanDaysOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.OLDER_THAN_DAYS; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            long days = cv.numericFrom().longValue();
            LocalDate cutoff = LocalDate.now().minusDays(days);
            return toDate(ctxValue).isBefore(cutoff);
        }
    }

    static class WithinLastDaysOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.WITHIN_LAST_DAYS; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.DATE, DataType.DATETIME); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            long days = cv.numericFrom().longValue();
            LocalDate cutoff = LocalDate.now().minusDays(days);
            LocalDate val = toDate(ctxValue);
            return !val.isBefore(cutoff) && !val.isAfter(LocalDate.now());
        }
    }

    static class IsTrueOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.IS_TRUE; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.BOOLEAN); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return toBoolean(ctxValue);
        }
    }

    static class IsFalseOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.IS_FALSE; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.BOOLEAN); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            return !toBoolean(ctxValue);
        }
    }

    static class ContainsAnyOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.CONTAINS_ANY; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING_LIST); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            List<String> ctxList = toStringList(ctxValue);
            List<String> condList = cv.listValues();
            if (condList == null || condList.isEmpty()) return false;
            Set<String> ctxSet = ctxList.stream().map(OperatorRegistry::normalize).collect(Collectors.toSet());
            return condList.stream().anyMatch(v -> ctxSet.contains(normalize(v)));
        }
    }

    static class ContainsAllOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.CONTAINS_ALL; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING_LIST); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return false;
            List<String> ctxList = toStringList(ctxValue);
            List<String> condList = cv.listValues();
            if (condList == null || condList.isEmpty()) return true;
            Set<String> ctxSet = ctxList.stream().map(OperatorRegistry::normalize).collect(Collectors.toSet());
            return condList.stream().allMatch(v -> ctxSet.contains(normalize(v)));
        }
    }

    static class NotContainsOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.NOT_CONTAINS; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING_LIST); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return true;
            List<String> ctxList = toStringList(ctxValue);
            List<String> condList = cv.listValues();
            if (condList == null || condList.isEmpty()) return true;
            Set<String> ctxSet = ctxList.stream().map(OperatorRegistry::normalize).collect(Collectors.toSet());
            return condList.stream().noneMatch(v -> ctxSet.contains(normalize(v)));
        }
    }

    static class IsEmptyOperator implements ConditionOperator {
        @Override public OperatorCode code() { return OperatorCode.IS_EMPTY; }
        @Override public Set<DataType> supportedTypes() { return Set.of(DataType.STRING_LIST); }
        @Override public boolean test(Object ctxValue, ConditionValue cv) {
            if (ctxValue == null) return true;
            return toStringList(ctxValue).isEmpty();
        }
    }

    // --- Utility methods ---

    static String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }

    static BigDecimal toDecimal(Object value) {
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return new BigDecimal(value.toString().replaceAll("[^\\d.\\-]", ""));
    }

    static LocalDate toDate(Object value) {
        if (value instanceof LocalDate ld) return ld;
        return LocalDate.parse(value.toString().substring(0, Math.min(10, value.toString().length())));
    }

    static boolean toBoolean(Object value) {
        if (value instanceof Boolean b) return b;
        String s = value.toString().trim().toLowerCase();
        return "true".equals(s) || "1".equals(s) || "yes".equals(s);
    }

    @SuppressWarnings("unchecked")
    static List<String> toStringList(Object value) {
        if (value instanceof List<?> list) return (List<String>) list;
        if (value instanceof String s) return Arrays.asList(s.split(","));
        return List.of(value.toString());
    }
}
