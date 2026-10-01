package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_FEEDBACK", indexes = {
    @Index(name = "idx_feedback_complaint", columnList = "complaintNumber"),
    @Index(name = "idx_feedback_phone", columnList = "complainantPhone"),
    @Index(name = "idx_feedback_office", columnList = "officeCode")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_feedback_complaint", columnNames = "complaintNumber")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String complaintNumber;

    @Column(nullable = false)
    private Integer overallRating;

    @Column(nullable = false)
    private Integer easeOfFiling;

    private Integer timelinessRating;

    private Integer communicationRating;

    private Integer satisfactionRating;

    @Column(nullable = false)
    private Integer grievanceRedressTime;

    @Column(nullable = false, length = 100)
    private String sourceOfInformation;

    @Column(length = 500)
    private String sourceOtherText;

    @Column(nullable = false, length = 50)
    private String cmsPortalAwareness;

    @Column(length = 500)
    private String feedbackText;

    @Column(length = 500)
    private String suggestions;

    @Column(length = 20)
    private String complainantPhone;

    @Column(length = 20)
    private String officeCode;

    private LocalDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        this.submittedAt = LocalDateTime.now();
    }
}
