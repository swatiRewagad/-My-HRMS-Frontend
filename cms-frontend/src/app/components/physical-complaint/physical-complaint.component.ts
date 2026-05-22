import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { OcrService } from '../../services/ocr.service';
import { ComplaintTextProcessorService, ProcessedComplaint } from '../../services/complaint-text-processor.service';
import { ComplaintStoreService } from '../../services/complaint-store.service';
import { CmsService } from '../../services/cms.service';
import { InputSanitizerService } from '../../services/input-sanitizer.service';
import { AiSuggestionsService, PastDecision, RelatedCircular } from '../../services/ai-suggestions.service';
import { MAX_FILE_SIZES } from '../../validators/form-validators';

@Component({
  selector: 'app-physical-complaint',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './physical-complaint.component.html',
  styleUrl: './physical-complaint.component.scss',
})
export class PhysicalComplaintComponent implements OnInit {
  // Info bar
  complaintId = '06846016';
  complaintNumber = 'N20262702300002';
  currentStatus = 'sent';
  statusLabel = 'Sent to Other Reg...';
  complaintOwner = 'Bhupinder Singh';

  // Upload
  uploadedFile: File | null = null;
  imagePreview: string | null = null;
  processing = false;
  processed = false;
  fileError = '';

  // Form
  currentStep: 'creation' | 'assessment' | 'closure' = 'creation';
  formData = {
    subject: '',
    complaintDetails: '',
    comments: '',
    modeOfReceipt: 'Physical Letter',
    receiptDate: '',
  };

  // Submission
  submitting = false;
  submitted = false;
  referenceNumber = '';

  // AI Suggestions
  pastDecisions: PastDecision[] = [];
  relatedCirculars: RelatedCircular[] = [];
  pastDecisionsOpen = true;
  circularsOpen = true;
  aiQuestion = '';
  aiAnswer = '';

  // Detail view
  selectedDecision: PastDecision | null = null;
  selectedComplaintDetail: any = null;

  constructor(
    private ocr: OcrService,
    private textProcessor: ComplaintTextProcessorService,
    private complaintStore: ComplaintStoreService,
    private cms: CmsService,
    private router: Router,
    private sanitizer: InputSanitizerService,
    private aiSuggestions: AiSuggestionsService,
  ) {}

  ngOnInit() {
    this.loadAiSuggestions();
  }

  loadAiSuggestions() {
    this.aiSuggestions.getPastDecisions(
      this.formData.subject,
      this.formData.complaintDetails,
    ).subscribe(d => this.pastDecisions = d);

    this.aiSuggestions.getRelatedCirculars(
      this.formData.subject,
      this.formData.complaintDetails,
    ).subscribe(c => this.relatedCirculars = c);
  }

  refreshSuggestions() {
    this.loadAiSuggestions();
  }

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
      const allowedExts = ['jpg', 'jpeg', 'png', 'gif', 'bmp', 'pdf', 'webp', 'xls', 'xlsx', 'tiff', 'tif'];
      if (!allowedExts.includes(ext)) {
        this.fileError = `File type ".${ext}" not allowed.`;
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
  }

  processOcr() {
    if (!this.uploadedFile) return;
    this.processing = true;

    this.ocr.extractText(this.uploadedFile).subscribe({
      next: (ocrResponse) => {
        const processed: ProcessedComplaint = this.textProcessor.process(ocrResponse.rawText);
        this.formData.subject = processed.fields['complaintCategory'] || processed.fields['subject'] || '';
        this.formData.complaintDetails = processed.fields['facts'] || ocrResponse.rawText || '';
        this.processing = false;
        this.processed = true;
        this.loadAiSuggestions();
      },
      error: () => {
        this.processing = false;
        this.fileError = 'OCR extraction failed. Please try again.';
      },
    });
  }

  submitComplaint() {
    this.submitting = true;

    const payload = {
      complainantName: this.complaintOwner,
      description: this.sanitizer.sanitizeText(this.formData.complaintDetails),
      complaintCategory: this.sanitizer.sanitizeText(this.formData.subject),
      comments: this.sanitizer.sanitizeText(this.formData.comments),
      filingType: 'physical',
    };

    this.cms.fileComplaint(payload).subscribe({
      next: (res) => {
        this.submitting = false;
        this.submitted = true;
        this.referenceNumber = res.complaintNumber || res.id || this.generateRefNumber();
        this.storeLocally();
      },
      error: () => {
        this.submitting = false;
        this.submitted = true;
        this.referenceNumber = this.generateRefNumber();
        this.storeLocally();
      },
    });
  }

  selectDecision(decision: PastDecision) {
    if (this.selectedDecision?.id === decision.id) {
      this.selectedDecision = null;
      this.selectedComplaintDetail = null;
      return;
    }
    this.selectedDecision = decision;

    // Try to fetch from backend by complaint number
    this.cms.trackComplaint(decision.id).subscribe({
      next: (complaint) => {
        this.selectedComplaintDetail = {
          complaintNumber: complaint.complaintNumber,
          complainantName: complaint.complainantName,
          subject: complaint.subject,
          description: complaint.description || 'No description available',
          status: complaint.status,
          filedAt: complaint.filedAt ? new Date(complaint.filedAt).toLocaleDateString('en-IN') : 'N/A',
          bankBranch: complaint.bankBranch || '',
          priority: complaint.priority || '',
          resolutionRemarks: complaint.reliefSought || '',
        };
      },
      error: () => {
        this.selectedComplaintDetail = {
          complaintNumber: decision.id,
          complainantName: decision.title,
          subject: decision.reason,
          description: decision.reason,
          status: decision.outcome,
          filedAt: decision.date || 'N/A',
          bankBranch: '',
          priority: '',
          resolutionRemarks: '',
        };
      },
    });
  }

  askAi() {
    if (!this.aiQuestion.trim()) return;
    this.aiSuggestions.getAiAnswer(this.aiQuestion).subscribe(answer => {
      this.aiAnswer = answer;
    });
  }

  scrollSuggestions() {}

  goBack() {
    this.router.navigate(['/']);
  }

  goToTrack() {
    this.router.navigate(['/track-complaint']);
  }

  resetForm() {
    this.uploadedFile = null;
    this.imagePreview = null;
    this.processing = false;
    this.processed = false;
    this.submitting = false;
    this.submitted = false;
    this.referenceNumber = '';
    this.formData = { subject: '', complaintDetails: '', comments: '', modeOfReceipt: 'Physical Letter', receiptDate: '' };
  }

  private generateRefNumber(): string {
    return 'N' + new Date().toISOString().slice(0, 10).replace(/-/g, '') + String(Math.floor(Math.random() * 900000) + 100000);
  }

  private storeLocally() {
    this.complaintStore.add({
      id: this.referenceNumber,
      complaintAgainst: 'Physical Letter Complaint',
      complaintDate: new Date().toLocaleDateString('en-GB', { day: '2-digit', month: '2-digit', year: 'numeric' }).replace(/\//g, '-'),
      status: 'in_progress',
      statusLabel: 'In Progress',
      comments: this.formData.comments || 'Physical complaint filed',
      action: 'withdraw',
    });
  }
}
