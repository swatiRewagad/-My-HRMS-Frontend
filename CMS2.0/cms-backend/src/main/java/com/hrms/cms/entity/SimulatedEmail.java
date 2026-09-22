package com.hrms.cms.entity;

import com.rbi.cms.common.enums.DeliveryStatus;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "SIMULATED_EMAILS", indexes = {
    @Index(name = "idx_email_thread", columnList = "threadId"),
    @Index(name = "idx_email_direction", columnList = "direction"),
    @Index(name = "idx_email_complaint", columnList = "COMPLAINT_ID"),
    @Index(name = "idx_email_complaint_number", columnList = "complaintNumber"),
    @Index(name = "idx_email_sent_at", columnList = "sentAt"),
    @Index(name = "idx_email_delivery_status", columnList = "deliveryStatus")
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

    @Column(length = 500)
    private String ccEmail;

    @Column(length = 500)
    private String bccEmail;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 10)
    private String direction;

    @Column(nullable = false, length = 20)
    private String status;

    /**
     * Outbound delivery lifecycle, and the single source of truth for it. Null means the row has no
     * delivery lifecycle — every INBOUND row, plus outbound rows written before this column existed, for
     * which callers fall back to {@link #status}. Deliberately not defaulted by the builder: a default
     * would silently label every inbound row a draft.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "DELIVERY_STATUS", length = 20)
    private DeliveryStatus deliveryStatus;

    /** Why the last dispatch attempt failed. Truncated to fit; only meaningful while FAILED. */
    @Column(name = "LAST_ERROR", length = 2000)
    private String lastError;

    @Column(name = "DISPATCH_ATTEMPTS")
    private Integer dispatchAttempts;

    @Column(name = "COMPLAINT_ID")
    private Long complaintId;

    @Column(length = 50)
    private String complaintNumber;

    @Column(length = 500)
    private String attachmentUrl;

    @Column(length = 100)
    private String createdBy;

    /**
     * sentAt doubles as the row creation time because every existing query orders by it; a draft that
     * is later sent keeps its original sentAt. updatedAt is what moves when a draft is edited.
     *
     * <p>Nothing may rewrite sentAt once set — moving it reorders the officer's activity list underneath
     * them. When a dispatch completes it is processedAt that records the moment, which is why the
     * outbound pipeline has no separate dispatchedAt column.</p>
     */
    private LocalDateTime sentAt;
    private LocalDateTime receivedAt;
    private LocalDateTime processedAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) this.status = "UNREAD";
        if (this.sentAt == null) this.sentAt = LocalDateTime.now();
        if (this.receivedAt == null) this.receivedAt = LocalDateTime.now();
        if (this.updatedAt == null) this.updatedAt = this.sentAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
