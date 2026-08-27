import { Component, Input, Output, EventEmitter, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  validateFile,
  validateFileSet,
  validateStepQuota,
  MAX_FILE_SIZE_MB,
  STEP_QUOTA_MB,
  MAX_FILE_COUNT,
  StepQuotaKey,
} from '../../utils/file-validator';

export interface UploadedFileInfo {
  file: File;
  name: string;
  url: string;
  size: number;
}

let nextId = 0;

@Component({
  selector: 'app-file-upload',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './file-upload.component.html',
  styleUrl: './file-upload.component.scss',
})
export class FileUploadComponent {
  @Input() multiple = true;
  @Input() accept = '.pdf,.doc,.jpg';
  @Input() maxFiles = MAX_FILE_COUNT;
  @Input() label = 'Upload documents relevant to the complaint';
  @Input() hint = 'Supported formats: PDF, DOC, JPG';
  @Input() sizeHint = `Max ${MAX_FILE_SIZE_MB}MB per file`;
  @Input() stepQuota: StepQuotaKey | null = null;
  @Input() externalBytesUsed = 0;

  @Output() filesChanged = new EventEmitter<File[]>();
  @Output() fileRemoved = new EventEmitter<number>();

  files: UploadedFileInfo[] = [];
  errorMessage = signal('');
  isDragOver = false;
  inputId = `file-upload-${nextId++}`;

  get quotaLimitMB(): number | null {
    return this.stepQuota ? STEP_QUOTA_MB[this.stepQuota] : null;
  }

  onFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    this.addFiles(Array.from(input.files));
    input.value = '';
  }

  onDragOver(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = true;
  }

  onDragLeave(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
  }

  onFileDrop(event: DragEvent): void {
    event.preventDefault();
    event.stopPropagation();
    this.isDragOver = false;
    const droppedFiles = event.dataTransfer?.files;
    if (!droppedFiles?.length) return;
    this.addFiles(Array.from(droppedFiles));
  }

  removeFile(index: number): void {
    const removed = this.files.splice(index, 1);
    if (removed[0]?.url) {
      URL.revokeObjectURL(removed[0].url);
    }
    this.errorMessage.set('');
    this.fileRemoved.emit(index);
    this.filesChanged.emit(this.files.map(f => f.file));
  }

  previewFile(index: number): void {
    const fileInfo = this.files[index];
    if (fileInfo?.url) {
      window.open(fileInfo.url, '_blank');
    }
  }

  private addFiles(newFiles: File[]): void {
    this.errorMessage.set('');

    if (!this.multiple && newFiles.length > 1) {
      newFiles = [newFiles[0]];
    }

    if (!this.multiple && this.files.length > 0) {
      this.files.forEach(f => URL.revokeObjectURL(f.url));
      this.files = [];
    }

    const setResult = validateFileSet(newFiles, this.files.length, this.maxFiles);
    if (!setResult.valid) {
      this.errorMessage.set(setResult.error!);
      return;
    }

    for (const file of newFiles) {
      const result = validateFile(file);
      if (!result.valid) {
        this.errorMessage.set(result.error!);
        return;
      }
    }

    if (this.stepQuota) {
      const existingInComponent = this.files.reduce((sum, f) => sum + f.size, 0);
      const totalExisting = existingInComponent + this.externalBytesUsed;
      const quotaResult = validateStepQuota(newFiles, totalExisting, this.stepQuota);
      if (!quotaResult.valid) {
        this.errorMessage.set(quotaResult.error!);
        return;
      }
    }

    for (const file of newFiles) {
      this.files.push({
        file,
        name: file.name,
        url: URL.createObjectURL(file),
        size: file.size,
      });
    }

    this.filesChanged.emit(this.files.map(f => f.file));
  }

  formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }
}
