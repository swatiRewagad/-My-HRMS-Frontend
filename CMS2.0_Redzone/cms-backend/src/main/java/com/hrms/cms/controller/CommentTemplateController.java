package com.hrms.cms.controller;

import com.hrms.cms.entity.CommentTemplate;
import com.hrms.cms.service.CommentTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/comment-templates")
@RequiredArgsConstructor
public class CommentTemplateController {

    private final CommentTemplateService templateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_HEAD')")
    public ResponseEntity<List<CommentTemplate>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<CommentTemplate>> getActive() {
        return ResponseEntity.ok(templateService.getActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommentTemplate> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @GetMapping("/by-category")
    public ResponseEntity<List<CommentTemplate>> getByCategory(@RequestParam String category) {
        return ResponseEntity.ok(templateService.getByCategory(category));
    }

    @GetMapping("/by-mode")
    public ResponseEntity<List<CommentTemplate>> getByMode(@RequestParam String modeOfReceipt) {
        return ResponseEntity.ok(templateService.getByModeOfReceipt(modeOfReceipt));
    }

    @GetMapping("/filtered")
    public ResponseEntity<List<CommentTemplate>> getFiltered(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String modeOfReceipt) {
        if (category != null && modeOfReceipt != null) {
            return ResponseEntity.ok(templateService.getByCategoryAndMode(category, modeOfReceipt));
        } else if (category != null) {
            return ResponseEntity.ok(templateService.getByCategory(category));
        } else if (modeOfReceipt != null) {
            return ResponseEntity.ok(templateService.getByModeOfReceipt(modeOfReceipt));
        }
        return ResponseEntity.ok(templateService.getActive());
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommentTemplate> create(@RequestBody CommentTemplate template) {
        return ResponseEntity.ok(templateService.create(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommentTemplate> update(@PathVariable Long id, @RequestBody CommentTemplate template) {
        return ResponseEntity.ok(templateService.update(id, template));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        templateService.deactivate(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        templateService.activate(id);
        return ResponseEntity.ok().build();
    }
}
