import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EcmService, ChunkUploadProgress } from '../../services/ecm.service';

interface FileUploadItem {
  file: File;
  progress: number;
  status: 'pending' | 'uploading' | 'completed' | 'error';
  error?: string;
  uploadedFileId?: number;
  extracting?: boolean;
  extraction?: any;
  extractionError?: string;
}

@Component({
  selector: 'app-file-upload',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './file-upload.component.html',
  styleUrl: './file-upload.component.scss',
})
export class FileUploadComponent implements OnInit {
  folders: any[] = [];
  flatFolders: { id: number; name: string; level: number; visibility: string }[] = [];
  selectedFolderId = 0;
  selectedFolderName = '';
  showFolderTree = false;
  expandedPickerFolders: Set<number> = new Set();
  uploadItems: FileUploadItem[] = [];
  uploading = false;
  dragOver = false;
  showExtraction = false;
  activeExtraction: any = null;

  projects: any[] = [];
  selectedProjectId = 0;
  selectedProject: any = null;
  docTypes: any[] = [];
  selectedDocTypeId = 0;
  selectedDocType: any = null;

  constructor(private ecm: EcmService, private router: Router) {}

  ngOnInit() {
    this.ecm.getRootFolders().subscribe(f => {
      this.folders = f;
      this.flatFolders = [];
      this.flattenFolders(f, 0);
    });
    this.ecm.getProjects().subscribe(p => this.projects = p);
  }

  private flattenFolders(folders: any[], level: number) {
    for (const folder of folders) {
      this.flatFolders.push({ id: folder.id, name: folder.name, level, visibility: folder.visibility });
      if (folder.children?.length) {
        this.flattenFolders(folder.children, level + 1);
      }
    }
  }

  togglePickerFolder(folder: any, event: Event) {
    event.stopPropagation();
    if (this.expandedPickerFolders.has(folder.id)) {
      this.expandedPickerFolders.delete(folder.id);
    } else {
      this.expandedPickerFolders.add(folder.id);
    }
  }

  pickFolder(folder: any) {
    if (folder.visibility === 'private') return;
    this.selectedFolderId = folder.id;
    this.selectedFolderName = folder.name;
    this.showFolderTree = false;
  }

  onProjectChange() {
    this.selectedProject = this.projects.find(p => p.id === +this.selectedProjectId) || null;
    this.docTypes = this.selectedProject?.documentTypes || [];
    this.selectedDocTypeId = 0;
    this.selectedDocType = null;
  }

  onDocTypeChange() {
    this.selectedDocType = this.docTypes.find(d => d.id === +this.selectedDocTypeId) || null;
  }

  getMaxFileSizeFormatted(): string {
    if (!this.selectedProject?.uploadConfig) return '';
    return this.selectedProject.uploadConfig.maxFileSizeFormatted;
  }

  getAllowedTypes(): string {
    if (!this.selectedProject?.uploadConfig) return '';
    return this.selectedProject.uploadConfig.allowedContentTypes;
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.addFiles(Array.from(input.files));
      input.value = '';
    }
  }

  onDragOver(event: DragEvent) {
    event.preventDefault();
    this.dragOver = true;
  }

  onDragLeave() {
    this.dragOver = false;
  }

  onDrop(event: DragEvent) {
    event.preventDefault();
    this.dragOver = false;
    if (event.dataTransfer?.files) {
      this.addFiles(Array.from(event.dataTransfer.files));
    }
  }

  addFiles(files: File[]) {
    const config = this.selectedProject?.uploadConfig;
    for (const file of files) {
      let error: string | undefined;
      if (config) {
        if (file.size > config.maxFileSizeBytes) {
          error = `File exceeds max size (${config.maxFileSizeFormatted})`;
        }
        const allowed = config.allowedContentTypes?.split(',').map((t: string) => t.trim()) || [];
        if (allowed.length > 0 && !allowed.includes(file.type)) {
          error = `File type "${file.type || 'unknown'}" not allowed for this project`;
        }
      }
      this.uploadItems.push({ file, progress: 0, status: error ? 'error' : 'pending', error });
    }
  }

  removeFile(index: number) {
    if (this.uploadItems[index].status !== 'uploading') {
      this.uploadItems.splice(index, 1);
    }
  }

  uploadAll() {
    if (!this.selectedFolderId || this.uploadItems.length === 0) return;
    this.uploading = true;

    const pending = this.uploadItems.filter(i => i.status === 'pending' || i.status === 'error');
    let completed = 0;

    for (const item of pending) {
      item.status = 'uploading';
      item.progress = 0;

      const progress$ = this.ecm.uploadFileChunked(item.file, this.selectedFolderId);
      progress$.subscribe({
        next: (p: ChunkUploadProgress) => {
          item.progress = p.percent;
          if (p.status === 'completed') {
            item.status = 'completed';
            if (p.file?.id) {
              item.uploadedFileId = p.file.id;
              if (this.isPdf(item) && this.selectedDocType?.extractionEnabled) {
                this.extractInvoice(item);
              }
            }
          } else if (p.status === 'error') {
            item.status = 'error';
            item.error = p.error;
          }
        },
        complete: () => {
          if (item.status === 'uploading') item.status = 'completed';
          completed++;
          if (completed === pending.length) this.uploading = false;
        },
        error: () => {
          item.status = 'error';
          completed++;
          if (completed === pending.length) this.uploading = false;
        },
      });
    }
  }

  get allCompleted(): boolean {
    return this.uploadItems.length > 0 && this.uploadItems.every(i => i.status === 'completed');
  }

  get hasPending(): boolean {
    return this.uploadItems.some(i => i.status === 'pending' || i.status === 'error');
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  isPdf(item: FileUploadItem): boolean {
    return item.file.type === 'application/pdf' || item.file.name.toLowerCase().endsWith('.pdf');
  }

  extractInvoice(item: FileUploadItem) {
    if (!item.uploadedFileId) return;
    item.extracting = true;
    item.extractionError = undefined;
    const docTypeId = this.selectedDocType?.extractionEnabled ? this.selectedDocType.id : undefined;
    this.ecm.extractInvoice(item.uploadedFileId, docTypeId).subscribe({
      next: (result) => {
        item.extracting = false;
        item.extraction = result;
        this.activeExtraction = result;
        this.showExtraction = true;
      },
      error: (err) => {
        item.extracting = false;
        item.extractionError = err.error?.message || err.message || 'Extraction failed';
      },
    });
  }

  viewExtraction(item: FileUploadItem) {
    this.activeExtraction = item.extraction;
    this.showExtraction = true;
  }

  goToFiles() {
    this.router.navigate(['/files']);
  }
}
