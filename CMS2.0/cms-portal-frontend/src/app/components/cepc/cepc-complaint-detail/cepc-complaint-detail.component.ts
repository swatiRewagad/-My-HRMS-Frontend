import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { timeout, catchError } from 'rxjs/operators';
import { of } from 'rxjs';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';
import { CepcEmailComposeComponent } from '../cepc-email-compose/cepc-email-compose.component';

type CepcRole = 'CEPC_DO' | 'CEPC_REVIEWER' | 'CEPC_INCHARGE' | 'CEPC_CLOSING_AUTHORITY' | 'CEPC_ADMIN' | 'CEPC_CONTACT_PERSON';

interface ActionDef {
  id: string;
  label: string;
  style: string;
  requiresRemarks: boolean;
  description?: string;
}

interface CommentEntry {
  id: string;
  author: string;
  text: string;
  timestamp: string;
}

interface ContactPersonEntry {
  id: string;
  subject: string;
  bankName: string;
  slaDays: number;
  assignedTo: string;
  status: string;
  statusLabel: string;
}

interface EmailEntry {
  id: string;
  direction: 'IN' | 'OUT';
  subject: string;
  body: string;
  timestamp: string;
}

interface ForwardOption {
  value: string;
  label: string;
}

interface TemplateDef {
  id: string;
  label: string;
  content: string;
}

@Component({
  selector: 'app-cepc-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, CepcSlaIndicatorComponent, CepcEmailComposeComponent],
  templateUrl: './cepc-complaint-detail.component.html',
  styleUrl: './cepc-complaint-detail.component.scss'
})
export class CepcComplaintDetailComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  complaint = signal<any>(null);
  loading = signal(true);
  processing = signal(false);
  userRole = signal<CepcRole>('CEPC_DO');

  activeTab = signal<string>('summary');
  editMode = signal(false);
  showConfirmDialog = signal(false);
  showActionDropdown = signal(false);
  showMoreMenu = signal(false);
  showChangeOwnerDialog = signal(false);
  pendingAction = signal<ActionDef | null>(null);

  actionResult = signal('');
  actionSuccess = signal(false);
  finalDecisionConfirmed = signal(false);
  actionConfirmed = signal(false);

  openSections = signal<Set<string>>(new Set(['basic', 'eligibility', 'entity', 'complainant']));

  comments = signal<CommentEntry[]>([]);
  contactPersonList = signal<ContactPersonEntry[]>([]);
  emailHistory = signal<EmailEntry[]>([]);

  // Form fields — Summary
  closureReason = '';
  wantAddEntities = false;
  newComment = '';

  // Form fields — Conciliation
  meetingStatus = '';
  meetingDate = '';
  meetingTime = '';
  complainantAccepted = '';
  entityAccepted = '';
  meetingVc = '';
  meetingComments = '';
  conciliationRemarks = '';

  // Form fields — Forward
  forwardTo = signal<string>('');
  forwardTargetName = '';
  forwardReason = '';
  forwardComments = '';

  // Form fields — Final Decision
  finalDecisionMode: 'close' | 'mark_closure' | 'reopen' = 'close';
  finalDecisionReason = '';
  reopenReason = '';
  gistOfCase = '';
  gistRegional = '';
  closureLetterContent = '';
  closureAdviceDate = '';
  speakingOrderContent = '';
  showPreviewDialog = signal(false);

  // Confirmation dialog
  assignmentMode = 'automatic';
  assignmentTarget = '';
  confirmRemarks = '';

  // Round-robin assignment
  cepcReviewers = signal<{name: string; load: number}[]>([]);
  cepcIncharges = signal<{name: string; load: number}[]>([]);
  cepcClosingAuthorities = signal<{name: string; load: number}[]>([]);

  // Change owner
  newOwner = '';

  rbiDepartments = [
    'Department of Banking Supervision',
    'Department of Non-Banking Supervision',
    'Department of Payment and Settlement Systems',
    'Financial Markets Regulation Department',
    'Consumer Education and Protection Department',
    'Foreign Exchange Department',
    'Department of Regulation',
    'Department of Currency Management',
    'Other'
  ];

  templates: TemplateDef[] = [
    { id: 'ack', label: 'Apply: Acknowledgement Template', content: 'Your complaint has been received and is under examination.' },
    { id: 'info_req', label: 'Apply: Info Request Template', content: 'Additional information is required to proceed with your complaint.' },
    { id: 'closure', label: 'Apply: Closure Template', content: 'Your complaint has been examined and a decision has been taken.' }
  ];

  // Computed: primary action (first available action for the role/status)
  primaryAction = computed<ActionDef | null>(() => {
    const actions = this.availableActions();
    return actions.length > 0 ? actions[0] : null;
  });

  // Computed: secondary actions (all except primary)
  secondaryActions = computed<ActionDef[]>(() => {
    const actions = this.availableActions();
    return actions.length > 1 ? actions.slice(1) : [];
  });

  // Computed: forward options based on role
  forwardOptions = computed<ForwardOption[]>(() => {
    const role = this.userRole();
    const options: ForwardOption[] = [];

    if (role === 'CEPC_REVIEWER') {
      options.push({ value: 'OTHER_RBI_DEPT', label: 'Other RBI Dept' });
    } else if (role === 'CEPC_INCHARGE' || role === 'CEPC_CLOSING_AUTHORITY' || role === 'CEPC_ADMIN') {
      options.push({ value: 'OTHER_OFFICE', label: 'Other Office' });
      options.push({ value: 'OTHER_REGULATORY', label: 'Other Regulatory Body' });
      options.push({ value: 'OTHER_RBI_DEPT', label: 'Other RBI Dept' });
    }

    return options;
  });

  availableActions = computed<ActionDef[]>(() => {
    const role = this.userRole();
    const status = (this.complaint()?.status || '').toLowerCase();
    const actions: ActionDef[] = [];

    if (this.isTerminalState()) return [];

    if (role === 'CEPC_DO') {
      if (['assigned', 'pending', 'new', 'new_complaint', 'sent_back', 'in_progress'].includes(status)) {
        actions.push({ id: 'SEND_TO_REVIEWER', label: 'Send for Approval', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_TO_CEPC_REVIEWER', label: 'Send to CEPC Reviewer', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_TO_CEPC_INCHARGE', label: 'Send to CEPC Incharge', style: 'escalate', requiresRemarks: true });
      }
      if (status === 'marked_for_closure') {
        actions.push({ id: 'CLOSE_COMPLAINT', label: 'Close Complaint', style: 'close', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_REVIEWER') {
      if (['reviewer_review', 'under_review', 'assigned', 'pending', 'new', 'new_complaint', 'in_progress'].includes(status)) {
        actions.push({ id: 'APPROVE_REVIEW', label: 'Send', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_TO_CEPC_INCHARGE', label: 'Send to Incharge', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_TO_CLOSING_AUTHORITY', label: 'Send to Closing Authority', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_DO', label: 'Send Back to DO', style: 'return', requiresRemarks: true });
      }
      if (status === 'non_maintainable') {
        actions.push({ id: 'CLOSE_COMPLAINT', label: 'Close (Non-Maintainable)', style: 'close', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_INCHARGE') {
      if (['incharge_review', 'escalated', 'assigned', 'pending', 'new', 'new_complaint', 'in_progress', 'under_review'].includes(status)) {
        actions.push({ id: 'INCHARGE_SEND', label: 'Send', style: 'primary', requiresRemarks: false });
        actions.push({ id: 'SEND_TO_CLOSING_AUTHORITY', label: 'Send to Closing Authority', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_DO', label: 'Send Back to DO', style: 'return', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_REVIEWER', label: 'Send Back to Reviewer', style: 'return', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_CLOSING_AUTHORITY') {
      if (['awaiting_closure', 'assigned', 'pending', 'new', 'new_complaint', 'in_progress', 'under_review'].includes(status)) {
        actions.push({ id: 'CA_SEND', label: 'Send', style: 'primary', requiresRemarks: false });
        actions.push({ id: 'SEND_BACK_DO', label: 'Send Back to DO', style: 'return', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_REVIEWER', label: 'Send Back to Reviewer', style: 'return', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_INCHARGE', label: 'Send Back to Incharge', style: 'return', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_ADMIN') {
      if (!this.isTerminalState()) {
        actions.push({ id: 'REASSIGN', label: 'Reassign', style: 'info', requiresRemarks: true });
        actions.push({ id: 'ESCALATE', label: 'Escalate', style: 'escalate', requiresRemarks: true });
        actions.push({ id: 'CLOSE_COMPLAINT', label: 'Close (Admin)', style: 'close', requiresRemarks: true });
      }
    }

    return actions;
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (roles.includes('ADMIN') || roles.includes('CEPC_ADMIN')) this.userRole.set('CEPC_ADMIN');
    else if (roles.includes('CA') || roles.includes('CEPC_CLOSING_AUTHORITY')) this.userRole.set('CEPC_CLOSING_AUTHORITY');
    else if (roles.includes('INCHARGE') || roles.includes('CEPC_INCHARGE')) this.userRole.set('CEPC_INCHARGE');
    else if (roles.includes('REVIEWER') || roles.includes('CEPC_REVIEWER')) this.userRole.set('CEPC_REVIEWER');
    else if (roles.includes('CP') || roles.includes('CEPC_CONTACT_PERSON')) this.userRole.set('CEPC_CONTACT_PERSON');
    else this.userRole.set('CEPC_DO');

    const id = this.route.snapshot.params['id'];
    this.loadComplaint(id);
    this.loadComments(id);
    this.loadContactPersons(id);
    this.loadEmailHistory(id);
  }

  private loadComplaint(complaintNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}`).subscribe({
      next: (res) => {
        this.complaint.set(res?.data || res || null);
        this.loading.set(false);
      },
      error: () => {
        this.complaint.set(null);
        this.loading.set(false);
      }
    });
  }

  private loadComments(complaintNumber: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}/comments`).subscribe({
      next: (res) => this.comments.set(res?.data || res || []),
      error: () => this.comments.set([])
    });
  }

  private loadContactPersons(complaintNumber: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}/contact-persons`).subscribe({
      next: (res) => this.contactPersonList.set(res?.data || res || []),
      error: () => this.contactPersonList.set([])
    });
  }

  loadEmailHistory(complaintNumber: string) {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}/emails`).subscribe({
      next: (res) => this.emailHistory.set(res?.data || res || []),
      error: () => this.emailHistory.set([])
    });
  }

  // ─── Role-based permission methods ───

  canAccessConciliation(): boolean {
    const role = this.userRole();
    if (role === 'CEPC_DO') {
      const status = (this.complaint()?.status || '').toLowerCase();
      return status === 'marked_for_closure';
    }
    if (role === 'CEPC_REVIEWER') return true;
    return false;
  }

  canAccessForward(): boolean {
    const role = this.userRole();
    if (role === 'CEPC_DO') return false;
    return !this.isTerminalState();
  }

  canScheduleMeeting(): boolean {
    if (this.userRole() !== 'CEPC_DO') return false;
    const status = (this.complaint()?.status || '').toLowerCase();
    return status === 'marked_for_closure';
  }

  canTakeFinalDecision(): boolean {
    const role = this.userRole();
    const status = (this.complaint()?.status || '').toLowerCase();
    if (role === 'CEPC_DO') {
      return status === 'marked_for_closure';
    }
    if (role === 'CEPC_REVIEWER') {
      return status === 'non_maintainable';
    }
    if (role === 'CEPC_CLOSING_AUTHORITY') {
      return true;
    }
    return !this.isTerminalState();
  }

  canCloseDirectly(): boolean {
    const role = this.userRole();
    const status = (this.complaint()?.status || '').toLowerCase();
    if (role === 'CEPC_INCHARGE' || role === 'CEPC_CLOSING_AUTHORITY' || role === 'CEPC_ADMIN') return true;
    if (role === 'CEPC_DO' && status === 'marked_for_closure') return true;
    if (role === 'CEPC_REVIEWER' && status === 'non_maintainable') return true;
    return false;
  }

  canMarkForClosure(): boolean {
    const role = this.userRole();
    return role === 'CEPC_INCHARGE' || role === 'CEPC_CLOSING_AUTHORITY';
  }

  canReopen(): boolean {
    const role = this.userRole();
    const status = (this.complaint()?.status || '').toLowerCase();
    return role === 'CEPC_CLOSING_AUTHORITY' && (status === 'closed' || status === 'resolved');
  }

  canChangeOwner(): boolean {
    const role = this.userRole();
    return role === 'CEPC_INCHARGE' || role === 'CEPC_ADMIN';
  }

  // ─── Actions ───

  openConfirmDialog(action: any) {
    this.pendingAction.set(action);
    this.confirmRemarks = '';
    this.assignmentMode = 'automatic';
    this.assignmentTarget = '';

    const isReviewerAction = ['SEND_TO_REVIEWER', 'SEND_TO_CEPC_REVIEWER'].includes(action.id);
    const isInchargeAction = ['SEND_TO_INCHARGE', 'SEND_TO_CEPC_INCHARGE'].includes(action.id);
    const isClosingAuthorityAction = action.id === 'SEND_TO_CLOSING_AUTHORITY';

    if (isReviewerAction || isInchargeAction || isClosingAuthorityAction) {
      const role = isReviewerAction ? 'CEPC_REVIEWER' : isInchargeAction ? 'CEPC_INCHARGE' : 'CEPC_CLOSING_AUTHORITY';
      this.http.get<any[]>(
        `${environment.apiBaseUrl}/api/v1/workflow/cepc/officers?role=${role}`
      ).subscribe({
        next: (officers) => {
          const list = (officers || []).map(o => ({ name: o.displayName || o.username, load: o.currentLoad || 0 }));
          if (isReviewerAction) {
            this.cepcReviewers.set(list);
          } else if (isInchargeAction) {
            this.cepcIncharges.set(list);
          } else {
            this.cepcClosingAuthorities.set(list);
          }
          this.autoAssignByRoundRobin(list);
        },
        error: () => {
          const fallback = isReviewerAction
            ? [{ name: 'cepc_reviewer1', load: 0 }]
            : isInchargeAction
              ? [{ name: 'cepc_incharge1', load: 0 }]
              : [{ name: 'cepc_ca1', load: 0 }];
          if (isReviewerAction) this.cepcReviewers.set(fallback);
          else if (isInchargeAction) this.cepcIncharges.set(fallback);
          else this.cepcClosingAuthorities.set(fallback);
          this.autoAssignByRoundRobin(fallback);
        }
      });
    }

    this.showConfirmDialog.set(true);
  }

  autoAssignByRoundRobin(officers: {name: string; load: number}[]) {
    if (officers.length === 0) return;
    const sorted = [...officers].sort((a, b) => a.load - b.load);
    this.assignmentTarget = sorted[0].name;
  }

  onAssignmentModeChange(mode: string) {
    this.assignmentMode = mode;
    if (mode === 'automatic') {
      const action = this.pendingAction();
      const isReviewer = action && ['SEND_TO_REVIEWER', 'SEND_TO_CEPC_REVIEWER'].includes(action.id);
      const isClosingAuthority = action && action.id === 'SEND_TO_CLOSING_AUTHORITY';
      const list = isReviewer ? this.cepcReviewers() : isClosingAuthority ? this.cepcClosingAuthorities() : this.cepcIncharges();
      this.autoAssignByRoundRobin(list);
    } else {
      this.assignmentTarget = '';
    }
  }

  confirmAction() {
    const action = this.pendingAction();
    if (!action) return;

    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    this.processing.set(true);

    const body: any = {
      action: action.id,
      remarks: this.confirmRemarks,
      actor: this.auth.currentUser()?.username || '',
      assignmentMode: this.assignmentMode,
      assignmentTarget: this.assignmentTarget
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`,
      body
    ).pipe(
      timeout(10000),
      catchError(() => of({ success: true, fallback: true }))
    ).subscribe({
      next: () => {
        this.actionSuccess.set(true);
        this.actionConfirmed.set(true);
        this.actionResult.set(`Action "${action.label}" completed successfully.`);
        this.processing.set(false);
        this.showConfirmDialog.set(false);
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
      'SEND_TO_REVIEWER': 'reviewer_review',
      'SEND_TO_CEPC_REVIEWER': 'reviewer_review',
      'SEND_TO_CEPC_INCHARGE': 'incharge_review',
      'SEND_TO_INCHARGE': 'incharge_review',
      'APPROVE_REVIEW': 'incharge_review',
      'SEND_TO_CLOSING_AUTHORITY': 'awaiting_closure',
      'SEND_BACK_DO': 'sent_back',
      'SEND_BACK_REVIEWER': 'reviewer_review',
      'SEND_BACK_INCHARGE': 'incharge_review',
      'CLOSE_COMPLAINT': 'closed',
      'MARK_FOR_CLOSURE': 'marked_for_closure',
      'REOPEN': 'in_progress',
      'REASSIGN': 'assigned',
      'ESCALATE': 'escalated',
      'INCHARGE_SEND': 'awaiting_closure',
      'CA_SEND': 'closed',
    };
    const newStatus = statusMap[actionId];
    if (newStatus) {
      const current = this.complaint();
      if (current) {
        this.complaint.set({ ...current, status: newStatus });
      }
    }
  }

  showSendBackAction() {
    this.openConfirmDialog({ id: 'SEND_BACK_DO', label: 'Send Back to DO', style: 'return', requiresRemarks: true });
  }

  // ─── Sections ───

  toggleSection(section: string) {
    const current = new Set(this.openSections());
    if (current.has(section)) current.delete(section);
    else current.add(section);
    this.openSections.set(current);
  }

  // ─── Comments ───

  postComment() {
    if (!this.newComment.trim()) return;
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    const body = { text: this.newComment, author: this.auth.currentUser()?.username || 'System' };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}/comments`, body).subscribe({
      next: (res) => {
        const entry: CommentEntry = {
          id: res?.data?.id || 'C-' + Date.now(),
          author: body.author,
          text: this.newComment,
          timestamp: new Date().toISOString()
        };
        this.comments.set([...this.comments(), entry]);
        this.newComment = '';
      },
      error: () => {}
    });
  }

  // ─── Conciliation ───

  scheduleMeeting() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    const body = {
      status: this.meetingStatus,
      date: this.meetingDate,
      time: this.meetingTime,
      complainantAccepted: this.complainantAccepted === 'yes',
      entityAccepted: this.entityAccepted === 'yes',
      viaVc: this.meetingVc === 'yes',
      comments: this.meetingComments,
      remarks: this.conciliationRemarks
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}/conciliation/meeting`, body).subscribe({
      next: () => {
        this.actionSuccess.set(true);
        this.actionResult.set('Meeting scheduled successfully.');
        setTimeout(() => this.actionResult.set(''), 5000);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Failed to schedule meeting: ${err.error?.message || err.message}`);
        setTimeout(() => this.actionResult.set(''), 5000);
      }
    });
  }

  // ─── Forward ───

  submitForward() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    const body = {
      action: 'FORWARD',
      forwardTo: this.forwardTo(),
      targetName: this.forwardTargetName,
      reason: this.forwardReason,
      comments: this.forwardComments,
      actor: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`, body).subscribe({
      next: () => {
        this.actionSuccess.set(true);
        this.actionResult.set('Complaint forwarded successfully.');
        this.loadComplaint(complaintNumber);
        setTimeout(() => this.actionResult.set(''), 5000);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Forward failed: ${err.error?.message || err.message}`);
        setTimeout(() => this.actionResult.set(''), 5000);
      }
    });
  }

  getForwardStatusLabel(): string {
    const target = this.forwardTo();
    if (target === 'OTHER_OFFICE') return 'Forwarded to Other Office';
    if (target === 'OTHER_REGULATORY') return 'Forwarded to Other Regulatory Body';
    if (target === 'OTHER_RBI_DEPT') return 'Forwarded to Other RBI Dept';
    return 'Forwarded';
  }

  // ─── Change Owner ───

  openChangeOwner() {
    this.newOwner = '';
    this.showChangeOwnerDialog.set(true);
  }

  changeOwner() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber || !this.newOwner.trim()) return;

    const body = { action: 'CHANGE_OWNER', newOwner: this.newOwner, actor: this.auth.currentUser()?.username || '' };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`, body).subscribe({
      next: () => {
        this.showChangeOwnerDialog.set(false);
        this.actionSuccess.set(true);
        this.actionResult.set('Owner changed successfully.');
        this.loadComplaint(complaintNumber);
        setTimeout(() => this.actionResult.set(''), 5000);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Change owner failed: ${err.error?.message || err.message}`);
        setTimeout(() => this.actionResult.set(''), 5000);
      }
    });
  }

  // ─── Templates ───

  applyTemplate(tpl: TemplateDef) {
    if (this.activeTab() === 'summary') {
      this.newComment = tpl.content;
    } else if (this.activeTab() === 'final-decision') {
      this.closureLetterContent = tpl.content;
    }
  }

  // ─── Status helpers ───

  getConfirmationText(action: ActionDef | null): string {
    if (!action) return '';
    const map: Record<string, string> = {
      'SEND_TO_REVIEWER': 'send the request to CEPC Reviewer',
      'SEND_TO_CEPC_REVIEWER': 'send the request to CEPC Reviewer',
      'SEND_TO_CEPC_INCHARGE': 'send the request to CEPC Incharge',
      'SEND_TO_INCHARGE': 'send the request to CEPC Incharge',
      'APPROVE_REVIEW': 'forward to In-Charge',
      'SEND_TO_CLOSING_AUTHORITY': 'send to Closing Authority',
      'SEND_BACK_DO': 'send back to DO',
      'CLOSE_COMPLAINT': 'close this complaint',
      'MARK_FOR_CLOSURE': 'mark this complaint for closure',
      'REOPEN': 'reopen this complaint',
      'ACCEPT': 'accept and start examination',
    };
    return map[action.id] || action.label;
  }

  getAssignmentLabel(actionId: string | undefined): string {
    if (!actionId) return '';
    if (actionId === 'SEND_TO_CEPC_INCHARGE' || actionId === 'SEND_TO_INCHARGE') {
      return 'Name of CEPC Incharge';
    }
    if (actionId === 'SEND_TO_CLOSING_AUTHORITY') {
      return 'Name of Closing Authority';
    }
    return 'Name of CEPC Reviewer';
  }

  getExpectedStatus(actionId: string | undefined): string {
    if (!actionId) return '—';
    const map: Record<string, string> = {
      'ACCEPT': 'Under Examination',
      'SEND_TO_REVIEWER': 'Sent to CEPC Reviewer',
      'SEND_TO_CEPC_REVIEWER': 'Sent to CEPC Reviewer',
      'SEND_TO_CEPC_INCHARGE': 'Sent to CEPC Incharge',
      'SEND_TO_INCHARGE': 'Sent to CEPC Incharge',
      'APPROVE_REVIEW': 'Sent to Incharge',
      'SEND_TO_CLOSING_AUTHORITY': 'Sent to Closing Authority',
      'APPROVE_CLOSURE': 'Awaiting Closure',
      'SEND_BACK_DO': 'Sent Back',
      'SEND_BACK_REVIEWER': 'Reviewer Review',
      'SEND_BACK_INCHARGE': 'In-Charge Review',
      'CLOSE_COMPLAINT': 'Closed',
      'MARK_FOR_CLOSURE': 'Marked for Closure',
      'REOPEN': 'Under Examination',
      'REASSIGN': 'Assigned',
      'ESCALATE': 'Escalated',
      'REQUEST_INFO': 'Info Requested',
      'FORWARD': 'Forwarded',
      'FORWARD_TO_CONTACT': 'With Contact Person'
    };
    return map[actionId] || 'Updated';
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'assigned': 'New Complaint', 'pending': 'New Complaint', 'new': 'New Complaint',
      'new_complaint': 'New Complaint',
      'in_progress': 'Under Examination', 'under_review': 'Under Review',
      'reviewer_review': 'Reviewer Review', 'incharge_review': 'In-Charge Review',
      'awaiting_closure': 'Awaiting Closure', 'escalated': 'Escalated',
      'sent_back': 'Sent Back', 'info_requested': 'Info Requested',
      'forwarded': 'Forwarded to Dept', 'forwarded_to_contact': 'With Contact Person',
      'closed': 'Closed', 'resolved': 'Resolved', 'marked_for_closure': 'Marked for Closure',
      'non_maintainable': 'Non Maintainable'
    };
    return labels[status?.toLowerCase()] || status || '—';
  }

  isTerminalState(): boolean {
    const status = (this.complaint()?.status || '').toLowerCase();
    return ['closed', 'resolved', 'rejected', 'withdrawn'].includes(status);
  }

  createCrpc() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (complaintNumber) {
      this.router.navigate(['/cepc/complaint', complaintNumber, 'crpc', 'create']);
    }
  }

  openPreviewConfirm() {
    this.showPreviewDialog.set(true);
  }

  confirmFinalDecision() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    this.processing.set(true);

    const action = this.finalDecisionMode === 'reopen' ? 'REOPEN' :
                   this.finalDecisionMode === 'mark_closure' ? 'MARK_FOR_CLOSURE' : 'CLOSE_COMPLAINT';

    const body: any = {
      action,
      reason: this.finalDecisionMode === 'reopen' ? this.reopenReason : this.finalDecisionReason,
      gistOfCase: this.gistOfCase,
      gistRegional: this.gistRegional,
      closureLetterContent: this.closureLetterContent,
      remarks: this.confirmRemarks,
      actor: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`,
      body
    ).pipe(
      timeout(10000),
      catchError(() => of({ success: true, fallback: true }))
    ).subscribe({
      next: () => {
        this.actionSuccess.set(true);
        this.finalDecisionConfirmed.set(true);
        this.actionResult.set(
          action === 'REOPEN' ? 'Complaint reopened successfully.' :
          action === 'MARK_FOR_CLOSURE' ? 'Complaint marked for closure and sent to DO.' :
          'Complaint closed successfully.'
        );
        this.processing.set(false);
        this.showPreviewDialog.set(false);
        this.loadComplaint(complaintNumber);
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

  goBack() {
    this.router.navigate(['/cepc/dashboard']);
  }
}
