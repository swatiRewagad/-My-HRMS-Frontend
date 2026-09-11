package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "ROLE_STATUS_MAPPING",
    uniqueConstraints = @UniqueConstraint(
        name = "UQ_ROLE_STATUS",
        columnNames = {"ROLE_NAME", "STATUS_CODE"}
    )
)
@Getter
@Setter
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

    @Column(name = "SEQUENCE", nullable = false)
    private Integer sequence;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "UPDATED_AT", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
