package com.rbi.cms.rules.controller;

import com.rbi.cms.common.dto.ApiResponse;
import com.rbi.cms.rules.entity.RuleCategory;
import com.rbi.cms.rules.repository.RuleCategoryRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/rules/categories")
@RequiredArgsConstructor
@Tag(name = "Rule Categories", description = "Rule category management")
public class RuleCategoryController {

    private final RuleCategoryRepository categoryRepository;

    @GetMapping
    @Operation(summary = "List all rule categories")
    public ResponseEntity<ApiResponse<List<RuleCategory>>> getCategories() {
        return ResponseEntity.ok(ApiResponse.success(categoryRepository.findAll(), "Categories fetched"));
    }

    @PostMapping
    @Operation(summary = "Create a new category")
    public ResponseEntity<ApiResponse<RuleCategory>> createCategory(@RequestBody RuleCategory category) {
        return ResponseEntity.ok(ApiResponse.success(categoryRepository.save(category), "Category created"));
    }
}
