package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "NODAL_OFFICER_RECORDS", indexes = {
    @Index(name = "idx_no_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_no_entity", columnList = "entityName"),
    @Index(name = "idx_no_status", columnList = "status"),
    @Index(name = "idx_no_last_modified", columnList = "lastModifiedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NodalOfficerRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(length = 200)
    private String entityName;

    @Column(length = 200)
    private String nodalOfficerName;

    @Column(length = 200)
    private String pnoName;

    @Column(length = 100)
    private String designation;

    @Column(length = 200)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 30, nullable = false)
    @Builder.Default
    private String status = "INFORMATION_REQUIRED";

    @Column(length = 200)
    private String assignedTo;

    private LocalDateTime createdAt;
    private LocalDateTime lastModifiedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.lastModifiedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.lastModifiedAt = LocalDateTime.now();
    }
}
