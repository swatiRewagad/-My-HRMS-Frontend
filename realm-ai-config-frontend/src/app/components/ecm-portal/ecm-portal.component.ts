import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { DomSanitizer, SafeResourceUrl, SafeHtml } from '@angular/platform-browser';
import { AuthService } from '../../services/auth.service';
import { HttpEventType } from '@angular/common/http';
import { EcmService, EcmFileDto, ExtractedFieldDto } from '../../services/ecm.service';
import { EXISTING_CONFIGS } from '../../data/realms.data';

interface FolderNode {
  name: string;
  path: string;
  children: FolderNode[];
  expanded: boolean;
  files: UploadedFile[];
  isPublic?: boolean;
}

interface UploadedFile {
  id: number;
  name: string;
  filePath: string;
  size: string;
  type: string;
  uploadedBy: string;
  uploadedAt: string;
  status: 'uploaded' | 'processing' | 'archived';
}

interface AccessEntry {
  userName: string;
  role: 'admin' | 'user';
  permissions: string[];
}

@Component({
  selector: 'app-ecm-portal',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ecm-portal.component.html',
  styleUrl: './ecm-portal.component.scss',
})
export class EcmPortalComponent implements OnInit {
  readonly auth = inject(AuthService);
  private router = inject(Router);
  private ecmService = inject(EcmService);
  private sanitizer = inject(DomSanitizer);
  private previewFileId = 0;

  readonly activeTab = signal<'files' | 'folders' | 'access'>('files');
  readonly uploadQueue = signal<{ file: File; progress: number; status: 'pending' | 'uploading' | 'done' | 'error'; errorMsg?: string }[]>([]);
  readonly showUploadModal = signal(false);
  readonly selectedFolder = signal<FolderNode | null>(null);
  readonly uploadTargetPath = signal('');
  readonly dragOver = signal(false);
  readonly folderTree = signal<FolderNode>({ name: '', path: '', children: [], expanded: true, files: [] });

  readonly showPreviewModal = signal(false);
  readonly previewFile$ = signal<UploadedFile | null>(null);
  readonly previewUrl = signal<SafeResourceUrl | null>(null);
  readonly previewType = signal<'pdf' | 'image' | 'video' | 'audio' | 'office' | 'text' | 'unknown'>('unknown');
  readonly showExtractedFields = signal(false);
  readonly extractedFields = signal<ExtractedFieldDto[]>([]);
  readonly extractedFieldsLoading = signal(false);
  readonly officeHtml = signal<SafeHtml | null>(null);
  readonly officeHtmlLoaded = signal(false);
  readonly officeSheets = signal<string[]>([]);
  readonly officeActiveSheet = signal('');
  readonly officeLoading = signal(false);
  readonly officeRows = signal<string[][]>([]);
  readonly officeEditing = signal(false);
  readonly officeSaving = signal(false);
  readonly officeDirty = signal(false);

  readonly ecmConfig = computed(() => {
    const realm = this.auth.currentRealm();
    if (!realm) return null;
    const config = EXISTING_CONFIGS[realm.id];
    if (!config?.serviceDetails) return null;

    const details = config.serviceDetails;
    return {
      mountPoint: details['ecm-file-upload__mountPoint']?.schemaName || '/mnt/kavach/realms/ecm/uploads',
      supportedFileTypes: details['ecm-file-upload__supportedFileTypes']?.schemaName || 'All file types',
      volumeSize: details['ecm-file-upload__volumeSize']?.schemaName || '500',
      maxFileSize: details['ecm-file-upload__maxFileSize']?.schemaName || '100',
      archivePeriod: details['ecm-archival__archivePeriod']?.schemaName || '365',
      archiveFrequency: details['ecm-archival__frequency']?.schemaName || 'Monthly',
      archiveLocation: details['ecm-archival__archiveLocation']?.schemaName || 's3://kavach-archive/ecm/',
      compression: details['ecm-archival__compression']?.schemaName || 'None',
      ocrEngine: details['ecm-ocr__ocrEngine']?.schemaName || 'Tesseract',
      ocrLanguage: details['ecm-ocr__ocrLanguage']?.schemaName || 'English',
      ocrDpi: details['ecm-ocr__ocrDpi']?.schemaName || '300',
      ocrConfidence: details['ecm-ocr__ocrConfidence']?.schemaName || '80',
    };
  });

  readonly uploadingToBackend = signal(false);

  readonly editingFile = signal<UploadedFile | null>(null);
  readonly editContent = signal('');
  readonly editLoading = signal(false);
  readonly editSaving = signal(false);
  readonly showEditModal = signal(false);
  readonly showReplaceModal = signal(false);
  readonly replaceFile = signal<UploadedFile | null>(null);
  readonly replaceQueue = signal<{ file: File; status: 'pending' | 'uploading' | 'done' | 'error'; errorMsg?: string } | null>(null);

  readonly isPublicFolder = computed(() => {
    const folder = this.selectedFolder();
    return folder?.isPublic === true;
  });

  readonly canEditFiles = computed(() => {
    if (!this.isPublicFolder()) return false;
    const role = this.auth.currentUser()?.role;
    return role === 'admin' || role === 'user';
  });

  ngOnInit(): void {
    this.buildFolderTree();
    this.loadFilesFromBackend();
  }

  private buildFolderTree(): void {
    const mount = this.ecmConfig()?.mountPoint || '/';
    const archiveLocation = this.ecmConfig()?.archiveLocation || '';

    const parts = mount.replace(/^\//, '').split('/').filter(p => p.length > 0);

    const publicFolder: FolderNode = {
      name: 'public',
      path: `${mount}/public`,
      expanded: false,
      files: [],
      children: [],
      isPublic: true,
    };

    const uploadsFolder: FolderNode = {
      name: 'uploads',
      path: `${mount}/uploads`,
      expanded: true,
      files: [],
      children: [
        { name: 'invoices', path: `${mount}/uploads/invoices`, expanded: false, files: [], children: [] },
        { name: 'contracts', path: `${mount}/uploads/contracts`, expanded: false, files: [], children: [] },
      ],
    };

    const archiveFolder: FolderNode | null = archiveLocation ? {
      name: 'archive',
      path: archiveLocation,
      expanded: false,
      files: [],
      children: [],
    } : null;

    const leafChildren = [publicFolder, uploadsFolder, ...(archiveFolder ? [archiveFolder] : [])];

    if (parts.length === 0) {
      this.folderTree.set({
        name: '/',
        path: mount,
        expanded: true,
        files: [],
        children: leafChildren,
      });
      this.selectedFolder.set(uploadsFolder);
      return;
    }

    let current: FolderNode = {
      name: parts[parts.length - 1],
      path: mount,
      expanded: true,
      files: [],
      children: leafChildren,
    };

    for (let i = parts.length - 2; i >= 0; i--) {
      const partPath = '/' + parts.slice(0, i + 1).join('/');
      current = {
        name: parts[i],
        path: partPath,
        expanded: true,
        files: [],
        children: [current],
      };
    }

    this.folderTree.set(current);
    this.selectedFolder.set(uploadsFolder);
  }

  readonly accessList = computed<AccessEntry[]>(() => {
    const realm = this.auth.currentRealm();
    if (!realm) return [];
    const users = this.auth.getRealmUsers(realm.id);
    return users.map(u => ({
      userName: u.name,
      role: u.role,
      permissions: u.role === 'admin'
        ? ['Upload', 'Download', 'Edit (Public)', 'Delete', 'Archive', 'Manage Access']
        : ['Upload', 'Download', 'Edit (Public)'],
    }));
  });

  readonly currentFolderFiles = computed(() => {
    const folder = this.selectedFolder();
    return folder?.files ?? [];
  });

  readonly currentFolderPath = computed(() => {
    const folder = this.selectedFolder();
    return folder?.path ?? this.ecmConfig()?.mountPoint ?? '';
  });

  readonly allFolders = computed<{ name: string; path: string }[]>(() => {
    const result: { name: string; path: string }[] = [];
    const collect = (node: FolderNode, prefix: string) => {
      const label = prefix ? `${prefix} / ${node.name}` : node.name;
      result.push({ name: label, path: node.path });
      node.children.forEach(c => collect(c, label));
    };
    const tree = this.folderTree();
    if (tree.name) collect(tree, '');
    return result;
  });

  readonly usedStorage = computed(() => '0');

  readonly allowedExtensions = computed(() => {
    const types = this.ecmConfig()?.supportedFileTypes ?? 'All file types';
    if (types === 'All file types') return '*';
    if (types === 'PDF') return '.pdf';
    if (types === 'PDF, DOCX') return '.pdf,.docx';
    if (types === 'PDF, DOCX, XLSX') return '.pdf,.docx,.xlsx';
    if (types === 'PDF, DOCX, XLSX, Images') return '.pdf,.docx,.xlsx,.png,.jpg,.jpeg';
    return '*';
  });

  goBack(): void {
    this.router.navigate(['/portal']);
  }

  selectFolder(folder: FolderNode): void {
    this.selectedFolder.set(folder);
    this.loadFilesFromBackend();
  }

  toggleFolder(folder: FolderNode): void {
    folder.expanded = !folder.expanded;
    this.selectFolder(folder);
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(true);
  }

  onDragLeave(): void {
    this.dragOver.set(false);
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const files = event.dataTransfer?.files;
    if (files) this.addFiles(files);
  }

  onFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files) this.addFiles(input.files);
    input.value = '';
  }

  private addFiles(fileList: FileList): void {
    const maxMb = parseInt(this.ecmConfig()?.maxFileSize ?? '100', 10);
    const items = Array.from(fileList).map(file => {
      const sizeMb = file.size / (1024 * 1024);
      if (sizeMb > maxMb) {
        return { file, progress: 0, status: 'error' as const, errorMsg: `Exceeds ${maxMb} MB limit` };
      }
      return { file, progress: 0, status: 'pending' as const };
    });
    this.uploadQueue.set(items);
    this.showUploadModal.set(true);
  }

  startUpload(): void {
    const queue = this.uploadQueue();
    const validFiles = queue.filter(i => i.status !== 'error').map(i => i.file);

    if (validFiles.length === 0) return;

    queue.forEach(item => {
      if (item.status !== 'error') item.status = 'uploading';
    });
    this.uploadQueue.set([...queue]);
    this.uploadingToBackend.set(true);

    const realmId = this.auth.currentRealm()?.id ?? '';
    const folderPath = this.uploadTargetPath() || this.currentFolderPath();
    const uploadedBy = this.auth.currentUser()?.userName ?? 'Unknown';

    this.ecmService.uploadFilesWithProgress(realmId, folderPath, uploadedBy, validFiles).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress && event.total) {
          const pct = Math.round((event.loaded / event.total) * 100);
          queue.forEach(item => {
            if (item.status === 'uploading') item.progress = pct;
          });
          this.uploadQueue.set([...queue]);
        } else if (event.type === HttpEventType.Response) {
          const uploaded = event.body as EcmFileDto[];
          queue.forEach(item => {
            if (item.status !== 'error') {
              item.progress = 100;
              item.status = 'done';
            }
          });
          this.uploadQueue.set([...queue]);
          this.uploadingToBackend.set(false);

          if (uploaded) {
            this.addUploadedFilesToFolder(uploaded);
          }
          const targetFolder = this.findFolderByPath(this.folderTree(), folderPath);
          if (targetFolder) {
            this.selectedFolder.set(targetFolder);
          }
        }
      },
      error: (err) => {
        const msg = err?.error?.error || err?.message || 'Upload failed — is the backend running?';
        queue.forEach(item => {
          if (item.status === 'uploading') {
            item.status = 'error';
            item.errorMsg = msg;
          }
        });
        this.uploadQueue.set([...queue]);
        this.uploadingToBackend.set(false);
      },
    });
  }

  private addUploadedFilesToFolder(dtos: EcmFileDto[]): void {
    const targetPath = this.uploadTargetPath() || this.currentFolderPath();
    const folder = this.findFolderByPath(this.folderTree(), targetPath) ?? this.selectedFolder();
    if (!folder) return;

    const newFiles: UploadedFile[] = dtos.map(dto => ({
      id: dto.id,
      name: dto.fileName,
      filePath: dto.filePath,
      size: this.formatFileSize(dto.fileSize),
      type: dto.fileType,
      uploadedBy: dto.uploadedBy,
      uploadedAt: dto.uploadedAt ? dto.uploadedAt.replace('T', ' ').substring(0, 16) : '',
      status: dto.status as 'uploaded' | 'processing' | 'archived',
    }));

    folder.files = [...folder.files, ...newFiles];
    this.selectedFolder.set({ ...folder });
  }

  loadFilesFromBackend(): void {
    const realmId = this.auth.currentRealm()?.id;
    const folderPath = this.currentFolderPath();
    if (!realmId) return;

    this.ecmService.getFiles(realmId, folderPath).subscribe({
      next: (dtos: EcmFileDto[]) => {
        const folder = this.selectedFolder();
        if (!folder) return;

        folder.files = dtos.map(dto => ({
          id: dto.id,
          name: dto.fileName,
          filePath: dto.filePath,
          size: this.formatFileSize(dto.fileSize),
          type: dto.fileType,
          uploadedBy: dto.uploadedBy,
          uploadedAt: dto.uploadedAt ? dto.uploadedAt.replace('T', ' ').substring(0, 16) : '',
          status: dto.status as 'uploaded' | 'processing' | 'archived',
        }));
        this.selectedFolder.set({ ...folder });
      },
    });
  }

  openUploadModal(): void {
    this.uploadTargetPath.set(this.currentFolderPath());
    this.showUploadModal.set(true);
  }

  closeUploadModal(): void {
    this.showUploadModal.set(false);
    this.uploadQueue.set([]);
  }

  private findFolderByPath(node: FolderNode, path: string): FolderNode | null {
    if (node.path === path) return node;
    for (const child of node.children) {
      const found = this.findFolderByPath(child, path);
      if (found) return found;
    }
    return null;
  }

  deleteFile(file: UploadedFile): void {
    if (!confirm(`Delete "${file.name}"? This will remove the file from disk and database.`)) return;

    this.ecmService.deleteFile(file.id).subscribe({
      next: () => {
        const folder = this.selectedFolder();
        if (folder) {
          folder.files = folder.files.filter(f => f.id !== file.id);
          this.selectedFolder.set({ ...folder });
        }
      },
      error: (err) => {
        alert('Failed to delete: ' + (err?.error?.error || err?.message || 'Unknown error'));
      },
    });
  }

  openPreview(file: UploadedFile): void {
    this.previewFile$.set(file);
    this.previewFileId = file.id;
    const type = this.detectPreviewType(file.name);
    this.previewType.set(type);
    const url = this.ecmService.getPreviewUrl(file.id);
    this.previewUrl.set(this.sanitizer.bypassSecurityTrustResourceUrl(url));
    this.showExtractedFields.set(false);
    this.extractedFields.set([]);
    this.officeHtml.set(null);
    this.officeHtmlLoaded.set(false);
    this.officeSheets.set([]);
    this.officeActiveSheet.set('');
    this.officeRows.set([]);
    this.officeEditing.set(false);
    this.officeSaving.set(false);
    this.officeDirty.set(false);
    this.showPreviewModal.set(true);
    this.loadExtractedFields(file.id);

    if (type === 'office') {
      this.loadSpreadsheet(file.id);
    }
  }

  closePreview(): void {
    this.showPreviewModal.set(false);
    this.previewFile$.set(null);
    this.previewUrl.set(null);
    this.showExtractedFields.set(false);
    this.extractedFields.set([]);
    this.officeHtml.set(null);
    this.officeHtmlLoaded.set(false);
    this.officeSheets.set([]);
    this.officeRows.set([]);
    this.officeEditing.set(false);
    this.officeDirty.set(false);
  }

  private loadSpreadsheet(fileId: number, sheetName?: string): void {
    this.officeLoading.set(true);
    this.officeHtmlLoaded.set(false);
    this.officeEditing.set(false);
    this.officeDirty.set(false);
    this.ecmService.getSpreadsheetHtml(fileId, sheetName).subscribe({
      next: (res) => {
        this.officeSheets.set(res.sheets);
        this.officeActiveSheet.set(res.activeSheet);
        this.officeHtml.set(this.sanitizer.bypassSecurityTrustHtml(res.html));
        this.officeRows.set(res.rows ?? []);
        this.officeHtmlLoaded.set(true);
        this.officeLoading.set(false);
      },
      error: () => {
        this.officeHtmlLoaded.set(false);
        this.officeLoading.set(false);
      },
    });
  }

  switchSheet(sheetName: string): void {
    this.officeActiveSheet.set(sheetName);
    this.loadSpreadsheet(this.previewFileId, sheetName);
  }

  toggleSpreadsheetEdit(): void {
    this.officeEditing.update(v => !v);
  }

  onCellEdit(rowIdx: number, colIdx: number, event: Event): void {
    const value = (event.target as HTMLElement).textContent ?? '';
    const rows = this.officeRows().map(r => [...r]);
    if (rows[rowIdx]) {
      rows[rowIdx][colIdx] = value;
      this.officeRows.set(rows);
      this.officeDirty.set(true);
    }
  }

  saveSpreadsheet(): void {
    const file = this.previewFile$();
    if (!file) return;
    this.officeSaving.set(true);
    const editedBy = this.auth.currentUser()?.userName ?? 'Unknown';
    this.ecmService.updateSpreadsheet(file.id, this.officeRows(), this.officeActiveSheet(), editedBy).subscribe({
      next: () => {
        this.officeSaving.set(false);
        this.officeDirty.set(false);
        this.loadSpreadsheet(file.id, this.officeActiveSheet());
      },
      error: (err: any) => {
        this.officeSaving.set(false);
        alert('Failed to save: ' + (err?.error?.error || err?.message || 'Unknown error'));
      },
    });
  }

  toggleExtractedFields(): void {
    this.showExtractedFields.update(v => !v);
  }

  private loadExtractedFields(fileId: number): void {
    this.extractedFieldsLoading.set(true);
    this.ecmService.getExtractedFields(fileId).subscribe({
      next: (fields) => {
        this.extractedFields.set(fields);
        this.extractedFieldsLoading.set(false);
      },
      error: () => {
        this.extractedFields.set([]);
        this.extractedFieldsLoading.set(false);
      },
    });
  }

  getConfidenceColor(confidence: number): string {
    if (confidence >= 90) return '#16a34a';
    if (confidence >= 80) return '#d97706';
    return '#dc2626';
  }

  getConfidenceBg(confidence: number): string {
    if (confidence >= 90) return '#f0fdf4';
    if (confidence >= 80) return '#fffbeb';
    return '#fef2f2';
  }

  private detectPreviewType(fileName: string): 'pdf' | 'image' | 'video' | 'audio' | 'office' | 'text' | 'unknown' {
    const lower = fileName.toLowerCase();
    if (lower.endsWith('.pdf')) return 'pdf';
    if (/\.(png|jpg|jpeg|gif|bmp|svg|webp)$/.test(lower)) return 'image';
    if (/\.(mp4|webm|avi|mov|mkv)$/.test(lower)) return 'video';
    if (/\.(mp3|wav|ogg|flac|aac)$/.test(lower)) return 'audio';
    if (/\.(xlsx|xls)$/.test(lower)) return 'office';
    if (/\.(csv|txt|json|xml)$/.test(lower)) return 'text';
    return 'unknown';
  }

  downloadFile(file: UploadedFile): void {
    const url = this.ecmService.getPreviewUrl(file.id);
    const a = document.createElement('a');
    a.href = url;
    a.download = file.name;
    a.click();
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  getStatusColor(status: string): string {
    switch (status) {
      case 'uploaded': return '#16a34a';
      case 'processing': return '#d97706';
      case 'archived': return '#6b7280';
      default: return '#6b7280';
    }
  }

  getInitials(name: string): string {
    return name.split(' ').map(n => n[0]).join('').toUpperCase();
  }

  getStatusBg(status: string): string {
    switch (status) {
      case 'uploaded': return '#f0fdf4';
      case 'processing': return '#fffbeb';
      case 'archived': return '#f8fafc';
      default: return '#f8fafc';
    }
  }

  isEditableText(fileName: string): boolean {
    return /\.(csv|txt|json|xml)$/i.test(fileName);
  }

  openEditFile(file: UploadedFile): void {
    if (this.isEditableText(file.name)) {
      this.editingFile.set(file);
      this.editLoading.set(true);
      this.editContent.set('');
      this.showEditModal.set(true);
      this.ecmService.getFileContent(file.id).subscribe({
        next: (text: string) => {
          this.editContent.set(text);
          this.editLoading.set(false);
        },
        error: () => {
          this.editContent.set('');
          this.editLoading.set(false);
        },
      });
    } else {
      this.replaceFile.set(file);
      this.replaceQueue.set(null);
      this.showReplaceModal.set(true);
    }
  }

  saveEditContent(): void {
    const file = this.editingFile();
    if (!file) return;
    this.editSaving.set(true);
    const editedBy = this.auth.currentUser()?.userName ?? 'Unknown';
    this.ecmService.updateFileContent(file.id, this.editContent(), editedBy).subscribe({
      next: () => {
        this.editSaving.set(false);
        this.showEditModal.set(false);
        this.loadFilesFromBackend();
      },
      error: (err: any) => {
        this.editSaving.set(false);
        alert('Failed to save: ' + (err?.error?.error || err?.message || 'Unknown error'));
      },
    });
  }

  closeEditModal(): void {
    this.showEditModal.set(false);
    this.editingFile.set(null);
    this.editContent.set('');
  }

  onReplaceFileSelect(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.replaceQueue.set({ file: input.files[0], status: 'pending' });
    }
    input.value = '';
  }

  startReplace(): void {
    const original = this.replaceFile();
    const item = this.replaceQueue();
    if (!original || !item) return;

    this.replaceQueue.set({ ...item, status: 'uploading' });
    const editedBy = this.auth.currentUser()?.userName ?? 'Unknown';
    this.ecmService.replaceFile(original.id, item.file, editedBy).subscribe({
      next: () => {
        this.replaceQueue.set({ ...item, status: 'done' });
        setTimeout(() => {
          this.showReplaceModal.set(false);
          this.replaceQueue.set(null);
          this.loadFilesFromBackend();
        }, 800);
      },
      error: (err: any) => {
        this.replaceQueue.set({ ...item, status: 'error', errorMsg: err?.error?.error || err?.message || 'Replace failed' });
      },
    });
  }

  closeReplaceModal(): void {
    this.showReplaceModal.set(false);
    this.replaceFile.set(null);
    this.replaceQueue.set(null);
  }
}
