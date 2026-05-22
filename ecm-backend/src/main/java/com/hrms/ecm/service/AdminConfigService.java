package com.hrms.ecm.service;

import com.hrms.ecm.dto.*;
import com.hrms.ecm.dto.ProjectDto.*;
import com.hrms.ecm.entity.*;
import com.hrms.ecm.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminConfigService {

    private final ProjectRepository projectRepo;
    private final ProjectUploadConfigRepository uploadConfigRepo;
    private final DocumentTypeConfigRepository docTypeRepo;
    private final InvoiceFieldConfigRepository fieldRepo;
    private final EcmUserRepository userRepo;

    // ───── Projects ─────

    public List<ProjectDto> getAllProjects() {
        return projectRepo.findAllByOrderByNameAsc().stream().map(this::toProjectDto).toList();
    }

    public ProjectDto getProject(Long id) {
        Project p = projectRepo.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        return toProjectDto(p);
    }

    public ProjectDto createProject(CreateProjectRequest req, Long userId) {
        if (projectRepo.findByCode(req.getCode()).isPresent()) {
            throw new RuntimeException("Project code already exists: " + req.getCode());
        }
        Project project = Project.builder()
                .code(req.getCode().toUpperCase())
                .name(req.getName())
                .description(req.getDescription())
                .createdBy(userId)
                .build();
        project = projectRepo.save(project);
        return toProjectDto(project);
    }

    public ProjectDto updateProject(Long id, CreateProjectRequest req) {
        Project project = projectRepo.findById(id).orElseThrow(() -> new RuntimeException("Project not found"));
        project.setName(req.getName());
        project.setDescription(req.getDescription());
        project = projectRepo.save(project);
        return toProjectDto(project);
    }

    public void deleteProject(Long id) {
        projectRepo.deleteById(id);
    }

    // ───── Upload Config ─────

    public UploadConfigDto saveUploadConfig(Long projectId, SaveUploadConfigRequest req) {
        projectRepo.findById(projectId).orElseThrow(() -> new RuntimeException("Project not found"));

        ProjectUploadConfig config = uploadConfigRepo.findByProjectId(projectId)
                .orElse(ProjectUploadConfig.builder().projectId(projectId).build());

        config.setMaxFileSizeBytes(req.getMaxFileSizeMb() * 1024 * 1024);
        config.setTotalAllocatedStorageBytes(req.getTotalAllocatedStorageGb() * 1024 * 1024 * 1024);
        config.setAllowedContentTypes(req.getAllowedContentTypes());
        config.setUploadBasePath(req.getUploadBasePath());
        config = uploadConfigRepo.save(config);
        return toUploadConfigDto(config);
    }

    public UploadConfigDto getUploadConfig(Long projectId) {
        ProjectUploadConfig config = uploadConfigRepo.findByProjectId(projectId)
                .orElseThrow(() -> new RuntimeException("No upload config found for project"));
        return toUploadConfigDto(config);
    }

    // ───── Document Types ─────

    public List<DocumentTypeConfigDto> getDocTypes(Long projectId) {
        return docTypeRepo.findByProjectIdOrderByTypeNameAsc(projectId).stream()
                .map(this::toDocTypeDto).toList();
    }

    @Transactional
    public DocumentTypeConfigDto saveDocType(Long projectId, SaveDocTypeRequest req) {
        projectRepo.findById(projectId).orElseThrow(() -> new RuntimeException("Project not found"));

        DocumentTypeConfig docType = DocumentTypeConfig.builder()
                .projectId(projectId)
                .typeName(req.getTypeName())
                .typeCode(req.getTypeCode().toUpperCase())
                .description(req.getDescription())
                .extractionEnabled(req.getExtractionEnabled() != null && req.getExtractionEnabled())
                .build();
        docType = docTypeRepo.save(docType);

        if (Boolean.TRUE.equals(req.getExtractionEnabled()) && req.getExtractionFields() != null) {
            saveFields(docType.getId(), req.getExtractionFields());
        }

        return toDocTypeDto(docType);
    }

    @Transactional
    public DocumentTypeConfigDto updateDocType(Long docTypeId, SaveDocTypeRequest req) {
        DocumentTypeConfig docType = docTypeRepo.findById(docTypeId)
                .orElseThrow(() -> new RuntimeException("Document type not found"));

        docType.setTypeName(req.getTypeName());
        docType.setDescription(req.getDescription());
        docType.setExtractionEnabled(req.getExtractionEnabled() != null && req.getExtractionEnabled());
        docType = docTypeRepo.save(docType);

        fieldRepo.deleteByDocTypeConfigId(docTypeId);
        if (Boolean.TRUE.equals(req.getExtractionEnabled()) && req.getExtractionFields() != null) {
            saveFields(docTypeId, req.getExtractionFields());
        }

        return toDocTypeDto(docType);
    }

    public void deleteDocType(Long docTypeId) {
        fieldRepo.deleteByDocTypeConfigId(docTypeId);
        docTypeRepo.deleteById(docTypeId);
    }

    // ───── Extraction Fields ─────

    public List<InvoiceFieldDto> getExtractionFields(Long docTypeId) {
        return fieldRepo.findByDocTypeConfigIdOrderByDisplayOrderAsc(docTypeId).stream()
                .map(this::toFieldDto).toList();
    }

    @Transactional
    public List<InvoiceFieldDto> saveExtractionFields(Long docTypeId, List<SaveDocTypeRequest.FieldEntry> fields) {
        fieldRepo.deleteByDocTypeConfigId(docTypeId);
        return saveFields(docTypeId, fields);
    }

    private List<InvoiceFieldDto> saveFields(Long docTypeId, List<SaveDocTypeRequest.FieldEntry> fields) {
        List<InvoiceFieldDto> saved = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            SaveDocTypeRequest.FieldEntry fe = fields.get(i);
            InvoiceFieldConfig field = InvoiceFieldConfig.builder()
                    .docTypeConfigId(docTypeId)
                    .fieldName(fe.getFieldName())
                    .fieldKey(fe.getFieldKey())
                    .fieldType(fe.getFieldType() != null ? fe.getFieldType() : "text")
                    .required(fe.getRequired() != null && fe.getRequired())
                    .displayOrder(fe.getDisplayOrder() != null ? fe.getDisplayOrder() : i)
                    .fieldCategory(fe.getFieldCategory() != null ? fe.getFieldCategory() : "header")
                    .build();
            field = fieldRepo.save(field);
            saved.add(toFieldDto(field));
        }
        return saved;
    }

    // ───── Mappers ─────

    private ProjectDto toProjectDto(Project p) {
        EcmUser creator = p.getCreatedBy() != null ? userRepo.findById(p.getCreatedBy()).orElse(null) : null;
        ProjectUploadConfig uploadConfig = uploadConfigRepo.findByProjectId(p.getId()).orElse(null);
        List<DocumentTypeConfig> docTypes = docTypeRepo.findByProjectIdOrderByTypeNameAsc(p.getId());

        return ProjectDto.builder()
                .id(p.getId())
                .code(p.getCode())
                .name(p.getName())
                .description(p.getDescription())
                .status(p.getStatus())
                .createdByName(creator != null ? creator.getDisplayName() : "System")
                .createdAt(p.getCreatedAt())
                .uploadConfig(uploadConfig != null ? toUploadConfigDto(uploadConfig) : null)
                .documentTypes(docTypes.stream().map(this::toDocTypeDto).toList())
                .build();
    }

    private UploadConfigDto toUploadConfigDto(ProjectUploadConfig c) {
        return UploadConfigDto.builder()
                .id(c.getId())
                .maxFileSizeBytes(c.getMaxFileSizeBytes())
                .maxFileSizeFormatted(formatSize(c.getMaxFileSizeBytes()))
                .totalAllocatedStorageBytes(c.getTotalAllocatedStorageBytes())
                .totalAllocatedStorageFormatted(formatSize(c.getTotalAllocatedStorageBytes()))
                .allowedContentTypes(c.getAllowedContentTypes())
                .uploadBasePath(c.getUploadBasePath())
                .build();
    }

    private DocumentTypeConfigDto toDocTypeDto(DocumentTypeConfig d) {
        List<InvoiceFieldConfig> fields = fieldRepo.findByDocTypeConfigIdOrderByDisplayOrderAsc(d.getId());
        return DocumentTypeConfigDto.builder()
                .id(d.getId())
                .projectId(d.getProjectId())
                .typeName(d.getTypeName())
                .typeCode(d.getTypeCode())
                .description(d.getDescription())
                .extractionEnabled(d.getExtractionEnabled())
                .status(d.getStatus())
                .extractionFields(fields.stream().map(this::toFieldDto).toList())
                .build();
    }

    private InvoiceFieldDto toFieldDto(InvoiceFieldConfig f) {
        return InvoiceFieldDto.builder()
                .id(f.getId())
                .docTypeConfigId(f.getDocTypeConfigId())
                .fieldName(f.getFieldName())
                .fieldKey(f.getFieldKey())
                .fieldType(f.getFieldType())
                .required(f.getRequired())
                .displayOrder(f.getDisplayOrder())
                .fieldCategory(f.getFieldCategory())
                .build();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
