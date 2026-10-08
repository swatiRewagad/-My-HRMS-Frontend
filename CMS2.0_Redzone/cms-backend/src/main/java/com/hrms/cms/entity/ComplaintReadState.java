package com.hrms.cms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Whether a given user has opened a given complaint.
 *
 * <p>Read state is <b>per user</b>, which is the whole reason this is a table. The CEPC dashboard previously
 * kept it in {@code localStorage}, so it was per browser instead: clearing site data or signing in from a
 * second machine marked every complaint unread again, and a complaint one officer had read showed as read to
 * their colleague on a shared workstation. Neither is recoverable from a client-side store.
 *
 * <p>Absence of a row means unread. That keeps the write path to a single insert on first open, rather than
 * needing a row per (user, complaint) pair to exist up front — and it is what makes the {@code unread}
 * filter expressible as a {@code NOT EXISTS} correlated subquery.
 *
 * <p>Modelled on {@link ComplaintQueryRead}, which set this precedent for query threads.
 */
@Entity
@Table(name = "COMPLAINT_READ_STATE",
        uniqueConstraints = @UniqueConstraint(name = "uk_complaint_read_state",
                columnNames = {"COMPLAINT_ID", "USER_ID"}),
        indexes = @Index(name = "idx_complaint_read_user", columnList = "USER_ID"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintReadState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false)
    private Long complaintId;

    @Column(name = "USER_ID", nullable = false, length = 200)
    private String userId;

    @Column(name = "FIRST_READ_AT")
    private LocalDateTime firstReadAt;

    @Column(name = "LAST_READ_AT")
    private LocalDateTime lastReadAt;
}
