import { Component, inject, OnInit, OnDestroy, signal, computed, ElementRef, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { TatService, TatResult } from '../../../services/tat.service';
import { RbioWorkflowService, ReassignmentCandidate } from '../../../services/rbio-workflow.service';
import { environment } from '../../../../environments/environment';
import { RbioConciliationComponent } from '../../rbio/rbio-conciliation/rbio-conciliation.component';
import { RbioAdjudicationComponent } from '../../rbio/rbio-adjudication/rbio-adjudication.component';
import { RbioAdvisoryComponent } from '../../rbio/rbio-advisory/rbio-advisory.component';
import { RbioSlaProgressComponent } from '../../rbio/rbio-sla-progress/rbio-sla-progress.component';
import { RbioDeputyDecisionComponent } from '../../rbio/rbio-deputy-decision/rbio-deputy-decision.component';
import { RbioAddEntityComponent } from '../../rbio/rbio-add-entity/rbio-add-entity.component';
import { RbioLegalCaseComponent } from '../../rbio/rbio-legal-case/rbio-legal-case.component';
import { RbioForwardRegulatoryComponent } from '../../rbio/rbio-forward-regulatory/rbio-forward-regulatory.component';
import { RbioActionOverrideHistoryComponent } from '../../rbio/rbio-action-override-history/rbio-action-override-history.component';
import { WorkflowTimelineComponent } from '../../shared/workflow-timeline/workflow-timeline.component';
import { ComplaintSummaryComponent } from '../../shared/complaint-summary/complaint-summary.component';
import { ComplaintSummaryItem } from '../../shared/complaint-summary/complaint-summary.types';
import { WorkflowActionBarComponent } from '../../shared/workflow-action-bar/workflow-action-bar.component';
import { WorkflowAction, WorkflowActionStyle } from '../../shared/workflow-action-bar/workflow-action-bar.types';
import { SimilarCasesComponent } from '../../shared/similar-cases/similar-cases.component';
import { CommentTemplatePickerComponent } from '../../shared/comment-template-picker/comment-template-picker.component';
import { CommentThreadComponent } from '../../shared/comment-thread/comment-thread.component';
import { CommentAudienceOption } from '../../shared/comment-thread/comment-thread.types';
import { highlightEmailText, escapeHtml } from '../../../utils/highlight-text.util';

@Component({
  selector: 'app-task-action',
  standalone: true,
  imports: [CommonModule, FormsModule, RbioConciliationComponent, RbioAdjudicationComponent, RbioAdvisoryComponent, RbioSlaProgressComponent, RbioDeputyDecisionComponent, RbioAddEntityComponent, RbioLegalCaseComponent, RbioForwardRegulatoryComponent, RbioActionOverrideHistoryComponent, WorkflowTimelineComponent, ComplaintSummaryComponent, WorkflowActionBarComponent, SimilarCasesComponent, CommentTemplatePickerComponent, CommentThreadComponent],
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

  // ── Staff drafts, auto-save and concurrency (UST673-675) ──────────────────────────────────────
  savingDraft = signal(false);
  /** The server's save timestamp. Null whenever the last save did not demonstrably succeed. */
  draftSavedAt = signal<string | null>(null);
  /** A failed draft save, shown instead of any success indicator. */
  draftError = signal<string | null>(null);
  /** Set when the server refused the save because a colleague wrote first (HTTP 409). */
  conflictWarning = signal<string | null>(null);
  /** Other officers with this complaint open. Advisory: never blocks a save. */
  otherEditors = signal<{ userId: string; displayName: string; since: string }[]>([]);
  private autoSaveInterval: any = null;
  private presenceInterval: any = null;
  selectedAction = signal<string>('');
  actionResult = signal<string>('');
  actionSuccess = signal(false);
  availableActions = signal<{label: string; value: string; style: WorkflowActionStyle}[]>([]);
  remarks = '';

  /**
   * The shared bar's shape, adapted from this screen's `value`-keyed list.
   *
   * Kept as an adapter rather than rewriting all forty pushes in determineActions(), which is imperative
   * and re-derives from roles plus status. `requiresRemarks` is true on every action because this screen
   * has always demanded remarks unconditionally — it is stated here rather than relied upon as a default.
   */
  actionBarActions = computed<WorkflowAction[]>(() =>
    this.availableActions().map(a => ({
      id: a.value,
      label: a.label,
      style: a.style,
      requiresRemarks: true,
    })));

  /**
   * Which saved-remark templates the §5.3.6 picker offers, derived from the pending action (§5.3.6).
   *
   * COMMENT_TEMPLATES.category is a free-text column the admin screen constrains to
   * GENERAL | CLOSURE | REJECTION | FOLLOWUP, so only those four are ever worth asking for. Anything
   * unmapped falls back to GENERAL rather than to null: null means "every active template", which
   * would offer closure wording while an officer is forwarding a complaint.
   */
  commentTemplateCategory = computed(() => {
    const action = this.selectedAction();
    if (action.includes('CLOSE') || action.includes('RESOLVE')) return 'CLOSURE';
    if (action.includes('REJECT') || action.includes('NON_MAINTAIN')) return 'REJECTION';
    if (action.includes('FOLLOW') || action.includes('REMIND')) return 'FOLLOWUP';
    return 'GENERAL';
  });

  /** Both captions name the pending transition, so an officer cannot confirm the wrong one by habit. */
  remarksLabel = computed(() => `Remarks for "${this.selectedAction()}"`);
  confirmLabel = computed(() => `Confirm ${this.selectedAction()}`);

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

  /**
   * What the shared similar-cases panel searches on.
   *
   * Subject plus description, because the endpoint runs `more_like_this` over those two indexed fields.
   * No category is passed: `/api/v1/complaints/{n}` returns the RESOLVED category NAME, and the search
   * filter is a term query on the indexed `categoryId`, so sending the name would filter every document
   * out and present as a genuine no-match — the exact class of silent failure this screen already had.
   */
  similarSearchText = computed(() => {
    const c = this.complaint();
    return [c?.subject, c?.description].filter(Boolean).join(' ').trim();
  });

  // Right sidebar - Past Complaints
  pastComplaints = signal<any[]>([]);
  loadingPastComplaints = signal(false);
  /**
   * True when the lookup FAILED, as distinct from the complainant having no history.
   *
   * <p>Without this the panel showed "No past complaints found" whether the complainant was a
   * first-time filer or the request had 401'd — and it was the latter for every complaint, undetected,
   * because the two states looked identical.
   */
  pastComplaintsError = signal(false);
  // Signal-backed: `filteredPastComplaints` is a computed() and a plain field read inside one
  // registers no dependency, so searching past complaints did nothing.
  pastComplaintSearch = signal('');

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
    const search = this.pastComplaintSearch().toLowerCase().trim();
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
    if (this.autoSaveInterval) clearInterval(this.autoSaveInterval);
    if (this.presenceInterval) clearInterval(this.presenceInterval);
    this.releaseEditPresence();
  }

  /** Reloads after a concurrent-edit conflict, so the retry carries the current version. */
  reloadAfterConflict() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;
    this.conflictWarning.set(null);
    this.loadComplaint(complaintNumber);
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
          // After determineActions(), so a restored selectedAction can only be one this role may still
          // take at the complaint's current status.
          this.resumeDraft(complaintNumber);
          this.startEditPresence(complaintNumber);
          if (!this.autoSaveInterval) this.startAutoSave();
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

  /** Pure toggle. The shared panel does its own lazy fetch the first time it is opened. */
  toggleSimilarPanel() {
    this.showSimilarPanel.set(!this.showSimilarPanel());
  }

  toggleHistoryPanel() {
    this.showHistoryPanel.set(!this.showHistoryPanel());
  }

  /**
   * The complainant's earlier complaints, fetched by COMPLAINT NUMBER rather than by email.
   *
   * <p>This previously called `GET /api/v1/complaints?complainantEmail=` — the CITIZEN
   * track-my-complaint list, which does not declare that parameter and answers 401 SESSION_EXPIRED to
   * any staff caller. The `error:` arm then set the list to `[]`, so the sidebar rendered a tidy
   * "No past complaints found" for every complaint ever opened and nothing in the UI said otherwise.
   *
   * <p>Even with the right endpoint, email was the wrong key: the detail response masks
   * `complainantEmail` to `q***@example.com`, so the lookup could never match. The identity resolution
   * therefore happens server-side from the complaint number — which this screen already has — and the
   * complainant's real address never reaches the browser.
   */
  loadPastComplaints() {
    const c = this.complaint();
    const complaintNumber = c?.complaintNumber || c?.complaintId;
    if (!complaintNumber) return;
    this.loadingPastComplaints.set(true);
    this.pastComplaintsError.set(false);
    this.http.get<any>(
      `${environment.apiBaseUrl}/api/v1/past-complaints/for-complaint/${encodeURIComponent(complaintNumber)}`)
      .subscribe({
        next: (res) => {
          const list = res?.data ?? res?.content ?? res ?? [];
          this.pastComplaints.set(Array.isArray(list) ? list : []);
          this.loadingPastComplaints.set(false);
        },
        error: () => {
          // A failed lookup is NOT an empty history. Showing the empty state here is what hid this
          // defect for as long as it existed, so the panel now says the lookup failed.
          this.pastComplaints.set([]);
          this.pastComplaintsError.set(true);
          this.loadingPastComplaints.set(false);
        }
      });
  }

  getDepartmentLabel(): string {
    const dept = this.auth.currentUser()?.department || 'RBIO';
    if (dept === 'CEPC') return 'CEPC, Chandigarh';
    return `RBIO, Mumbai`;
  }

  /**
   * RESTRICTED targets offered by the comment composer, keyed on the acting department rather than a
   * fixed list: this one component serves BOTH /staff/rbio/task/:id and /staff/cepc/task/:id, and
   * offering a CEPC officer the RBIO ladder would name an audience that cannot read their office's
   * cases. The lists mirror the ones the two detail screens already offer.
   */
  readonly commentAudienceRoles = computed<readonly CommentAudienceOption[]>(() =>
    this.auth.currentUser()?.department === 'CEPC'
      ? [
          { value: 'CEPC_DO', label: 'Dealing Official' },
          { value: 'CEPC_REVIEWER', label: 'Reviewer' },
          { value: 'CEPC_INCHARGE', label: 'In-Charge' },
          { value: 'CEPC_CLOSING_AUTHORITY', label: 'Closing Authority' }
        ]
      : [
          { value: 'RBIO_OFFICER', label: 'RBIO Officer' },
          { value: 'RBIO_SUPERVISOR', label: 'RBIO Supervisor' },
          { value: 'RBIO_DEPUTY_OMBUDSMAN', label: 'Deputy Ombudsman' },
          { value: 'RBIO_OMBUDSMAN', label: 'Ombudsman' },
          { value: 'RBIO_ADJUDICATOR', label: 'Adjudicator' }
        ]);

  scrollToActions() {
    this.sectionOpen['actions'] = true;
    setTimeout(() => {
      this.actionSectionEl?.nativeElement?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 100);
  }

  private determineActions() {
    const roles = this.auth.getRoles();
    const status = (this.complaint()?.status || '').toLowerCase();
    const actions: {label: string; value: string; style: WorkflowActionStyle}[] = [];

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
    doc.setFontSize(8);
    // No signing certificate exists, so do not claim "[Digitally Signed] / Signed at" — the
    // approval of record lives in the workflow audit trail, not in this PDF.
    doc.text(`System-generated on ${new Date().toLocaleString('en-IN')}. Not a digitally signed document.`, 14, y);
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

  /**
   * The milestone the current action belongs to, for UST674's five save points.
   *
   * <p>Conciliation, Forward and Final Decision all live on THIS component, which had no saveDraft at
   * all — so three of the five required milestones had no draft surface. The milestone is derived from
   * the selected action rather than the route, because one screen serves all three.
   */
  private draftMilestone(): string {
    const action = this.selectedAction();
    if (action.startsWith('CONCILIATION')) return 'CONCILIATION';
    if (action.startsWith('FORWARD_TO_')) return 'FORWARD';
    if (action === 'CLOSE_COMPLAINT' || action === 'ADJUDICATION_AWARD') return 'FINAL_DECISION';
    return 'ASSESSMENT';
  }

  /**
   * The in-progress values, hand-enumerated.
   *
   * <p>This component has no FormGroup — it binds raw properties and signals through ngModel — so there
   * is no .value snapshot to serialise. closureLetterFile is deliberately excluded: a File cannot be
   * JSON-serialised, so a resumed draft re-prompts for the upload rather than appearing to hold it.
   */
  private draftFormData(): Record<string, any> {
    return {
      selectedAction: this.selectedAction(),
      remarks: this.remarks,
      customClosureText: this.customClosureText(),
      closureClause: this.closureClause(),
      dateOfSending: this.dateOfSending()
    };
  }

  /** UST674: save in draft at Conciliation, Forward and Final Decision. */
  saveDraft(autosave = false) {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    this.savingDraft.set(true);
    this.draftError.set(null);

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts`, {
      milestone: this.draftMilestone(),
      complaintNumber,
      autosave,
      formData: this.draftFormData()
    }).subscribe({
      next: (res) => {
        this.savingDraft.set(false);
        this.draftSavedAt.set(res?.savedAt ?? null);
      },
      error: (err) => {
        this.savingDraft.set(false);
        this.draftSavedAt.set(null);
        // A failed save says so. The sibling implementations in this codebase either set their success
        // flag inside the error handler or omit the error handler entirely.
        this.draftError.set(err?.error?.message
          || 'Your draft could not be saved. Your entries are still on screen — please try again.');
      }
    });
  }

  /** Restores an in-progress draft for this complaint and milestone (UST674). */
  private resumeDraft(complaintNumber: string) {
    const milestone = this.draftMilestone();
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts/${milestone}`, {
      params: { complaintNumber }
    }).subscribe({
      next: (res) => {
        const data = res?.formData;
        if (!data) return;
        if (data.remarks) this.remarks = data.remarks;
        if (data.customClosureText) this.customClosureText.set(data.customClosureText);
        if (data.closureClause) this.closureClause.set(data.closureClause);
        if (data.dateOfSending) this.dateOfSending.set(data.dateOfSending);
        // Restored LAST, and only if still offered: determineActions() re-derives what this role may do
        // at the complaint's CURRENT status, and a draft may predate a status change.
        if (data.selectedAction && this.availableActions().some((a: any) => a.code === data.selectedAction)) {
          this.selectedAction.set(data.selectedAction);
        }
        this.draftSavedAt.set(res?.updatedAt ?? null);
      },
      error: () => {}
    });
  }

  /**
   * UST673: auto-save on the SYSTEM_CONFIG interval, silently.
   *
   * <p>Only fires once the officer has typed something. remarks is mandatory for every action, so it is
   * the reliable signal that there is work worth preserving; saving an untouched form would replace a
   * good draft with an empty one.
   */
  private startAutoSave() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/staff-drafts/config`).subscribe({
      next: (cfg) => {
        const seconds = Number(cfg?.autosaveIntervalSeconds);
        if (!cfg?.autosaveEnabled || !Number.isFinite(seconds) || seconds <= 0) return;
        this.autoSaveInterval = setInterval(() => {
          if (this.remarks.trim() || this.customClosureText().trim()) {
            this.saveDraft(true);
          }
        }, seconds * 1000);
      },
      error: () => {}
    });
  }

  /**
   * UST675: registers this officer as editing, and warns about anyone else who is.
   *
   * <p>Advisory only. The save is gated by the @Version check on Complaint, which returns 409 — see the
   * error handler in executeWorkflowAction. This exists because optimistic locking is detect-on-write:
   * without it the second officer discovers the conflict only after doing the work.
   */
  private startEditPresence(complaintNumber: string) {
    const beat = () => {
      this.http.post<any>(
        `${environment.apiBaseUrl}/api/v1/staff-drafts/presence/${complaintNumber}`, {}
      ).subscribe({
        next: (res) => {
          this.otherEditors.set(res?.otherEditors || []);
          if (!this.presenceInterval && res?.heartbeatIntervalSeconds > 0) {
            this.presenceInterval = setInterval(beat, res.heartbeatIntervalSeconds * 1000);
          }
        },
        error: () => {}
      });
    };
    beat();
  }

  private releaseEditPresence() {
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;
    this.http.delete(
      `${environment.apiBaseUrl}/api/v1/staff-drafts/presence/${complaintNumber}`
    ).subscribe({ next: () => {}, error: () => {} });
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
        this.conflictWarning.set(null);
        // UST673: the auto-saved draft is discarded on a successful save-and-proceed. A manually saved
        // draft is left alone — the server distinguishes the two by SAVE_SOURCE.
        this.http.post(`${environment.apiBaseUrl}/api/v1/staff-drafts/discard-autosave`,
          { complaintNumber }).subscribe({ next: () => {}, error: () => {} });
        this.draftSavedAt.set(null);
        this.loadComplaint(complaintNumber);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.processing.set(false);

        // UST675: 409 means a colleague saved first. Wave 0 added @Version to Complaint and
        // GlobalExceptionHandler maps the conflict to 409 with messageKey common.error_record_changed
        // and retryable:true — but NOTHING consumed it, so the officer saw a generic "Failed: ..." with
        // no reload affordance and no way to distinguish a conflict from a validation error. Repeating
        // the request with the same stale version would conflict again, so reload-then-retry is the only
        // recovery, and the warning has to say so.
        if (err?.status === 409) {
          this.conflictWarning.set(err?.error?.message
            || 'This complaint was changed by someone else while you were working on it. '
               + 'Reload to see the latest version before saving again.');
          // Cleared so the generic failure line does not sit alongside the conflict notice, which would
          // give the officer two different explanations for one refusal.
          this.actionResult.set('');
          return;
        }

        this.actionResult.set(`Failed: ${err.error?.message || err.message || 'Unknown error'}`);
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

  /**
   * The strip's facts, as data for the shared app-complaint-summary.
   *
   * Status now goes through the shared badge. This screen previously printed {@code complaint().status}
   * raw, so the same complaint read "IN_PROGRESS" here and "Under Examination" on the dashboard that
   * linked to it.
   */
  readonly summaryItems = computed<ComplaintSummaryItem[]>(() => {
    const c = this.complaint();
    if (!c) return [];
    return [
      { labelKey: 'ui.col.complaint_id', value: c.complaintId || c.id, icon: 'pi-id-card' },
      { labelKey: 'ui.col.complaint_number', value: c.complaintNumber, icon: 'pi-file' },
      { labelKey: 'ui.col.status', value: c.status, kind: 'status', icon: 'pi-flag' },
      { labelKey: 'ui.col.priority', value: c.priority, icon: 'pi-tag' },
      { labelKey: 'ui.col.assigned_officer', value: c.assignedTo || 'Unassigned', icon: 'pi-user', tone: 'owner' }
    ];
  });

  goBack() {
    const dept = this.auth.currentUser()?.department?.toLowerCase() || 'rbio';
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigate([`/staff/${dept}/tasks`]);
    });
  }

  /**
   * Whether THIS COMPLAINT is an RBIO complaint.
   *
   * <p>THE LAYOUT FIX (UST632/633, 771, 772). This read the LOGGED-IN USER's department, not the
   * complaint's — so this shared screen chose its field set from who was looking rather than what they were
   * looking at. That is precisely why converting a complaint's layout on transfer approval had no visible
   * effect: flipping COMPLAINTS.department could not change what an officer saw, because nothing rendered
   * off it.
   *
   * <p>The complaint's own department is now authoritative, with the user's as a fallback only when the
   * complaint carries none (an older row). "Layout" in this codebase means exactly this — the owning module
   * plus the status vocabulary it implies; see the V96 migration header for the ruling and its evidence.
   */
  isRbioComplaint(): boolean {
    const complaintDept = (this.complaint()?.department || '').toUpperCase();
    if (complaintDept) {
      return complaintDept === 'RBIO';
    }
    const dept = this.auth.currentUser()?.department || '';
    return dept.toUpperCase() === 'RBIO';
  }

  /**
   * The conciliation milestone (UST643, 646, 649).
   *
   * <p>Opened to the four roles those stories name — Dealing Official, Reviewer, Deputy Ombudsman and
   * Ombudsman — plus the Conciliator whose function this is, and the legacy equivalents. It admitted
   * {@code RBIO_CONCILIATOR} ALONE, which failed in both directions at once: three roles the server
   * authorises could never reach the screen, while this browser check protected nothing against a direct
   * POST.
   *
   * <p>The status condition is inverted to the UST497 exclusion set, because a Reviewer holding a file at
   * {@code reviewer_review} legitimately convenes meetings and the old two-status allow-list refused them.
   * This list is an AFFORDANCE: the authoritative gate is the server's RBIO_STATUS_MASTER.BLOCKS_MEETING
   * check, so a wrong entry here cannot admit an unlawful meeting.
   */
  showRbioConciliation(): boolean {
    if (!this.isRbioComplaint()) return false;
    const roles = this.auth.getRoles();
    const allowed = ['RBIO_CONCILIATOR', 'RBIO_DEALING_OFFICIAL', 'RBIO_REVIEWER',
      'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN', 'RBIO_OFFICER', 'RBIO_SUPERVISOR', 'RBIO_ADMIN'];
    if (!allowed.some(r => roles.includes(r))) return false;

    const excluded = ['advisory_complied', 'settled', 'withdrawn', 'rejected',
      'award_passed', 'ombudsman_decision', 'conciliated', 'adjudicated', 'closed'];
    const status = (this.complaint()?.status || '').toLowerCase();
    return !excluded.includes(status);
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
  // Not RBIO-only: a complaint can be sub judice while it still sits with CEPC, and the officer
  // handling it then needs the court reference visible and recordable. The server admits the CEPC
  // case-handling roles on /legal-case for the same reason.
  showLegalCase(): boolean {
    if (this.isRbioComplaint()) return true;
    return this.auth.getRoles().some(r =>
      ['CEPC_DO', 'CEPC_REVIEWER', 'CEPC_INCHARGE', 'CEPC_CLOSING_AUTHORITY', 'CEPC_ADMIN',
        'CEPC_CONTACT_PERSON'].includes(r));
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
