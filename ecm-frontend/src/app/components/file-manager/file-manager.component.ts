import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { EcmService } from '../../services/ecm.service';
import { KeycloakService } from '../../services/keycloak.service';
import { environment } from '../../../environments/environment';
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
  expandedFolders: Set<number> = new Set();
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
  selectedAccessUserIds: Set<number> = new Set();

  showShare = false;
  shareFileId = 0;
  shareType = 'user';
  shareUserId = 0;
  sharePermission = 'read';
  shareExpiry = 24;
  generatedShareLink = '';
  linkCopied = false;

  showPreview = false;
  previewFile: any = null;

  showExtraction = false;
  activeExtraction: any = null;
  extractingFileId: number | null = null;

  showConfirm = false;
  confirmMessage = '';
  confirmAction: (() => void) | null = null;

  constructor(private ecm: EcmService, private keycloak: KeycloakService) {}

  get availableUsers(): any[] {
    const currentUsername = this.keycloak.username;
    return this.users.filter(u => u.username !== currentUsername);
  }

  ngOnInit() {
    this.loadFolders();
    this.ecm.getUsers().subscribe(u => this.users = u);
  }

  loadFolders() {
    this.ecm.getRootFolders().subscribe(f => this.folders = f);
  }

  toggleFolder(folder: any, event: Event) {
    event.stopPropagation();
    if (this.expandedFolders.has(folder.id)) {
      this.expandedFolders.delete(folder.id);
    } else {
      this.expandedFolders.add(folder.id);
    }
  }

  selectFolder(folder: any) {
    this.selectedFolder = folder;
    this.expandedFolders.add(folder.id);
    this.buildBreadcrumbs(folder);
    this.ecm.getFolder(folder.id).subscribe(f => this.selectedFolder = f);
    this.ecm.getFilesByFolder(folder.id).subscribe(f => this.files = f);
  }

  buildBreadcrumbs(folder: any) {
    this.breadcrumbs = [folder];
    let current = this.findParentFolder(folder.parentId, this.folders);
    while (current) {
      this.breadcrumbs.unshift(current);
      current = this.findParentFolder(current.parentId, this.folders);
    }
  }

  private findParentFolder(parentId: number | null, folders: any[]): any {
    if (!parentId) return null;
    for (const f of folders) {
      if (f.id === parentId) return f;
      if (f.children?.length) {
        const found = this.findParentFolder(parentId, f.children);
        if (found) return found;
      }
    }
    return null;
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

  deleteFolder(folder: any, event: Event) {
    event.stopPropagation();
    this.openConfirm(`Delete folder "${folder.name}" and all its contents?`, () => {
      this.ecm.deleteFolder(folder.id).subscribe(() => {
        if (this.selectedFolder?.id === folder.id) {
          this.selectedFolder = null;
          this.files = [];
          this.breadcrumbs = [];
        }
        this.loadFolders();
      });
    });
  }

  isOwner(folder: any): boolean {
    return folder && String(this.ecm.currentUserId) !== '-1' && String(this.ecm.currentUserId) === String(folder.ownerId);
  }

  canWrite(folder: any): boolean {
    if (!folder) return false;
    if (this.isOwner(folder)) return true;
    const access = folder.accessList?.find((a: any) => String(a.userId) === String(this.ecm.currentUserId));
    return access?.permission === 'read-write' || access?.permission === 'write';
  }

  canManageAccess(folder: any): boolean {
    return this.isOwner(folder) && folder?.visibility === 'public';
  }

  isReadOnly(folder: any): boolean {
    if (!folder) return true;
    if (this.isOwner(folder)) return false;
    const access = folder.accessList?.find((a: any) => String(a.userId) === String(this.ecm.currentUserId));
    if (access?.permission === 'read-write' || access?.permission === 'write') return false;
    return true;
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
    this.openConfirm(`Delete file "${file.originalName}"? This cannot be undone.`, () => {
      this.ecm.deleteFile(file.id).subscribe(() => {
        this.files = this.files.filter(f => f.id !== file.id);
      });
    });
  }

  openAccess() {
    this.showAccess = true;
    this.accessUserId = 0;
    this.accessPermission = 'read';
    this.selectedAccessUserIds = new Set();
    if (this.selectedFolder?.accessList?.length) {
      for (const a of this.selectedFolder.accessList) {
        this.selectedAccessUserIds.add(a.userId);
      }
    }
  }

  toggleAccessUser(userId: number) {
    if (this.selectedAccessUserIds.has(userId)) {
      this.selectedAccessUserIds.delete(userId);
    } else {
      this.selectedAccessUserIds.add(userId);
    }
  }

  grantAccess() {
    if (!this.selectedFolder || !this.accessUserId) return;
    this.ecm.grantAccess(this.selectedFolder.id, this.accessUserId, this.accessPermission).subscribe(() => {
      this.showAccess = false;
      this.ecm.getFolder(this.selectedFolder.id).subscribe(f => this.selectedFolder = f);
    });
  }

  grantBulkAccess() {
    if (!this.selectedFolder) return;
    const existingUserIds: number[] = (this.selectedFolder.accessList || []).map((a: any) => a.userId as number);
    const revokeIds = existingUserIds.filter(id => !this.selectedAccessUserIds.has(id));

    const requests = Array.from(this.selectedAccessUserIds).map(userId => ({
      userId,
      permission: this.accessPermission,
    }));

    const revokes = revokeIds.map(uid => this.ecm.revokeAccess(this.selectedFolder.id, uid));

    if (requests.length > 0) {
      this.ecm.grantBulkAccess(this.selectedFolder.id, requests).subscribe(() => {
        revokes.forEach(r$ => r$.subscribe());
        this.showAccess = false;
        this.ecm.getFolder(this.selectedFolder.id).subscribe(f => this.selectedFolder = f);
        this.loadFolders();
      });
    } else if (revokes.length > 0) {
      revokes.forEach((r$, i) => r$.subscribe(() => {
        if (i === revokes.length - 1) {
          this.showAccess = false;
          this.ecm.getFolder(this.selectedFolder.id).subscribe(f => this.selectedFolder = f);
          this.loadFolders();
        }
      }));
    } else {
      this.showAccess = false;
    }
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
    this.generatedShareLink = '';
    this.linkCopied = false;
  }

  shareFile() {
    const data: any = { fileId: this.shareFileId, shareType: this.shareType, permission: this.sharePermission };
    if (this.shareType === 'user') data.sharedWith = Number(this.shareUserId);
    if (this.shareType === 'link') data.expiresInHours = this.shareExpiry;
    this.ecm.shareFile(data).subscribe((resp: any) => {
      if (this.shareType === 'link' && resp?.shareToken) {
        this.generatedShareLink = `${environment.apiUrl}/files/shared/${resp.shareToken}`;
        this.linkCopied = false;
      } else {
        this.showShare = false;
      }
    });
  }

  copyShareLink() {
    navigator.clipboard.writeText(this.generatedShareLink).then(() => {
      this.linkCopied = true;
      setTimeout(() => this.linkCopied = false, 3000);
    });
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

  openConfirm(message: string, action: () => void) {
    this.confirmMessage = message;
    this.confirmAction = action;
    this.showConfirm = true;
  }

  executeConfirm() {
    this.showConfirm = false;
    this.confirmAction?.();
    this.confirmAction = null;
  }

  cancelConfirm() {
    this.showConfirm = false;
    this.confirmAction = null;
  }
}
