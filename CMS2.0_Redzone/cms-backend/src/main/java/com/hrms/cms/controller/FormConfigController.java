package com.hrms.cms.controller;

import com.hrms.cms.entity.FormConfig;
import com.hrms.cms.service.FormConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/form-config")
@RequiredArgsConstructor
public class FormConfigController {

    private final FormConfigService formConfigService;
    private final ObjectMapper objectMapper;

    @GetMapping("/{formKey}")
    public ResponseEntity<JsonNode> getFormSchema(@PathVariable String formKey) {
        return ResponseEntity.ok(formConfigService.getFormSchema(formKey));
    }

    @PutMapping("/{formKey}")
    public ResponseEntity<JsonNode> updateFormSchema(
            @PathVariable String formKey,
            @RequestBody JsonNode body) {
        String schemaJson = body.toString();
        FormConfig updated = formConfigService.updateSchema(formKey, schemaJson);
        try {
            return ResponseEntity.ok(objectMapper.readTree(updated.getSchemaJson()));
        } catch (Exception e) {
            return ResponseEntity.ok(body);
        }
    }
}
