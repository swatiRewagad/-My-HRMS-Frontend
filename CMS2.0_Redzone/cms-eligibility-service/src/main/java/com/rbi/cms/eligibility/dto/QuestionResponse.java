package com.rbi.cms.eligibility.dto;

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
@Schema(description = "Eligibility question for pre-complaint check")
public class QuestionResponse {

    @Schema(description = "Unique question code", example = "Q_COURT_MATTER")
    private String questionCode;

    @Schema(description = "Question text shown to user")
    private String questionText;

    @Schema(description = "Question type: YES_NO, SINGLE_CHOICE, TEXT", example = "YES_NO")
    private String questionType;

    @Schema(description = "Question category", example = "GENERAL")
    private String category;

    @Schema(description = "Whether answer is required")
    private Boolean isMandatory;

    @Schema(description = "Display order")
    private Integer displayOrder;

    @Schema(description = "Available options for choice-type questions")
    private List<String> options;
}
