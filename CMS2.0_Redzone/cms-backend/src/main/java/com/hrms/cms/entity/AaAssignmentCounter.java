package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * The persisted round-robin pointer for one role group.
 *
 * Maps the existing WF_ASSIGNMENT_COUNTER table. LAST_ASSIGNED_INDEX is left untouched because
 * cms-workflow-service still uses it for the CRPC/RBIO/CEPC pools; this module writes
 * LAST_ASSIGNED_USER_ID instead.
 *
 * Why a user id and not the index: the index was applied modulo a candidate list that is re-sorted by
 * workload on every call, and whose size changes with leave and activation. Position 3 in one call is
 * a different officer in the next, so the stored index did not identify anybody and the rotation was
 * not deterministic. A user id survives re-sorting, pool membership changes and a restart.
 */
@Entity
@Table(name = "wf_assignment_counter")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AaAssignmentCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_group", nullable = false, unique = true)
    private String roleGroup;

    /** Legacy positional pointer, owned by cms-workflow-service. Not read by this module. */
    @Column(name = "last_assigned_index", nullable = false)
    private int lastAssignedIndex;

    /**
     * The officer who received the most recent AA assignment in this role group.
     *
     * Null means "no rotation history", in which case selection starts at the head of the ordered
     * candidate list. Story 4's pointer reset is expressed by setting this back to null.
     */
    @Column(name = "last_assigned_user_id", length = 255)
    private String lastAssignedUserId;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }
}
