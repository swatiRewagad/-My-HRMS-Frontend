import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService } from '../../../services/rbio-workflow.service';
import { UploadLinkStatusComponent } from '../../../shared/upload-link-status/upload-link-status.component';
import { RbioDeputyDecisionComponent } from '../rbio-deputy-decision/rbio-deputy-decision.component';
import { RbioAddEntityComponent } from '../rbio-add-entity/rbio-add-entity.component';
import { RbioLegalCaseComponent } from '../rbio-legal-case/rbio-legal-case.component';
import { RbioForwardRegulatoryComponent } from '../rbio-forward-regulatory/rbio-forward-regulatory.component';
import { RbioActionOverrideHistoryComponent } from '../rbio-action-override-history/rbio-action-override-history.component';
import { environment } from '../../../../environments/environment';

interface ComplaintDetail {
  complaintId: string;
  complaintNumber: string;
  complainantName: string;
  complainantEmail: string;
  subject: string;
  description: string;
  status: string;
  category: string;
  entityName: string;
  priority: string;
  modeOfReceipt: string;
  receiptDate: string;
  assignedTo: string;
  slaDueDate: string;
  slaBreachHours: number;
  comments: string;
  proposedAction: string;
  proposedClause: string;
  speakingOrder: string;
}

interface Comment {
  id: string;
  author: string;
  text: string;
  timestamp: string;
}

@Component({
  selector: 'app-rbio-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, UploadLinkStatusComponent, RbioDeputyDecisionComponent, RbioAddEntityComponent, RbioLegalCaseComponent, RbioForwardRegulatoryComponent, RbioActionOverrideHistoryComponent],
  templateUrl: './rbio-complaint-detail.component.html',
  styleUrl: './rbio-complaint-detail.component.scss'
})
export class RbioComplaintDetailComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);
  private rbioWorkflow = inject(RbioWorkflowService);

  complaint = signal<ComplaintDetail | null>(null);
  loading = signal(true);
  activeTab = signal('summary');
  showApprovalDropdown = signal(false);
  showAssignmentModal = signal(false);
  assignmentTarget = signal('');
  editMode = signal(false);

  // Assessment fields
  proposedAction = signal('');
  proposedClause = signal('');
  newComment = signal('');
  speakingOrder = signal('');
  comments = signal<Comment[]>([]);

  // Assignment modal fields
  assignmentType = signal('Automatic');
  assigneeName = signal('');
  systemicIssue = signal(false);
  crpcProposedAction = signal('');
  crpcProposedClause = signal('');

  // Upload link status
  uploadLinkActive = signal(false);
  documentsSubmitted = signal(false);

  // ═══ Closure Features (UST504-509, UST576, UST577, UST580, UST581-584) ═══
  showClosureConfirmPopup = signal(false);
  showNoEmailPopup = signal(false);
  showSampleLetterModal = signal(false);
  customClosureText = signal('');
  closureClause = signal('');
  allowedClosureClauses = signal<any[]>([]);
  dateOfSending = signal('');
  closureLetterFile = signal<File | null>(null);
  sampleLetterClause = signal('');
  emailValidationError = signal('');

  get customClosureTextLength(): number {
    return this.customClosureText().length;
  }

  loggedInUser: { id: string; name: string; role: string } | null = null;

  tabs = [
    { key: 'summary', label: 'Summary' },
    { key: 'nodal', label: 'Nodal Officer Record' },
    { key: 'conciliation', label: 'Conciliation' },
    { key: 'forward', label: 'Forward' },
    { key: 'email', label: 'Email Communication' },
    { key: 'final', label: 'Final Decision' },
    { key: 'legal', label: 'Legal Case' },
    { key: 'history', label: 'Complaint History' },
  ];

  approvalOptions = [
    { key: 'reviewer', label: 'Send to RBIO Reviewer', nameLabel: 'Name of RBIO Reviewer' },
    { key: 'deputy', label: 'Send to RBIO Deputy Ombudsman', nameLabel: 'Name of RBIO Deputy Ombudsman' },
    { key: 'ombudsman', label: 'Send to RBIO Ombudsman', nameLabel: 'Name of RBIO Ombudsman' },
  ];

  selectedApprovalOption = signal<any>(null);

  ngOnInit() {
    const stored = sessionStorage.getItem('rbio_user');
    if (stored) this.loggedInUser = JSON.parse(stored);

    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadComplaint(id);
  }

  loadComplaint(id: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}`).subscribe({
      next: (res) => {
        const d = res.data || res;
        this.complaint.set({
          complaintId: d.complaintId || d.complaintNumber || id,
          complaintNumber: d.complaintNumber || 'Not Assigned',
          complainantName: d.complainantName || '',
          complainantEmail: d.complainantEmail || '',
          subject: d.subject || '',
          description: d.description || '',
          status: d.status || 'DRAFT',
          category: d.category || 'General',
          entityName: d.entityName || '',
          priority: d.priority || 'MEDIUM',
          modeOfReceipt: d.filingType || d.modeOfReceipt || 'Email',
          receiptDate: d.createdAt || '',
          assignedTo: d.assignedTo || d.assignedTeam || '',
          slaDueDate: d.slaDueDate || '',
          slaBreachHours: this.calculateSlaHours(d.slaDueDate),
          comments: '',
          proposedAction: '',
          proposedClause: '',
          speakingOrder: '',
        });
        this.comments.set([
          { id: '1', author: 'Full Name RO DO', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers. These systems provide a centralized platform...', timestamp: '2 hrs ago' },
          { id: '2', author: 'Full Name', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers.', timestamp: '1 hrs ago' },
        ]);
        this.loading.set(false);
        this.checkFinalDecisionStatus();
      },
      error: () => {
        this.complaint.set({
          complaintId: id,
          complaintNumber: 'N20223317000005',
          complainantName: 'Sagar Chauhan',
          complainantEmail: 'saurabh.pradhan@gmail.com',
          subject: 'Re: URGENT: Closed Loan Account Falsely Reported as Delinquent Under Same CIF – Kankarbagh Branch',
          description: 'Hold placed on my Canara Bank account due to a cyber crime investigation since 16 February. My account has been blocked, and I am unable to operate it or withdraw/credit funds. ...',
          status: 'NEW',
          category: 'Loans and Advances',
          entityName: 'ASNU FINVEST...',
          priority: 'MEDIUM',
          modeOfReceipt: 'Email',
          receiptDate: '27-04-2026',
          assignedTo: 'Bhupinder Singh',
          slaDueDate: '2026-06-15',
          slaBreachHours: 36,
          comments: '',
          proposedAction: '',
          proposedClause: '',
          speakingOrder: '',
        });
        this.comments.set([
          { id: '1', author: 'Full Name RO DO', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers. These systems provide a centralized platform...', timestamp: '2 hrs ago' },
          { id: '2', author: 'Full Name', text: 'Core banking systems are the central nervous system of any bank. They process a range of transactions, from deposits and withdrawals to loan payments and fund transfers.', timestamp: '1 hrs ago' },
        ]);
        this.loading.set(false);
      }
    });
  }

  private calculateSlaHours(slaDueDate: string): number {
    if (!slaDueDate) return 0;
    const due = new Date(slaDueDate);
    const now = new Date();
    return Math.max(0, Math.ceil((due.getTime() - now.getTime()) / (1000 * 60 * 60)));
  }

  goBack() {
    this.router.navigate(['/rbio']);
  }

  openApprovalOption(option: any) {
    this.selectedApprovalOption.set(option);
    this.assigneeName.set(option.nameLabel.replace('Name of ', ''));
    this.showApprovalDropdown.set(false);
    this.showAssignmentModal.set(true);
  }

  confirmAssignment() {
    const c = this.complaint();
    if (!c) return;
    this.showAssignmentModal.set(false);
    this.router.navigate(['/rbio']);
  }

  cancelAssignment() {
    this.showAssignmentModal.set(false);
    this.selectedApprovalOption.set(null);
  }

  addComment() {
    if (!this.newComment()) return;
    this.comments.update(list => [...list, {
      id: Date.now().toString(),
      author: this.loggedInUser?.name || 'Officer',
      text: this.newComment(),
      timestamp: 'Just now'
    }]);
    this.newComment.set('');
  }

  onLinkStatusChange(event: { linkActive: boolean; documentsSubmitted: boolean }) {
    this.uploadLinkActive.set(event.linkActive);
    this.documentsSubmitted.set(event.documentsSubmitted);
  }

  /** Check if closure actions are blocked (upload link active, user not Deputy/Ombudsman) */
  isClosureBlocked(): boolean {
    if (!this.uploadLinkActive()) return false;
    const role = this.loggedInUser?.role?.toUpperCase() || '';
    const exemptRoles = ['RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'DEPUTY_OMBUDSMAN', 'OMBUDSMAN'];
    return !exemptRoles.includes(role);
  }

  /** Check if forwarding is restricted (upload link active, user not Deputy/Ombudsman) */
  isForwardingRestricted(): boolean {
    return this.isClosureBlocked();
  }

  // ═══ Closure Methods ═══
  loadClosureClauses() {
    const role = this.loggedInUser?.role || 'REVIEWER';
    let mappedRole = 'REVIEWER';
    if (role.includes('OMBUDSMAN')) mappedRole = 'OMBUDSMAN';
    else if (role.includes('DEPUTY')) mappedRole = 'DEPUTY_OMBUDSMAN';
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/closure-clauses`, { params: { role: mappedRole } }).subscribe({
      next: (res) => this.allowedClosureClauses.set(res?.data || []),
      error: () => this.allowedClosureClauses.set([])
    });
  }
  onClosureClauseChange(clauseCode: string) {
    this.closureClause.set(clauseCode);
    const clause = this.allowedClosureClauses().find((cl: any) => cl.code === clauseCode);
    if (clause?.newIn2026) { this.sampleLetterClause.set(clauseCode); this.showSampleLetterModal.set(true); }
  }
  acknowledgeSampleLetter() { this.showSampleLetterModal.set(false); }
  validateEmailRecipients(recipients: string[]): boolean {
    const invalid = recipients.filter(e => !e.toLowerCase().endsWith('@rbi.org.in') && !e.toLowerCase().endsWith('@rbi.gov.in'));
    if (invalid.length > 0) { this.emailValidationError.set('Only official RBI email addresses can be used for outbound complaint emails'); return false; }
    this.emailValidationError.set(''); return true;
  }
  onClosureLetterFileUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) { this.closureLetterFile.set(input.files[0]); }
  }

  // ═══ Feature: Block DO Editing After Decision (UST756) ═══
  hasFinalDecisionUpstream = signal(false);
  finalDecisionBy = signal('');
  finalDecisionByRole = signal('');

  checkFinalDecisionStatus() {
    const c = this.complaint();
    if (!c) return;
    const complaintId = c.complaintNumber || c.complaintId;
    this.rbioWorkflow.checkFinalDecision(complaintId).subscribe({
      next: (result) => {
        this.hasFinalDecisionUpstream.set(result.hasFinalDecision);
        this.finalDecisionBy.set(result.decidedBy || '');
        this.finalDecisionByRole.set(result.decidedByRole || '');
      },
      error: () => {}
    });
  }

  isFieldReadOnlyDueToDecision(): boolean {
    return this.hasFinalDecisionUpstream() && (this.loggedInUser?.role || '').toUpperCase() === 'RBIO_OFFICER';
  }

  getReadOnlyTooltip(): string {
    if (!this.hasFinalDecisionUpstream()) return '';
    return `Set by ${this.finalDecisionByRole() || 'Deputy/Ombudsman'}, cannot be modified`;
  }

  // ═══ Feature: Proposed Action Override (UST639-642) ═══
  private previousProposedAction = '';
  private previousProposedClause = '';

  onProposedActionChange(newValue: string) {
    const roles = this.auth.getRoles();
    const overrideRoles = ['RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_ADJUDICATOR'];
    if (roles.some(r => overrideRoles.includes(r)) && this.previousProposedAction && this.previousProposedAction !== newValue) {
      const c = this.complaint();
      const complaintId = c?.complaintNumber || c?.complaintId;
      if (complaintId) {
        this.rbioWorkflow.recordActionOverride(complaintId, {
          fieldName: 'Proposed Action',
          oldValue: this.previousProposedAction,
          newValue,
          overriddenBy: this.auth.currentUser()?.username || this.loggedInUser?.name || '',
          overriddenByRole: roles.find(r => overrideRoles.includes(r)) || ''
        }).subscribe();
      }
    }
    this.previousProposedAction = newValue;
  }

  onProposedClauseChange(newValue: string) {
    const roles = this.auth.getRoles();
    const overrideRoles = ['RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_ADJUDICATOR'];
    if (roles.some(r => overrideRoles.includes(r)) && this.previousProposedClause && this.previousProposedClause !== newValue) {
      const c = this.complaint();
      const complaintId = c?.complaintNumber || c?.complaintId;
      if (complaintId) {
        this.rbioWorkflow.recordActionOverride(complaintId, {
          fieldName: 'Proposed Clause',
          oldValue: this.previousProposedClause,
          newValue,
          overriddenBy: this.auth.currentUser()?.username || this.loggedInUser?.name || '',
          overriddenByRole: roles.find(r => overrideRoles.includes(r)) || ''
        }).subscribe();
      }
    }
    this.previousProposedClause = newValue;
  }

  // ═══ Feature: Deputy Ombudsman Decision visibility ═══
  showDeputyDecision(): boolean {
    return this.auth.hasRole('RBIO_DEPUTY_OMBUDSMAN');
  }

  onDeputyDecisionSubmitted() {
    const c = this.complaint();
    if (c) this.loadComplaint(c.complaintId);
  }

  // ═══ Feature: Add Entity visibility ═══
  showAddEntity(): boolean {
    const roles = this.auth.getRoles();
    return roles.some(r => ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN'].includes(r));
  }

  // ═══ Feature: Forward Regulatory visibility ═══
  showForwardRegulatory(): boolean {
    const roles = this.auth.getRoles();
    return roles.some(r => ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_ADJUDICATOR'].includes(r));
  }
}
