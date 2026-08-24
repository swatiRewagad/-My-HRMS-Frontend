package com.rbi.cms.assignment.domain.entity;

import com.rbi.cms.assignment.domain.enums.OperatorCode;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ASGN_RULE_CONDITION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRuleCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "RULE_ID", nullable = false)
    private Long ruleId;

    @Column(name = "ATTRIBUTE_CODE", nullable = false, length = 100)
    private String attributeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "OPERATOR", nullable = false, length = 30)
    private OperatorCode operator;

    @Column(name = "VALUE_TEXT", length = 500)
    private String valueText;

    @Column(name = "VALUE_NUM_FROM", precision = 19, scale = 4)
    private BigDecimal valueNumFrom;

    @Column(name = "VALUE_NUM_TO", precision = 19, scale = 4)
    private BigDecimal valueNumTo;

    @Column(name = "VALUE_DATE_FROM")
    private LocalDate valueDateFrom;

    @Column(name = "VALUE_DATE_TO")
    private LocalDate valueDateTo;

    @Lob
    @Column(name = "VALUE_LIST")
    private String valueList;
}
