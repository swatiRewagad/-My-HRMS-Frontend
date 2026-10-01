package com.rbi.cms.eligibility.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.eligibility.dto.EligibilityCheckRequest;
import com.rbi.cms.eligibility.dto.EligibilityCheckResponse;
import com.rbi.cms.eligibility.dto.QuestionResponse;
import com.rbi.cms.eligibility.service.EligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/eligibility")
@RequiredArgsConstructor
@Tag(name = "Eligibility", description = "Pre-complaint eligibility questionnaire and evaluation")
public class EligibilityController {

    private final EligibilityService eligibilityService;

    @GetMapping("/questions")
    @Operation(summary = "Get active eligibility questions", description = "Returns configurable questionnaire for pre-complaint check")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getQuestions(
            @RequestParam(required = false) String category) {

        List<QuestionResponse> questions;
        if (category != null && !category.isBlank()) {
            questions = eligibilityService.getQuestionsByCategory(category);
        } else {
            questions = eligibilityService.getActiveQuestions();
        }
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @PostMapping("/check")
    @Operation(summary = "Evaluate eligibility", description = "Evaluates answers against Drools rules and returns eligibility outcome")
    public ResponseEntity<ApiResponse<EligibilityCheckResponse>> checkEligibility(
            @Valid @RequestBody EligibilityCheckRequest request,
            HttpServletRequest httpRequest) {

        if (request.getIpAddress() == null) {
            request.setIpAddress(httpRequest.getRemoteAddr());
        }
        if (request.getSessionId() == null) {
            request.setSessionId(httpRequest.getSession().getId());
        }

        EligibilityCheckResponse response = eligibilityService.evaluateEligibility(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
