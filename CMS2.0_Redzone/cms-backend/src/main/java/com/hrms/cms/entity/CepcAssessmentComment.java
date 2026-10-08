package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A free-text note on a CEPC complaint or one of its nodal officer records — the "Comments" box on the
 * complaint detail view's Assessment/Forward/Final Decision tabs, and the "To NO" / "To PNO" boxes on the
 * nodal record detail panel.
 *
 * <p>A note, not a transition: like {@link CepcContactPerson}, this table is read and written on its own
 * and never touches the complaint, its status or its workflow stage. The complaint write path stays off
 * limits for CEPC work.
 *
 * <p>One table serves both screens rather than two, because the nodal payload already carries the
 * complaint number alongside the record number — {@code nodalRecordNumber} is simply left {@code null}
 * for a complaint-level comment, and set for a nodal-level one. {@code target} ("NO"/"PNO") is meaningful
 * only on the nodal side; it stays null for complaint-level rows.
 */
@Entity
@Table(name = "CEPC_ASSESSMENT_COMMENTS",
    indexes = {
        @Index(name = "idx_cepc_ac_complaint", columnList = "complaintNumber"),
        @Index(name = "idx_cepc_ac_nodal_record", columnList = "nodalRecordNumber")
    })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcAssessmentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String complaintNumber;

    /** Null for a complaint-level comment; set to key a nodal record's own thread. */
    @Column(length = 60)
    private String nodalRecordNumber;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(nullable = false, length = 200)
    private String author;

    @Column(length = 10)
    private String initials;

    /** The commenter's role at the time, e.g. "CEPC_DO" — shown as a badge, never used for access control. */
    @Column(length = 50)
    private String role;

    /** "NO" or "PNO" on a nodal-record comment; null on a complaint-level one. */
    @Column(length = 10)
    private String target;

    @Column(length = 20)
    private String color;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
    }
}
