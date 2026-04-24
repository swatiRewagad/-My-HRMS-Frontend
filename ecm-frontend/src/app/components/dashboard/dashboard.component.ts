import { Component, OnInit } from '@angular/core';
import { CommonModule, DecimalPipe } from '@angular/common';
import { EcmService } from '../../services/ecm.service';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, DecimalPipe],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent implements OnInit {
  stats: any = { totalFiles: 0, totalFolders: 0, publicFolders: 0, privateFolders: 0, totalShares: 0, totalUsers: 0 };
  storage: any = { usedFormatted: '0 B', usedPercent: 0 };
  storageBreakdown: { type: string; count: number; sizeBytes: number }[] = [];
  recentUploads: any[] = [];
  activities: any[] = [];
  folderSummary: any = { publicCount: 0, privateCount: 0, publicFiles: 0, privateFiles: 0 };

  constructor(private ecm: EcmService) {}

  ngOnInit() {
    this.ecm.getDashboard().subscribe({
      next: (data) => {
        this.stats = data.stats || this.stats;
        this.storage = data.storage || this.storage;
        this.storageBreakdown = (data.fileTypeBreakdown || []).map((b: any) => ({
          type: this.friendlyType(b.type),
          count: b.count,
          sizeBytes: b.sizeBytes,
        }));
        this.recentUploads = data.recentUploads || [];
        this.activities = (data.recentActivity || []).map((a: any) => ({
          action: a.action,
          entityType: a.entityType,
          entityName: a.entityName,
          userName: a.userName,
        }));
        this.folderSummary = data.folderSummary || this.folderSummary;
      },
      error: () => this.loadFallback(),
    });
  }

  friendlyType(mime: string): string {
    if (mime?.includes('pdf')) return 'PDF';
    if (mime?.includes('word') || mime?.includes('document')) return 'Documents';
    if (mime?.includes('spreadsheet') || mime?.includes('sheet')) return 'Spreadsheets';
    if (mime?.includes('presentation')) return 'Presentations';
    if (mime?.includes('image')) return 'Images';
    return 'Other';
  }

  loadFallback() {
    this.stats = { totalFiles: 47, totalFolders: 8, publicFolders: 3, privateFolders: 5, totalShares: 12, totalUsers: 5 };
    this.storage = { usedFormatted: '256 MB', capacityFormatted: '10.00 GB', usedPercent: 2.5 };
    this.storageBreakdown = [
      { type: 'PDF', count: 15, sizeBytes: 89128960 },
      { type: 'Images', count: 12, sizeBytes: 67108864 },
      { type: 'Documents', count: 10, sizeBytes: 54525952 },
      { type: 'Spreadsheets', count: 6, sizeBytes: 34603008 },
      { type: 'Other', count: 4, sizeBytes: 23068672 },
    ];
    this.recentUploads = [
      { id: 1, name: 'Q4-Report.pdf', contentType: 'application/pdf', size: 2400000, folderName: 'Reports' },
      { id: 2, name: 'Team-Photo.jpg', contentType: 'image/jpeg', size: 5200000, folderName: 'Media' },
      { id: 3, name: 'Budget-2026.xlsx', contentType: 'application/vnd.ms-excel', size: 1800000, folderName: 'Finance' },
    ];
    this.activities = [
      { action: 'File uploaded', entityType: 'FILE', entityName: 'Q4-Report.pdf', userName: 'John Doe' },
      { action: 'Folder created', entityType: 'FOLDER', entityName: 'Archives', userName: 'Jane Smith' },
      { action: 'File shared', entityType: 'FILE', entityName: 'Proposal.docx', userName: 'John Doe' },
    ];
    this.folderSummary = { publicCount: 3, privateCount: 5, publicFiles: 25, privateFiles: 22 };
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
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  getActionIcon(action: string): string {
    const a = action?.toLowerCase() || '';
    if (a.includes('upload')) return 'pi pi-upload';
    if (a.includes('creat')) return 'pi pi-plus-circle';
    if (a.includes('shar')) return 'pi pi-share-alt';
    if (a.includes('delet')) return 'pi pi-trash';
    if (a.includes('download')) return 'pi pi-download';
    if (a.includes('access')) return 'pi pi-users';
    return 'pi pi-info-circle';
  }

  getTypeBarWidth(count: number): string {
    const max = Math.max(...this.storageBreakdown.map(s => s.count));
    return max > 0 ? ((count / max) * 100) + '%' : '0%';
  }

  getTypeColor(index: number): string {
    const colors = ['#dc2626', '#7c3aed', '#1a56db', '#16a34a', '#d97706'];
    return colors[index % colors.length];
  }
}
