package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * An entity-side internal note on a complaint (UST860).
 *
 * Visible only to RE-side roles within the owning entity — never to CEPC/RBIOS users and never
 * to the complainant. Kept in its own table rather than as a {@link ComplaintQueryMessage} kind
 * because UST857 requires thread messages to be permanently immutable while these allow the
 * author a short edit window.
 *
 * editLockedAt is stamped by the server at insert time from the configured window; the server
 * checks that column on edit, so the lock cannot be bypassed by a client-supplied timestamp.
 */
@Entity
@Table(name = "COMPLAINT_INTERNAL_NOTE", indexes = {
    @Index(name = "idx_cin_complaint", columnList = "complaintId"),
    @Index(name = "idx_cin_entity", columnList = "entityCode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintInternalNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 50)
    private String entityCode;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 100)
    private String authorUserId;

    @Column(nullable = false, length = 200)
    private String authorName;

    @Column(length = 50)
    private String authorRole;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private LocalDateTime editLockedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer editCount = 0;

    public boolean isEditable(LocalDateTime now) {
        return now.isBefore(editLockedAt);
    }
}
