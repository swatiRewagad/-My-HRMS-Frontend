package com.hrms.cms.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_TIMELINE", indexes = {
    @Index(name = "idx_timeline_complaint", columnList = "COMPLAINT_ID"),
    @Index(name = "idx_timeline_performed_at", columnList = "performedAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintTimeline {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 50)
    private String action;

    /**
     * Hidden from JSON because it is a login credential. {@code GET /api/complaints/{id}/timeline}
     * serialises this entity directly, so the only way to keep usernames off the wire there is to hide
     * the field and publish {@link #displayActor()} under the same key.
     */
    @JsonIgnore
    @Column(length = 200)
    private String performedBy;

    /**
     * The actor's name as it read when the action happened, captured alongside the username instead of
     * joined in at read time. Two reasons, both load-bearing: a timeline is an audit record, so it must
     * keep showing the name the officer had at the time even after a later marital, legal or structural
     * rename; and the read path renders whole histories at once, where a per-row lookup would be a join
     * per entry. Null for rows written before this column existed, and for actors with no officer-pool
     * entry ({@code System}, {@code system}, {@code REVIEWER}) — readers fall back to
     * {@link #getPerformedBy()}.
     */
    @Column(length = 250)
    private String performedByName;

    @Column(columnDefinition = "TEXT")
    private String remarks;

    @Column(length = 30)
    private String fromStatus;

    @Column(length = 30)
    private String toStatus;

    private LocalDateTime performedAt;

    @PrePersist
    protected void onCreate() {
        this.performedAt = LocalDateTime.now();
    }

    /**
     * What an audit response should print for the actor: the captured name, or the username when this
     * row predates {@link #performedByName} or the actor was never a real officer. Read paths use this
     * instead of {@link #getPerformedBy()} so a login credential does not reach the browser.
     */
    @JsonProperty("performedBy")
    public String displayActor() {
        return performedByName != null && !performedByName.isBlank() ? performedByName : performedBy;
    }
}
