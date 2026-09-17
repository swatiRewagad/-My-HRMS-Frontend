package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "EMAIL_DRAFT_ATTACHMENTS", indexes = {
    @Index(name = "idx_draft_att_draft", columnList = "draftId"),
    @Index(name = "idx_draft_att_parent", columnList = "linkedComplaintNumber")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailDraftAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String draftId;

    @Column(nullable = false, length = 500)
    private String fileName;

    @Column(length = 100)
    private String fileType;

    private long fileSize;

    @Column(length = 1000)
    private String storagePath;

    @Column(columnDefinition = "TEXT")
    private String ocrText;

    private int ocrConfidence;

    @Column(length = 200)
    private String uploadedBy;

    /**
     * Set when a duplicate email's attachments are linked to the existing parent complaint instead
     * of to a new draft. draftId stays populated for provenance.
     */
    @Column(length = 50)
    private String linkedComplaintNumber;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) this.createdAt = LocalDateTime.now();
    }
}
