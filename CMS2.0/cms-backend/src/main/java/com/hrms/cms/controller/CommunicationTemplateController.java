package com.hrms.cms.controller;

import com.hrms.cms.entity.CommunicationTemplate;
import com.hrms.cms.service.CommunicationTemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/communication-templates")
@RequiredArgsConstructor
public class CommunicationTemplateController {

    private final CommunicationTemplateService templateService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'CRPC_HEAD')")
    public ResponseEntity<List<CommunicationTemplate>> getAll() {
        return ResponseEntity.ok(templateService.getAll());
    }

    @GetMapping("/active")
    public ResponseEntity<List<CommunicationTemplate>> getActive() {
        return ResponseEntity.ok(templateService.getActive());
    }

    @GetMapping("/{id}")
    public ResponseEntity<CommunicationTemplate> getById(@PathVariable Long id) {
        return ResponseEntity.ok(templateService.getById(id));
    }

    @GetMapping("/by-trigger")
    public ResponseEntity<List<CommunicationTemplate>> getByTrigger(
            @RequestParam String triggerCondition,
            @RequestParam(required = false) String mode) {
        if (mode != null) {
            return ResponseEntity.ok(templateService.getByTriggerAndMode(triggerCondition, mode));
        }
        return ResponseEntity.ok(templateService.getByTrigger(triggerCondition));
    }

    @GetMapping("/for-scheme")
    public ResponseEntity<List<CommunicationTemplate>> getForScheme(
            @RequestParam String triggerCondition,
            @RequestParam String schemeVersion) {
        return ResponseEntity.ok(templateService.getForScheme(triggerCondition, schemeVersion));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommunicationTemplate> create(@RequestBody CommunicationTemplate template) {
        return ResponseEntity.ok(templateService.create(template));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<CommunicationTemplate> update(@PathVariable Long id, @RequestBody CommunicationTemplate template) {
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

    @PostMapping("/render")
    public ResponseEntity<Map<String, String>> render(@RequestParam Long templateId, @RequestBody Map<String, String> variables) {
        CommunicationTemplate template = templateService.getById(templateId);
        String subject = templateService.renderSubject(template, variables);
        String body = templateService.renderBody(template, variables);
        return ResponseEntity.ok(Map.of("subject", subject, "body", body));
    }
}
