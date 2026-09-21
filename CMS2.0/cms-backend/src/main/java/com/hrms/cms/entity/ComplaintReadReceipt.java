package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per officer who has opened a complaint.
 *
 * <p>Replaces the single {@code COMPLAINTS.IS_READ} flag, which marked a complaint read for everyone as
 * soon as any one officer opened it — so a supervisor's glance hid it from the officer it was assigned
 * to. Read state belongs to the reader, not to the complaint.
 *
 * <p>No unique constraint on {@code (COMPLAINT_ID, USERNAME)}: the same officer opening the complaint
 * twice concurrently would then fail the insert and take the whole detail page down with it, while a
 * duplicate row is harmless here — read state is "a receipt exists", not a count.
 */
@Entity
@Table(name = "COMPLAINT_READ_RECEIPTS", indexes = {
    @Index(name = "idx_read_receipt_lookup", columnList = "complaintId,username")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintReadReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long complaintId;

    /** Keycloak {@code preferred_username}, the same identity {@code assignedOfficer} holds. */
    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false)
    private LocalDateTime readAt;
}
