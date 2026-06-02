import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';

interface Attachment {
  id: string;
  name: string;
  size: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
}

interface HistoryEntry {
  id: string;
  timestamp: string;
  action: string;
  performedBy: string;
  role: string;
  remarks: string;
}

@Component({
  selector: 'app-reviewer-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './reviewer-assessment.component.html',
  styleUrl: './reviewer-assessment.component.scss'
})
export class ReviewerAssessmentComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);

  draftId = '';
  loading = signal(true);
  editMode = signal(false);

  currentTab = signal<'summary' | 'email' | 'attachments' | 'history' | 'action'>('summary');

  // ─── Draft Fields (Read-only by default; editable after click "Edit") ───
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantAddress = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';

  modeOfReceipt = '';
  cpgramsReference = '';
  category = '';
  subCategory = '';
  entityName = '';
  entityType = '';
  subject = '';
  description = '';
  amountInvolved: number | null = null;
  transactionDate = '';
  receivedDate = '';
  vernacular = false;
  vernacularLanguage = '';
  emailType = '';
  systemSuggestion = '';

  // DEO Assessment data (read-only for reviewer reference)
  deoDecision = '';
  deoRemarks = '';
  deoNonMaintainableReason = '';
  deoClosureTag = '';
  maintainabilityScore = 0;

  // ─── Attachments ───
  attachments = signal<Attachment[]>([]);

  // ─── Email Communication ───
  emailThread = signal<any[]>([]);

  // ─── History / Audit Trail ───
  history = signal<HistoryEntry[]>([]);

  // ─── Reviewer Actions ───
  reviewerDecision: 'APPROVE' | 'SENT_BACK_TO_DEO' | 'NOT_A_COMPLAINT' | '' = '';
  reviewerRemarks = '';
  savedTemplateId = '';

  // Sent back to DEO
  sentBackToDeoId = '';
  activeDeos = [
    { id: 'DEO-001', name: 'Mr. Ramesh Patil', active: true },
    { id: 'DEO-002', name: 'Ms. Kavitha Nair', active: true },
    { id: 'DEO-003', name: 'Mr. Arjun Singh', active: true },
    { id: 'DEO-004', name: 'Ms. Fatima Begum', active: false },
  ];

  // Not a Complaint disposition
  notComplaintDisposition: 'CLOSED' | 'SENT_TO_OTHER_DEPARTMENT' | 'SUGGESTION' | '' = '';
  targetDepartment = '';
  targetOffice = '';

  // Routing for approved complaints
  targetRegionalOffice = '';
  regionalOffices = [
    { id: 'RBIO-MUM', name: 'RBIO Mumbai' },
    { id: 'RBIO-DEL', name: 'RBIO Delhi' },
    { id: 'RBIO-CHE', name: 'RBIO Chennai' },
    { id: 'RBIO-KOL', name: 'RBIO Kolkata' },
    { id: 'CEPD', name: 'CEPD (Central)' },
    { id: 'CEPC', name: 'CEPC (Central)' },
  ];

  commentTemplates = [
    { id: 'RT1', label: 'Approved - Standard', text: 'Reviewed and approved. All fields verified. Complaint number to be generated.' },
    { id: 'RT2', label: 'Approved - With Edits', text: 'Reviewed and approved with minor corrections to entity/category fields. Complaint progressed.' },
    { id: 'RT3', label: 'Sent Back - Incomplete Fields', text: 'Returning to DEO. Mandatory fields incomplete — please verify state, district and contact details.' },
    { id: 'RT4', label: 'Sent Back - Screening Incomplete', text: 'Auto-closure screening not fully answered. Please complete all screening questions before resubmitting.' },
    { id: 'RT5', label: 'Not a Complaint - Closed', text: 'Finalized as Not a Complaint. No complaint number generated. Record closed.' },
    { id: 'RT6', label: 'Suggestion - Sent to Other Dept', text: 'Closed as Suggestion. Communications sent. Status: Sent to Other Department.' },
  ];

  submitting = signal(false);
  submitted = signal(false);
  generatedComplaintNumber = signal('');

  categories = ['ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'];
  states = [
    'AN', 'AP', 'AR', 'AS', 'BR', 'CH', 'CT', 'DL', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK',
    'KA', 'KL', 'LA', 'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'PB', 'PY', 'RJ',
    'SK', 'TN', 'TS', 'TR', 'UK', 'UP', 'WB'
  ];

  ngOnInit() {
    this.draftId = this.route.snapshot.paramMap.get('id') || '';
    this.loadDraft();
  }

  loadDraft() {
    this.loading.set(true);
    setTimeout(() => {
      this.complainantName = 'Rajesh Kumar';
      this.complainantPhone = '9876543210';
      this.complainantEmail = 'rajesh@example.com';
      this.complainantAddress = '42, MG Road, Mumbai';
      this.complainantState = 'MH';
      this.complainantDistrict = 'Mumbai';
      this.complainantPincode = '400001';

      this.modeOfReceipt = 'EMAIL';
      this.category = 'ATM';
      this.entityName = 'SBI';
      this.entityType = 'BANK';
      this.subject = 'ATM cash not dispensed but account debited';
      this.description = 'On 28-May-2026, I tried to withdraw Rs. 10,000 from SBI ATM at Andheri branch. The machine showed "Transaction Successful" but no cash was dispensed.';
      this.amountInvolved = 10000;
      this.transactionDate = '2026-05-28';
      this.receivedDate = '2026-06-01';
      this.emailType = 'TO';
      this.systemSuggestion = 'MAINTAINABLE';
      this.vernacular = false;

      this.deoDecision = 'MAINTAINABLE';
      this.deoRemarks = 'Complaint meets all criteria for processing under the RBIOS scheme. All mandatory fields verified. Forwarding to Reviewer for action.';
      this.maintainabilityScore = 87;

      this.attachments.set([
        { id: 'ATT-001', name: 'original_email.eml', size: '156 KB', type: 'message/rfc822', uploadedAt: '2026-06-01T10:00:00Z', uploadedBy: 'SYSTEM' },
        { id: 'ATT-002', name: 'mini_statement.jpg', size: '450 KB', type: 'image/jpeg', uploadedAt: '2026-06-01T10:00:00Z', uploadedBy: 'SYSTEM' },
      ]);

      this.emailThread.set([
        { id: 'E1', direction: 'RECEIVED', from: 'rajesh@example.com', to: 'crpc@rbi.org.in', subject: 'ATM cash not dispensed', sentAt: '2026-05-29T14:30:00Z', body: 'Dear Sir/Madam, I am writing to report...' },
      ]);

      this.history.set([
        { id: 'H1', timestamp: '2026-06-01T10:00:00Z', action: 'DRAFT_CREATED', performedBy: 'SYSTEM', role: 'SYSTEM', remarks: 'Email ingested and draft created automatically.' },
        { id: 'H2', timestamp: '2026-06-01T10:05:00Z', action: 'ASSIGNED_TO_DEO', performedBy: 'SYSTEM', role: 'SYSTEM', remarks: 'Assigned to DEO-001 via Round Robin.' },
        { id: 'H3', timestamp: '2026-06-01T14:30:00Z', action: 'ASSESSMENT_COMPLETED', performedBy: 'DEO-001 (Ramesh Patil)', role: 'DEO', remarks: 'Marked as Maintainable. Screening complete.' },
        { id: 'H4', timestamp: '2026-06-01T14:31:00Z', action: 'SENT_TO_REVIEWER', performedBy: 'DEO-001 (Ramesh Patil)', role: 'DEO', remarks: 'Routed to REV-002 via Round Robin.' },
      ]);

      this.loading.set(false);
    }, 800);
  }

  toggleEditMode() {
    this.editMode.set(!this.editMode());
  }

  applyTemplate(templateId: string) {
    const tmpl = this.commentTemplates.find(t => t.id === templateId);
    if (tmpl) {
      this.reviewerRemarks = tmpl.text;
      this.savedTemplateId = templateId;
    }
  }

  canSubmit(): boolean {
    if (!this.reviewerDecision) return false;
    if (!this.reviewerRemarks.trim()) return false;
    if (this.reviewerDecision === 'SENT_BACK_TO_DEO' && !this.sentBackToDeoId) return false;
    if (this.reviewerDecision === 'NOT_A_COMPLAINT' && !this.notComplaintDisposition) return false;
    if (this.reviewerDecision === 'APPROVE' && !this.targetRegionalOffice) return false;
    return true;
  }

  submitDecision() {
    if (!this.canSubmit()) return;
    this.submitting.set(true);

    setTimeout(() => {
      if (this.reviewerDecision === 'APPROVE') {
        const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
        const rand = Math.floor(100000 + Math.random() * 900000);
        this.generatedComplaintNumber.set(`CMP-${dateStr}-${rand}`);
      }
      this.submitting.set(false);
      this.submitted.set(true);
    }, 1500);
  }

  goBack() {
    this.router.navigate(['/crpc/reviewer']);
  }
}
