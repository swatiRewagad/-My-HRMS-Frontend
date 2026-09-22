package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single append-only message within a {@link ComplaintQuery} thread (UST857).
 *
 * There is deliberately no update or delete path for these rows anywhere in the application:
 * a correction is made by posting a follow-up message. Entity-side internal notes, which DO
 * allow a short author edit window, live in the separate {@link ComplaintInternalNote} table
 * so that this table has no mutable rows at all.
 */
@Entity
@Table(name = "COMPLAINT_QUERY_MESSAGE", indexes = {
    @Index(name = "idx_cqm_query", columnList = "queryId,postedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintQueryMessage {

    /** Written by a person. */
    public static final String KIND_MESSAGE = "MESSAGE";
    /** Written by the server to record a decision (approve/reject/accept/decline). */
    public static final String KIND_SYSTEM = "SYSTEM";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long queryId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 100)
    private String authorUserId;

    @Column(nullable = false, length = 200)
    private String authorName;

    @Column(length = 50)
    private String authorRole;

    /** RE or RBI. */
    @Column(nullable = false, length = 10)
    private String authorSide;

    @Column(nullable = false, length = 20)
    private String messageKind;

    @Column(nullable = false)
    private LocalDateTime postedAt;

    @PrePersist
    protected void onCreate() {
        if (this.postedAt == null) {
            this.postedAt = LocalDateTime.now();
        }
        if (this.messageKind == null) {
            this.messageKind = KIND_MESSAGE;
        }
    }
}
