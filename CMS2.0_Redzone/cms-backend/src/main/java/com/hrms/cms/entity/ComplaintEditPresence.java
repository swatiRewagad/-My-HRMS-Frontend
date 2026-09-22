package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Who currently has a complaint open for editing — ADVISORY ONLY (UST675).
 *
 * <h2>This is not a lock, and that is a decision rather than an omission</h2>
 * UST675 reads like a lease ("prevents the second user from saving until the first completes or releases
 * the record"). A lease was rejected as the ENFORCEMENT mechanism for two reasons:
 *
 * <ul>
 *   <li><b>A lease must expire, and any safe expiry is unsafe.</b> Without an expiry a crashed browser
 *       locks a complaint forever. With one short enough to be useful, it fires mid-edit and the second
 *       user overwrites work in progress — converting a concurrency problem into silent data loss, which
 *       is strictly worse than the problem.</li>
 *   <li><b>Enforcement already exists and is correct.</b> {@code Complaint.recordVersion} (@Version,
 *       column {@code record_version}) plus the 409 in {@code GlobalExceptionHandler} is the
 *       authoritative "you cannot save over someone else's write". A lease on top would be a SECOND
 *       answer to the same question, and two authorisation decisions in different places is how they
 *       come to disagree.</li>
 * </ul>
 *
 * <p>So this table answers only "should I warn you before you start?" — which the version check cannot
 * do, because optimistic locking is detect-on-write and by then the user has already typed. Saving is
 * gated by the version check alone. Because the row is advisory, a stale one is harmless, which is
 * precisely why it can use a short heartbeat without risking anyone's work.
 */
@Entity
@Table(name = "COMPLAINT_EDIT_PRESENCE",
        uniqueConstraints = @UniqueConstraint(
                name = "UK_EDIT_PRESENCE",
                columnNames = {"COMPLAINT_NUMBER", "USER_ID"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComplaintEditPresence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_NUMBER", nullable = false, length = 50)
    private String complaintNumber;

    /** Resolved from the JWT server-side. */
    @Column(name = "USER_ID", nullable = false, length = 200)
    private String userId;

    /** Shown in the warning, so the second user knows who to coordinate with rather than just that
     *  somebody is there. */
    @Column(name = "DISPLAY_NAME", length = 250)
    private String displayName;

    /**
     * Refreshed while the form is open. A row older than the configured staleness window is IGNORED
     * rather than deleted — deleting on read would mean a transient network blip silently discards the
     * presence of a colleague who is still typing.
     */
    @Column(name = "HEARTBEAT_AT", nullable = false)
    private LocalDateTime heartbeatAt;
}
