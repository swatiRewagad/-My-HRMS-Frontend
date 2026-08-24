package com.rbi.cms.assignment.domain.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "ASGN_RR_COUNTER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRoundRobinCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "GROUP_ID", nullable = false, length = 100)
    private String groupId;

    @Column(name = "STRATEGY_KEY", nullable = false, length = 100)
    private String strategyKey;

    @Column(name = "LAST_INDEX")
    private Integer lastIndex;

    @Version
    @Column(name = "VERSION")
    private Long version;
}
