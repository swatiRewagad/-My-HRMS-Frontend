import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

interface ScreeningQuestion {
  id: string;
  question: string;
  answer: 'YES' | 'NO' | null;
  autoCloseIfYes: boolean;
}

@Component({
  selector: 'app-physical-letter',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './physical-letter.component.html',
  styleUrl: './physical-letter.component.scss'
})
export class PhysicalLetterComponent implements OnInit {

  private router = inject(Router);

  currentStep = signal(1);
  totalSteps = 4;

  // Step 1: Scan/Upload
  scannedFile: File | null = null;
  scanError = '';
  ocrInProgress = signal(false);
  ocrComplete = signal(false);

  // Step 2: Complainant Details (OCR-prefilled, editable)
  complainantName = '';
  complainantAddress = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';
  complainantPhone = '';
  complainantEmail = '';
  vernacular = false;
  vernacularLanguage = '';

  // Step 3: Complaint Details
  category = '';
  subCategory = '';
  entityName = '';
  entityType = 'BANK';
  subject = '';
  description = '';
  amountInvolved: number | null = null;
  transactionDate = '';
  letterDate = '';
  receivedDate = '';

  // Step 4: Auto-closure Screening
  screeningQuestions: ScreeningQuestion[] = [
    { id: 'Q1', question: 'Is the complaint addressed to a specific Regulated Entity (RE)?', answer: null, autoCloseIfYes: false },
    { id: 'Q2', question: 'Has the complainant already approached the concerned RE and received a final response?', answer: null, autoCloseIfYes: false },
    { id: 'Q3', question: 'Is the complaint older than 1 year from the date of cause of action?', answer: null, autoCloseIfYes: true },
    { id: 'Q4', question: 'Is the matter already sub-judice or decided by any court/forum?', answer: null, autoCloseIfYes: true },
    { id: 'Q5', question: 'Is the complaint regarding service charges/interest rates which are within the purview of the RE?', answer: null, autoCloseIfYes: true },
    { id: 'Q6', question: 'Is the complaint anonymous (no name/address of complainant)?', answer: null, autoCloseIfYes: true },
  ];

  autoCloseTriggered = signal(false);
  autoCloseReason = signal('');

  // Submission
  submitting = signal(false);
  submitted = signal(false);
  draftId = signal('');

  categories = [
    'ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'
  ];

  states = [
    'AN', 'AP', 'AR', 'AS', 'BR', 'CH', 'CT', 'DL', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK',
    'KA', 'KL', 'LA', 'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'PB', 'PY', 'RJ',
    'SK', 'TN', 'TS', 'TR', 'UK', 'UP', 'WB'
  ];

  ngOnInit() {
    this.receivedDate = new Date().toISOString().split('T')[0];
  }

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff'];

    if (!allowed.includes(file.type)) {
      this.scanError = 'Only PDF, JPEG, PNG, or TIFF files are accepted.';
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      this.scanError = 'File size must not exceed 10 MB.';
      return;
    }

    this.scannedFile = file;
    this.scanError = '';
  }

  runOcr() {
    if (!this.scannedFile) return;
    this.ocrInProgress.set(true);

    setTimeout(() => {
      this.complainantName = 'Suresh Patel';
      this.complainantAddress = '12, Nehru Nagar, Sector 5, Jaipur';
      this.complainantState = 'RJ';
      this.complainantDistrict = 'Jaipur';
      this.complainantPincode = '302001';
      this.complainantPhone = '9412345678';
      this.ocrInProgress.set(false);
      this.ocrComplete.set(true);
    }, 2000);
  }

  skipOcr() {
    this.ocrComplete.set(true);
  }

  nextStep() {
    if (this.currentStep() < this.totalSteps) {
      this.currentStep.set(this.currentStep() + 1);
    }
  }

  prevStep() {
    if (this.currentStep() > 1) {
      this.currentStep.set(this.currentStep() - 1);
    }
  }

  canProceedStep1(): boolean {
    return this.scannedFile !== null;
  }

  canProceedStep2(): boolean {
    return this.complainantName.trim().length > 0 &&
           this.complainantState.trim().length > 0;
  }

  canProceedStep3(): boolean {
    return this.category.trim().length > 0 &&
           this.entityName.trim().length > 0 &&
           this.subject.trim().length > 0;
  }

  onScreeningAnswerChange() {
    const triggered = this.screeningQuestions.find(
      q => q.autoCloseIfYes && q.answer === 'YES'
    );
    if (triggered) {
      this.autoCloseTriggered.set(true);
      this.autoCloseReason.set(triggered.question);
    } else {
      this.autoCloseTriggered.set(false);
      this.autoCloseReason.set('');
    }
  }

  allScreeningAnswered(): boolean {
    return this.screeningQuestions.every(q => q.answer !== null);
  }

  submitDraft() {
    this.submitting.set(true);

    setTimeout(() => {
      const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
      const rand = Math.floor(100000 + Math.random() * 900000);
      const newDraftId = `DRF-${dateStr}-${rand}`;
      this.draftId.set(newDraftId);
      this.submitting.set(false);
      this.submitted.set(true);

      sessionStorage.setItem('physicalLetterDraft', JSON.stringify({
        complainantName: this.complainantName,
        complainantPhone: this.complainantPhone,
        complainantEmail: this.complainantEmail,
        complainantAddress: this.complainantAddress,
        complainantState: this.complainantState,
        complainantDistrict: this.complainantDistrict,
        complainantPincode: this.complainantPincode,
        vernacular: this.vernacular,
        vernacularLanguage: this.vernacularLanguage,
        category: this.category,
        entityName: this.entityName,
        entityType: this.entityType,
        subject: this.subject,
        description: this.description,
        amountInvolved: this.amountInvolved,
        transactionDate: this.transactionDate,
        letterDate: this.letterDate,
        receivedDate: this.receivedDate,
        fileName: this.scannedFile?.name || 'scanned_letter.pdf',
        fileSize: this.scannedFile ? (this.scannedFile.size / 1024 / 1024).toFixed(2) + ' MB' : '2.4 MB',
      }));

      setTimeout(() => {
        this.router.navigate(['/crpc/draft', newDraftId]);
      }, 2000);
    }, 1500);
  }

  goBack() {
    this.router.navigate(['/crpc/home']);
  }

  openDraft() {
    this.router.navigate(['/crpc/draft', this.draftId()]);
  }
}
