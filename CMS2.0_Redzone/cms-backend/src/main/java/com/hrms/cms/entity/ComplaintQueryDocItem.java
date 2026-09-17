package com.hrms.cms.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One requested document within a DOCUMENT_REQUEST query thread (UST854).
 *
 * attachmentId links the upload that satisfied the item, so an item can only be marked
 * resolved with evidence attached.
 */
@Entity
@Table(name = "COMPLAINT_QUERY_DOC_ITEM", indexes = {
    @Index(name = "idx_cq_doc_query", columnList = "queryId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComplaintQueryDocItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long queryId;

    @Column(nullable = false, length = 300)
    private String itemLabel;

    @Column(length = 1000)
    private String itemDescription;

    @Column(nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean resolved = false;

    private LocalDateTime resolvedAt;

    @Column(length = 100)
    private String resolvedBy;

    private Long attachmentId;
}
