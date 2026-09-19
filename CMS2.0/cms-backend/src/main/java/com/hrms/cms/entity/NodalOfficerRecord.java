package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
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

    // The business key the officer sees, and the key the NO-record comment endpoints already address
    // records by (ComplaintComment.noRecordNumber). Derived from the identity id after insert rather
    // than from a counter table of its own, so it is unique without a second sequence to keep in step.
    @Column(length = 50, unique = true)
    private String recordNumber;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    @Column(length = 200)
    private String entityName;

    @Column(length = 200)
    private String nodalOfficerName;

    @Column(length = 200)
    private String pnoName;

    // Snapshotted alongside pnoName for the same reason the nodal officer's own contact details are:
    // the record must keep showing who was reachable when the complaint was forwarded.
    @Column(length = 200)
    private String pnoEmail;

    @Column(length = 20)
    private String pnoPhone;

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

    // The officer's assessment of the record. Which of these apply is decided by status: the advisory
    // fields belong to ADVISORY_ISSUED, the two award dates to AWARD_PASS, and the comply date to
    // 13_1_NOTICE. They are kept on the record rather than on the complaint because a complaint can be
    // assessed once per nodal officer record, and Complaint.awardAmount already holds the single
    // adjudicated figure that the separate adjudication flow writes.
    private LocalDate advisoryComplianceDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal disputeAmount;

    @Column(precision = 15, scale = 2)
    private BigDecimal compensationLoss;

    @Column(precision = 15, scale = 2)
    private BigDecimal compensationMental;

    private LocalDate awardImplementationDate;

    private LocalDate awardAcceptanceDate;

    // Stored rather than recomputed on each page load: the screen used to derive it as "today + 15
    // days", so the deadline the nodal officer was held to moved every time anybody opened the record.
    // Named explicitly because the implicit strategy's handling of a digit/camelCase boundary is not
    // obvious enough to hand-write matching DDL against.
    @Column(name = "notice_131_comply_date")
    private LocalDate notice131ComplyDate;

    // Evidence on the record itself that the forward happened, independent of RE_RESPONSE_TRACKER,
    // which is keyed by complaint and owned by the responsiveness sweep.
    private LocalDateTime forwardedToReAt;

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
