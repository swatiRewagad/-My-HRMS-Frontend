import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { EcmService } from '../../services/ecm.service';
import { FilePreviewComponent } from '../file-preview/file-preview.component';

@Component({
  selector: 'app-shared-files',
  standalone: true,
  imports: [CommonModule, FilePreviewComponent],
  templateUrl: './shared-files.component.html',
  styleUrl: './shared-files.component.scss',
})
export class SharedFilesComponent implements OnInit {
  sharedFiles: any[] = [];
  showPreview = false;
  previewFile: any = null;

  constructor(private ecm: EcmService) {}

  ngOnInit() {
    this.ecm.getSharedWithMe().subscribe(f => this.sharedFiles = f);
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

  getFileIcon(contentType: string): string {
    if (contentType?.includes('pdf')) return 'pi pi-file-pdf';
    if (contentType?.includes('image')) return 'pi pi-image';
    if (contentType?.includes('spreadsheet') || contentType?.includes('excel')) return 'pi pi-file-excel';
    return 'pi pi-file';
  }

  getFileIconColor(contentType: string): string {
    if (contentType?.includes('pdf')) return '#dc2626';
    if (contentType?.includes('image')) return '#7c3aed';
    if (contentType?.includes('spreadsheet') || contentType?.includes('excel')) return '#16a34a';
    return '#737373';
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }
}
