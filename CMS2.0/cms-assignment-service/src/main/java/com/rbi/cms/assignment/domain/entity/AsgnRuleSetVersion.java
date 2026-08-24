package com.rbi.cms.assignment.domain.entity;

import com.rbi.cms.assignment.domain.enums.VersionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ASGN_RULE_SET_VERSION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AsgnRuleSetVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "TENANT_ID", nullable = false, length = 50)
    private String tenantId;

    @Column(name = "RULE_SET_ID", nullable = false)
    private Long ruleSetId;

    @Column(name = "VERSION_NO", nullable = false)
    private Integer versionNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 30)
    private VersionStatus status;

    @Column(name = "EFFECTIVE_FROM")
    private Instant effectiveFrom;

    @Column(name = "EFFECTIVE_TO")
    private Instant effectiveTo;

    @Column(name = "MAKER_ID", length = 100)
    private String makerId;

    @Column(name = "MAKER_AT")
    private Instant makerAt;

    @Column(name = "MAKER_REMARKS", length = 1000)
    private String makerRemarks;

    @Column(name = "CHECKER_ID", length = 100)
    private String checkerId;

    @Column(name = "CHECKER_AT")
    private Instant checkerAt;

    @Column(name = "CHECKER_REMARKS", length = 1000)
    private String checkerRemarks;

    @Column(name = "PUBLISHED_BY", length = 100)
    private String publishedBy;

    @Column(name = "PUBLISHED_AT")
    private Instant publishedAt;

    @Column(name = "CHECKSUM", length = 64)
    private String checksum;

    @Version
    @Column(name = "OPT_LOCK")
    private Long optLock;

    @Column(name = "CREATED_AT", updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }
}
