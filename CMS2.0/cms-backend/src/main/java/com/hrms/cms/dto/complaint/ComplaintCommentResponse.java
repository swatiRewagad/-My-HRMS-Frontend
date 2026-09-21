package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A comment on a complaint. Distinct from {@link NodalRecordCommentResponse}, which carries a
 * {@code target} where this carries the author's {@code role} — the two screens key off different
 * fields of the same entity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComplaintCommentResponse {

    private Long id;
    private String author;
    private String initials;
    private String text;
    private String role;
    private String color;
    private String createdAt;
}
