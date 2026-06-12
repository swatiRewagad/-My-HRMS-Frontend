import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { CrpcService } from '../../../services/crpc.service';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

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
  private http = inject(HttpClient);
  private crpcService = inject(CrpcService);
  private auth = inject(KeycloakAuthService);

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
  activeDeos = signal<{ id: string; name: string; active: boolean }[]>([]);

  // Not a Complaint disposition
  notComplaintDisposition: 'CLOSED' | 'SENT_TO_OTHER_DEPARTMENT' | 'SUGGESTION' | '' = '';
  targetDepartment = '';
  targetOffice = '';

  // Routing for approved complaints (auto-determined by entity rules, reviewer can override)
  targetRegionalOffice = '';
  autoRoutedOffice = '';
  routingReason = '';
  regionalOffices = [
    { id: 'RBIO-MUM', name: 'RBIO Mumbai', dept: 'RBIO' },
    { id: 'RBIO-DEL', name: 'RBIO Delhi', dept: 'RBIO' },
    { id: 'RBIO-CHE', name: 'RBIO Chennai', dept: 'RBIO' },
    { id: 'RBIO-KOL', name: 'RBIO Kolkata', dept: 'RBIO' },
    { id: 'CEPC', name: 'CEPC (Central)', dept: 'CEPC' },
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
  assignedToUser = signal('');

  categories = ['ATM', 'CREDIT_CARD', 'UPI', 'LOAN', 'DEPOSIT', 'INSURANCE', 'NEFT_RTGS', 'GENERAL'];
  states = [
    'AN', 'AP', 'AR', 'AS', 'BR', 'CH', 'CT', 'DL', 'GA', 'GJ', 'HP', 'HR', 'JH', 'JK',
    'KA', 'KL', 'LA', 'MH', 'ML', 'MN', 'MP', 'MZ', 'NL', 'OD', 'PB', 'PY', 'RJ',
    'SK', 'TN', 'TS', 'TR', 'UK', 'UP', 'WB'
  ];

  ngOnInit() {
    this.draftId = this.route.snapshot.paramMap.get('id') || '';
    this.loadDraft();
    this.loadDeos();
  }

  private loadDeos() {
    this.crpcService.getDeos().subscribe(deos => {
      this.activeDeos.set(deos.map((d: any) => ({
        id: d.userId,
        name: d.displayName,
        active: d.isActive ?? true,
      })));
    });
  }

  loadDraft() {
    this.loading.set(true);

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`)
      .subscribe({
        next: (res) => {
          const draft = res?.data || {};
          this.complainantName = draft.complainantName || '';
          this.complainantPhone = draft.complainantPhone || '';
          this.complainantEmail = draft.senderEmail || '';
          this.complainantAddress = draft.complainantAddress || '';
          this.complainantState = draft.complainantState || '';
          this.complainantDistrict = draft.complainantDistrict || '';
          this.complainantPincode = draft.complainantPincode || '';

          this.modeOfReceipt = draft.modeOfReceipt || 'EMAIL';
          this.category = draft.category || '';
          this.entityName = draft.entityName || '';
          this.entityType = draft.entityType || 'BANK';
          this.subject = draft.subject || draft.complaintSummary || '';
          this.description = draft.body || '';
          this.amountInvolved = draft.amountInvolved || null;
          this.transactionDate = draft.transactionDate || '';
          this.receivedDate = draft.receivedAt ? draft.receivedAt.split('T')[0] : '';
          this.emailType = 'TO';
          this.systemSuggestion = draft.systemSuggestion || 'MAINTAINABLE';
          this.vernacular = draft.isVernacular || false;
          this.vernacularLanguage = draft.languageName || '';

          this.deoDecision = draft.deoDecision || '';
          this.deoRemarks = draft.deoRemarks || '';
          this.deoNonMaintainableReason = draft.nonMaintainableReason || '';
          this.maintainabilityScore = draft.maintainabilityScore || 0;

          const attachments = (draft.attachments || []).map((a: any, i: number) => ({
            id: a.id || `ATT-${i + 1}`,
            name: a.fileName || `attachment_${i + 1}`,
            size: a.fileSize ? this.formatSize(a.fileSize) : 'Unknown',
            type: a.fileType || 'application/octet-stream',
            uploadedAt: a.createdAt || new Date().toISOString(),
            uploadedBy: 'SYSTEM',
          }));
          this.attachments.set(attachments);

          this.emailThread.set([{
            id: 'E1',
            direction: 'RECEIVED',
            from: draft.senderEmail || '',
            to: 'crpc@rbi.org.in',
            subject: draft.subject || '',
            sentAt: draft.receivedAt || '',
            body: draft.body || '',
          }]);

          this.history.set([
            { id: 'H1', timestamp: draft.receivedAt || '', action: 'DRAFT_CREATED', performedBy: 'SYSTEM', role: 'SYSTEM', remarks: 'Email ingested and draft created automatically.' },
            { id: 'H2', timestamp: draft.createdAt || '', action: 'ASSIGNED_TO_DEO', performedBy: 'SYSTEM', role: 'SYSTEM', remarks: `Assigned to ${draft.assignedTo || 'DEO'} via Round Robin.` },
            { id: 'H3', timestamp: draft.createdAt || '', action: 'SENT_TO_REVIEWER', performedBy: draft.assignedTo || 'DEO', role: 'DEO', remarks: `Routed to reviewer via Round Robin.` },
          ]);

          if (draft.convertedComplaintId) {
            this.generatedComplaintNumber.set(draft.convertedComplaintId);
            this.reviewerDecision = 'APPROVE';
            this.targetRegionalOffice = draft.targetOffice || 'CEPC';
            this.assignedToUser.set(draft.assignedTo || '');
            this.submitted.set(true);
          } else if (draft.status === 'SENT_BACK_TO_DEO') {
            this.reviewerDecision = 'SENT_BACK_TO_DEO';
            this.sentBackToDeoId = draft.processedBy || '';
            this.submitted.set(true);
          } else if (draft.status === 'CLOSED_NOT_A_COMPLAINT') {
            this.reviewerDecision = 'NOT_A_COMPLAINT';
            this.submitted.set(true);
          }

          this.computeAutoRouting();
          this.loading.set(false);
        },
        error: () => {
          this.loading.set(false);
        }
      });
  }

  private computeAutoRouting() {
    const entityName = this.entityName || '';

    if (!entityName.trim()) {
      this.autoRoutedOffice = 'RBIO-MUM';
      this.routingReason = 'No entity name — default routing to RBIO Mumbai';
      this.targetRegionalOffice = this.autoRoutedOffice;
      return;
    }

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/resolve-by-name`, {
      params: { entityName }
    }).subscribe({
      next: (res) => {
        const data = res?.data || {};
        const dept = data.department || 'RBIO';
        const matchType = data.matchType || 'NOT_FOUND';
        const matchedName = data.matchedEntityName || '';
        const reason = data.reason || '';

        if (dept === 'CEPC') {
          this.autoRoutedOffice = 'CEPC';
          this.routingReason = `${reason}${matchedName ? ' (' + matchedName + ')' : ''}`;
        } else {
          const office = this.resolveRbioOffice(this.complainantState);
          this.autoRoutedOffice = office;
          this.routingReason = `${reason} | ${this.getOfficeLabel(office)}`;
        }

        this.targetRegionalOffice = this.autoRoutedOffice;
      },
      error: () => {
        this.autoRoutedOffice = 'RBIO-MUM';
        this.routingReason = 'API unavailable — default routing to RBIO Mumbai';
        this.targetRegionalOffice = this.autoRoutedOffice;
      }
    });
  }

  private resolveRbioOffice(state: string): string {
    const s = (state || '').toUpperCase();
    const westStates = ['MH', 'GJ', 'GA', 'MP', 'CT'];
    const northStates = ['DL', 'HR', 'PB', 'UP', 'UK', 'HP', 'JK', 'LA', 'RJ', 'CH'];
    const southStates = ['TN', 'KA', 'KL', 'AP', 'TS', 'PY'];
    const eastStates = ['WB', 'BR', 'JH', 'OD', 'AS', 'NL', 'MN', 'MZ', 'TR', 'ML', 'AR', 'SK', 'AN'];

    if (westStates.includes(s)) return 'RBIO-MUM';
    if (northStates.includes(s)) return 'RBIO-DEL';
    if (southStates.includes(s)) return 'RBIO-CHE';
    if (eastStates.includes(s)) return 'RBIO-KOL';
    return 'RBIO-MUM';
  }

  private getOfficeLabel(officeId: string): string {
    const office = this.regionalOffices.find(o => o.id === officeId);
    return office ? office.name : officeId;
  }

  private formatSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(0) + ' KB';
    return (bytes / 1024 / 1024).toFixed(1) + ' MB';
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
    return true;
  }

  submitDecision() {
    if (!this.canSubmit()) return;
    this.submitting.set(true);

    let newStatus = '';
    if (this.reviewerDecision === 'APPROVE') newStatus = 'APPROVED_ROUTED';
    else if (this.reviewerDecision === 'SENT_BACK_TO_DEO') newStatus = 'SENT_BACK_TO_DEO';
    else if (this.reviewerDecision === 'NOT_A_COMPLAINT') newStatus = 'CLOSED_NOT_A_COMPLAINT';

    this.http.put<any>(`${environment.apiBaseUrl}/api/v1/email-syndication/drafts/${this.draftId}`, {
      status: newStatus,
      reviewerDecision: this.reviewerDecision,
      reviewerRemarks: this.reviewerRemarks,
      targetOffice: this.targetRegionalOffice,
      assignedTo: this.reviewerDecision === 'SENT_BACK_TO_DEO' ? this.sentBackToDeoId : this.targetRegionalOffice,
    }).subscribe({
      next: (res) => {
        if (this.reviewerDecision === 'APPROVE') {
          const complaintId = res?.data?.convertedComplaintId;
          const assignedTo = res?.data?.assignedTo;
          if (complaintId) {
            this.generatedComplaintNumber.set(complaintId);
          } else {
            const dateStr = new Date().toISOString().slice(0, 10).replace(/-/g, '');
            const rand = Math.floor(100000 + Math.random() * 900000);
            this.generatedComplaintNumber.set(`CMP-${dateStr}-${rand}`);
          }
          this.assignedToUser.set(assignedTo || this.targetRegionalOffice);
        }
        this.submitting.set(false);
        this.submitted.set(true);
      },
      error: () => {
        this.submitting.set(false);
      }
    });
  }

  goBack() {
    this.router.navigate(['/crpc/reviewer']);
  }
}
