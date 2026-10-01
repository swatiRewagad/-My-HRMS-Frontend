package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Per-user read position within a query thread (UST856).
 *
 * A thread is unread for a user when no row exists, or when lastReadMessageId is behind the
 * thread's newest message id. The list-view badge and the bell count are both derived from
 * this, so the two cannot disagree.
 */
@Entity
@Table(name = "COMPLAINT_QUERY_READ",
    uniqueConstraints = @UniqueConstraint(name = "uk_cq_read", columnNames = {"queryId", "userId"}),
    indexes = @Index(name = "idx_cq_read_user", columnList = "userId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintQueryRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long queryId;

    @Column(nullable = false, length = 100)
    private String userId;

    private Long lastReadMessageId;

    private LocalDateTime lastReadAt;
}
