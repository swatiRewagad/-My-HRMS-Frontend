package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "IN_APP_NOTIFICATIONS", indexes = {
    @Index(name = "idx_notif_user", columnList = "targetUserId"),
    @Index(name = "idx_notif_read", columnList = "targetUserId,isRead"),
    @Index(name = "idx_notif_created", columnList = "createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InAppNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String targetUserId;

    @Column(nullable = false, length = 50)
    private String type; // ASSIGNMENT, TRANSFER_PENDING, PENDING_3DAY, DUPLICATE_ACTIVITY, SENT_BACK, BULK_CLOSE

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(length = 100)
    private String relatedEntityId; // complaintNumber or draftId

    @Column(length = 50)
    private String relatedEntityType; // COMPLAINT, DRAFT, TRANSFER

    @Column(length = 200)
    private String actionUrl;

    private boolean isRead;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.isRead = false;
    }
}
