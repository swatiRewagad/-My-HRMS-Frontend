import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OcrService } from '../../services/ocr.service';
import { ComplaintTextProcessorService, ProcessedComplaint } from '../../services/complaint-text-processor.service';
import { ComplaintStoreService } from '../../services/complaint-store.service';
import { CmsService } from '../../services/cms.service';
import { InputSanitizerService } from '../../services/input-sanitizer.service';
import { MAX_FILE_SIZES } from '../../validators/form-validators';

interface OcrResult {
  rawText: string;
  language: string;
  confidence: number;
  fields: Record<string, string>;
}

@Component({
  selector: 'app-physical-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './physical-complaint.component.html',
  styleUrl: './physical-complaint.component.scss',
})
export class PhysicalComplaintComponent {
  uploadedFile: File | null = null;
  imagePreview: string | null = null;
  processing = false;
  processed = false;
  ocrResult: OcrResult | null = null;
  submitting = false;
  submitted = false;
  referenceNumber = '';

  fileError = '';

  constructor(
    private ocr: OcrService,
    private textProcessor: ComplaintTextProcessorService,
    private complaintStore: ComplaintStoreService,
    private cms: CmsService,
    private router: Router,
    private sanitizer: InputSanitizerService,
  ) {}

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    this.fileError = '';
    if (input.files && input.files[0]) {
      const file = input.files[0];

      if (file.size > MAX_FILE_SIZES['image']) {
        this.fileError = `File exceeds maximum size of ${MAX_FILE_SIZES['image'] / (1024 * 1024)}MB`;
        input.value = '';
        return;
      }

      if (file.size === 0) {
        this.fileError = 'File is empty';
        input.value = '';
        return;
      }

      const ext = this.sanitizer.getFileExtension(file.name);
      const allowedExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'pdf', 'webp'];
      if (!allowedExts.includes(ext)) {
        this.fileError = `File type ".${ext}" not allowed. Accepted: ${allowedExts.join(', ')}`;
        input.value = '';
        return;
      }

      if (this.sanitizer.hasDoubleExtension(file.name)) {
        this.fileError = 'File has suspicious double extension';
        input.value = '';
        return;
      }

      this.uploadedFile = file;
      this.processed = false;
      this.ocrResult = null;

      const reader = new FileReader();
      reader.onload = (e) => {
        this.imagePreview = e.target?.result as string;
      };
      reader.readAsDataURL(this.uploadedFile);
    }
  }

  removeFile() {
    this.uploadedFile = null;
    this.imagePreview = null;
    this.processed = false;
    this.ocrResult = null;
  }

  processOcr() {
    if (!this.uploadedFile) return;
    this.processing = true;

    this.ocr.extractText(this.uploadedFile).subscribe(ocrResponse => {
      const processed: ProcessedComplaint = this.textProcessor.process(ocrResponse.rawText);

      this.ocrResult = {
        rawText: processed.rawText,
        language: processed.language,
        confidence: ocrResponse.confidence,
        fields: processed.fields,
      };

      this.processing = false;
      this.processed = true;
    });
  }

  updateField(key: string, event: Event) {
    if (!this.ocrResult) return;
    const rawValue = (event.target as HTMLInputElement).value;
    this.ocrResult.fields[key] = this.sanitizer.sanitizeText(rawValue);
  }

  submitComplaint() {
    if (!this.ocrResult) return;
    this.submitting = true;

    const fields = this.ocrResult.fields;
    const payload = {
      complainantName: this.sanitizer.sanitizeText(fields['name'] || ''),
      complainantPhone: this.sanitizer.sanitizeNumeric(fields['mobileNumber'] || ''),
      complainantEmail: this.sanitizer.sanitizeText(fields['email'] || ''),
      complainantAddress: this.sanitizer.sanitizeText(fields['address'] || ''),
      bankName: this.sanitizer.sanitizeText(fields['bankName'] || ''),
      bankBranch: this.sanitizer.sanitizeText(fields['branch'] || ''),
      accountNumber: this.sanitizer.sanitizeAlphanumeric(fields['accountNumber'] || '', 20),
      state: this.sanitizer.sanitizeAlphanumeric(fields['state'] || '', 50),
      district: this.sanitizer.sanitizeAlphanumeric(fields['district'] || '', 50),
      pincode: this.sanitizer.sanitizeNumeric(fields['pincode'] || '').substring(0, 6),
      complaintCategory: this.sanitizer.sanitizeText(fields['complaintCategory'] || ''),
      description: this.sanitizer.sanitizeText(fields['facts'] || ''),
      disputeAmount: this.sanitizer.sanitizeNumeric((fields['disputeAmount'] || '').replace(/[₹,\s]/g, '')),
      filingType: 'physical',
    };

    this.cms.fileComplaint(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        this.submitted = true;
        this.referenceNumber = res.complaintNumber || res.id || this.generateRefNumber();
        this.storeLocally(fields);
      },
      error: () => {
        this.submitting = false;
        this.submitted = true;
        this.referenceNumber = this.generateRefNumber();
        this.storeLocally(fields);
      },
    });
  }

  private generateRefNumber(): string {
    return 'CMS-' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + '-' + String(Math.floor(Math.random() * 900000) + 100000);
  }

  private storeLocally(fields: Record<string, string>) {
    this.complaintStore.add({
      id: this.referenceNumber,
      complaintAgainst: fields['bankName'] || 'Unknown Bank',
      complaintDate: new Date().toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-'),
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: 'Physical complaint filed',
      action: 'withdraw',
      details: fields,
    });
  }

  goToTrack() {
    this.router.navigate(['/track-complaint']);
  }

  resetForm() {
    this.uploadedFile = null;
    this.imagePreview = null;
    this.processing = false;
    this.processed = false;
    this.ocrResult = null;
    this.submitting = false;
    this.submitted = false;
    this.referenceNumber = '';
  }
}
