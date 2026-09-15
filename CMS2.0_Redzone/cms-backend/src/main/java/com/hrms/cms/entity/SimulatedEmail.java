package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SIMULATED_EMAILS", indexes = {
    @Index(name = "idx_email_thread", columnList = "threadId"),
    @Index(name = "idx_email_direction", columnList = "direction"),
    @Index(name = "idx_email_complaint", columnList = "COMPLAINT_ID"),
    @Index(name = "idx_email_complaint_number", columnList = "complaintNumber"),
    @Index(name = "idx_email_sent_at", columnList = "sentAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SimulatedEmail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String messageId;

    @Column(nullable = false, length = 100)
    private String threadId;

    @Column(nullable = false, length = 200)
    private String fromEmail;

    @Column(nullable = false, length = 200)
    private String toEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(name = "COMPLAINT_ID")
    private Long complaintId;

    @Column(length = 50)
    private String complaintNumber;

    @Column(length = 500)
    private String attachmentUrl;

    private LocalDateTime sentAt;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) this.status = "UNREAD";
        if (this.sentAt == null) this.sentAt = LocalDateTime.now();
        if (this.receivedAt == null) this.receivedAt = LocalDateTime.now();
    }
}
