package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "WF_OFFICER_POOL", indexes = {
    @Index(name = "idx_wf_officer_user_id", columnList = "userId", unique = true)
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WfOfficerPool {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, length = 100, unique = true)
    private String userId;

    @Column(name = "display_name", length = 200)
    private String displayName;

    @Column(name = "role_group", length = 50)
    private String roleGroup;

    @Column(name = "regional_office", length = 100)
    private String regionalOffice;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_on_leave", nullable = false)
    private boolean onLeave;

    @Column(name = "current_workload")
    private int currentWorkload;

    @Column(name = "max_workload")
    private int maxWorkload;
}
