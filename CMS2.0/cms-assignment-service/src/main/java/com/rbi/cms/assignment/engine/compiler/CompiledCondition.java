package com.rbi.cms.assignment.engine.compiler;

import com.rbi.cms.assignment.domain.enums.DataType;
import com.rbi.cms.assignment.domain.enums.OperatorCode;
import com.rbi.cms.assignment.engine.operator.ConditionValue;

public record CompiledCondition(
    String attributeCode,
    DataType dataType,
    OperatorCode operator,
    ConditionValue value,
    boolean caseSensitive
) {}
