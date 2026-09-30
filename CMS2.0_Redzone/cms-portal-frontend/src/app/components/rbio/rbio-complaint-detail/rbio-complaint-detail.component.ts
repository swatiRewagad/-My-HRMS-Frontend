import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { DomSanitizer, SafeResourceUrl } from '@angular/platform-browser';
import { ComplaintAttachmentRow } from '../../../services/complaint-correspondence.service';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { RbioWorkflowService } from '../../../services/rbio-workflow.service';
import { UploadLinkStatusComponent } from '../../../shared/upload-link-status/upload-link-status.component';
import { RbioDeputyDecisionComponent } from '../rbio-deputy-decision/rbio-deputy-decision.component';
import { RbioAddEntityComponent } from '../rbio-add-entity/rbio-add-entity.component';
import { RbioLegalCaseComponent } from '../rbio-legal-case/rbio-legal-case.component';
import { RbioForwardRegulatoryComponent } from '../rbio-forward-regulatory/rbio-forward-regulatory.component';
import { RbioActionOverrideHistoryComponent } from '../rbio-action-override-history/rbio-action-override-history.component';
import { environment } from '../../../../environments/environment';
import { ComplaintSummaryComponent } from '../../shared/complaint-summary/complaint-summary.component';
import { ComplaintSummaryItem } from '../../shared/complaint-summary/complaint-summary.types';
import { CommentThreadComponent } from '../../shared/comment-thread/comment-thread.component';
import { CommentAudienceOption } from '../../shared/comment-thread/comment-thread.types';
import { ContextRailComponent } from '../../shared/context-rail/context-rail.component';
import { ContextRailPanel } from '../../shared/context-rail/context-rail.types';
// <<< [S6] START >>>
import { RbioEmailCommunicationComponent } from '../rbio-email-communication/rbio-email-communication.component';
import { RbioAttachmentsComponent } from '../rbio-attachments/rbio-attachments.component';
import { RbioComplaintHistoryComponent } from '../rbio-complaint-history/rbio-complaint-history.component';
// <<< [S6] END >>>

/** One document open as a tab beside the complaint. */
interface OpenDoc {
  id: number;
  fileName: string;
  url: string;
  safeUrl: SafeResourceUrl;
  previewable: boolean;
  source: string;
}

type RailKey = 'history' | 'attachments' | 'email';

interface ComplaintDetail {
  /**
   * The BUSINESS key (CMP-…). Named `complaintId` because that is what the payload calls it, and what
   * every workflow endpoint on this screen is keyed on.
   */
  complaintId: string;
  /**
   * The numeric PRIMARY key, which is a different thing and not interchangeable with the above. The
   * files API is `@PathVariable Long`, so passing it a complaint number is a 400 — which is exactly what
   * the attachments panel did on every complaint, for as long as it has existed, because the service
   * signature accepted `number | string` and so nothing objected.
   */
  dbId: number | null;
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

@Component({
  selector: 'app-rbio-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, UploadLinkStatusComponent, RbioDeputyDecisionComponent, RbioAddEntityComponent, RbioLegalCaseComponent, RbioForwardRegulatoryComponent, RbioActionOverrideHistoryComponent, ComplaintSummaryComponent, CommentThreadComponent, ContextRailComponent,
    // <<< [S6] START >>>
    RbioEmailCommunicationComponent, RbioAttachmentsComponent, RbioComplaintHistoryComponent,
    // <<< [S6] END >>>
  ],
  templateUrl: './rbio-complaint-detail.component.html',
  styleUrl: './rbio-complaint-detail.component.scss'
})
export class RbioComplaintDetailComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);
  private rbioWorkflow = inject(RbioWorkflowService);
  private sanitizer = inject(DomSanitizer);

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
  speakingOrder = signal('');

  /**
   * RESTRICTED targets the composer offers on an RBIO complaint.
   *
   * The RBIO ladder only, not every role in the realm: the point of RESTRICTED is to address the people
   * who will actually act on the complaint, and a list that included CEPC or AA roles would invite an
   * officer to address someone who never opens this screen.
   */
  readonly commentAudienceRoles: readonly CommentAudienceOption[] = [
    { value: 'RBIO_OFFICER', label: 'RBIO Officer' },
    { value: 'RBIO_SUPERVISOR', label: 'RBIO Supervisor' },
    { value: 'RBIO_DEPUTY_OMBUDSMAN', label: 'Deputy Ombudsman' },
    { value: 'RBIO_OMBUDSMAN', label: 'Ombudsman' },
    { value: 'RBIO_ADJUDICATOR', label: 'Adjudicator' }
  ];

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

  /**
   * The WORK sections only. Email, Attachments and Complaint History left this list because they moved
   * into the right rail — keeping a tab that no longer has a body would have been a dead control, and
   * keeping both would have given an officer two places to look for the same attachment list.
   */
  tabs = [
    { key: 'summary', label: 'Summary' },
    { key: 'nodal', label: 'Nodal Officer Record' },
    { key: 'conciliation', label: 'Conciliation' },
    { key: 'forward', label: 'Forward' },
    { key: 'final', label: 'Final Decision' },
    { key: 'legal', label: 'Legal Case' },
  ];

  approvalOptions = [
    { key: 'reviewer', label: 'Send to RBIO Reviewer', nameLabel: 'Name of RBIO Reviewer' },
    { key: 'deputy', label: 'Send to RBIO Deputy Ombudsman', nameLabel: 'Name of RBIO Deputy Ombudsman' },
    { key: 'ombudsman', label: 'Send to RBIO Ombudsman', nameLabel: 'Name of RBIO Ombudsman' },
  ];

  selectedApprovalOption = signal<any>(null);

  /**
   * The strip's facts, as data for the shared app-complaint-summary.
   *
   * The SLA severity is stated here rather than inferred by the strip, because "how close is too close"
   * is a per-workflow judgement — RBIO's own copy treated anything under 48h as a breach — and a shared
   * component guessing it would apply RBIO's threshold to CEPC's clock.
   */
  readonly summaryItems = computed<ComplaintSummaryItem[]>(() => {
    const c = this.complaint();
    if (!c) return [];
    return [
      { labelKey: 'ui.col.complaint_number', value: c.complaintNumber, icon: 'pi-file' },
      { labelKey: 'ui.col.complainant_name', value: c.complainantName, icon: 'pi-user', tone: 'owner' },
      { labelKey: 'ui.col.entity_name', value: c.entityName, icon: 'pi-building' },
      { labelKey: 'ui.col.status', value: c.status, kind: 'status', icon: 'pi-flag' },
      { labelKey: 'ui.col.category', value: c.category, icon: 'pi-tag' },
      {
        labelKey: 'ui.col.sla_remaining',
        value: `${c.slaBreachHours} hrs`,
        kind: 'sla',
        icon: 'pi-clock',
        severity: c.slaBreachHours < 24 ? 'danger' : c.slaBreachHours < 48 ? 'warn' : 'ok'
      }
    ];
  });

  // ═══ Document tabs ═════════════════════════════════════════════════════════════════════════════
  // A document opens as a SIBLING TAB of the complaint, never as a modal over it: the reason to open an
  // attachment is to read it against the complaint, and a modal hides exactly what the officer is
  // comparing. `null` means the complaint tab, which is why it cannot be closed.

  openDocId = signal<number | null>(null);
  openDocs = signal<OpenDoc[]>([]);

  readonly activeDoc = computed<OpenDoc | null>(() => {
    const id = this.openDocId();
    return id === null ? null : this.openDocs().find(d => d.id === id) ?? null;
  });

  /**
   * Only formats the browser itself renders. A type not on this list falls back to a download link,
   * because an iframe pointed at a .docx renders a download prompt or nothing at all depending on the
   * browser, and a preview pane that silently shows nothing reads as a broken attachment.
   */
  private static readonly PREVIEWABLE = ['pdf', 'png', 'jpg', 'jpeg', 'gif', 'webp', 'txt'];

  openDoc(doc: OpenDoc) {
    if (!this.openDocs().some(d => d.id === doc.id)) {
      this.openDocs.update(list => [...list, doc]);
    }
    this.openDocId.set(doc.id);
  }

  closeDoc(id: number) {
    this.openDocs.update(list => list.filter(d => d.id !== id));
    if (this.openDocId() === id) this.openDocId.set(null);
  }

  // ═══ Right context rail ════════════════════════════════════════════════════════════════════════
  // History, attachments and email are context an officer CONSULTS; the assessment is what they work
  // in. These were three full-width tabs below the fold, so checking the history scrolled the
  // assessment off screen. The rail keeps both visible and collapses when not in use.

  railOpen = signal<RailKey | null>(null);

  readonly railPanels: readonly ContextRailPanel<RailKey>[] = [
    { key: 'history', label: 'Complaint History', icon: 'pi-history' },
    { key: 'attachments', label: 'Attachments', icon: 'pi-paperclip' },
    { key: 'email', label: 'Email Communication', icon: 'pi-envelope' }
  ];

  /** Opening the rail AT a panel, used by the tab strip's `+`, which means "show me the documents". */
  openRail(key: RailKey) {
    this.railOpen.set(key);
  }

  /** A filename clicked inside app-rbio-attachments: read it beside the complaint. */
  openAttachment(row: ComplaintAttachmentRow) {
    this.openDoc(this.toOpenDoc(row));
  }

  private toOpenDoc(row: ComplaintAttachmentRow): OpenDoc {
    const url = `${environment.apiBaseUrl}/api/files/download/${row.id}`;
    const ext = (row.originalName || '').split('.').pop()?.toLowerCase() || '';
    return {
      id: row.id,
      fileName: row.originalName,
      url,
      safeUrl: this.sanitizer.bypassSecurityTrustResourceUrl(url),
      previewable: RbioComplaintDetailComponent.PREVIEWABLE.includes(ext),
      source: row.source || 'UNKNOWN'
    };
  }

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
          dbId: typeof d.id === 'number' ? d.id : null,
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
        this.loading.set(false);
        this.checkFinalDecisionStatus();
      },
      error: () => {
        this.complaint.set({
          complaintId: id,
          // No numeric id is knowable when the fetch failed, and `null` disables the file panels rather
          // than letting them call the endpoint with a value that cannot be a primary key.
          dbId: null,
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
