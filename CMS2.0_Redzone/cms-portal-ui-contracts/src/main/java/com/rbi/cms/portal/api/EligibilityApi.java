package com.rbi.cms.portal.api;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.portal.dto.request.EligibilityCheckRequestDto;
import com.rbi.cms.portal.dto.response.EligibilityCheckResponseDto;
import com.rbi.cms.portal.dto.response.QuestionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Eligibility", description = "Pre-complaint eligibility check APIs consumed by the portal")
@RequestMapping("/api/v1/eligibility")
public interface EligibilityApi {

    @GetMapping("/questions")
    @Operation(summary = "Load eligibility questionnaire", description = "Returns active configurable questions from Oracle")
    ResponseEntity<ApiResponse<List<QuestionDto>>> getQuestions(
            @RequestParam(required = false) String category);

    @PostMapping("/check")
    @Operation(summary = "Evaluate eligibility", description = "Drools evaluates answers and returns ELIGIBLE or NOT_ELIGIBLE with standard response")
    ResponseEntity<ApiResponse<EligibilityCheckResponseDto>> checkEligibility(
            @Valid @RequestBody EligibilityCheckRequestDto request);
}
