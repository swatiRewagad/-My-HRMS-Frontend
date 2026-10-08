package com.hrms.cms.dto.cepc;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body for both {@code POST /api/v1/complaints/{complaintNumber}/comments} and
 * {@code POST /api/v1/complaints/nodal-records/{recordNumber}/comments} — the client sends the same
 * shape to both. {@code complaintNumber} is only read by the nodal endpoint, which has no complaint
 * number in its own path; the complaint-level endpoint ignores it in favour of its path variable.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcAssessmentCommentRequest {

    @NotBlank(message = "Comment text is required.")
    @Size(max = 4000, message = "Comment must not exceed 4000 characters.")
    private String text;

    @NotBlank(message = "Comment author is required.")
    @Size(max = 200, message = "Author must not exceed 200 characters.")
    private String author;

    @Size(max = 10, message = "Initials must not exceed 10 characters.")
    private String initials;

    @Size(max = 50, message = "Role must not exceed 50 characters.")
    private String role;

    /** "NO" or "PNO" — required by the nodal endpoint only. */
    @Size(max = 10)
    private String target;

    @Size(max = 20)
    private String color;

    /** Only read by the nodal endpoint, which has no complaint number in its own path. */
    @Size(max = 50)
    private String complaintNumber;
}
