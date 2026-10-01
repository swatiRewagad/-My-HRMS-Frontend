package com.rbi.cms.portal.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Eligibility question for the portal questionnaire form")
public class QuestionDto {

    @Schema(description = "Unique identifier for this question", example = "Q_COURT_MATTER")
    private String questionCode;

    @Schema(description = "Question text to display")
    private String questionText;

    @Schema(description = "Type: YES_NO, SINGLE_CHOICE, TEXT")
    private String questionType;

    @Schema(description = "Category grouping")
    private String category;

    @Schema(description = "Whether this question must be answered")
    private boolean mandatory;

    @Schema(description = "Render order on the form")
    private int displayOrder;

    @Schema(description = "Options for choice-based questions")
    private List<String> options;
}
