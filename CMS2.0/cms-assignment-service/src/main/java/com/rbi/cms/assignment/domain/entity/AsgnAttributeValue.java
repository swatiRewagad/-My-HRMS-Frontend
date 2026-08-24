package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ASGN_ATTRIBUTE_VALUE")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnAttributeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "ATTRIBUTE_CODE", nullable = false, length = 100)
    private String attributeCode;

    @Column(name = "VALUE_CODE", nullable = false, length = 100)
    private String valueCode;

    @Column(name = "VALUE_LABEL", nullable = false, length = 200)
    private String valueLabel;

    @Column(name = "SORT_ORDER")
    private Integer sortOrder;

    @Column(name = "ACTIVE")
    private boolean active;
}
