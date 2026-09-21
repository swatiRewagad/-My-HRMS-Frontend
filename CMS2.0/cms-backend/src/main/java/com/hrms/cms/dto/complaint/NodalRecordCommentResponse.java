package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A comment on a nodal officer record. Carries {@code target} (who the comment is addressed to) where
 * {@link ComplaintCommentResponse} carries the author's {@code role}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodalRecordCommentResponse {

    private Long id;
    private String author;
    private String initials;
    private String text;
    private String target;
    private String color;
    private String createdAt;
}
