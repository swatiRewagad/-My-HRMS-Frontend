package com.rbi.cms.mailintake.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Append-only audit trail — the evidence record for a regulated intake path. Every status
 * transition, every admin action (replay, force-link, download), and every quarantine decision
 * writes exactly one row here. Rows are never updated or deleted; see the retention job (Stage 5)
 * for how long they're kept relative to the raw bytes they reference.
 */
@Entity
@Table(name = "INBOUND_EMAIL_EVENT")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InboundEmailEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "EMAIL_ID", nullable = false)
    private Long emailId;

    @Column(name = "EVENT_AT", nullable = false)
    private Instant eventAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "FROM_STATUS", length = 20)
    private InboundEmailStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "TO_STATUS", nullable = false, length = 20)
    private InboundEmailStatus toStatus;

    /** "system:parser-pipeline", "system:smtp-listener", or an admin username for operator
     *  actions — always populated, never left to infer from context. */
    @Column(name = "ACTOR", nullable = false, length = 100)
    private String actor;

    @Column(name = "DETAIL", length = 2000)
    private String detail;

    @PrePersist
    protected void onCreate() {
        if (eventAt == null) eventAt = Instant.now();
    }
}
