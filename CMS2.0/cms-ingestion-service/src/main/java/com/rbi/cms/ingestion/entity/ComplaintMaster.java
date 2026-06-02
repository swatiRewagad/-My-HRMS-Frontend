package com.rbi.cms.ingestion.entity;

import com.rbi.cms.common.enums.Channel;
import com.rbi.cms.common.enums.ComplaintCategory;
import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.common.enums.Priority;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "COMPLAINT_MASTER")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "complaint_seq")
    @SequenceGenerator(name = "complaint_seq", sequenceName = "COMPLAINT_MASTER_SEQ", allocationSize = 1)
    @Column(name = "ID")
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false, unique = true, length = 30)
    private String complaintId;

    @Enumerated(EnumType.STRING)
    @Column(name = "CHANNEL", nullable = false, length = 20)
    private Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(name = "CATEGORY", nullable = false, length = 30)
    private ComplaintCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "STATUS", nullable = false, length = 20)
    private ComplaintStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "PRIORITY", nullable = false, length = 10)
    private Priority priority;

    @Column(name = "COMPLAINANT_NAME", nullable = false, length = 200)
    private String complainantName;

    @Column(name = "COMPLAINANT_EMAIL", length = 200)
    private String complainantEmail;

    @Column(name = "COMPLAINANT_PHONE", length = 20)
    private String complainantPhone;

    @Column(name = "ENTITY_NAME", nullable = false, length = 200)
    private String entityName;

    @Column(name = "ENTITY_TYPE", length = 50)
    private String entityType;

    @Column(name = "SUBJECT", nullable = false, length = 500)
    private String subject;

    @Column(name = "DESCRIPTION", nullable = false, length = 4000)
    private String description;

    @Column(name = "AMOUNT_INVOLVED")
    private Double amountInvolved;

    @Column(name = "TRANSACTION_DATE")
    private LocalDate transactionDate;

    @Column(name = "JURISDICTION_CODE", length = 20)
    private String jurisdictionCode;

    @Column(name = "ASSIGNED_TO", length = 100)
    private String assignedTo;

    @Column(name = "ASSIGNED_TEAM", length = 50)
    private String assignedTeam;

    @Column(name = "SLA_DUE_DATE")
    private Instant slaDueDate;

    @Column(name = "RESOLUTION_SUMMARY", length = 4000)
    private String resolutionSummary;

    @Column(name = "RESOLVED_AT")
    private Instant resolvedAt;

    @Column(name = "CLOSED_AT")
    private Instant closedAt;

    @Column(name = "CREATED_AT", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "UPDATED_AT")
    private Instant updatedAt;

    @Column(name = "CREATED_BY", length = 100)
    private String createdBy;

    @Version
    @Column(name = "VERSION")
    private Long version;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
        if (status == null) {
            status = ComplaintStatus.NEW;
        }
        if (priority == null) {
            priority = Priority.MEDIUM;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
