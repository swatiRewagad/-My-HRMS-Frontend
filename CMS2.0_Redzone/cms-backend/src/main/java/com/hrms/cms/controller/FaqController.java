package com.hrms.cms.controller;

import com.hrms.cms.entity.Faq;
import com.hrms.cms.repository.FaqRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/faq")
@RequiredArgsConstructor
public class FaqController {

    private final FaqRepository faqRepository;

    /**
     * Public GET — returns all active FAQs ordered by sortOrder.
     * Optionally filter by category query param.
     */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllFaqs(
            @RequestParam(required = false) String category) {

        List<Faq> faqs;
        if (category != null && !category.isBlank()) {
            faqs = faqRepository.findByIsActiveTrueAndCategoryOrderBySortOrderAsc(category);
        } else {
            faqs = faqRepository.findByIsActiveTrueOrderBySortOrderAsc();
        }

        List<Map<String, Object>> result = faqs.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Admin — create a new FAQ.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createFaq(@RequestBody Map<String, Object> body) {
        Faq faq = Faq.builder()
                .questionKey((String) body.get("questionKey"))
                .answerKey((String) body.get("answerKey"))
                .category((String) body.get("category"))
                .sortOrder(body.get("sortOrder") != null ? ((Number) body.get("sortOrder")).intValue() : 0)
                .isActive(true)
                .build();

        Faq saved = faqRepository.save(faq);
        return ResponseEntity.ok(toDto(saved));
    }

    /**
     * Admin — update an existing FAQ.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateFaq(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return faqRepository.findById(id)
                .map(faq -> {
                    if (body.containsKey("questionKey")) faq.setQuestionKey((String) body.get("questionKey"));
                    if (body.containsKey("answerKey")) faq.setAnswerKey((String) body.get("answerKey"));
                    if (body.containsKey("category")) faq.setCategory((String) body.get("category"));
                    if (body.containsKey("sortOrder")) faq.setSortOrder(((Number) body.get("sortOrder")).intValue());
                    Faq saved = faqRepository.save(faq);
                    return ResponseEntity.ok(toDto(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Admin — soft-delete an FAQ (set isActive = false).
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteFaq(@PathVariable Long id) {
        return faqRepository.findById(id)
                .map(faq -> {
                    faq.setIsActive(false);
                    faqRepository.save(faq);
                    return ResponseEntity.ok(Map.of("message", "FAQ deactivated", "id", id));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toDto(Faq faq) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", faq.getId());
        dto.put("questionKey", faq.getQuestionKey());
        dto.put("answerKey", faq.getAnswerKey());
        dto.put("category", faq.getCategory());
        dto.put("sortOrder", faq.getSortOrder());
        return dto;
    }
}
