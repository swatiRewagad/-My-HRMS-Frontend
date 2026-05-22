package com.hrms.ecm.controller;

import com.hrms.ecm.dto.*;
import com.hrms.ecm.dto.ProjectDto.*;
import com.hrms.ecm.service.AdminConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminConfigController {

    private final AdminConfigService configService;

    // ───── Projects ─────

    @GetMapping("/projects")
    public ResponseEntity<List<ProjectDto>> getAllProjects() {
        return ResponseEntity.ok(configService.getAllProjects());
    }

    @GetMapping("/projects/{id}")
    public ResponseEntity<ProjectDto> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(configService.getProject(id));
    }

    @PostMapping("/projects")
    public ResponseEntity<ProjectDto> createProject(
            @Valid @RequestBody CreateProjectRequest request,
            @RequestHeader(value = "X-User-Id", defaultValue = "1") Long userId) {
        return ResponseEntity.ok(configService.createProject(request, userId));
    }

    @PutMapping("/projects/{id}")
    public ResponseEntity<ProjectDto> updateProject(
            @PathVariable Long id,
            @Valid @RequestBody CreateProjectRequest request) {
        return ResponseEntity.ok(configService.updateProject(id, request));
    }

    @DeleteMapping("/projects/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        configService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    // ───── Upload Config ─────

    @GetMapping("/projects/{projectId}/upload-config")
    public ResponseEntity<UploadConfigDto> getUploadConfig(@PathVariable Long projectId) {
        return ResponseEntity.ok(configService.getUploadConfig(projectId));
    }

    @PostMapping("/projects/{projectId}/upload-config")
    public ResponseEntity<UploadConfigDto> saveUploadConfig(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveUploadConfigRequest request) {
        return ResponseEntity.ok(configService.saveUploadConfig(projectId, request));
    }

    // ───── Document Types ─────

    @GetMapping("/projects/{projectId}/doc-types")
    public ResponseEntity<List<DocumentTypeConfigDto>> getDocTypes(@PathVariable Long projectId) {
        return ResponseEntity.ok(configService.getDocTypes(projectId));
    }

    @PostMapping("/projects/{projectId}/doc-types")
    public ResponseEntity<DocumentTypeConfigDto> createDocType(
            @PathVariable Long projectId,
            @Valid @RequestBody SaveDocTypeRequest request) {
        return ResponseEntity.ok(configService.saveDocType(projectId, request));
    }

    @PutMapping("/doc-types/{docTypeId}")
    public ResponseEntity<DocumentTypeConfigDto> updateDocType(
            @PathVariable Long docTypeId,
            @Valid @RequestBody SaveDocTypeRequest request) {
        return ResponseEntity.ok(configService.updateDocType(docTypeId, request));
    }

    @DeleteMapping("/doc-types/{docTypeId}")
    public ResponseEntity<Void> deleteDocType(@PathVariable Long docTypeId) {
        configService.deleteDocType(docTypeId);
        return ResponseEntity.noContent().build();
    }

    // ───── Extraction Fields ─────

    @GetMapping("/doc-types/{docTypeId}/fields")
    public ResponseEntity<List<InvoiceFieldDto>> getExtractionFields(@PathVariable Long docTypeId) {
        return ResponseEntity.ok(configService.getExtractionFields(docTypeId));
    }

    @PostMapping("/doc-types/{docTypeId}/fields")
    public ResponseEntity<List<InvoiceFieldDto>> saveExtractionFields(
            @PathVariable Long docTypeId,
            @RequestBody List<SaveDocTypeRequest.FieldEntry> fields) {
        return ResponseEntity.ok(configService.saveExtractionFields(docTypeId, fields));
    }
}
