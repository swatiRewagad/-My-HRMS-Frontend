package com.rbi.cms.assignment.engine.operator;

import com.rbi.cms.assignment.domain.enums.DataType;
import com.rbi.cms.assignment.domain.enums.OperatorCode;

import java.util.Set;

public interface ConditionOperator {

    OperatorCode code();

    Set<DataType> supportedTypes();

    boolean test(Object contextValue, ConditionValue conditionValue);
}
