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
  selectedFolderId = 0;
  uploadItems: FileUploadItem[] = [];
  uploading = false;
  dragOver = false;

  constructor(private ecm: EcmService, private router: Router) {}

  ngOnInit() {
    this.ecm.getRootFolders().subscribe(f => this.folders = f);
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
    for (const file of files) {
      this.uploadItems.push({ file, progress: 0, status: 'pending' });
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

  goToFiles() {
    this.router.navigate(['/files']);
  }
}
