import { Component, inject, OnInit, OnDestroy, signal, computed, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { TatService, TatResult } from '../../../services/tat.service';
import { RbioWorkflowService, ReassignmentCandidate } from '../../../services/rbio-workflow.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { RbioConciliationComponent } from '../../rbio/rbio-conciliation/rbio-conciliation.component';
import { RbioAdjudicationComponent } from '../../rbio/rbio-adjudication/rbio-adjudication.component';
import { RbioAdvisoryComponent } from '../../rbio/rbio-advisory/rbio-advisory.component';
import { RbioSlaProgressComponent } from '../../rbio/rbio-sla-progress/rbio-sla-progress.component';
import { RbioDeputyDecisionComponent } from '../../rbio/rbio-deputy-decision/rbio-deputy-decision.component';
import { RbioAddEntityComponent } from '../../rbio/rbio-add-entity/rbio-add-entity.component';
import { RbioLegalCaseComponent } from '../../rbio/rbio-legal-case/rbio-legal-case.component';
import { RbioForwardRegulatoryComponent } from '../../rbio/rbio-forward-regulatory/rbio-forward-regulatory.component';
import { RbioActionOverrideHistoryComponent } from '../../rbio/rbio-action-override-history/rbio-action-override-history.component';
import { highlightEmailText, escapeHtml } from '../../../utils/highlight-text.util';

@Component({
  selector: 'app-task-action',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, RbioConciliationComponent, RbioAdjudicationComponent, RbioAdvisoryComponent, RbioSlaProgressComponent, RbioDeputyDecisionComponent, RbioAddEntityComponent, RbioLegalCaseComponent, RbioForwardRegulatoryComponent, RbioActionOverrideHistoryComponent],
  templateUrl: './task-action.component.html',
  styleUrls: ['./task-action.component.scss']
})
export class TaskActionComponent implements OnInit, OnDestroy {
  auth = inject(KeycloakAuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private tatService = inject(TatService);
  private rbioWorkflow = inject(RbioWorkflowService);

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

  // ═══ Closure Features (UST504-509, UST576, UST577, UST580, UST581-584) ═══
  showClosureConfirmPopup = signal(false);
  showNoEmailPopup = signal(false);
  showSampleLetterModal = signal(false);
  closureLetterDispatching = signal(false);
  customClosureText = signal('');
  closureClause = signal('');
  allowedClosureClauses = signal<any[]>([]);
  loadingClosureClauses = signal(false);
  dateOfSending = signal('');
  closureLetterFile = signal<File | null>(null);
  sampleLetterClause = signal('');

  // ═══ Email Restriction (UST656) ═══
  emailValidationError = signal('');

  get customClosureTextLength(): number {
    return this.customClosureText().length;
  }

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
          this.checkFinalDecisionStatus();
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

    if (roles.includes('RBIO_DEPUTY_OMBUDSMAN')) {
      if (status === 'deputy_review' || status === 'in_progress' || status === 'pending_deputy_decision') {
        actions.push({ label: 'Send Back to DO', value: 'SEND_BACK_DO', style: 'return' });
        actions.push({ label: 'Send Back to Reviewer', value: 'SEND_BACK_REVIEWER', style: 'return' });
        actions.push({ label: 'Forward to Ombudsman', value: 'FORWARD_TO_OMBUDSMAN', style: 'approve' });
      }
    }

    if (roles.includes('RBIO_ADJUDICATOR')) {
      if (status === 'escalated' || status === 'adjudication') {
        actions.push({ label: 'Award (Adjudication)', value: 'ADJUDICATION_AWARD', style: 'approve' });
        actions.push({ label: 'Reject', value: 'REJECT', style: 'reject' });
        actions.push({ label: 'Send Back to DO', value: 'SEND_BACK_DO', style: 'return' });
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
    const action = this.selectedAction();
    // UST577: If closure action, show confirmation popup first
    if (action === 'CLOSE_COMPLAINT') {
      this.initiateClosureFlow();
      return;
    }
    this.executeWorkflowAction();
  }

  private initiateClosureFlow() {
    const c = this.complaint();
    const hasEmail = c?.complainantEmail && c.complainantEmail.trim().length > 0;
    if (hasEmail) {
      this.showClosureConfirmPopup.set(true);
    } else {
      this.showNoEmailPopup.set(true);
    }
  }

  confirmClosureDispatch() {
    this.showClosureConfirmPopup.set(false);
    this.executeWorkflowAction();
  }

  editClosureForm() {
    this.showClosureConfirmPopup.set(false);
  }

  async generateClosureLetterPdf() {
    const { jsPDF } = await import('jspdf');
    const c = this.complaint();
    const doc = new jsPDF();
    const pageWidth = doc.internal.pageSize.getWidth();
    doc.setFontSize(14);
    doc.setFont('helvetica', 'bold');
    doc.text('RESERVE BANK OF INDIA', pageWidth / 2, 20, { align: 'center' });
    doc.setFontSize(11);
    doc.text('COMPLAINT RESOLUTION & PROCESS CELL', pageWidth / 2, 28, { align: 'center' });
    doc.setFontSize(10);
    doc.setFont('helvetica', 'normal');
    doc.text('Closure Communication', pageWidth / 2, 35, { align: 'center' });
    let y = 50;
    doc.text(`Ref No: ${c?.complaintNumber || ''}`, 14, y); y += 7;
    doc.text(`Date: ${new Date().toLocaleDateString('en-IN')}`, 14, y); y += 10;
    doc.text(`To: ${c?.complainantName || ''}`, 14, y); y += 7;
    if (c?.complainantAddress) { doc.text(`Address: ${c.complainantAddress}`, 14, y); y += 10; }
    doc.setFont('helvetica', 'bold');
    doc.text(`Subject: Closure of Complaint - ${c?.complaintNumber || ''}`, 14, y); y += 12;
    doc.setFont('helvetica', 'normal');
    const bodyText = `Dear ${c?.complainantName || 'Sir/Madam'},\n\nThis is to inform you that your complaint bearing reference number ${c?.complaintNumber || ''} has been examined and is being closed under the provisions of the RBI Integrated Ombudsman Scheme.`;
    const splitBody = doc.splitTextToSize(bodyText, pageWidth - 28);
    doc.text(splitBody, 14, y); y += splitBody.length * 5 + 10;
    if (this.closureClause()) { doc.text(`Closure Clause: ${this.closureClause()}`, 14, y); y += 10; }
    if (this.customClosureText()) {
      const customSplit = doc.splitTextToSize(this.customClosureText(), pageWidth - 28);
      doc.text(customSplit, 14, y); y += customSplit.length * 5 + 10;
    }
    y += 15;
    doc.text('Yours faithfully,', 14, y); y += 10;
    const authorityName = ((this.auth.currentUser()?.firstName || '') + ' ' + (this.auth.currentUser()?.lastName || '')).trim();
    doc.setFont('helvetica', 'bold');
    doc.text(authorityName || 'Closing Authority', 14, y); y += 7;
    doc.setFont('helvetica', 'normal');
    const rolesForPdf = this.auth.getRoles();
    const desig = rolesForPdf.includes('CEPC_CLOSING_AUTHORITY') ? 'Closing Authority, CEPC' :
                  rolesForPdf.includes('RBIO_ADJUDICATOR') ? 'Ombudsman, RBIO' : 'Officer, CMS';
    doc.text(desig, 14, y); y += 7;
    doc.setFont('helvetica', 'italic');
    doc.setTextColor(100, 100, 100);
    doc.text('[Digitally Signed]', 14, y); y += 5;
    doc.setFontSize(8);
    doc.text(`Signed at: ${new Date().toLocaleString('en-IN')}`, 14, y);
    doc.save(`Closure_Letter_${c?.complaintNumber || 'draft'}.pdf`);
  }

  onClosureLetterFileUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files?.length) { this.closureLetterFile.set(input.files[0]); }
  }

  completeManualClosure() {
    if (!this.dateOfSending() || !this.closureLetterFile()) return;
    this.showNoEmailPopup.set(false);
    this.executeWorkflowAction();
  }

  private executeWorkflowAction() {
    const dept = this.auth.currentUser()?.department || 'RBIO';
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    const actor = this.auth.currentUser()?.username || '';
    const authorityName = ((this.auth.currentUser()?.firstName || '') + ' ' + (this.auth.currentUser()?.lastName || '')).trim();
    const roles = this.auth.getRoles();
    const designation = roles.includes('CEPC_CLOSING_AUTHORITY') ? 'Closing Authority, CEPC' :
                        roles.includes('RBIO_ADJUDICATOR') ? 'Ombudsman, RBIO' : 'Officer, CMS';
    this.processing.set(true);
    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/${dept.toLowerCase()}/action/${complaintNumber}`,
      {
        action: this.selectedAction(),
        remarks: this.remarks,
        actor,
        customClosureText: this.customClosureText(),
        closureClause: this.closureClause(),
        closureAuthorityName: authorityName,
        closureAuthorityDesignation: designation,
        dateOfSending: this.dateOfSending()
      }
    ).subscribe({
      next: (res) => {
        this.actionSuccess.set(true);
        const newStatus = res.data?.newStatus || '';
        const assignedRole = res.data?.assignedRole || '';
        const msg = assignedRole
          ? `Action completed. Complaint forwarded to ${assignedRole.replace(/_/g, ' ')}. Status: ${newStatus}`
          : `Action completed. New status: ${newStatus}`;
        this.actionResult.set(msg);
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

  loadClosureClauses() {
    const roles = this.auth.getRoles();
    let role = 'REVIEWER';
    if (roles.includes('CEPC_CLOSING_AUTHORITY') || roles.includes('RBIO_ADJUDICATOR')) role = 'OMBUDSMAN';
    else if (roles.includes('CEPC_INCHARGE') || roles.includes('RBIO_SUPERVISOR')) role = 'DEPUTY_OMBUDSMAN';
    this.loadingClosureClauses.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/workflow/closure-clauses`, { params: { role } })
      .subscribe({
        next: (res) => { this.allowedClosureClauses.set(res?.data || []); this.loadingClosureClauses.set(false); },
        error: () => { this.allowedClosureClauses.set([]); this.loadingClosureClauses.set(false); }
      });
  }

  onClosureClauseChange(clauseCode: string) {
    this.closureClause.set(clauseCode);
    const clause = this.allowedClosureClauses().find((cl: any) => cl.code === clauseCode);
    if (clause?.newIn2026) {
      this.sampleLetterClause.set(clauseCode);
      this.showSampleLetterModal.set(true);
    }
  }

  acknowledgeSampleLetter() {
    this.showSampleLetterModal.set(false);
  }

  validateEmailRecipients(recipients: string[]): boolean {
    const invalidEmails = recipients.filter(email =>
      !email.toLowerCase().endsWith('@rbi.org.in') && !email.toLowerCase().endsWith('@rbi.gov.in')
    );
    if (invalidEmails.length > 0) {
      this.emailValidationError.set('Only official RBI email addresses can be used for outbound complaint emails');
      this.http.post(`${environment.apiBaseUrl}/api/v1/workflow/validate-email-recipients`, {
        recipients, actor: this.auth.currentUser()?.username || 'unknown'
      }).subscribe();
      return false;
    }
    this.emailValidationError.set('');
    return true;
  }

  isTerminalState(): boolean {
    const status = (this.complaint()?.status || '').toLowerCase();
    return ['resolved', 'closed', 'rejected', 'withdrawn', 'adjudicated', 'conciliated'].includes(status);
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

  // ═══ Feature: Deputy Ombudsman Decision (UST758) ═══
  showDeputyDecision(): boolean {
    if (!this.isRbioComplaint()) return false;
    return this.auth.hasRole('RBIO_DEPUTY_OMBUDSMAN');
  }

  onDeputyDecisionSubmitted(event: { action: string; result: any }) {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (complaintNumber) {
      this.loadComplaint(complaintNumber);
    }
  }

  // ═══ Feature: Auto-Reassign to Last-Active (UST759) ═══
  showReassignModal = signal(false);
  reassignCandidate = signal<ReassignmentCandidate | null>(null);
  reassignLoading = signal(false);
  reassignError = signal('');
  manualAssigneeId = '';

  handleSendBack(action: string) {
    const complaintId = this.complaint()?.complaintNumber || this.complaint()?.complaintId;
    if (!complaintId) return;

    this.reassignLoading.set(true);
    this.reassignError.set('');
    this.rbioWorkflow.getLastActiveOfficer(complaintId).subscribe({
      next: (candidate) => {
        this.reassignLoading.set(false);
        if (candidate && candidate.isActive && !candidate.isOnLeave) {
          // Auto-assign to last active officer
          const actor = this.auth.currentUser()?.username || '';
          this.rbioWorkflow.reassignComplaint(complaintId, candidate.userId, actor).subscribe({
            next: () => {
              this.actionSuccess.set(true);
              this.actionResult.set(`Auto-reassigned to ${candidate.name} (last active officer).`);
              this.loadComplaint(complaintId);
            },
            error: (err) => {
              this.actionSuccess.set(false);
              this.actionResult.set(err.error?.message || 'Failed to reassign.');
            }
          });
        } else {
          // Show error popup for manual selection
          this.reassignCandidate.set(candidate);
          this.showReassignModal.set(true);
          if (candidate && !candidate.isActive) {
            this.reassignError.set(`Last officer "${candidate.name}" is inactive. Please select manually.`);
          } else if (candidate && candidate.isOnLeave) {
            this.reassignError.set(`Last officer "${candidate.name}" is on leave. Please select manually.`);
          } else {
            this.reassignError.set('No previous officer found. Please select manually.');
          }
        }
      },
      error: () => {
        this.reassignLoading.set(false);
        this.showReassignModal.set(true);
        this.reassignError.set('Unable to determine last active officer. Please select manually.');
      }
    });
  }

  confirmManualReassign() {
    if (!this.manualAssigneeId.trim()) return;
    const complaintId = this.complaint()?.complaintNumber || this.complaint()?.complaintId;
    const actor = this.auth.currentUser()?.username || '';

    this.rbioWorkflow.reassignComplaint(complaintId, this.manualAssigneeId, actor).subscribe({
      next: () => {
        this.showReassignModal.set(false);
        this.actionSuccess.set(true);
        this.actionResult.set('Complaint reassigned successfully.');
        this.manualAssigneeId = '';
        this.loadComplaint(complaintId);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(err.error?.message || 'Failed to reassign.');
      }
    });
  }

  cancelReassignModal() {
    this.showReassignModal.set(false);
    this.manualAssigneeId = '';
  }

  // ═══ Feature: Restrict Closure Without Email (UST549, UST764) ═══
  showNoEmailClosurePopup = signal(false);
  noEmailAlternativeActions = [
    { label: 'Decision', value: 'DECISION' },
    { label: 'Withdrawn', value: 'WITHDRAWN' },
    { label: 'Settled', value: 'SETTLED' },
    { label: 'Rejected', value: 'REJECTED' },
    { label: 'Advisory', value: 'ADVISORY' },
    { label: 'Award', value: 'AWARD' }
  ];

  validateClosureEmail(): boolean {
    const c = this.complaint();
    if (!c?.complainantEmail || !c.complainantEmail.trim()) {
      this.showNoEmailClosurePopup.set(true);
      return false;
    }
    return true;
  }

  dismissNoEmailPopup() {
    this.showNoEmailClosurePopup.set(false);
  }

  selectAlternativeAction(actionValue: string) {
    this.showNoEmailClosurePopup.set(false);
    this.selectAction(actionValue);
  }

  routeBackToDo() {
    this.showNoEmailClosurePopup.set(false);
    const complaintId = this.complaint()?.complaintNumber || this.complaint()?.complaintId;
    const actor = this.auth.currentUser()?.username || '';
    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintId}`,
      { action: 'SEND_BACK_DO', remarks: 'Routed back - complainant email missing for closure', actor }
    ).subscribe({
      next: () => {
        this.actionSuccess.set(true);
        this.actionResult.set('Complaint routed back to DO due to missing email.');
        this.loadComplaint(complaintId);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(err.error?.message || 'Failed to route back.');
      }
    });
  }

  // ═══ Feature: Block DO Editing After Decision (UST756) ═══
  hasFinalDecisionUpstream = signal(false);
  finalDecisionBy = signal('');
  finalDecisionByRole = signal('');

  checkFinalDecisionStatus() {
    const complaintId = this.complaint()?.complaintNumber || this.complaint()?.complaintId;
    if (!complaintId) return;
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
    return this.hasFinalDecisionUpstream() && this.auth.hasRole('RBIO_OFFICER');
  }

  getReadOnlyTooltip(): string {
    if (!this.hasFinalDecisionUpstream()) return '';
    return `Set by ${this.finalDecisionByRole() || 'Deputy/Ombudsman'}, cannot be modified`;
  }

  // ═══ Feature: Proposed Action Override History (UST639-642) ═══
  showOverrideHistory = signal(false);

  trackProposedActionChange(fieldName: string, oldValue: string, newValue: string) {
    if (oldValue === newValue) return;
    const roles = this.auth.getRoles();
    const overrideRoles = ['RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_ADJUDICATOR'];
    if (!roles.some(r => overrideRoles.includes(r))) return;

    const complaintId = this.complaint()?.complaintNumber || this.complaint()?.complaintId;
    const username = this.auth.currentUser()?.username || '';
    const role = roles.find(r => overrideRoles.includes(r)) || '';

    this.rbioWorkflow.recordActionOverride(complaintId, {
      fieldName,
      oldValue,
      newValue,
      overriddenBy: username,
      overriddenByRole: role
    }).subscribe();
  }

  toggleOverrideHistory() {
    this.showOverrideHistory.set(!this.showOverrideHistory());
  }

  // ═══ Feature: Add Entity (UST487-495) ═══
  showAddEntity(): boolean {
    if (!this.isRbioComplaint()) return false;
    const roles = this.auth.getRoles();
    return roles.some(r => ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN'].includes(r));
  }

  // ═══ Feature: Legal Case Tab (UST553-555) ═══
  showLegalCase(): boolean {
    return this.isRbioComplaint();
  }

  // ═══ Feature: Forward to Regulatory Body (UST766) ═══
  showForwardRegulatory(): boolean {
    if (!this.isRbioComplaint()) return false;
    const roles = this.auth.getRoles();
    return roles.some(r => ['RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_ADJUDICATOR'].includes(r));
  }

  // Override submitAction to add closure email check and send-back auto-reassign
  originalSubmitAction() {
    const action = this.selectedAction();

    // UST549/UST764: Check email before closure
    if (['CLOSE_COMPLAINT', 'RESOLVE'].includes(action)) {
      if (!this.validateClosureEmail()) return;
    }

    // UST759: Auto-reassign on send-back
    if (['SEND_BACK_DO', 'SEND_BACK_REVIEWER', 'RETURN_TO_OFFICER'].includes(action)) {
      if (this.auth.hasRole('RBIO_DEPUTY_OMBUDSMAN') || this.auth.hasRole('RBIO_ADJUDICATOR')) {
        this.handleSendBack(action);
        return;
      }
    }

    this.submitAction();
  }
}
