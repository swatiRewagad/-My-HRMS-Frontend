import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EcmService } from '../../services/ecm.service';
import { FilePreviewComponent } from '../file-preview/file-preview.component';

@Component({
  selector: 'app-file-manager',
  standalone: true,
  imports: [CommonModule, FormsModule, FilePreviewComponent],
  templateUrl: './file-manager.component.html',
  styleUrl: './file-manager.component.scss',
})
export class FileManagerComponent implements OnInit {
  folders: any[] = [];
  files: any[] = [];
  users: any[] = [];
  selectedFolder: any = null;
  breadcrumbs: any[] = [];
  searchQuery = '';

  showNewFolder = false;
  newFolder = { name: '', visibility: 'public', description: '' };

  showUpload = false;
  selectedFile: File | null = null;
  uploading = false;
  uploadProgress = 0;

  showAccess = false;
  accessUserId = 0;
  accessPermission = 'read';

  showShare = false;
  shareFileId = 0;
  shareType = 'user';
  shareUserId = 0;
  sharePermission = 'read';
  shareExpiry = 24;

  showPreview = false;
  previewFile: any = null;

  showExtraction = false;
  activeExtraction: any = null;
  extractingFileId: number | null = null;

  constructor(private ecm: EcmService) {}

  ngOnInit() {
    this.loadFolders();
    this.ecm.getUsers().subscribe(u => this.users = u);
  }

  loadFolders() {
    this.ecm.getRootFolders().subscribe(f => this.folders = f);
  }

  selectFolder(folder: any) {
    this.selectedFolder = folder;
    this.breadcrumbs = [folder];
    this.ecm.getFilesByFolder(folder.id).subscribe(f => this.files = f);
  }

  goToRoot() {
    this.selectedFolder = null;
    this.breadcrumbs = [];
    this.files = [];
  }

  openNewFolder() {
    this.showNewFolder = true;
    this.newFolder = { name: '', visibility: 'public', description: '' };
  }

  createFolder() {
    if (!this.newFolder.name.trim()) return;
    const data: any = { ...this.newFolder };
    if (this.selectedFolder) data.parentId = this.selectedFolder.id;
    this.ecm.createFolder(data).subscribe(() => {
      this.showNewFolder = false;
      this.loadFolders();
    });
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) this.selectedFile = input.files[0];
  }

  uploadFile() {
    if (!this.selectedFile || !this.selectedFolder) return;
    this.uploading = true;
    this.uploadProgress = 0;

    const progress$ = this.ecm.uploadFileChunked(this.selectedFile, this.selectedFolder.id);
    progress$.subscribe({
      next: (p) => {
        this.uploadProgress = p.percent;
        if (p.status === 'completed') {
          this.uploading = false;
          this.showUpload = false;
          this.selectedFile = null;
          this.uploadProgress = 0;
          this.ecm.getFilesByFolder(this.selectedFolder.id).subscribe(f => this.files = f);
        }
      },
      error: () => {
        this.uploading = false;
        this.uploadProgress = 0;
      },
    });
  }

  openPreview(file: any) {
    this.previewFile = file;
    this.showPreview = true;
  }

  downloadFile(file: any) {
    this.ecm.downloadFile(file.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = file.originalName;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  deleteFile(file: any) {
    this.ecm.deleteFile(file.id).subscribe(() => {
      this.files = this.files.filter(f => f.id !== file.id);
    });
  }

  openAccess() {
    this.showAccess = true;
    this.accessUserId = 0;
    this.accessPermission = 'read';
  }

  grantAccess() {
    if (!this.selectedFolder || !this.accessUserId) return;
    this.ecm.grantAccess(this.selectedFolder.id, this.accessUserId, this.accessPermission).subscribe(() => {
      this.showAccess = false;
      this.ecm.getFolder(this.selectedFolder.id).subscribe(f => this.selectedFolder = f);
    });
  }

  revokeAccess(userId: number) {
    if (!this.selectedFolder) return;
    this.ecm.revokeAccess(this.selectedFolder.id, userId).subscribe(() => {
      this.ecm.getFolder(this.selectedFolder.id).subscribe(f => this.selectedFolder = f);
    });
  }

  openShare(fileId: number) {
    this.shareFileId = fileId;
    this.showShare = true;
    this.shareType = 'user';
    this.shareUserId = 0;
    this.sharePermission = 'read';
    this.shareExpiry = 24;
  }

  shareFile() {
    const data: any = { fileId: this.shareFileId, shareType: this.shareType, permission: this.sharePermission };
    if (this.shareType === 'user') data.sharedWith = this.shareUserId;
    if (this.shareType === 'link') data.expiresInHours = this.shareExpiry;
    this.ecm.shareFile(data).subscribe(() => this.showShare = false);
  }

  searchFiles() {
    if (!this.searchQuery.trim()) return;
    this.ecm.searchFiles(this.searchQuery).subscribe(f => {
      this.files = f;
      this.selectedFolder = null;
      this.breadcrumbs = [{ name: `Search: "${this.searchQuery}"` }];
    });
  }

  isPdf(file: any): boolean {
    return file.contentType === 'application/pdf';
  }

  extractInvoice(file: any) {
    this.extractingFileId = file.id;
    this.ecm.extractInvoice(file.id).subscribe({
      next: (result) => {
        this.extractingFileId = null;
        this.activeExtraction = result;
        this.showExtraction = true;
      },
      error: () => {
        this.extractingFileId = null;
      },
    });
  }

  getFileIcon(contentType: string): string {
    if (contentType?.includes('pdf')) return 'pi pi-file-pdf';
    if (contentType?.includes('image')) return 'pi pi-image';
    if (contentType?.includes('spreadsheet') || contentType?.includes('excel')) return 'pi pi-file-excel';
    if (contentType?.includes('word') || contentType?.includes('document')) return 'pi pi-file-word';
    return 'pi pi-file';
  }

  getFileIconColor(contentType: string): string {
    if (contentType?.includes('pdf')) return '#dc2626';
    if (contentType?.includes('image')) return '#7c3aed';
    if (contentType?.includes('spreadsheet') || contentType?.includes('excel')) return '#16a34a';
    if (contentType?.includes('word') || contentType?.includes('document')) return '#1a56db';
    return '#737373';
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }
}
