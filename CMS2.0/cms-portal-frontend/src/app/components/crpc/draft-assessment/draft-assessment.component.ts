import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { CrpcService } from '../../../services/crpc.service';
import { ReviewerUser } from '../../../models/crpc.model';

interface MaintainabilityQuestion {
  id: string;
  question: string;
  answer: 'YES' | 'NO' | 'NA' | null;
  weight: number;
}

interface Attachment {
  id: string;
  name: string;
  size: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
}

interface EmailCorrespondence {
  id: string;
  direction: 'SENT' | 'RECEIVED';
  subject: string;
  to: string;
  sentAt: string;
  body: string;
}

@Component({
  selector: 'app-draft-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './draft-assessment.component.html',
  styleUrl: './draft-assessment.component.scss'
})
export class DraftAssessmentComponent implements OnInit {

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private crpcService = inject(CrpcService);

  draftId = '';
  loading = signal(true);

  currentTab = signal<'details' | 'attachments' | 'assessment' | 'screening' | 'route'>('details');

  // ─── Editable Draft Fields (DEO can modify) ───
  complainantName = '';
  complainantPhone = '';
  complainantEmail = '';
  complainantAddress = '';
  complainantState = '';
  complainantDistrict = '';
  complainantPincode = '';
  contactPreference: 'EMAIL' | 'PHONE' | 'POST' = 'EMAIL';

  modeOfReceipt: 'EMAIL' | 'PHYSICAL_LETTER' | 'PORTAL' | 'CPGRAMS' = 'EMAIL';
  cpgramsReference = '';
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
  vernacular = false;
  vernacularLanguage = '';
  emailType: 'TO' | 'CC_BCC' | '' = '';
  systemSuggestion: 'MAINTAINABLE' | 'NON_MAINTAINABLE' | 'PENDING' = 'PENDING';

  // ─── Attachments ───
  attachments = signal<Attachment[]>([]);
  uploadError = '';

  // ─── Email Correspondence (Request Additional Info) ───
  emailCorrespondence = signal<EmailCorrespondence[]>([]);
  showEmailComposer = signal(false);
  emailTo = '';
  emailSubject = '';
  emailBody = '';
  sendingEmail = signal(false);
  attachFormPdf = false;
  formPdfFields = [
    { key: 'name', label: 'Full Name', include: true },
    { key: 'phone', label: 'Phone Number', include: true },
    { key: 'email', label: 'Email Address', include: true },
    { key: 'address', label: 'Complete Address', include: true },
    { key: 'state', label: 'State', include: false },
    { key: 'district', label: 'District', include: false },
    { key: 'pincode', label: 'Pincode', include: false },
    { key: 'accountNumber', label: 'Account Number', include: false },
    { key: 'transactionId', label: 'Transaction ID / Reference', include: true },
    { key: 'transactionDate', label: 'Transaction Date', include: true },
    { key: 'amount', label: 'Amount Involved (₹)', include: true },
    { key: 'branchName', label: 'Branch Name', include: false },
    { key: 'description', label: 'Detailed Description', include: true },
    { key: 'supportingDocs', label: 'List of Supporting Documents', include: false },
  ];

  // ─── Maintainability Assessment ───
  maintainabilityQuestions: MaintainabilityQuestion[] = [
    { id: 'MQ1', question: 'Is the complaint against a Regulated Entity (Bank/NBFC/Payment System)?', answer: null, weight: 3 },
    { id: 'MQ2', question: 'Does the complaint fall under the grounds specified in the RBI Ombudsman Scheme?', answer: null, weight: 3 },
    { id: 'MQ3', question: 'Has the complainant first approached the RE and waited the stipulated period?', answer: null, weight: 2 },
    { id: 'MQ4', question: 'Is the complaint within the limitation period (1 year)?', answer: null, weight: 2 },
    { id: 'MQ5', question: 'Is the matter NOT sub-judice before any court/tribunal/forum?', answer: null, weight: 3 },
    { id: 'MQ6', question: 'Is the complainant identifiable (not anonymous)?', answer: null, weight: 1 },
    { id: 'MQ7', question: 'Does the complaint contain sufficient details to proceed?', answer: null, weight: 1 },
    { id: 'MQ8', question: 'Is the relief sought quantifiable or specific?', answer: null, weight: 1 },
  ];

  // ─── Auto-Closure Screening (Sequential) ───
  screeningQuestions = signal([
    { id: 'SQ1', question: 'Is the complaint time-barred (filed beyond 1 year from cause of action)?', answer: null as ('YES' | 'NO' | null), closureClause: 'TIME_BARRED' },
    { id: 'SQ2', question: 'Is the matter currently sub-judice before a court/tribunal/forum?', answer: null as ('YES' | 'NO' | null), closureClause: 'SUB_JUDICE' },
    { id: 'SQ3', question: 'Is the complaint regarding a matter already settled by agreement?', answer: null as ('YES' | 'NO' | null), closureClause: 'ALREADY_SETTLED' },
    { id: 'SQ4', question: 'Has the complainant not approached the RE before filing with the Ombudsman?', answer: null as ('YES' | 'NO' | null), closureClause: 'NOT_APPROACHED_RE' },
    { id: 'SQ5', question: 'Is the complaint anonymous (no identifiable complainant)?', answer: null as ('YES' | 'NO' | null), closureClause: 'ANONYMOUS' },
    { id: 'SQ6', question: 'Does the complaint lack specifics required for investigation?', answer: null as ('YES' | 'NO' | null), closureClause: 'INSUFFICIENT_DETAILS' },
    { id: 'SQ7', question: 'Is the subject matter outside the jurisdiction of RBI Ombudsman?', answer: null as ('YES' | 'NO' | null), closureClause: 'OUT_OF_JURISDICTION' },
    { id: 'SQ8', question: 'Is the complaint frivolous or vexatious in nature?', answer: null as ('YES' | 'NO' | null), closureClause: 'FRIVOLOUS' },
  ]);

  currentScreeningIndex = signal(0);

  autoClosureTriggered = computed(() => {
    return this.screeningQuestions().some(q => q.answer === 'YES');
  });

  autoClosureClause = computed(() => {
    const triggered = this.screeningQuestions().find(q => q.answer === 'YES');
    return triggered?.closureClause || '';
  });

  allScreeningComplete = computed(() => {
    return this.screeningQuestions().every(q => q.answer !== null);
  });

  // ─── Decision & Routing ───
  deoDecision: 'MAINTAINABLE' | 'NON_MAINTAINABLE' | '' = '';
  nonMaintainableReason = '';
  closureTag = '';
  selectedReviewer = '';
  deoRemarks = '';
  savedTemplateId = '';

  nonMaintainableReasons = [
    { value: 'TIME_BARRED', label: 'Time Barred (beyond 1 year)' },
    { value: 'SUB_JUDICE', label: 'Matter is Sub-Judice' },
    { value: 'ALREADY_SETTLED', label: 'Already Settled by Agreement' },
    { value: 'NOT_APPROACHED_RE', label: 'Complainant has not approached RE' },
    { value: 'ANONYMOUS', label: 'Anonymous Complaint' },
    { value: 'INSUFFICIENT_DETAILS', label: 'Insufficient Details' },
    { value: 'OUT_OF_JURISDICTION', label: 'Outside Jurisdiction' },
    { value: 'FRIVOLOUS', label: 'Frivolous/Vexatious' },
    { value: 'DUPLICATE', label: 'Duplicate Complaint' },
    { value: 'OTHER', label: 'Other (specify in remarks)' },
  ];

  closureTags = [
    { value: 'NM_CLAUSE_1', label: 'NM Clause 1 - Time Barred' },
    { value: 'NM_CLAUSE_2', label: 'NM Clause 2 - Sub Judice' },
    { value: 'NM_CLAUSE_3', label: 'NM Clause 3 - Outside Jurisdiction' },
    { value: 'NM_CLAUSE_4', label: 'NM Clause 4 - No Pecuniary Loss' },
    { value: 'NM_CLAUSE_5', label: 'NM Clause 5 - HR/Service Matter' },
    { value: 'VERNACULAR', label: 'Vernacular - Translation Required' },
  ];

  commentTemplates = [
    { id: 'T1', label: 'Standard Maintainable', text: 'Complaint meets all criteria for processing under the RBIOS scheme. All mandatory fields verified. Forwarding to Reviewer for action.' },
    { id: 'T2', label: 'Non-Maintainable - Time Barred', text: 'Complaint is time-barred (filed beyond 1 year from cause of action). Auto-closure screening confirmed. Recommended for closure under NM Clause 1.' },
    { id: 'T3', label: 'Non-Maintainable - Sub Judice', text: 'Matter is currently before a court/tribunal. Cannot be processed under RBIOS. Closure under NM Clause 2.' },
    { id: 'T4', label: 'Non-Maintainable - Outside Jurisdiction', text: 'Subject matter falls outside the jurisdiction of RBI Ombudsman. Closure under NM Clause 3.' },
    { id: 'T5', label: 'Incomplete - Awaiting Info', text: 'Complaint lacks sufficient details. Additional information requested from complainant via email. Pending response.' },
    { id: 'T6', label: 'CPGRAMS Referral', text: 'Complaint received via CPGRAMS. Reference verified. Processing as per CPGRAMS SLA guidelines.' },
    { id: 'T7', label: 'Vernacular - Translation Pending', text: 'Complaint received in regional language. Flagged for translation. Assessment pending translated content.' },
  ];

  reviewers = signal<ReviewerUser[]>([]);

  activeReviewers = computed(() => this.reviewers().filter(r => r.isActive && !r.isOnLeave));

  suggestedReviewer = computed(() => {
    const active = this.activeReviewers();
    const sorted = [...active].sort((a, b) => a.currentLoad - b.currentLoad);
    return sorted[0]?.id || '';
  });

  maintainabilityScore = computed(() => {
    let score = 0;
    let maxScore = 0;
    for (const q of this.maintainabilityQuestions) {
      if (q.answer === 'NA') continue;
      maxScore += q.weight;
      if (q.answer === 'YES') score += q.weight;
    }
    return maxScore > 0 ? Math.round((score / maxScore) * 100) : 0;
  });

  allQuestionsAnswered = computed(() => {
    return this.maintainabilityQuestions.every(q => q.answer !== null);
  });

  // ─── Mandatory Field Validation ───
  mandatoryFieldsComplete = computed(() => {
    return this.complainantName.trim().length > 0 &&
           this.complainantState.trim().length > 0 &&
           this.complainantDistrict.trim().length > 0 &&
           (this.complainantPhone.trim().length > 0 || this.complainantEmail.trim().length > 0) &&
           this.category.trim().length > 0 &&
           this.entityName.trim().length > 0 &&
           this.subject.trim().length > 0;
  });

  missingMandatoryFields = computed(() => {
    const missing: string[] = [];
    if (!this.complainantName.trim()) missing.push('Complainant Name');
    if (!this.complainantState.trim()) missing.push('State');
    if (!this.complainantDistrict.trim()) missing.push('District');
    if (!this.complainantPhone.trim() && !this.complainantEmail.trim()) missing.push('Phone or Email');
    if (!this.category.trim()) missing.push('Category');
    if (!this.entityName.trim()) missing.push('Entity Name');
    if (!this.subject.trim()) missing.push('Subject');
    return missing;
  });

  submitting = signal(false);
  submitted = signal(false);
  savingDraft = signal(false);
  draftSaved = signal(false);

  categories = ['ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'];

  states = [
    'AN', 'AP', 'AR', 'AS', 'BR', 'CH', 'CT', 'DL', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK',
    'KA', 'KL', 'LA', 'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'PB', 'PY', 'RJ',
    'SK', 'TN', 'TS', 'TR', 'UK', 'UP', 'WB'
  ];

  ngOnInit() {
    this.draftId = this.route.snapshot.paramMap.get('id') || '';
    this.loadDraft();
    this.crpcService.getReviewers().subscribe(data => this.reviewers.set(data));
  }

  loadDraft() {
    this.loading.set(true);
    const isPhysicalLetter = this.draftId.startsWith('DRF-2026');

    setTimeout(() => {
      if (isPhysicalLetter) {
        // Physical Letter draft — OCR-prefilled fields from wizard, scanned file auto-attached
        const saved = sessionStorage.getItem('physicalLetterDraft');
        const draft = saved ? JSON.parse(saved) : null;

        this.complainantName = draft?.complainantName || 'Suresh Patel';
        this.complainantPhone = draft?.complainantPhone || '9412345678';
        this.complainantEmail = draft?.complainantEmail || '';
        this.complainantAddress = draft?.complainantAddress || '12, Nehru Nagar, Sector 5, Jaipur';
        this.complainantState = draft?.complainantState || 'RJ';
        this.complainantDistrict = draft?.complainantDistrict || 'Jaipur';
        this.complainantPincode = draft?.complainantPincode || '302001';
        this.contactPreference = 'POST';

        this.modeOfReceipt = 'PHYSICAL_LETTER';
        this.category = draft?.category || '';
        this.entityName = draft?.entityName || '';
        this.entityType = draft?.entityType || 'BANK';
        this.subject = draft?.subject || '';
        this.description = draft?.description || '';
        this.amountInvolved = draft?.amountInvolved || null;
        this.transactionDate = draft?.transactionDate || '';
        this.letterDate = draft?.letterDate || '';
        this.receivedDate = draft?.receivedDate || new Date().toISOString().split('T')[0];
        this.emailType = '';
        this.systemSuggestion = 'PENDING';
        this.vernacular = draft?.vernacular || false;
        this.vernacularLanguage = draft?.vernacularLanguage || '';

        this.attachments.set([
          { id: 'ATT-001', name: draft?.fileName || 'scanned_letter.pdf', size: draft?.fileSize || '2.4 MB', type: 'application/pdf', uploadedAt: new Date().toISOString(), uploadedBy: 'DEO' },
        ]);

        this.emailCorrespondence.set([]);
      } else {
        // Email-originated draft — prefilled from OCR/parsing
        this.complainantName = 'Rajesh Kumar';
        this.complainantPhone = '9876543210';
        this.complainantEmail = 'rajesh@example.com';
        this.complainantAddress = '42, MG Road, Mumbai';
        this.complainantState = 'MH';
        this.complainantDistrict = 'Mumbai';
        this.complainantPincode = '400001';
        this.contactPreference = 'EMAIL';

        this.modeOfReceipt = 'EMAIL';
        this.category = 'ATM';
        this.entityName = 'SBI';
        this.entityType = 'BANK';
        this.subject = 'ATM cash not dispensed but account debited';
        this.description = 'On 28-May-2026, I tried to withdraw Rs. 10,000 from SBI ATM at Andheri branch. The machine showed "Transaction Successful" but no cash was dispensed. My account was debited Rs. 10,000. I have attached the mini statement.';
        this.amountInvolved = 10000;
        this.transactionDate = '2026-05-28';
        this.receivedDate = '2026-06-01';
        this.emailType = 'TO';
        this.systemSuggestion = 'MAINTAINABLE';
        this.vernacular = false;

        this.attachments.set([
          { id: 'ATT-001', name: 'original_email.eml', size: '156 KB', type: 'message/rfc822', uploadedAt: '2026-06-01T10:00:00Z', uploadedBy: 'SYSTEM' },
          { id: 'ATT-002', name: 'mini_statement.jpg', size: '450 KB', type: 'image/jpeg', uploadedAt: '2026-06-01T10:00:00Z', uploadedBy: 'SYSTEM' },
        ]);

        this.emailCorrespondence.set([
          { id: 'EC-001', direction: 'RECEIVED', subject: 'ATM cash not dispensed', to: 'crpc@rbi.org.in', sentAt: '2026-05-29T14:30:00Z', body: 'Original complaint email received.' },
        ]);
      }

      this.loading.set(false);
    }, 800);
  }

  // ─── Attachment Management ───
  onFileUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const file = input.files[0];
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/tiff', 'message/rfc822'];
    if (!allowed.includes(file.type) && !file.name.endsWith('.eml')) {
      this.uploadError = 'Only PDF, JPEG, PNG, TIFF, or EML files are accepted.';
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      this.uploadError = 'File size must not exceed 10 MB.';
      return;
    }

    this.uploadError = '';
    const newAtt: Attachment = {
      id: 'ATT-' + Date.now(),
      name: file.name,
      size: (file.size / 1024).toFixed(0) + ' KB',
      type: file.type,
      uploadedAt: new Date().toISOString(),
      uploadedBy: 'DEO'
    };
    this.attachments.set([...this.attachments(), newAtt]);
    input.value = '';
  }

  removeAttachment(id: string) {
    this.attachments.set(this.attachments().filter(a => a.id !== id));
  }

  // ─── Email: Request Additional Information ───
  openEmailComposer() {
    this.emailTo = this.complainantEmail;
    this.emailSubject = `Re: ${this.subject} — Additional Information Required [${this.draftId}]`;
    this.emailBody = '';
    this.showEmailComposer.set(true);
  }

  sendEmail() {
    if (!this.emailTo || !this.emailBody) return;
    this.sendingEmail.set(true);

    if (this.attachFormPdf) {
      this.generateAndAttachFormPdf();
    }

    setTimeout(() => {
      const attachNote = this.attachFormPdf ? '\n\n[Attached: Additional_Information_Form.pdf (editable)]' : '';
      const newEmail: EmailCorrespondence = {
        id: 'EC-' + Date.now(),
        direction: 'SENT',
        subject: this.emailSubject,
        to: this.emailTo,
        sentAt: new Date().toISOString(),
        body: this.emailBody + attachNote,
      };
      this.emailCorrespondence.set([...this.emailCorrespondence(), newEmail]);
      this.sendingEmail.set(false);
      this.showEmailComposer.set(false);
      this.attachFormPdf = false;
    }, 1000);
  }

  cancelEmail() {
    this.showEmailComposer.set(false);
    this.attachFormPdf = false;
  }

  async downloadFormPdf() {
    const { jsPDF } = await import('jspdf');
    const doc = this.buildFormPdf(jsPDF);
    doc.save('Additional_Information_Form.pdf');
  }

  private async generateAndAttachFormPdf() {
    const { jsPDF } = await import('jspdf');
    this.buildFormPdf(jsPDF);
  }

  private getFieldPrefillValue(key: string): string {
    const map: Record<string, string> = {
      name: this.complainantName || '',
      phone: this.complainantPhone || '',
      email: this.complainantEmail || '',
      address: this.complainantAddress || '',
      state: this.complainantState || '',
      district: this.complainantDistrict || '',
      pincode: this.complainantPincode || '',
      accountNumber: '',
      transactionId: '',
      transactionDate: this.transactionDate || '',
      amount: this.amountInvolved ? String(this.amountInvolved) : '',
      branchName: '',
      description: this.description || '',
      supportingDocs: '',
    };
    return map[key] || '';
  }

  private buildFormPdf(jsPDF: any): any {
    const doc = new jsPDF();
    const selectedFields = this.formPdfFields.filter(f => f.include);
    const pageWidth = doc.internal.pageSize.getWidth();
    const fieldWidth = pageWidth - 28;

    // Header
    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.text('Reserve Bank of India - Integrated Ombudsman Scheme', pageWidth / 2, 20, { align: 'center' });

    doc.setFontSize(11);
    doc.setFont('helvetica', 'normal');
    doc.text('ADDITIONAL INFORMATION REQUEST FORM', pageWidth / 2, 28, { align: 'center' });

    doc.setFontSize(9);
    doc.text(`Reference: ${this.draftId}`, 14, 38);
    doc.text(`Date: ${new Date().toLocaleDateString('en-IN')}`, 14, 44);
    doc.text(`To: ${this.complainantName || 'Complainant'}`, 14, 50);

    doc.setFontSize(9);
    doc.setFont('helvetica', 'italic');
    doc.text('Please fill in / correct the details below and return this form to crpc@rbi.org.in', 14, 60);
    doc.text('Fields are editable - click on any field to type or modify the pre-filled value.', 14, 66);
    doc.setFont('helvetica', 'normal');

    // Editable AcroForm fields
    let y = 76;

    for (const field of selectedFields) {
      if (y > 260) {
        doc.addPage();
        y = 20;
      }

      doc.setFontSize(9);
      doc.setFont('helvetica', 'bold');
      doc.text(`${field.label}:`, 14, y);
      y += 2;

      const isMultiline = field.key === 'description' || field.key === 'address';
      const fieldHeight = isMultiline ? 25 : 10;
      const prefill = this.getFieldPrefillValue(field.key);

      const textField = new doc.AcroFormTextField();
      textField.fieldName = field.key;
      textField.x = 14;
      textField.y = y;
      textField.width = fieldWidth;
      textField.height = fieldHeight;
      textField.fontSize = 10;
      textField.value = prefill;
      textField.defaultValue = prefill;
      textField.multiline = isMultiline;
      textField.readOnly = false;
      doc.addField(textField);

      y += fieldHeight + 8;
    }

    // Footer
    y += 5;
    if (y > 270) {
      doc.addPage();
      y = 20;
    }
    doc.setFontSize(8);
    doc.setFont('helvetica', 'italic');
    doc.text('Signature / Date: _______________________________', 14, y);
    y += 8;
    doc.text('Note: Failure to provide the requested information within 15 days may result in closure of the complaint.', 14, y);

    return doc;
  }

  // ─── Auto-Closure Screening (Sequential) ───
  answerScreening(answer: 'YES' | 'NO') {
    const idx = this.currentScreeningIndex();
    const updated = this.screeningQuestions().map((q, i) =>
      i === idx ? { ...q, answer } : q
    );
    this.screeningQuestions.set(updated);

    if (idx < updated.length - 1) {
      this.currentScreeningIndex.set(idx + 1);
    }
  }

  resetScreening() {
    this.screeningQuestions.set(this.screeningQuestions().map(q => ({ ...q, answer: null })));
    this.currentScreeningIndex.set(0);
  }

  // ─── Comment Templates ───
  applyTemplate(templateId: string) {
    const tmpl = this.commentTemplates.find(t => t.id === templateId);
    if (tmpl) {
      this.deoRemarks = tmpl.text;
      this.savedTemplateId = templateId;
    }
  }

  // ─── Reviewer Routing ───
  useRoundRobin() {
    this.selectedReviewer = this.suggestedReviewer();
  }

  // ─── Save Draft (without sending) ───
  saveDraft() {
    this.savingDraft.set(true);
    setTimeout(() => {
      this.savingDraft.set(false);
      this.draftSaved.set(true);
      setTimeout(() => this.draftSaved.set(false), 3000);
    }, 800);
  }

  // ─── Validation for Send for Approval ───
  canSendForApproval(): boolean {
    if (!this.deoDecision) return false;
    if (!this.mandatoryFieldsComplete()) return false;
    if (!this.selectedReviewer) return false;

    if (this.deoDecision === 'NON_MAINTAINABLE' && !this.nonMaintainableReason) return false;
    if (this.deoDecision === 'MAINTAINABLE' && !this.allScreeningComplete()) return false;

    return true;
  }

  sendForApproval() {
    if (!this.canSendForApproval()) return;
    this.submitting.set(true);

    setTimeout(() => {
      this.submitting.set(false);
      this.submitted.set(true);
    }, 1500);
  }

  goBack() {
    this.router.navigate(['/crpc/home']);
  }
}
