import { Component, inject, OnInit, OnDestroy, signal, computed, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { TatService, TatResult } from '../../../services/tat.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { RbioConciliationComponent } from '../../rbio/rbio-conciliation/rbio-conciliation.component';
import { RbioAdjudicationComponent } from '../../rbio/rbio-adjudication/rbio-adjudication.component';
import { RbioAdvisoryComponent } from '../../rbio/rbio-advisory/rbio-advisory.component';
import { RbioSlaProgressComponent } from '../../rbio/rbio-sla-progress/rbio-sla-progress.component';
import { highlightEmailText, escapeHtml } from '../../../utils/highlight-text.util';

@Component({
  selector: 'app-task-action',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, RbioConciliationComponent, RbioAdjudicationComponent, RbioAdvisoryComponent, RbioSlaProgressComponent],
  templateUrl: './task-action.component.html',
  styleUrls: ['./task-action.component.scss']
})
export class TaskActionComponent implements OnInit, OnDestroy {
  auth = inject(KeycloakAuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private tatService = inject(TatService);

  @ViewChild('actionSection') actionSectionEl!: ElementRef;

  complaint = signal<any>(null);
  loading = signal(true);
  processing = signal(false);
  selectedAction = signal<string>('');
  actionResult = signal<string>('');
  actionSuccess = signal(false);
  availableActions = signal<{label: string; value: string; style: string}[]>([]);
  remarks = '';

  // TAT Timer
  tatData = signal<TatResult | null>(null);
  tatTimerDisplay = signal('');
  private tatInterval: any;
  Math = Math;

  // MRE Copilot
  copilotData = signal<any>(null);
  copilotLoading = signal(false);
  showCopilot = signal(false);

  // Panel expand/collapse
  expandedPanel = signal<'email' | 'complaint' | null>(null);

  // Section open state
  sectionOpen = {
    basic: true,
    complainant: true,
    entity: false,
    copilot: false,
    actions: true,
    timeline: false
  };

  // Sliding panels
  showSimilarPanel = signal(false);
  showHistoryPanel = signal(false);
  loadingSimilarCases = signal(false);
  similarCases = signal<any[]>([]);

  // Right sidebar - Past Complaints
  pastComplaints = signal<any[]>([]);
  loadingPastComplaints = signal(false);
  pastComplaintSearch = '';

  // Email body highlight on field focus
  focusedFieldValue = signal<string>('');

  highlightedEmailBody = computed(() => {
    const c = this.complaint();
    if (!c?.description) return '';
    const fieldVal = this.focusedFieldValue();
    if (!fieldVal) return escapeHtml(c.description);
    return highlightEmailText(c.description, fieldVal);
  });

  isEmailSource = computed(() => {
    const c = this.complaint();
    if (!c) return false;
    return !!(c.description || c.subject) && !this.hasOnlyPdfAttachments();
  });

  private hasOnlyPdfAttachments(): boolean {
    const c = this.complaint();
    if (!c?.attachments?.length) return false;
    const hasEmailBody = !!(c.description && c.description.trim().length > 0);
    if (hasEmailBody) return false;
    return c.attachments.every((att: any) => att.type?.includes('pdf') || att.name?.endsWith('.pdf'));
  }

  onFieldFocus(fieldValue: string | undefined | null) {
    if (!this.isEmailSource()) return;
    this.focusedFieldValue.set(fieldValue?.trim() || '');
  }

  onFieldBlur() {
    this.focusedFieldValue.set('');
  }

  filteredPastComplaints = computed(() => {
    const search = this.pastComplaintSearch.toLowerCase().trim();
    const list = this.pastComplaints();
    if (!search) return list;
    return list.filter((pc: any) =>
      (pc.complaintId || '').toLowerCase().includes(search) ||
      (pc.subject || '').toLowerCase().includes(search)
    );
  });

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const id = this.route.snapshot.params['id'];
    this.loadComplaint(id);
  }

  ngOnDestroy() {
    if (this.tatInterval) clearInterval(this.tatInterval);
  }

  private loadComplaint(complaintNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}`)
      .subscribe({
        next: (res) => {
          this.complaint.set(res.data);
          this.determineActions();
          this.loading.set(false);
          this.loadTat(complaintNumber);
          this.loadPastComplaints();
        },
        error: () => {
          this.complaint.set(null);
          this.loading.set(false);
        }
      });
  }

  private loadTat(complaintNumber: string) {
    this.tatService.getComplaintTat(complaintNumber).subscribe({
      next: (tat) => {
        this.tatData.set(tat);
        this.updateTatDisplay();
        if (!tat.breached && tat.status !== 'RESOLVED') {
          this.tatInterval = setInterval(() => this.updateTatDisplay(), 60000);
        }
      },
      error: () => {}
    });
  }

  private updateTatDisplay() {
    const tat = this.tatData();
    if (!tat) return;
    if (tat.breached) {
      this.tatTimerDisplay.set('SLA BREACHED');
    } else if (tat.remainingBusinessHours <= 0) {
      this.tatTimerDisplay.set('SLA BREACHED');
    } else {
      const days = Math.floor(tat.remainingBusinessHours / 9);
      const hrs = Math.round(tat.remainingBusinessHours % 9);
      this.tatTimerDisplay.set(`${days}d ${hrs}h remaining`);
    }
  }

  getTatProgressColor(): string {
    const tat = this.tatData();
    if (!tat) return '#2e7d32';
    if (tat.percentUsed >= 90 || tat.breached) return '#c62828';
    if (tat.percentUsed >= 70) return '#f57c00';
    return '#2e7d32';
  }

  loadCopilot() {
    const existing = this.copilotData();
    if ((existing && !existing.error) || this.copilotLoading()) return;
    const c = this.complaint();
    const cid = c?.id || c?.complaintId;
    if (!cid) return;
    this.copilotData.set(null);
    this.copilotLoading.set(true);
    this.showCopilot.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/copilot/maintainability/${cid}`)
      .subscribe({
        next: (res) => {
          this.copilotData.set(res);
          this.copilotLoading.set(false);
        },
        error: () => {
          this.copilotData.set({ error: true, suggestedDetermination: 'UNABLE_TO_ASSESS', mreVerdict: { overallSignal: 'Service unavailable', grounds: [] } });
          this.copilotLoading.set(false);
        }
      });
  }

  toggleExpand(panel: 'email' | 'complaint') {
    this.expandedPanel.set(this.expandedPanel() === panel ? null : panel);
  }

  toggleSimilarPanel() {
    const isOpen = !this.showSimilarPanel();
    this.showSimilarPanel.set(isOpen);
    if (isOpen && this.similarCases().length === 0) {
      this.loadSimilarCases();
    }
  }

  toggleHistoryPanel() {
    this.showHistoryPanel.set(!this.showHistoryPanel());
  }

  loadSimilarCases() {
    const c = this.complaint();
    const cid = c?.id || c?.complaintId;
    if (!cid) return;
    this.loadingSimilarCases.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${cid}/similar`)
      .subscribe({
        next: (res) => {
          this.similarCases.set(res.data || res || []);
          this.loadingSimilarCases.set(false);
        },
        error: () => {
          this.similarCases.set([]);
          this.loadingSimilarCases.set(false);
        }
      });
  }

  loadPastComplaints() {
    const c = this.complaint();
    const email = c?.complainantEmail;
    if (!email) return;
    this.loadingPastComplaints.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints`, { params: { complainantEmail: email } })
      .subscribe({
        next: (res) => {
          const list = res.data || res.content || res || [];
          this.pastComplaints.set(Array.isArray(list) ? list : []);
          this.loadingPastComplaints.set(false);
        },
        error: () => {
          this.pastComplaints.set([]);
          this.loadingPastComplaints.set(false);
        }
      });
  }

  getDepartmentLabel(): string {
    const dept = this.auth.currentUser()?.department || 'RBIO';
    if (dept === 'CEPC') return 'CEPC, Chandigarh';
    return `RBIO, Mumbai`;
  }

  scrollToActions() {
    this.sectionOpen['actions'] = true;
    setTimeout(() => {
      this.actionSectionEl?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 100);
  }

  private determineActions() {
    const roles = this.auth.getRoles();
    const status = (this.complaint()?.status || '').toLowerCase();
    const actions: {label: string; value: string; style: string}[] = [];

    if (this.isTerminalState()) {
      if (roles.includes('CEPC_CLOSING_AUTHORITY') || roles.includes('CEPC_ADMIN') || roles.includes('ADMIN')) {
        if (status === 'closed' || status === 'resolved') {
          actions.push({ label: 'Reopen Complaint', value: 'REOPEN', style: 'escalate' });
        }
      }
      this.availableActions.set(actions);
      return;
    }

    if (roles.includes('CEPC_DO')) {
      if (status === 'assigned' || status === 'in_progress' || status === 'sent_back') {
        actions.push({ label: 'Forward to Reviewer', value: 'SUBMIT_FOR_REVIEW', style: 'approve' });
        actions.push({ label: 'Forward to In-Charge', value: 'FORWARD_TO_INCHARGE', style: 'approve' });
        actions.push({ label: 'Request Info', value: 'REQUEST_INFO', style: 'escalate' });
        actions.push({ label: 'Forward to Contact Person', value: 'FORWARD_TO_CONTACT', style: 'resolve' });
      }
    }

    if (roles.includes('CEPC_REVIEWER')) {
      if (status === 'reviewer_review' || status === 'in_progress') {
        actions.push({ label: 'Forward to In-Charge', value: 'APPROVE_REVIEW', style: 'approve' });
        actions.push({ label: 'Forward to Closing Authority', value: 'FORWARD_TO_CLOSING_AUTHORITY', style: 'approve' });
        actions.push({ label: 'Send Back to DO', value: 'SEND_BACK_DO', style: 'return' });
      }
    }

    if (roles.includes('CEPC_INCHARGE')) {
      if (status === 'incharge_review' || status === 'in_progress') {
        actions.push({ label: 'Forward to Closing Authority', value: 'APPROVE_CLOSURE', style: 'approve' });
        actions.push({ label: 'Send Back to Reviewer', value: 'SEND_BACK_REVIEWER', style: 'return' });
        actions.push({ label: 'Send Back to DO', value: 'SEND_BACK_DO', style: 'return' });
      }
    }

    if (roles.includes('CEPC_CLOSING_AUTHORITY')) {
      if (status === 'awaiting_closure' || status === 'in_progress') {
        actions.push({ label: 'Close Complaint', value: 'CLOSE_COMPLAINT', style: 'approve' });
        actions.push({ label: 'Send Back to In-Charge', value: 'SEND_BACK_INCHARGE', style: 'return' });
        actions.push({ label: 'Forward to Other Office', value: 'FORWARD_TO_OTHER_OFFICE', style: 'escalate' });
        actions.push({ label: 'Forward to Regulatory Body', value: 'FORWARD_TO_REGULATORY_BODY', style: 'escalate' });
        actions.push({ label: 'Forward to Other RBI Dept', value: 'FORWARD_TO_OTHER_RBI_DEPT', style: 'escalate' });
      }
    }

    if (roles.includes('RBIO_OFFICER')) {
      if (status === 'pending' || status === 'assigned' || status === 'in_progress') {
        actions.push({ label: 'Escalate', value: 'ESCALATE', style: 'escalate' });
        actions.push({ label: 'Resolve', value: 'RESOLVE', style: 'resolve' });
        actions.push({ label: 'Reject', value: 'REJECT', style: 'reject' });
      }
    }

    if (roles.includes('RBIO_SUPERVISOR')) {
      if (status === 'escalated' || status === 'in_progress') {
        actions.push({ label: 'Approve & Escalate', value: 'APPROVE', style: 'approve' });
        actions.push({ label: 'Return to Officer', value: 'RETURN_TO_OFFICER', style: 'return' });
        actions.push({ label: 'Resolve', value: 'RESOLVE', style: 'resolve' });
      }
    }

    if (roles.includes('RBIO_CONCILIATOR')) {
      if (status === 'escalated' || status === 'conciliation') {
        actions.push({ label: 'Conciliation Success', value: 'CONCILIATION_SUCCESS', style: 'approve' });
        actions.push({ label: 'Conciliation Failed', value: 'CONCILIATION_FAILED', style: 'escalate' });
      }
    }

    if (roles.includes('RBIO_ADJUDICATOR')) {
      if (status === 'escalated' || status === 'adjudication') {
        actions.push({ label: 'Award (Adjudication)', value: 'ADJUDICATION_AWARD', style: 'approve' });
        actions.push({ label: 'Reject', value: 'REJECT', style: 'reject' });
      }
    }

    if (roles.includes('CEPC_ADMIN') || roles.includes('ADMIN')) {
      actions.push({ label: 'Reassign', value: 'REASSIGN', style: 'resolve' });
      if (status === 'closed' || status === 'resolved') {
        actions.push({ label: 'Reopen', value: 'REOPEN', style: 'escalate' });
      }
    }

    this.availableActions.set(actions);
  }

  selectAction(action: string) {
    this.selectedAction.set(action);
    this.actionResult.set('');
    this.remarks = '';
  }

  cancelAction() {
    this.selectedAction.set('');
    this.remarks = '';
  }

  submitAction() {
    const dept = this.auth.currentUser()?.department || 'RBIO';
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    const actor = this.auth.currentUser()?.username || '';

    this.processing.set(true);
    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/${dept.toLowerCase()}/action/${complaintNumber}`,
      {
        action: this.selectedAction(),
        remarks: this.remarks,
        actor: actor
      }
    ).subscribe({
      next: (res) => {
        this.actionSuccess.set(true);
        this.actionResult.set(`Action "${this.selectedAction()}" completed. New status: ${res.data?.newStatus}`);
        this.processing.set(false);
        this.selectedAction.set('');
        this.loadComplaint(complaintNumber);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Failed: ${err.error?.message || err.message || 'Unknown error'}`);
        this.processing.set(false);
      }
    });
  }

  isTerminalState(): boolean {
    const status = (this.complaint()?.status || '').toLowerCase();
    return ['resolved', 'closed', 'rejected', 'withdrawn', 'adjudicated', 'conciliated', 'approved'].includes(status);
  }

  goBack() {
    const dept = this.auth.currentUser()?.department?.toLowerCase() || 'rbio';
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigate([`/staff/${dept}/tasks`]);
    });
  }

  // RBIO component visibility helpers
  isRbioComplaint(): boolean {
    const dept = this.auth.currentUser()?.department || '';
    return dept.toUpperCase() === 'RBIO';
  }

  showRbioConciliation(): boolean {
    if (!this.isRbioComplaint()) return false;
    const roles = this.auth.getRoles();
    const status = (this.complaint()?.status || '').toLowerCase();
    return roles.includes('RBIO_CONCILIATOR') && (status === 'conciliation' || status === 'escalated');
  }

  showRbioAdjudication(): boolean {
    if (!this.isRbioComplaint()) return false;
    const roles = this.auth.getRoles();
    const status = (this.complaint()?.status || '').toLowerCase();
    return roles.includes('RBIO_ADJUDICATOR') && (status === 'adjudication' || status === 'escalated');
  }

  showRbioAdvisory(): boolean {
    if (!this.isRbioComplaint()) return false;
    const roles = this.auth.getRoles();
    const isOfficerOrSupervisor = roles.includes('RBIO_OFFICER') || roles.includes('RBIO_SUPERVISOR');
    return isOfficerOrSupervisor && !this.isTerminalState();
  }

  getRbioCurrentStage(): string {
    return this.complaint()?.workflowStage || this.complaint()?.status || '';
  }
}
