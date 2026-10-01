package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Which channels one notification event goes out on (UST658-661, UST668).
 *
 * <p>The stories specify per-event channel matrices — some events are Email only, some Email + SMS,
 * and UST668 is bell-icon only with NO email. Those matrices are DATA here rather than {@code if}
 * branches in the notification call sites, because the difference between "we chose not to email"
 * and "we forgot to email" is invisible in code but explicit in a row.
 *
 * <p>That distinction is what makes UST668 testable. A story that says "no email" cannot be verified
 * by the absence of a line of code; it is verified by asserting {@code emailEnabled = false} on the
 * row and asserting no EMAIL delivery-log entry was written. Omission proves nothing — a row does.
 *
 * <p>One row per event type, keyed by the same {@code type} string
 * {@link com.hrms.cms.service.NotificationService} already persists on IN_APP_NOTIFICATIONS, so no
 * new vocabulary is introduced.
 */
@Entity
@Table(name = "NOTIFICATION_EVENT_CHANNEL")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationEventChannel {

    /** The notification type, e.g. PENDING_5DAY. Matches InAppNotification.type. */
    @Id
    @Column(name = "EVENT_TYPE", nullable = false, length = 50)
    private String eventType;

    /**
     * The in-app bell. Defaults to enabled because every event in the batch shows in the bell; it is
     * still a column so an operator can silence a noisy event without a redeploy.
     */
    @Column(name = "IN_APP_ENABLED", nullable = false, length = 1)
    @Builder.Default
    private String inAppEnabled = "Y";

    @Column(name = "EMAIL_ENABLED", nullable = false, length = 1)
    @Builder.Default
    private String emailEnabled = "N";

    @Column(name = "SMS_ENABLED", nullable = false, length = 1)
    @Builder.Default
    private String smsEnabled = "N";

    /**
     * Human-readable note on why this matrix is what it is, carrying the story reference.
     *
     * <p>Worth a column: "why does this event not email?" is otherwise answerable only by finding the
     * story, and the row is what an operator sees when they are deciding whether to change it.
     */
    @Column(name = "DESCRIPTION", length = 500)
    private String description;

    @Column(name = "IS_ACTIVE", nullable = false, length = 1)
    @Builder.Default
    private String isActive = "Y";

    public boolean isInApp() {
        return "Y".equalsIgnoreCase(inAppEnabled);
    }

    public boolean isEmail() {
        return "Y".equalsIgnoreCase(emailEnabled);
    }

    public boolean isSms() {
        return "Y".equalsIgnoreCase(smsEnabled);
    }

    public boolean isActiveRow() {
        return "Y".equalsIgnoreCase(isActive);
    }
}
