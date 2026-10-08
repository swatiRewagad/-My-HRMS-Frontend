import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';
import { CepcTimelineComponent } from '../cepc-timeline/cepc-timeline.component';
import { CepcConciliationComponent } from '../cepc-conciliation/cepc-conciliation.component';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { CommentThreadComponent } from '../../shared/comment-thread/comment-thread.component';
import { CommentAudienceOption } from '../../shared/comment-thread/comment-thread.types';
import { WorkflowActionBarComponent } from '../../shared/workflow-action-bar/workflow-action-bar.component';
import { WorkflowAction, WorkflowActionStyle } from '../../shared/workflow-action-bar/workflow-action-bar.types';
import { ComplaintSummaryComponent } from '../../shared/complaint-summary/complaint-summary.component';
import { ComplaintSummaryItem } from '../../shared/complaint-summary/complaint-summary.types';
import { ContextRailComponent } from '../../shared/context-rail/context-rail.component';
import { ContextRailPanel } from '../../shared/context-rail/context-rail.types';
// Reused as-is from the RBIO detail screen: both are complaint-scoped, not vertical-specific. See
// railPanels for the endpoints that were verified live before wiring them in here.
import { RbioEmailCommunicationComponent } from '../../rbio/rbio-email-communication/rbio-email-communication.component';
import { RbioLegalCaseComponent } from '../../rbio/rbio-legal-case/rbio-legal-case.component';
import { ToastService } from '../../../services/toast.service';
import { UploadLimitsService } from '../../../services/upload-limits.service';
import { TranslateOrPipe } from '../../../pipes/translate-or.pipe';

interface TimelineEntry {
  action: string;
  fromStatus: string;
  toStatus: string;
  timestamp: string;
  remarks: string;
  performedBy?: string;
}

type CepcRole = 'CEPC_DO' | 'CEPC_REVIEWER' | 'CEPC_INCHARGE' | 'CEPC_CLOSING_AUTHORITY' | 'CEPC_ADMIN' | 'CEPC_CONTACT_PERSON';

/** The rail panels this screen can populate. The audit trail is NOT one; see the template. */
type RailKey = 'documents' | 'comments' | 'email' | 'legal';

/** The shared action contract plus the target picker only this module renders. */
interface ActionDef extends WorkflowAction {
  label: string;
  description: string;
  style: WorkflowActionStyle;
  requiresRemarks: boolean;
  requiresTarget?: boolean;
  targetType?: 'user' | 'department';
}

@Component({
  selector: 'app-cepc-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, CepcSlaIndicatorComponent, CepcTimelineComponent, CepcConciliationComponent, StatusBadgeComponent, CommentThreadComponent, WorkflowActionBarComponent, ComplaintSummaryComponent, ContextRailComponent, RbioEmailCommunicationComponent, RbioLegalCaseComponent, TranslateOrPipe],
  templateUrl: './cepc-complaint-detail.component.html',
  styleUrl: './cepc-complaint-detail.component.scss'
})
export class CepcComplaintDetailComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private toast = inject(ToastService);
  private uploadLimits = inject(UploadLimitsService);
  auth = inject(KeycloakAuthService);

  complaint = signal<any>(null);
  loading = signal(true);
  processing = signal(false);
  userRole = signal<CepcRole>('CEPC_DO');

  selectedAction = signal<ActionDef | null>(null);
  remarks = '';
  targetUser = '';
  targetDepartment = '';

  actionResult = signal('');
  actionSuccess = signal(false);

  /**
   * RESTRICTED targets the composer offers on a CEPC complaint — the CEPC ladder only.
   *
   * RBIO roles are absent even though a CEPC complaint can be escalated to RBIO: addressing a comment
   * to a role that does not open this screen would read as delivered and never be.
   */
  readonly commentAudienceRoles: readonly CommentAudienceOption[] = [
    { value: 'CEPC_DO', label: 'Dealing Official' },
    { value: 'CEPC_REVIEWER', label: 'Reviewer' },
    { value: 'CEPC_INCHARGE', label: 'In-Charge' },
    { value: 'CEPC_CLOSING_AUTHORITY', label: 'Closing Authority' }
  ];

  // For forwarding to other departments
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

  // For reassignment within CEPC
  cepcOfficers = signal<{ id: string; name: string }[]>([]);
  contactPersons = signal<{ id: string; name: string }[]>([]);

  // Document upload
  documents = signal<{ id: string; name: string; size: string; uploadedBy: string; uploadedAt: string }[]>([]);
  uploadingDoc = signal(false);

  /**
   * The shared summary strip's facts, the same band RBIO and the CRPC screens lead with.
   *
   * No SLA item: the strip's 'sla' kind wants a remaining-time string and a severity, and this screen's
   * SLA is owned by app-cepc-sla-indicator, which computes both from the due date and renders a progress
   * bar besides. Reproducing that arithmetic here would be a second place for it to drift, so the
   * indicator stays in the left header and the strip carries the due date as plain text.
   */
  readonly summaryItems = computed<readonly ComplaintSummaryItem[]>(() => {
    const c = this.complaint();
    if (!c) return [];
    return [
      { labelKey: 'ui.col.complaint_number', value: c.complaintNumber || c.complaintId, icon: 'pi-file' },
      { labelKey: 'ui.col.complainant_name', value: c.complainantName, icon: 'pi-user', tone: 'owner' },
      { labelKey: 'ui.col.entity_name', value: c.entityName, icon: 'pi-building' },
      { labelKey: 'ui.col.status', value: c.status, kind: 'status', icon: 'pi-flag' },
      { labelKey: 'ui.col.category', value: c.category, icon: 'pi-tag' },
      // assigned_officer, not an invented assigned_to: an unseeded key renders as the key itself.
      { labelKey: 'ui.col.assigned_officer', value: c.assignedTo || c.assignedTeam, icon: 'pi-users' }
    ];
  });

  // ═══ Milestone ladder ══════════════════════════════════════════════════════════════════════════

  /**
   * The coarse PHASE ladder — Register → Assessment → Conciliation → Forward → Final decision — and
   * which rung this complaint is on.
   *
   * <p>SERVER DATA, not a literal list here. Both arrive on the existing detail response
   * (`GET /api/v1/complaints/{n}` → `data.milestones` and `data.milestone`), where the ladder is
   * derived from `RBIO_STATUS_MASTER.MILESTONE_CODE` ordered by DISPLAY_ORDER and the current rung is
   * resolved from the complaint's STATUS through that same table. Holding the five names in the
   * component instead would have given the officer a ladder that could disagree with the status filter
   * tabs, which are built from the same master rows.
   *
   * <p>If the server sends neither — an older build, or a complaint whose status maps to no milestone
   * — the strip renders NOTHING rather than a guessed ladder. A phase display that invents its own
   * content is worse than an absent one.
   */
  readonly milestones = computed<readonly { code: string; label: string }[]>(() => {
    const raw = this.complaint()?.milestones;
    return Array.isArray(raw) ? raw.filter((m: any) => m?.code && m?.label) : [];
  });

  readonly currentMilestone = computed<string | null>(() => this.complaint()?.milestone ?? null);

  /** Index of the current rung, or -1 when the status maps to no milestone. */
  readonly currentMilestoneIndex = computed(() => {
    const code = this.currentMilestone();
    if (!code) return -1;
    return this.milestones().findIndex(m => m.code.toUpperCase() === String(code).toUpperCase());
  });

  /** A rung is 'done' once passed, 'current' on arrival, 'todo' ahead. */
  milestoneState(index: number): 'done' | 'current' | 'todo' {
    const at = this.currentMilestoneIndex();
    if (at < 0) return 'todo';
    if (index < at) return 'done';
    return index === at ? 'current' : 'todo';
  }

  // ═══ Right context rail ════════════════════════════════════════════════════════════════════════
  // Documents and the comment thread are material an officer CONSULTS; the action cards are what they
  // work in. Both used to be full-width blocks stacked under the complaint facts, so a complaint with
  // any documents at all pushed the actions below the fold.

  railOpen = signal<RailKey | null>(null);

  /**
   * Four panels. Documents and Comments were already here; Email Communication and Legal Case
   * Details are new, and they are REUSED components rather than CEPC copies — the same
   * `app-rbio-email-communication` and `app-rbio-legal-case` the RBIO detail screen renders.
   *
   * <p>Neither is RBIO-specific in anything but its selector prefix: both take a complaint number and
   * read complaint-scoped routes that answer for a CEPC complaint too. Verified live on 8092 against
   * a real CEPC complaint rather than assumed — `GET /api/v1/complaints/{n}/emails` → 200
   * {"success":true,"data":[]} and `GET /api/v1/complaints/{n}/legal-case` → 200. That check mattered:
   * this repo has a documented history of UI built against routes the server never served, and
   * `/api/v1/templates`, `/api/v1/legal-cases`, `/api/v1/reference-documents` and
   * `/api/v1/references` are all still 404 today.
   *
   * <p>They are rail panels and not a tab strip ON PURPOSE. RBIO carries exactly this material in its
   * own rail (`railPanels` = history / attachments / email, rbio-complaint-detail.component.ts:237-241)
   * because it is material an officer CONSULTS; what the officer WORKS in stays in the centre region.
   * Adding a tab strip here would have undone the three-region layout the homogenisation pass just
   * put in, and the spec contracts the template records (`.detail-layout`, `.detail-panel`,
   * `.action-panel`) exist to stop exactly that.
   */
  readonly railPanels: readonly ContextRailPanel<RailKey>[] = [
    { key: 'documents', label: 'Attachments', icon: 'pi-paperclip' },
    { key: 'email', label: 'Email Communications', icon: 'pi-envelope' },
    { key: 'legal', label: 'Legal case details', icon: 'pi-briefcase' },
    { key: 'comments', label: 'Comments', icon: 'pi-comments' }
  ];

  availableActions = computed<ActionDef[]>(() => {
    const role = this.userRole();
    const status = (this.complaint()?.status || '').toLowerCase();
    const actions: ActionDef[] = [];

    if (this.isTerminalState()) return [];

    if (role === 'CEPC_DO') {
      if (['assigned', 'pending', 'new', 'sent_back'].includes(status)) {
        actions.push({ id: 'ACCEPT', label: 'Accept & Start Examination', description: 'Accept complaint and begin examination', style: 'primary', requiresRemarks: false });
      }
      if (status === 'in_progress') {
        actions.push({ id: 'REQUEST_INFO', label: 'Request Additional Information', description: 'Seek additional info from complainant', style: 'info', requiresRemarks: true });
        actions.push({ id: 'FORWARD_DEPT', label: 'Forward to RBI Department', description: 'Forward for comments from another RBI department/office', style: 'forward', requiresRemarks: true, requiresTarget: true, targetType: 'department' });
        actions.push({ id: 'SCHEDULE_MEETING', label: 'Schedule Meeting', description: 'Schedule meeting with complainant/entity', style: 'info', requiresRemarks: true });
        actions.push({ id: 'SUBMIT_FOR_REVIEW', label: 'Forward to Reviewer', description: 'Forward to CEPC Reviewer for scrutiny', style: 'review', requiresRemarks: true });
        actions.push({ id: 'FORWARD_TO_INCHARGE', label: 'Forward to In-Charge', description: 'Forward directly to CEPC In-Charge', style: 'escalate', requiresRemarks: true });
      }
      if (status === 'info_requested') {
        actions.push({ id: 'INFO_RECEIVED', label: 'Mark Info Received', description: 'Additional information received, resume examination', style: 'primary', requiresRemarks: true });
      }
      if (status === 'forwarded') {
        actions.push({ id: 'COMMENTS_RECEIVED', label: 'Comments Received', description: 'Department comments received, resume examination', style: 'primary', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_REVIEWER') {
      if (['reviewer_review', 'under_review'].includes(status)) {
        actions.push({ id: 'APPROVE_REVIEW', label: 'Forward to In-Charge', description: 'Approve DO examination and forward to In-Charge', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'FORWARD_TO_CLOSING_AUTHORITY', label: 'Forward to Closing Authority', description: 'Forward directly to Closing Authority for final decision', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_DO', label: 'Send Back to DO', description: 'Return to Dealing Officer for rework', style: 'return', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_INCHARGE') {
      if (['incharge_review', 'escalated'].includes(status)) {
        actions.push({ id: 'APPROVE_CLOSURE', label: 'Approve for Closure', description: 'Approve and forward to Closing Authority', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_REVIEWER', label: 'Send Back to Reviewer', description: 'Return to Reviewer for further scrutiny', style: 'return', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_DO', label: 'Send Back to Dealing Officer', description: 'Return to DO for additional examination', style: 'return', requiresRemarks: true });
        actions.push({ id: 'REASSIGN', label: 'Reassign to Another DO', description: 'Reassign to a different Dealing Officer', style: 'info', requiresRemarks: true, requiresTarget: true, targetType: 'user' });
      }
    }

    if (role === 'CEPC_CLOSING_AUTHORITY') {
      if (status === 'awaiting_closure') {
        actions.push({ id: 'CLOSE_COMPLAINT', label: 'Close Complaint', description: 'Final decision — close complaint', style: 'close', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_INCHARGE', label: 'Send Back to In-Charge', description: 'Return for further review', style: 'return', requiresRemarks: true });
        actions.push({ id: 'FORWARD_TO_OTHER_OFFICE', label: 'Forward to Other Office', description: 'Forward to another RBI office', style: 'forward', requiresRemarks: true, requiresTarget: true, targetType: 'department' });
        actions.push({ id: 'FORWARD_TO_REGULATORY_BODY', label: 'Forward to Regulatory Body', description: 'Forward to external regulatory body (SEBI, IRDAI, etc.)', style: 'forward', requiresRemarks: true, requiresTarget: true, targetType: 'department' });
        actions.push({ id: 'FORWARD_TO_OTHER_RBI_DEPT', label: 'Forward to Other RBI Dept', description: 'Forward to another RBI department', style: 'forward', requiresRemarks: true, requiresTarget: true, targetType: 'department' });
      }
      if (status === 'closed' || status === 'resolved') {
        actions.push({ id: 'REOPEN', label: 'Reopen Complaint', description: 'Reopen a closed complaint for further action', style: 'escalate', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_ADMIN') {
      if (!this.isTerminalState()) {
        actions.push({ id: 'REASSIGN', label: 'Reassign', description: 'Reassign to another officer', style: 'info', requiresRemarks: true, requiresTarget: true, targetType: 'user' });
        actions.push({ id: 'ESCALATE', label: 'Escalate', description: 'Escalate complaint', style: 'escalate', requiresRemarks: true });
        actions.push({ id: 'CLOSE_COMPLAINT', label: 'Close (Admin)', description: 'Admin closure', style: 'close', requiresRemarks: true });
      }
      if (status === 'closed' || status === 'resolved') {
        actions.push({ id: 'REOPEN', label: 'Reopen Complaint', description: 'Reopen closed complaint if needed', style: 'escalate', requiresRemarks: true });
      }
    }

    if (role === 'CEPC_CONTACT_PERSON') {
      if (status === 'forwarded_to_contact') {
        actions.push({ id: 'CONTACT_RESPONSE', label: 'Submit Response', description: 'Submit response and return to Dealing Officer', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'CONTACT_REASSIGN', label: 'Reassign to Another Contact', description: 'Reassign to a different contact person in the region', style: 'info', requiresRemarks: true, requiresTarget: true, targetType: 'user' });
      }
    }

    // DO can forward to contact person
    if (role === 'CEPC_DO' && status === 'in_progress') {
      actions.push({ id: 'FORWARD_TO_CONTACT', label: 'Forward to Contact Person', description: 'Forward to entity Contact Person for regional input', style: 'forward', requiresRemarks: true, requiresTarget: true, targetType: 'user' });
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
    if (roles.includes('CEPC_ADMIN')) this.userRole.set('CEPC_ADMIN');
    else if (roles.includes('CEPC_CLOSING_AUTHORITY')) this.userRole.set('CEPC_CLOSING_AUTHORITY');
    else if (roles.includes('CEPC_INCHARGE')) this.userRole.set('CEPC_INCHARGE');
    else if (roles.includes('CEPC_REVIEWER')) this.userRole.set('CEPC_REVIEWER');
    else if (roles.includes('CEPC_CONTACT_PERSON')) this.userRole.set('CEPC_CONTACT_PERSON');
    else this.userRole.set('CEPC_DO');

    const id = this.route.snapshot.params['id'];
    this.loadComplaint(id);
    this.loadCepcOfficers();
    this.loadContactPersons();
  }

  private loadComplaint(complaintNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}`).subscribe({
      next: (res) => {
        this.complaint.set(res?.data || null);
        this.loading.set(false);
      },
      error: () => {
        this.complaint.set(null);
        this.loading.set(false);
      }
    });
  }

  private loadCepcOfficers() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/by-role?role=CEPC_DO`).subscribe({
      next: (res) => {
        const users = (res || []).map((u: any) => ({ id: u.username || u.userId, name: u.displayName || `${u.firstName} ${u.lastName}` }));
        this.cepcOfficers.set(users);
      },
      error: () => this.cepcOfficers.set([])
    });
  }

  private loadContactPersons() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/by-role?role=CEPC_CONTACT_PERSON`).subscribe({
      next: (res) => {
        const users = (res || []).map((u: any) => ({ id: u.username || u.userId, name: u.displayName || `${u.firstName} ${u.lastName}` }));
        this.contactPersons.set(users);
      },
      error: () => this.contactPersons.set([])
    });
  }

  onDocumentUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;
    const file = input.files[0];
    if (file.size > this.uploadLimits.maxFileSizeBytes()) {
      this.toast.error('ui.upload.error_file_too_large', { size: String(this.uploadLimits.maxFileSizeMb()) });
      input.value = '';
      return;
    }

    this.uploadingDoc.set(true);
    const doc = {
      id: 'DOC-' + Date.now(),
      name: file.name,
      size: file.size < 1024 * 1024 ? (file.size / 1024).toFixed(0) + ' KB' : (file.size / 1024 / 1024).toFixed(1) + ' MB',
      uploadedBy: this.auth.currentUser()?.username || '',
      uploadedAt: new Date().toISOString()
    };
    this.documents.set([...this.documents(), doc]);
    this.uploadingDoc.set(false);
    input.value = '';
  }

  /** "Confirm Close Complaint", not a bare "Confirm": the caption restates what is about to happen. */
  confirmLabel = computed(() => {
    const action = this.selectedAction();
    return action ? `Confirm ${action.label}` : 'Confirm';
  });

  selectAction(action: ActionDef) {
    this.selectedAction.set(action);
    this.remarks = '';
    this.targetUser = '';
    this.targetDepartment = '';
    this.actionResult.set('');
  }

  cancelAction() {
    this.selectedAction.set(null);
    this.remarks = '';
  }

  submitAction() {
    const action = this.selectedAction();
    if (!action) return;

    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    if (!complaintNumber) return;

    this.processing.set(true);

    const body: any = {
      action: action.id,
      remarks: this.remarks,
      actor: this.auth.currentUser()?.username || '',
      targetUser: this.targetUser,
      targetDepartment: this.targetDepartment
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`,
      body
    ).subscribe({
      next: (res) => {
        this.actionSuccess.set(true);
        const assignedTo = res?.data?.assignedOfficer ? ` Assigned to: ${res.data.assignedOfficer}` : '';
        this.actionResult.set(`Action "${action.label}" completed successfully. New status: ${res?.data?.newStatus || 'updated'}.${assignedTo}`);
        this.processing.set(false);
        this.selectedAction.set(null);
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
    return ['closed', 'resolved', 'rejected', 'withdrawn'].includes(status);
  }

  // ── Editing the complaint record ────────────────────────────────────────────────────────────────
  // Mirrors the server's own rule (ComplaintController PUT /{id}): the case-handling roles may edit,
  // a contact person may not, and a settled record may not be edited at all. The server refuses
  // independently — this only avoids offering a control that would be rejected.
  private static readonly EDIT_ROLES: CepcRole[] = [
    'CEPC_DO', 'CEPC_REVIEWER', 'CEPC_INCHARGE', 'CEPC_CLOSING_AUTHORITY', 'CEPC_ADMIN'
  ];

  editing = signal(false);
  savingEdit = signal(false);
  editError = signal('');
  editForm = { status: '', priority: '', assignedOfficer: '', remarks: '' };

  canEdit(): boolean {
    return !this.isTerminalState()
      && CepcComplaintDetailComponent.EDIT_ROLES.includes(this.userRole());
  }

  startEdit() {
    const c = this.complaint() || {};
    this.editForm = {
      status: (c.status || '').toLowerCase(),
      priority: (c.priority || '').toLowerCase(),
      assignedOfficer: c.assignedTo || '',
      remarks: ''
    };
    this.editError.set('');
    this.editing.set(true);
  }

  cancelEdit() {
    this.editing.set(false);
    this.editError.set('');
  }

  saveEdit() {
    const id = this.complaint()?.id;
    if (!id) return;

    // The server rejects a blank mandatory field with "Please fill mandatory fields"; catching it here
    // saves a round trip without becoming the only check.
    if (!this.editForm.status.trim() || !this.editForm.priority.trim()) {
      this.editError.set('Please fill mandatory fields');
      return;
    }

    this.savingEdit.set(true);
    this.editError.set('');
    this.http.put<any>(`${environment.apiBaseUrl}/api/complaints/${id}`, this.editForm).subscribe({
      next: () => {
        this.savingEdit.set(false);
        this.editing.set(false);
        this.loadComplaint(this.complaint()?.complaintNumber || this.complaint()?.complaintId);
      },
      error: (err) => {
        this.savingEdit.set(false);
        this.editError.set(err.error?.message || err.message || 'The edit could not be saved');
      }
    });
  }

  goBack() {
    this.router.navigate(['/cepc/dashboard']);
  }
}
