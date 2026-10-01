package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One append-only row per delivery attempt of a notification (UST845).
 *
 * <p>A separate table rather than status columns on {@code IN_APP_NOTIFICATION}: a notification may
 * be attempted more than once and over more than one channel, so delivery state is one-to-many and
 * would not fit on the notification row without losing the earlier attempts. Keeping attempts
 * distinct is the point — "failed once then succeeded" and "succeeded first time" are different
 * facts when diagnosing why an officer says they were never told.
 *
 * <p>Rows are never updated. A retry appends a new attempt; the failed one stays visible.
 */
@Entity
@Table(name = "NOTIFICATION_DELIVERY_LOG", indexes = {
    @Index(name = "idx_ndl_notification", columnList = "notificationId"),
    @Index(name = "idx_ndl_recipient", columnList = "recipientUserId"),
    @Index(name = "idx_ndl_status", columnList = "status"),
    @Index(name = "idx_ndl_attempted_at", columnList = "attemptedAt"),
    @Index(name = "idx_ndl_type", columnList = "notificationType")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationDeliveryLog {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    public static final String CHANNEL_IN_APP = "IN_APP";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Reference to the IN_APP_NOTIFICATIONS row, nullable by design: a failure that happened before
     * the notification could be persisted still has to be recorded, and that is precisely the case
     * worth logging.
     */
    private Long notificationId;

    @Column(nullable = false, length = 200, updatable = false)
    private String recipientUserId;

    /** Translation key of the notification, so the log is readable without re-deriving the text. */
    @Column(length = 200, updatable = false)
    private String notificationType;

    /** IN_APP for now. A column rather than an assumption, so adding email needs no schema change. */
    @Column(nullable = false, length = 20, updatable = false)
    private String channel;

    /** SENT | FAILED */
    @Column(nullable = false, length = 20, updatable = false)
    private String status;

    /** Truncated at the service boundary; a stack trace does not belong in an audit row. */
    @Column(length = 1000, updatable = false)
    private String errorMessage;

    @Column(length = 50, updatable = false)
    private String relatedComplaintNumber;

    @Column(updatable = false)
    private LocalDateTime attemptedAt;

    @PrePersist
    protected void onCreate() {
        if (attemptedAt == null) attemptedAt = LocalDateTime.now();
    }
}
