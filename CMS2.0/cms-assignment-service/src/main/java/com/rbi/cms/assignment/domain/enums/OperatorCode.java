package com.rbi.cms.assignment.domain.enums;

public enum OperatorCode {
    // String / Enum
    EQ, NEQ, IN, NOT_IN, STARTS_WITH, ENDS_WITH, CONTAINS, IS_NULL, IS_NOT_NULL,

    // Numeric / Money
    GT, GTE, LT, LTE, BETWEEN,

    // Date / DateTime
    BEFORE, ON_OR_BEFORE, AFTER, ON_OR_AFTER, OLDER_THAN_DAYS, WITHIN_LAST_DAYS,

    // Boolean
    IS_TRUE, IS_FALSE,

    // String List
    CONTAINS_ANY, CONTAINS_ALL, NOT_CONTAINS, IS_EMPTY
}
