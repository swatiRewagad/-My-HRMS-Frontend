package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Which status-dropdown entries a CEPC role is offered, and in what order.
 *
 * <p><b>Division of labour with {@link CepcDashboardFilter}.</b> This table answers "may a CEPC_REVIEWER
 * pick this entry, and where does it sit in the list". It does not answer "what does this entry select" —
 * that stays in {@code CEPC_DASHBOARD_FILTER}, one row per {@link #statusCode}, carrying the predicate.
 * The split is deliberate: a status appears here once per role that can see it (five rows for
 * {@code ALL_COMPLAINTS}), so holding the predicate here too would mean five copies of one query free to
 * drift apart.
 *
 * <p><b>Every {@link #statusCode} must have a matching {@code STATUS}-dimension row in
 * {@code CEPC_DASHBOARD_FILTER}.</b> {@code CepcComplaintSearchService} resolves the selected code against
 * that table and, finding nothing, fails closed — the grid comes back empty rather than raising. So a code
 * seeded here without a filter row there is a dropdown entry that silently matches no complaints.
 *
 * <p>{@link #roleName} holds the role as {@code CepcIdentityResolver.CEPC_ROLES} spells it —
 * {@code CEPC_INCHARGE}, not {@code CEPC_IN_CHARGE}. The resolver returns that spelling and the lookup here
 * is an equality match, so a differently-spelled row is a row no caller ever sees.
 */
@Entity
@Table(name = "ROLE_STATUS_MAPPING",
        uniqueConstraints = @UniqueConstraint(name = "UQ_ROLE_STATUS",
                columnNames = {"ROLE_NAME", "STATUS_CODE"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleStatusMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "ROLE_NAME", nullable = false, length = 50)
    private String roleName;

    @Column(name = "STATUS_CODE", nullable = false, length = 50)
    private String statusCode;

    @Column(name = "DESCRIPTION", length = 255)
    private String description;

    /**
     * Position in this role's dropdown, ascending.
     *
     * <p>Per-role rather than per-status, which is the point of carrying it here: {@code ALL_COMPLAINTS}
     * leads the list for every role, but a dealing officer wants their send-back queue near the top where a
     * closing authority wants closure entries there.
     */
    @Column(name = "SEQUENCE", nullable = false)
    private Integer sequence;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
