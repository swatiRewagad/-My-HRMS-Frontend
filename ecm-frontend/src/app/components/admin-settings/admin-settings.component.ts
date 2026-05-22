import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EcmService } from '../../services/ecm.service';

@Component({
  selector: 'app-admin-settings',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-settings.component.html',
  styleUrl: './admin-settings.component.scss',
})
export class AdminSettingsComponent implements OnInit {
  projects: any[] = [];
  selectedProject: any = null;
  activeTab: 'projects' | 'upload' | 'docTypes' = 'projects';

  // Project form
  showProjectForm = false;
  editingProject: any = null;
  projectForm = { code: '', name: '', description: '' };

  // Upload config form
  uploadForm = { maxFileSizeMb: 50, totalAllocatedStorageGb: 10, allowedContentTypes: '', uploadBasePath: '' };

  // Doc type form
  showDocTypeForm = false;
  editingDocType: any = null;
  docTypeForm = { typeName: '', typeCode: '', description: '', extractionEnabled: false, extractionFields: [] as any[] };
  docTypeOptions = [
    { label: 'Invoice', code: 'INVOICE', extraction: true },
    { label: 'Text', code: 'TEXT', extraction: false },
  ];

  // Field form
  showFieldForm = false;
  fieldForm = { fieldName: '', fieldKey: '', fieldType: 'text', required: false, displayOrder: 0, fieldCategory: 'header' };

  saving = false;

  constructor(private ecm: EcmService) {}

  ngOnInit() {
    this.loadProjects();
  }

  loadProjects() {
    this.ecm.getProjects().subscribe(p => this.projects = p);
  }

  selectProject(project: any) {
    this.ecm.getProject(project.id).subscribe(p => {
      this.selectedProject = p;
      this.activeTab = 'upload';
      if (p.uploadConfig) {
        this.uploadForm = {
          maxFileSizeMb: Math.round(p.uploadConfig.maxFileSizeBytes / (1024 * 1024)),
          totalAllocatedStorageGb: Math.round(p.uploadConfig.totalAllocatedStorageBytes / (1024 * 1024 * 1024)),
          allowedContentTypes: p.uploadConfig.allowedContentTypes,
          uploadBasePath: p.uploadConfig.uploadBasePath,
        };
      } else {
        this.uploadForm = { maxFileSizeMb: 50, totalAllocatedStorageGb: 10, allowedContentTypes: 'application/pdf,image/png,image/jpeg', uploadBasePath: 'C:/ecm-storage/' + p.code };
      }
    });
  }

  backToProjects() {
    this.selectedProject = null;
    this.activeTab = 'projects';
    this.loadProjects();
  }

  // ───── Project CRUD ─────

  openNewProject() {
    this.editingProject = null;
    this.projectForm = { code: '', name: '', description: '' };
    this.showProjectForm = true;
  }

  openEditProject(p: any) {
    this.editingProject = p;
    this.projectForm = { code: p.code, name: p.name, description: p.description || '' };
    this.showProjectForm = true;
  }

  saveProject() {
    if (!this.projectForm.code.trim() || !this.projectForm.name.trim()) return;
    this.saving = true;
    const obs = this.editingProject
      ? this.ecm.updateProject(this.editingProject.id, this.projectForm)
      : this.ecm.createProject(this.projectForm);
    obs.subscribe({
      next: () => {
        this.saving = false;
        this.showProjectForm = false;
        this.loadProjects();
      },
      error: () => this.saving = false,
    });
  }

  deleteProject(p: any) {
    this.ecm.deleteProject(p.id).subscribe(() => this.loadProjects());
  }

  // ───── Upload Config ─────

  saveUploadConfig() {
    if (!this.selectedProject) return;
    this.saving = true;
    this.ecm.saveUploadConfig(this.selectedProject.id, this.uploadForm).subscribe({
      next: (config) => {
        this.saving = false;
        this.selectedProject.uploadConfig = config;
      },
      error: () => this.saving = false,
    });
  }

  // ───── Document Types ─────

  openNewDocType() {
    this.editingDocType = null;
    this.docTypeForm = { typeName: '', typeCode: '', description: '', extractionEnabled: false, extractionFields: [] };
    this.showDocTypeForm = true;
  }

  onDocTypeSelected(code: string) {
    const opt = this.docTypeOptions.find(o => o.code === code);
    if (opt) {
      this.docTypeForm.typeName = opt.label;
      this.docTypeForm.typeCode = opt.code;
      this.docTypeForm.extractionEnabled = opt.extraction;
    }
  }

  openEditDocType(dt: any) {
    this.editingDocType = dt;
    this.docTypeForm = {
      typeName: dt.typeName,
      typeCode: dt.typeCode,
      description: dt.description || '',
      extractionEnabled: dt.extractionEnabled,
      extractionFields: dt.extractionFields ? [...dt.extractionFields] : [],
    };
    this.showDocTypeForm = true;
  }

  saveDocType() {
    if (!this.selectedProject || !this.docTypeForm.typeName.trim()) return;
    this.saving = true;
    const obs = this.editingDocType
      ? this.ecm.updateDocType(this.editingDocType.id, this.docTypeForm)
      : this.ecm.createDocType(this.selectedProject.id, this.docTypeForm);
    obs.subscribe({
      next: () => {
        this.saving = false;
        this.showDocTypeForm = false;
        this.selectProject(this.selectedProject);
      },
      error: () => this.saving = false,
    });
  }

  deleteDocType(dt: any) {
    this.ecm.deleteDocType(dt.id).subscribe(() => this.selectProject(this.selectedProject));
  }

  // ───── Extraction Fields ─────

  openAddField() {
    this.fieldForm = {
      fieldName: '', fieldKey: '', fieldType: 'text', required: false,
      displayOrder: this.docTypeForm.extractionFields.length,
      fieldCategory: 'header',
    };
    this.showFieldForm = true;
  }

  addField() {
    if (!this.fieldForm.fieldName.trim()) return;
    this.fieldForm.fieldKey = this.toCamelCase(this.fieldForm.fieldName);
    this.docTypeForm.extractionFields.push({ ...this.fieldForm });
    this.showFieldForm = false;
  }

  private toCamelCase(name: string): string {
    return name.trim().split(/[\s_\-\/]+/).map((w, i) =>
      i === 0 ? w.toLowerCase() : w.charAt(0).toUpperCase() + w.slice(1).toLowerCase()
    ).join('');
  }

  removeField(index: number) {
    this.docTypeForm.extractionFields.splice(index, 1);
    this.docTypeForm.extractionFields.forEach((f: any, i: number) => f.displayOrder = i);
  }

  moveField(index: number, direction: number) {
    const target = index + direction;
    if (target < 0 || target >= this.docTypeForm.extractionFields.length) return;
    const temp = this.docTypeForm.extractionFields[index];
    this.docTypeForm.extractionFields[index] = this.docTypeForm.extractionFields[target];
    this.docTypeForm.extractionFields[target] = temp;
    this.docTypeForm.extractionFields.forEach((f: any, i: number) => f.displayOrder = i);
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB';
    return (bytes / 1073741824).toFixed(1) + ' GB';
  }
}
