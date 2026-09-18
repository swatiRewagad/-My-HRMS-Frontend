package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "wf_officer_pool")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WfOfficerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "is_active", nullable = false, columnDefinition = "BIT(1)")
    private boolean isActive;

    @Column(name = "current_workload")
    private Integer currentWorkload;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "max_workload")
    private Integer maxWorkload;

    @Column(name = "is_on_leave", nullable = false, columnDefinition = "BIT(1)")
    private boolean isOnLeave;

    @Column(name = "regional_office")
    private String regionalOffice;

    @Column(name = "role_group", nullable = false)
    private String roleGroup;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "department")
    private String department;
}
