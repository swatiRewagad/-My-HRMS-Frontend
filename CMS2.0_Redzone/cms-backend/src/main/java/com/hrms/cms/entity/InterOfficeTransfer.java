package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "INTER_OFFICE_TRANSFERS", indexes = {
    @Index(name = "idx_iot_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_iot_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterOfficeTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(nullable = false, length = 50)
    private String fromOffice;

    @Column(nullable = false, length = 50)
    private String toOffice;

    @Column(nullable = false, length = 20)
    private String transferType; // RBIO_RBIO, RBIO_CEPC, CEPC_CEPC, CEPC_RBIO, CEPD_RBIO

    @Column(nullable = false, length = 20)
    private String status; // PENDING, APPROVED, REJECTED

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String rejectionComment;

    @Column(length = 100)
    private String requestedBy;

    @Column(length = 100)
    private String approvedBy;

    @Column(length = 200)
    private String previousOwner;

    private LocalDateTime requestedAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    protected void onCreate() {
        if (requestedAt == null) requestedAt = LocalDateTime.now();
        if (status == null) status = "PENDING";
    }
}
