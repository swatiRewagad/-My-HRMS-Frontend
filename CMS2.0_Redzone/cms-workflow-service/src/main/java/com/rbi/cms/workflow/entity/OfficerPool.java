package com.rbi.cms.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "WF_OFFICER_POOL")
public class OfficerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "USER_ID", nullable = false)
    private String userId;

    @Column(name = "DISPLAY_NAME")
    private String displayName;

    @Column(name = "ROLE_GROUP", nullable = false)
    private String roleGroup;

    @Column(name = "REGIONAL_OFFICE")
    private String regionalOffice;

    @Column(name = "IS_ACTIVE", nullable = false)
    private boolean active;

    @Column(name = "IS_ON_LEAVE", nullable = false)
    private boolean onLeave;

    @Column(name = "CURRENT_WORKLOAD")
    private int currentWorkload;

    @Column(name = "MAX_WORKLOAD")
    private int maxWorkload;
}
