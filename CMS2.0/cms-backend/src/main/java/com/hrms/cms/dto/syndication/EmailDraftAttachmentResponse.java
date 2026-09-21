package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A file attached to an email draft, as the CRPC assessment screens render it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailDraftAttachmentResponse {

    /** Prefixed form of the row id ({@code ATT-12}), not the numeric id — the screens key list rows on it. */
    private String id;
    private String fileName;
    private String fileType;
    private Long fileSize;
    private String ocrText;
    private Integer ocrConfidence;
    private String createdAt;
    private String uploadedBy;
}
