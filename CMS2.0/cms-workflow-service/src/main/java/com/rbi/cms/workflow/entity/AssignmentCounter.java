package com.rbi.cms.workflow.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "WF_ASSIGNMENT_COUNTER")
public class AssignmentCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ROLE_GROUP", nullable = false, unique = true)
    private String roleGroup;

    @Column(name = "LAST_ASSIGNED_INDEX", nullable = false)
    private int lastAssignedIndex;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @PreUpdate
    @PrePersist
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
