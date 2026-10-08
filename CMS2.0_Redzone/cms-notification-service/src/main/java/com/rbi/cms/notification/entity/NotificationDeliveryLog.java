package com.rbi.cms.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * The slice of cms-backend's NOTIFICATION_DELIVERY_LOG this service is allowed to touch.
 *
 * <p>Deliberately NOT the full entity. cms-backend owns this table and writes every column; this
 * service only settles a queued attempt, so it maps the four fields it reads or updates and nothing
 * else. Mirroring all of cms-backend's columns here would create a second definition of the same table
 * that could drift — and would invite this service to write fields it has no business owning.
 *
 * <p>There is no {@code @GeneratedValue} on the id for the same reason: rows are never inserted here.
 */
@Entity
@Table(name = "NOTIFICATION_DELIVERY_LOG")
@Getter
@Setter
@NoArgsConstructor
public class NotificationDeliveryLog {

    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    private Long id;

    @Column(name = "dispatch_ref", insertable = false, updatable = false)
    private String dispatchRef;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "settled_at")
    private LocalDateTime settledAt;
}
