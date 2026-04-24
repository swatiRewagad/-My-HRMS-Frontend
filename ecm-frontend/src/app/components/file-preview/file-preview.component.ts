import { Component, Input, Output, EventEmitter, OnChanges, SimpleChanges } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DomSanitizer, SafeResourceUrl, SafeHtml } from '@angular/platform-browser';
import { EcmService } from '../../services/ecm.service';

@Component({
  selector: 'app-file-preview',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './file-preview.component.html',
  styleUrl: './file-preview.component.scss',
})
export class FilePreviewComponent implements OnChanges {
  @Input() file: any = null;
  @Input() visible = false;
  @Output() visibleChange = new EventEmitter<boolean>();

  previewUrl: SafeResourceUrl | null = null;
  objectUrl: string | null = null;
  previewType: 'pdf' | 'image' | 'audio' | 'video' | 'excel' | 'word' | 'text' | 'unsupported' = 'unsupported';
  loading = false;
  textContent = '';
  htmlContent: SafeHtml | null = null;

  sheetNames: string[] = [];
  activeSheet = '';
  allRows: string[][] = [];
  sheetData: string[][] = [];
  visibleRows = 100;
  totalRows = 0;
  private workbook: any = null;
  private xlsxUtils: any = null;

  constructor(private ecm: EcmService, private sanitizer: DomSanitizer) {}

  ngOnChanges(changes: SimpleChanges) {
    if (changes['visible'] || changes['file']) {
      if (this.visible && this.file) {
        this.loadPreview();
      } else {
        this.cleanup();
      }
    }
  }

  close() {
    this.visible = false;
    this.visibleChange.emit(false);
    this.cleanup();
  }

  private loadPreview() {
    this.cleanup();
    this.loading = true;
    const ct = (this.file.contentType || '').toLowerCase();
    const name = (this.file.originalName || '').toLowerCase();
    console.log('FILE PREVIEW DEBUG:', { ct, name, file: this.file, isExcel: this.isExcelFile(ct, name) });

    if (ct.includes('pdf')) {
      this.previewType = 'pdf';
      this.loadBlob();
    } else if (ct.includes('image')) {
      this.previewType = 'image';
      this.loadBlob();
    } else if (ct.includes('audio')) {
      this.previewType = 'audio';
      this.loadStreamUrl();
    } else if (ct.includes('video')) {
      this.previewType = 'video';
      this.loadStreamUrl();
    } else if (this.isExcelFile(ct, name)) {
      this.previewType = 'excel';
      this.loadExcel();
    } else if (this.isWordFile(ct, name)) {
      this.previewType = 'word';
      this.loadWord();
    } else if (ct.includes('text') || name.endsWith('.txt') || name.endsWith('.csv') || name.endsWith('.json') || name.endsWith('.xml') || name.endsWith('.log')) {
      this.previewType = 'text';
      this.loadText();
    } else {
      this.previewType = 'unsupported';
      this.loading = false;
    }
  }

  private loadStreamUrl() {
    const url = this.ecm.getStreamUrl(this.file.id);
    this.previewUrl = this.sanitizer.bypassSecurityTrustResourceUrl(url);
    this.loading = false;
  }

  private loadBlob() {
    this.ecm.previewFile(this.file.id).subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        this.previewUrl = this.sanitizer.bypassSecurityTrustResourceUrl(this.objectUrl);
        this.loading = false;
      },
      error: () => {
        this.previewType = 'unsupported';
        this.loading = false;
      },
    });
  }

  private loadText() {
    this.ecm.previewFile(this.file.id).subscribe({
      next: (blob) => {
        blob.text().then(text => {
          this.textContent = text;
          this.loading = false;
        });
      },
      error: () => {
        this.previewType = 'unsupported';
        this.loading = false;
      },
    });
  }

  private loadExcel() {
    this.ecm.previewFile(this.file.id).subscribe({
      next: (blob) => {
        const reader = new FileReader();
        reader.onload = async () => {
          try {
            console.log('EXCEL BLOB SIZE:', blob.size, 'READER RESULT SIZE:', (reader.result as ArrayBuffer).byteLength);
            const XLSX = await import('xlsx');
            this.xlsxUtils = XLSX.utils;
            this.workbook = XLSX.read(reader.result, { type: 'buffer', dense: false });
            this.sheetNames = this.workbook.SheetNames;
            this.activeSheet = this.sheetNames[0];
            this.applySheet(this.activeSheet);
            this.loading = false;
          } catch (err) {
            console.error('EXCEL PARSE ERROR:', err);
            this.previewType = 'unsupported';
            this.loading = false;
          }
        };
        reader.onerror = () => {
          console.error('EXCEL FILEREADER ERROR:', reader.error);
          this.previewType = 'unsupported';
          this.loading = false;
        };
        reader.readAsArrayBuffer(blob);
      },
      error: (err) => {
        console.error('EXCEL DOWNLOAD ERROR:', err);
        this.previewType = 'unsupported';
        this.loading = false;
      },
    });
  }

  private applySheet(sheetName: string) {
    const sheet = this.workbook.Sheets[sheetName];
    this.allRows = this.xlsxUtils.sheet_to_json(sheet, { header: 1, defval: '' }) as string[][];
    this.totalRows = this.allRows.length;
    this.visibleRows = 100;
    this.sheetData = this.allRows.slice(0, this.visibleRows);
  }

  selectSheet(sheetName: string) {
    this.activeSheet = sheetName;
    if (this.workbook) {
      this.applySheet(sheetName);
    }
  }

  loadMoreRows() {
    this.visibleRows += 100;
    this.sheetData = this.allRows.slice(0, this.visibleRows);
  }

  get hasMoreRows(): boolean {
    return this.visibleRows < this.totalRows;
  }

  private loadWord() {
    this.ecm.previewFile(this.file.id).subscribe({
      next: async (blob) => {
        try {
          const buffer = await blob.arrayBuffer();
          const mammothModule = await import('mammoth');
          const mammoth = mammothModule.default || mammothModule;
          const result = await mammoth.convertToHtml({ arrayBuffer: buffer });
          this.htmlContent = this.sanitizer.bypassSecurityTrustHtml(result.value);
          this.loading = false;
        } catch (err) {
          console.error('WORD PARSE ERROR:', err);
          this.previewType = 'unsupported';
          this.loading = false;
        }
      },
      error: (err) => {
        console.error('WORD DOWNLOAD ERROR:', err);
        this.previewType = 'unsupported';
        this.loading = false;
      },
    });
  }

  private isExcelFile(ct: string, name: string): boolean {
    return ct.includes('spreadsheetml') || ct.includes('spreadsheet') || ct.includes('excel')
      || ct === 'application/vnd.ms-excel'
      || name.endsWith('.xls') || name.endsWith('.xlsx') || name.endsWith('.csv');
  }

  private isWordFile(ct: string, name: string): boolean {
    return ct.includes('wordprocessingml') || ct.includes('msword') || ct.includes('word')
      || ct === 'application/vnd.ms-word'
      || name.endsWith('.doc') || name.endsWith('.docx');
  }

  download() {
    this.ecm.downloadFile(this.file.id).subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = this.file.originalName;
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  getFileIcon(): string {
    const ct = (this.file?.contentType || '').toLowerCase();
    if (ct.includes('pdf')) return 'pi pi-file-pdf';
    if (ct.includes('image')) return 'pi pi-image';
    if (ct.includes('audio')) return 'pi pi-volume-up';
    if (ct.includes('video')) return 'pi pi-video';
    if (ct.includes('spreadsheet') || ct.includes('excel') || ct.includes('sheet')) return 'pi pi-file-excel';
    if (ct.includes('word') || ct.includes('document')) return 'pi pi-file-word';
    if (ct.includes('presentation') || ct.includes('powerpoint')) return 'pi pi-file';
    return 'pi pi-file';
  }

  formatSize(bytes: number): string {
    if (!bytes) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / 1048576).toFixed(1) + ' MB';
  }

  private cleanup() {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
    this.previewUrl = null;
    this.textContent = '';
    this.htmlContent = null;
    this.sheetNames = [];
    this.sheetData = [];
    this.allRows = [];
    this.totalRows = 0;
    this.workbook = null;
  }
}
