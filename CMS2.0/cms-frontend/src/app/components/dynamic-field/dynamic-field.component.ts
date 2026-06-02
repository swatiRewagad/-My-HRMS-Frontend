import { Component, Input, Output, EventEmitter } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { FormField } from '../../models/form-schema.model';
import { InputSanitizerService } from '../../services/input-sanitizer.service';
import { ALLOWED_MIME_TYPES, FILE_MAGIC_BYTES, MAX_FILE_SIZES } from '../../validators/form-validators';

@Component({
  selector: 'app-dynamic-field',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './dynamic-field.component.html',
  styleUrl: './dynamic-field.component.scss',
})
export class DynamicFieldComponent {
  @Input() field!: FormField;
  @Input() control!: FormControl;
  @Input() resolvedOptions: { label: string; value: any }[] = [];
  @Output() fileSelected = new EventEmitter<{ key: string; files: FileList }>();
  @Output() verifyClicked = new EventEmitter<string>();
  @Output() voiceClicked = new EventEmitter<string>();

  fileError = '';
  todayDate = new Date().toISOString().split('T')[0];

  constructor(private sanitizer: InputSanitizerService) {}

  onFileChange(event: Event) {
    const input = event.target as HTMLInputElement;
    this.fileError = '';

    if (!input.files || input.files.length === 0) return;

    const maxFiles = this.field.multiple ? 10 : 1;
    if (input.files.length > maxFiles) {
      this.fileError = `Maximum ${maxFiles} file(s) allowed`;
      input.value = '';
      return;
    }

    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      const error = this.validateFile(file);
      if (error) {
        this.fileError = error;
        input.value = '';
        return;
      }
    }

    this.verifyFileMagicBytes(input.files[0]).then(valid => {
      if (!valid) {
        this.fileError = 'File content does not match its extension. Possible spoofed file.';
        input.value = '';
        return;
      }
      this.fileSelected.emit({ key: this.field.key, files: input.files! });
      input.value = '';
    });
  }

  private validateFile(file: File): string | null {
    const maxSize = this.field.maxSize || MAX_FILE_SIZES['default'];
    if (file.size > maxSize) {
      return `File "${file.name}" exceeds maximum size of ${(maxSize / (1024 * 1024)).toFixed(0)}MB`;
    }

    if (file.size === 0) {
      return `File "${file.name}" is empty`;
    }

    const ext = this.sanitizer.getFileExtension(file.name);
    if (!ext) {
      return `File "${file.name}" has no extension`;
    }

    const allowedExts = this.getAcceptedExtensions();
    if (allowedExts.length > 0 && !allowedExts.includes(ext)) {
      return `File type ".${ext}" is not allowed. Accepted: ${allowedExts.join(', ')}`;
    }

    if (this.sanitizer.hasDoubleExtension(file.name)) {
      return `File "${file.name}" has a suspicious double extension`;
    }

    if (this.sanitizer.hasPathTraversal(file.name)) {
      return `File name contains invalid path characters`;
    }

    const expectedMime = ALLOWED_MIME_TYPES[ext];
    if (expectedMime && file.type && file.type !== expectedMime) {
      if (!(ext === 'jpg' && file.type === 'image/jpeg') &&
          !(ext === 'csv' && file.type === 'text/plain')) {
        return `File MIME type mismatch: expected ${expectedMime}, got ${file.type}`;
      }
    }

    return null;
  }

  private async verifyFileMagicBytes(file: File): Promise<boolean> {
    const ext = this.sanitizer.getFileExtension(file.name);
    const expectedBytes = FILE_MAGIC_BYTES[ext];
    if (!expectedBytes) return true;

    try {
      const slice = file.slice(0, expectedBytes.length);
      const buffer = await slice.arrayBuffer();
      const bytes = new Uint8Array(buffer);
      return expectedBytes.every((b, i) => bytes[i] === b);
    } catch {
      return true;
    }
  }

  private getAcceptedExtensions(): string[] {
    if (!this.field.accept) return [];
    return this.field.accept
      .split(',')
      .map(s => s.trim().replace(/^\./, '').toLowerCase())
      .filter(s => s.length > 0 && !s.includes('/'));
  }

  onVerify() {
    this.verifyClicked.emit(this.field.key);
  }

  selectRadio(value: any) {
    this.control.setValue(value);
  }

  onNumericKeypress(event: KeyboardEvent) {
    if (!/[0-9]/.test(event.key)) {
      event.preventDefault();
    }
  }

  onNumericInput(event: Event) {
    const input = event.target as HTMLInputElement;
    const cleaned = input.value.replace(/\D/g, '');
    if (cleaned !== input.value) {
      input.value = cleaned;
      this.control.setValue(cleaned);
    }
  }

  onTextInput(event: Event) {
    const input = event.target as HTMLInputElement;
    if (this.sanitizer.hasXssPayload(input.value)) {
      const cleaned = this.sanitizer.sanitizeText(input.value);
      input.value = cleaned;
      this.control.setValue(cleaned);
    }
  }

  onPaste(event: ClipboardEvent) {
    const pasted = event.clipboardData?.getData('text') || '';
    if (this.sanitizer.hasXssPayload(pasted) || this.sanitizer.hasSqlInjectionPattern(pasted)) {
      event.preventDefault();
      const cleaned = this.sanitizer.sanitizeText(pasted);
      document.execCommand('insertText', false, cleaned);
    }
  }
}
