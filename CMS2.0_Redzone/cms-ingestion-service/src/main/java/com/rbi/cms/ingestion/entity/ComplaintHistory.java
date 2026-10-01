package com.rbi.cms.ingestion.entity;

import com.rbi.cms.common.enums.ComplaintStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "COMPLAINT_HISTORY")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "complaint_hist_seq")
    @SequenceGenerator(name = "complaint_hist_seq", sequenceName = "COMPLAINT_HISTORY_SEQ", allocationSize = 1)
    @Column(name = "HISTORY_ID")
    private Long historyId;

    @Column(name = "COMPLAINT_ID", nullable = false, length = 30)
    private String complaintId;

    @Enumerated(EnumType.STRING)
    @Column(name = "PREVIOUS_STATUS", length = 20)
    private ComplaintStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "NEW_STATUS", nullable = false, length = 20)
    private ComplaintStatus newStatus;

    @Column(name = "ACTION", nullable = false, length = 100)
    private String action;

    @Column(name = "REMARKS", length = 2000)
    private String remarks;

    @Column(name = "PERFORMED_BY", nullable = false, length = 100)
    private String performedBy;

    @Column(name = "PERFORMED_AT", nullable = false)
    private Instant performedAt;

    @PrePersist
    protected void onCreate() {
        performedAt = Instant.now();
    }
}
