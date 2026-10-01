package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A document attached to an appeal or representation.
 *
 * Appeal attachments previously had no home: AppealWorkflowService.storeAttachments wrote
 * ComplaintAttachment rows, passing the appeal number where a complaint number was expected and the
 * appeal id where a complaint id was expected. That silently mixed appeal documents into the parent
 * complaint's attachment set, so an appeal's evidence appeared on the complaint and vice versa.
 *
 * sourceDraftId records the EMAIL_DRAFTS row an attachment arrived on, so a document captured at
 * intake can be re-parented to the appeal at registration without losing its provenance.
 */
@Entity
@Table(name = "APPEAL_ATTACHMENTS", indexes = {
    @Index(name = "idx_appeal_att_number", columnList = "appealNumber"),
    @Index(name = "idx_appeal_att_draft", columnList = "sourceDraftId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AppealAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String appealNumber;

    @Column(nullable = false, length = 400)
    private String fileName;

    @Column(length = 100)
    private String contentType;

    private Long fileSize;

    @Column(nullable = false, length = 1000)
    private String storagePath;

    /**
     * What this document is: APPELLANT_EVIDENCE, ED_APPROVAL, SCANNED_LETTER, EMAIL_ATTACHMENT.
     * The ED-approval document has to be identifiable because an entity-raised appeal is only
     * compliant when that sign-off is on record.
     */
    @Column(length = 40)
    private String documentType;

    /** EMAIL_DRAFTS row this attachment came from, when it originated at intake. */
    @Column(length = 60)
    private String sourceDraftId;

    @Column(length = 200)
    private String uploadedBy;

    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        this.uploadedAt = LocalDateTime.now();
    }
}
