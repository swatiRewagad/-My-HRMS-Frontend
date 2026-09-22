package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "COMPLAINT_ATTACHMENTS", indexes = {
    @Index(name = "idx_attachment_complaint", columnList = "COMPLAINT_ID")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "COMPLAINT_ID", nullable = false)
    private Long complaintId;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(nullable = false, length = 500)
    private String originalName;

    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @Column(length = 1000)
    private String storagePath;

    private LocalDateTime uploadedAt;

    /**
     * ═══ Source distinguishability (UST589) ═══
     *
     * <p>UST589 requires the attachments list to distinguish where a document came from — an officer's
     * own upload, a complainant's submission through the secure link, an RE response. That was
     * impossible rather than merely unwired: this entity had eight columns and not one recorded an
     * uploader or an origin, while the CEPC screen FABRICATED a {@code uploadedBy} value client-side
     * from the logged-in username — so the column it displayed did not exist in the database and was
     * wrong for every document somebody else had uploaded.
     *
     * <p>Shape copied from {@code AppealAttachment}, which already records exactly this triple.
     *
     * <p>All three are NULLABLE and must stay so: schema reaches the shared dev database through
     * Hibernate ddl-auto, and the existing rows have no recoverable answer for any of them.
     */
    @Column(name = "uploaded_by", length = 200)
    private String uploadedBy;

    /**
     * What the uploader was acting as: OFFICER | COMPLAINANT | REGULATED_ENTITY | SYSTEM.
     *
     * <p>Separate from {@code uploadedBy} because the display rule is about PROVENANCE, not identity —
     * staff need to see at a glance which documents came from outside RBI, and deriving that by
     * pattern-matching a username would break the moment a username format changed.
     */
    @Column(name = "source", length = 30)
    private String source;

    /** Optional classification, e.g. MEETING_MINUTES or SIGNED_LETTER. Free-form by design. */
    @Column(name = "document_type", length = 40)
    private String documentType;

    /** Source values, so callers do not spell them differently in each place. */
    public static final String SOURCE_OFFICER = "OFFICER";
    public static final String SOURCE_COMPLAINANT = "COMPLAINANT";
    public static final String SOURCE_REGULATED_ENTITY = "REGULATED_ENTITY";
    public static final String SOURCE_SYSTEM = "SYSTEM";

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
