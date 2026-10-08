package com.hrms.cms.controller;

import com.hrms.cms.entity.AcknowledgementLetterTemplate;
import com.hrms.cms.repository.AcknowledgementLetterTemplateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin surface for the acknowledgement letter wording ({@code AcknowledgementLetterTemplate}),
 * mirroring {@link CommunicationTemplateController} so editing this letter's text later is a
 * data update through this endpoint, not a code change.
 */
@RestController
@RequestMapping("/api/v1/acknowledgement-templates")
@RequiredArgsConstructor
public class AcknowledgementLetterTemplateController {

    private final AcknowledgementLetterTemplateRepository repository;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_HEAD')")
    public ResponseEntity<List<AcknowledgementLetterTemplate>> getAll() {
        return ResponseEntity.ok(repository.findAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<AcknowledgementLetterTemplate>> getActive() {
        return ResponseEntity.ok(repository.findByActiveTrue());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_HEAD')")
    public ResponseEntity<AcknowledgementLetterTemplate> getById(@PathVariable Long id) {
        return repository.findById(id).map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<AcknowledgementLetterTemplate> update(@PathVariable Long id,
                                                                  @RequestBody AcknowledgementLetterTemplate updates) {
        AcknowledgementLetterTemplate existing = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        existing.setBodyTemplate(updates.getBodyTemplate());
        return ResponseEntity.ok(repository.save(existing));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        repository.findById(id).ifPresent(t -> {
            t.setActive(false);
            repository.save(t);
        });
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public ResponseEntity<Void> activate(@PathVariable Long id) {
        repository.findById(id).ifPresent(t -> {
            t.setActive(true);
            repository.save(t);
        });
        return ResponseEntity.ok().build();
    }
}
