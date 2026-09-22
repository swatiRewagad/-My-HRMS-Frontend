package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * An append-only clarification added to a reassignment request after the fact (UST840).
 *
 * <p>This table is the reason {@link ReassignmentRequest#getReason()} can be immutable. A requester
 * who needs to add context, or a PNO who asks for more detail, appends a row here; the original
 * justification stays exactly as the approver read it. Same principle the query threads use: history
 * is corrected by addition, never by edit.
 *
 * <p>Every column is written once at insert. There is no update path and no service method that
 * modifies a persisted row — {@code note} is {@code updatable = false} so that remains true even if
 * a future caller obtains a managed instance and calls a setter.
 */
@Entity
@Table(name = "REASSIGNMENT_CLARIFICATIONS", indexes = {
    @Index(name = "idx_rc_request", columnList = "reassignmentRequestId"),
    @Index(name = "idx_rc_added_at", columnList = "addedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReassignmentClarification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reassignmentRequestId;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String note;

    /** Attribution is resolved server-side from RequestIdentity, never taken from the request body. */
    @Column(nullable = false, length = 200, updatable = false)
    private String addedBy;

    @Column(length = 200, updatable = false)
    private String addedByName;

    /** RE or RBI — which side added the clarification. */
    @Column(length = 10, updatable = false)
    private String addedBySide;

    @Column(updatable = false)
    private LocalDateTime addedAt;

    @PrePersist
    protected void onCreate() {
        if (addedAt == null) addedAt = LocalDateTime.now();
    }
}
