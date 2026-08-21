import { Component, OnInit, inject, signal, computed } from '@angular/core';
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

interface WorkflowAction {
  id: string;
  label: string;
  assignTo: string;
  style: 'primary' | 'escalate' | 'close' | 'info';
  requiresRemarks: boolean;
}

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
  showWorkflowDropdown = signal(false);
  showWorkflowModal = signal(false);
  editMode = signal(false);
  processing = signal(false);
  actionResult = signal('');
  actionSuccess = signal(false);

  // Assessment fields
  proposedAction = signal('');
  proposedClause = signal('');
  newComment = signal('');
  speakingOrder = signal('');
  comments = signal<Comment[]>([]);

  // Workflow modal fields
  pendingWorkflowAction = signal<WorkflowAction | null>(null);
  assignmentMode = signal('Automatic');
  assigneeName = signal('');
  workflowRemarks = signal('');
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

  // ═══ RBIOS Workflow Actions by Role ═══

  private dealingOfficialActions: WorkflowAction[] = [
    { id: 'SEND_TO_REVIEWER', label: 'Send to Reviewer', assignTo: 'Reviewer', style: 'primary', requiresRemarks: true },
    { id: 'SEND_TO_DEPUTY_OMBUDSMAN', label: 'Send to Deputy Ombudsman', assignTo: 'Deputy Ombudsman', style: 'escalate', requiresRemarks: true },
    { id: 'SEND_TO_OMBUDSMAN', label: 'Send to Ombudsman', assignTo: 'Ombudsman', style: 'escalate', requiresRemarks: true },
    { id: 'SCHEDULE_MEETING', label: 'Schedule Meeting', assignTo: 'Nodal Officer', style: 'info', requiresRemarks: true },
    { id: 'RESCHEDULE_MEETING', label: 'Re-Schedule Meeting', assignTo: 'Nodal Officer', style: 'info', requiresRemarks: true },
    { id: 'MEETING_COMPLETED', label: 'Meeting Completed', assignTo: 'Nodal Officer', style: 'info', requiresRemarks: true },
    { id: 'COMPLAINT_CLOSED', label: 'Complaint Closed', assignTo: '', style: 'close', requiresRemarks: true },
    { id: 'DRAFT_COMPLAINT', label: 'Draft Complaint', assignTo: 'CRPC DEO', style: 'primary', requiresRemarks: true },
    { id: 'INFORMATION_REQUIRED', label: 'Information Required', assignTo: 'Nodal Officer', style: 'info', requiresRemarks: true },
    { id: '13_1_NOTICE', label: '13-1 Notice', assignTo: 'Nodal Officer', style: 'info', requiresRemarks: true },
    { id: 'AWARD_PASSED', label: 'Award Passed', assignTo: 'Nodal Officer', style: 'primary', requiresRemarks: true },
    { id: 'MEETING_SCHEDULED', label: 'Meeting Scheduled', assignTo: 'Nodal Officer', style: 'info', requiresRemarks: true },
  ];

  private reviewerActions: WorkflowAction[] = [
    { id: 'SEND_TO_DEPUTY_OMBUDSMAN', label: 'Send to Deputy Ombudsman', assignTo: 'Deputy Ombudsman', style: 'escalate', requiresRemarks: true },
    { id: 'SEND_TO_OMBUDSMAN', label: 'Send to Ombudsman', assignTo: 'Ombudsman', style: 'escalate', requiresRemarks: true },
    { id: 'SEND_BACK_TO_DO', label: 'Send back to Dealing Official', assignTo: 'Dealing Official', style: 'primary', requiresRemarks: true },
    { id: 'COMPLAINT_CLOSED', label: 'Complaint Closed', assignTo: '', style: 'close', requiresRemarks: true },
  ];

  private deputyOmbudsmanActions: WorkflowAction[] = [
    { id: 'SEND_TO_OMBUDSMAN', label: 'Send to Ombudsman', assignTo: 'Ombudsman', style: 'escalate', requiresRemarks: true },
    { id: 'SEND_BACK_TO_DO', label: 'Send back to Dealing Official', assignTo: 'Dealing Official', style: 'primary', requiresRemarks: true },
    { id: 'SEND_BACK_TO_REVIEWER', label: 'Send back to Reviewer', assignTo: 'Reviewer', style: 'primary', requiresRemarks: true },
    { id: 'DEPUTY_OMBUDSMAN_DECISION', label: 'Deputy Ombudsman Decision', assignTo: 'Dealing Official', style: 'escalate', requiresRemarks: true },
    { id: 'SEND_TO_OTHER_OFFICE', label: 'Send to Other Office', assignTo: 'CRPC Head', style: 'info', requiresRemarks: true },
    { id: 'SEND_TO_OTHER_REGULATORY_BODY', label: 'Send to Other Regulatory Body', assignTo: '', style: 'info', requiresRemarks: true },
    { id: 'SEND_TO_OTHER_DEPARTMENT', label: 'Send to Other Department', assignTo: 'Other Department', style: 'info', requiresRemarks: true },
    { id: 'FACILITATION_REJECTION', label: 'Facilitation/Rejection', assignTo: 'Dealing Official', style: 'escalate', requiresRemarks: true },
    { id: 'COMPLAINT_CLOSED', label: 'Complaint Closed', assignTo: '', style: 'close', requiresRemarks: true },
  ];

  private ombudsmanActions: WorkflowAction[] = [
    { id: 'SEND_BACK_TO_DO', label: 'Send back to Dealing Official', assignTo: 'Dealing Official', style: 'primary', requiresRemarks: true },
    { id: 'SEND_BACK_TO_REVIEWER', label: 'Send back to Reviewer', assignTo: 'Reviewer', style: 'primary', requiresRemarks: true },
    { id: 'SEND_BACK_TO_DEPUTY_OMBUDSMAN', label: 'Send back to Deputy Ombudsman', assignTo: 'Deputy Ombudsman', style: 'primary', requiresRemarks: true },
    { id: 'OMBUDSMAN_DECISION', label: 'Ombudsman Decision', assignTo: 'Dealing Official', style: 'escalate', requiresRemarks: true },
    { id: 'SEND_TO_OTHER_OFFICE', label: 'Send to Other Office', assignTo: 'CRPC Head', style: 'info', requiresRemarks: true },
    { id: 'SEND_TO_OTHER_REGULATORY_BODY', label: 'Send to Other Regulatory Body', assignTo: '', style: 'info', requiresRemarks: true },
    { id: 'SEND_TO_OTHER_DEPARTMENT', label: 'Send to Other Department', assignTo: 'Other Department', style: 'info', requiresRemarks: true },
    { id: 'ADVISORY_COMPLIED', label: 'Advisory Complied', assignTo: 'Dealing Official', style: 'primary', requiresRemarks: true },
    { id: 'AWARD_PASSED', label: 'Award Passed', assignTo: 'Dealing Official', style: 'primary', requiresRemarks: true },
    { id: 'COMPLAINT_SETTLED_WITHDRAWN_REJECTED', label: 'Complaint Settled/Withdrawn/Rejected', assignTo: 'Dealing Official', style: 'close', requiresRemarks: true },
    { id: 'COMPLAINT_CLOSED', label: 'Complaint Closed', assignTo: '', style: 'close', requiresRemarks: true },
    { id: 'COMPLAINT_REOPENED', label: 'Complaint Re-opened', assignTo: 'Dealing Official', style: 'info', requiresRemarks: true },
  ];

  isViewOnly = computed<boolean>(() => {
    const status = (this.complaint()?.status || '').toUpperCase();
    const role = (this.loggedInUser?.role || '').toUpperCase();
    const keycloakRoles = this.auth.getRoles().map(r => r.toUpperCase());
    const isDO = role === 'DEALING_OFFICIAL' || role === 'RBIO_OFFICER' || role === 'DO' || keycloakRoles.includes('RBIO_OFFICER');
    if (!isDO) return false;
    const doViewOnlyStatuses = ['SENT_TO_REVIEWER', 'SENT_TO_DEPUTY_OMBUDSMAN', 'SENT_TO_OMBUDSMAN',
      'REVIEWER_REVIEW', 'DEPUTY_REVIEW', 'OMBUDSMAN_REVIEW', 'CLOSED', 'RESOLVED', 'REJECTED', 'WITHDRAWN'];
    return doViewOnlyStatuses.includes(status);
  });

  availableActions = computed<WorkflowAction[]>(() => {
    if (this.isViewOnly()) return [];

    const role = (this.loggedInUser?.role || '').toUpperCase();
    const keycloakRoles = this.auth.getRoles().map(r => r.toUpperCase());

    if (role === 'DEALING_OFFICIAL' || role === 'RBIO_OFFICER' || role === 'DO' || keycloakRoles.includes('RBIO_OFFICER')) {
      return this.dealingOfficialActions;
    }
    if (role === 'REVIEWER' || role === 'RBIO_REVIEWER' || keycloakRoles.includes('RBIO_REVIEWER')) {
      return this.reviewerActions;
    }
    if (role === 'DEPUTY_OMBUDSMAN' || role === 'RBIO_DEPUTY_OMBUDSMAN' || keycloakRoles.includes('RBIO_DEPUTY_OMBUDSMAN')) {
      return this.deputyOmbudsmanActions;
    }
    if (role === 'OMBUDSMAN' || role === 'RBIO_OMBUDSMAN' || keycloakRoles.includes('RBIO_OMBUDSMAN')) {
      return this.ombudsmanActions;
    }
    return this.dealingOfficialActions;
  });

  ngOnInit() {
    const stored = sessionStorage.getItem('rbio_user');
    if (stored) this.loggedInUser = JSON.parse(stored);

    const id = this.route.snapshot.paramMap.get('id');
    if (id) this.loadComplaint(id);
  }

  loadComplaint(id: string) {
    this.loading.set(true);
    const cachedStatus = sessionStorage.getItem(`rbio_status_${id}`);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${id}`).subscribe({
      next: (res) => {
        const d = res.data || res;
        const apiStatus = d.status || 'DRAFT';
        this.complaint.set({
          complaintId: d.complaintId || d.complaintNumber || id,
          complaintNumber: d.complaintNumber || 'Not Assigned',
          complainantName: d.complainantName || '',
          complainantEmail: d.complainantEmail || '',
          subject: d.subject || '',
          description: d.description || '',
          status: cachedStatus || apiStatus,
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
          status: cachedStatus || 'NEW',
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

  openWorkflowAction(action: WorkflowAction) {
    this.pendingWorkflowAction.set(action);
    this.assigneeName.set('');
    this.workflowRemarks.set('');
    this.assignmentMode.set('Automatic');
    this.showWorkflowDropdown.set(false);
    this.showWorkflowModal.set(true);
  }

  confirmWorkflowAction() {
    const c = this.complaint();
    const action = this.pendingWorkflowAction();
    if (!c || !action) return;

    const complaintId = c.complaintNumber || c.complaintId;
    this.processing.set(true);

    const body = {
      action: action.id,
      assignTo: action.assignTo,
      assigneeName: this.assigneeName(),
      assignmentMode: this.assignmentMode(),
      remarks: this.workflowRemarks(),
      actor: this.auth.currentUser()?.username || this.loggedInUser?.name || '',
      systemicIssue: this.systemicIssue()
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintId}`,
      body
    ).subscribe({
      next: () => {
        this.actionSuccess.set(true);
        this.actionResult.set(`Action "${action.label}" completed successfully.`);
        this.processing.set(false);
        this.showWorkflowModal.set(false);
        this.pendingWorkflowAction.set(null);
        this.updateLocalStatus(action.id);
        setTimeout(() => this.actionResult.set(''), 5000);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Failed: ${err.error?.message || err.message || 'Unknown error'}`);
        this.processing.set(false);
        setTimeout(() => this.actionResult.set(''), 5000);
      }
    });
  }

  private updateLocalStatus(actionId: string) {
    const statusMap: Record<string, string> = {
      'SEND_TO_REVIEWER': 'SENT_TO_REVIEWER',
      'SEND_TO_DEPUTY_OMBUDSMAN': 'SENT_TO_DEPUTY_OMBUDSMAN',
      'SEND_TO_OMBUDSMAN': 'SENT_TO_OMBUDSMAN',
      'SEND_BACK_TO_DO': 'SENT_BACK',
      'SEND_BACK_TO_REVIEWER': 'REVIEWER_REVIEW',
      'SEND_BACK_TO_DEPUTY_OMBUDSMAN': 'DEPUTY_REVIEW',
      'COMPLAINT_CLOSED': 'CLOSED',
      'SCHEDULE_MEETING': 'MEETING_SCHEDULED',
      'RESCHEDULE_MEETING': 'MEETING_SCHEDULED',
      'MEETING_COMPLETED': 'MEETING_COMPLETED',
      'MEETING_SCHEDULED': 'MEETING_SCHEDULED',
      'INFORMATION_REQUIRED': 'INFORMATION_REQUIRED',
      'AWARD_PASSED': 'AWARD_PASSED',
    };
    const newStatus = statusMap[actionId];
    if (newStatus) {
      const current = this.complaint();
      if (current) {
        this.complaint.set({ ...current, status: newStatus });
        const complaintId = current.complaintNumber || current.complaintId;
        sessionStorage.setItem(`rbio_status_${complaintId}`, newStatus);
      }
    }
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'NEW': 'New Complaint',
      'DRAFT': 'Draft',
      'ASSIGNED': 'Assigned',
      'SENT_TO_REVIEWER': 'Sent to Reviewer',
      'SENT_TO_DEPUTY_OMBUDSMAN': 'Sent to Deputy Ombudsman',
      'SENT_TO_OMBUDSMAN': 'Sent to Ombudsman',
      'REVIEWER_REVIEW': 'Reviewer Review',
      'DEPUTY_REVIEW': 'Deputy Ombudsman Review',
      'OMBUDSMAN_REVIEW': 'Ombudsman Review',
      'SENT_BACK': 'Sent Back',
      'CLOSED': 'Closed',
      'RESOLVED': 'Resolved',
      'MEETING_SCHEDULED': 'Meeting Scheduled',
      'MEETING_COMPLETED': 'Meeting Completed',
      'INFORMATION_REQUIRED': 'Information Required',
      'AWARD_PASSED': 'Award Passed',
      'IN_PROGRESS': 'In Progress',
    };
    return labels[status?.toUpperCase()] || status || '—';
  }

  cancelWorkflowAction() {
    this.showWorkflowModal.set(false);
    this.pendingWorkflowAction.set(null);
  }

  getExpectedStatus(action: WorkflowAction): string {
    switch (action.id) {
      case 'SEND_TO_REVIEWER': return 'Sent to Reviewer';
      case 'SEND_TO_DEPUTY_OMBUDSMAN': return 'Sent to Deputy Ombudsman';
      case 'SEND_TO_OMBUDSMAN': return 'Sent to Ombudsman';
      case 'SEND_BACK_TO_DO': return 'Sent back to Dealing Official';
      case 'SEND_BACK_TO_REVIEWER': return 'Sent back to Reviewer';
      case 'SEND_BACK_TO_DEPUTY_OMBUDSMAN': return 'Sent back to Deputy Ombudsman';
      case 'SCHEDULE_MEETING': return 'Meeting Scheduled';
      case 'RESCHEDULE_MEETING': return 'Meeting Re-Scheduled';
      case 'MEETING_COMPLETED': return 'Meeting Completed';
      case 'MEETING_SCHEDULED': return 'Meeting Scheduled';
      case 'COMPLAINT_CLOSED': return 'Closed';
      case 'DRAFT_COMPLAINT': return 'Draft Sent to CRPC DEO';
      case 'INFORMATION_REQUIRED': return 'Information Required';
      case '13_1_NOTICE': return '13-1 Notice Issued';
      case 'AWARD_PASSED': return 'Award Passed';
      case 'DEPUTY_OMBUDSMAN_DECISION': return 'Deputy Ombudsman Decision Taken';
      case 'OMBUDSMAN_DECISION': return 'Ombudsman Decision Taken';
      case 'SEND_TO_OTHER_OFFICE': return 'Sent to Other Office';
      case 'SEND_TO_OTHER_REGULATORY_BODY': return 'Sent to Other Regulatory Body';
      case 'SEND_TO_OTHER_DEPARTMENT': return 'Sent to Other Department';
      case 'FACILITATION_REJECTION': return 'Facilitation/Rejection Decision';
      case 'ADVISORY_COMPLIED': return 'Advisory Complied';
      case 'COMPLAINT_SETTLED_WITHDRAWN_REJECTED': return 'Complaint Settled/Withdrawn/Rejected';
      case 'COMPLAINT_REOPENED': return 'Complaint Re-opened';
      default: return 'In Progress';
    }
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
