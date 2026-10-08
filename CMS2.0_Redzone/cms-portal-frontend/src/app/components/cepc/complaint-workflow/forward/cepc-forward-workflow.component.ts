import { Component, DestroyRef, EventEmitter, Input, OnChanges, OnInit, Output, SimpleChanges, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../../environments/environment';
import { CepcContextService } from '../../../../services/cepc-context.service';
import { KeycloakAuthService } from '../../../../services/keycloak-auth.service';
import { SpeechButtonComponent } from '../../../../shared/speech-button/speech-button.component';
import { CommentEntry, ComplaintCommentsComponent } from '../shared/complaint-comments/complaint-comments.component';
import { TranslateOrPipe } from '../../../../pipes/translate-or.pipe';

// A row from REGULATOR_MASTER or RBI_DEPARTMENT_MASTER, as returned by /api/v1/masters/regulators and
// /api/v1/masters/rbi-departments. email is nullable: an admin may register a destination before its
// mailbox is confirmed, and the Forward tab refuses to submit without one.
interface ForwardRecipient {
  id: number;
  code: string;
  name: string;
  email: string | null;
}

/** What a successful forward submission changes on the parent-owned header/navigation chrome. */
export interface ForwardCompleted {
  approvalSentTo: string;
  complaintStatus: string;
}

@Component({
  selector: 'app-cepc-forward-workflow',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, ComplaintCommentsComponent, TranslateOrPipe],
  templateUrl: './cepc-forward-workflow.component.html',
  styleUrl: './cepc-forward-workflow.component.scss',
})
export class CepcForwardWorkflowComponent implements OnInit, OnChanges {
  private http = inject(HttpClient);
  readonly dept = inject(CepcContextService);
  private auth = inject(KeycloakAuthService);
  private destroyRef = inject(DestroyRef);
  // Resolves to the SAME instance the parent provides at component level — see the identical note in
  // CepcConciliationWorkflowComponent.
  private messageService = inject(MessageService);

  @Input() complaintId: any;
  @Input() complaintNumber = '';
  @Input() complaintOffice = '';
  @Input() userRole: 'DO' | 'REVIEWER' | 'INCHARGE' | 'CLOSING_AUTHORITY' | 'HEAD' = 'DO';
  @Input() complaintStatus = '';
  @Input() complaintStatusNav = '';
  @Input() complaintStatusNavLabel = '';
  /**
   * The forwardDraftDto block from the last /summary read. Re-applied whenever a new value arrives, even
   * while this tab is already open and mounted — matching the monolith, where every /summary read
   * hydrated these fields regardless of which tab happened to be visible at the time (see ngOnChanges).
   */
  @Input() initialDraft: any = null;

  // The comment feed is genuinely shared across the Assessment/Forward/Final Decision tabs (same draft
  // text, same list, visible no matter which tab you switch to), so it stays owned by the parent and is
  // only ever read/relayed here — see ComplaintCommentsComponent's class doc for the full rationale.
  @Input() assessmentComment = '';
  @Output() assessmentCommentChange = new EventEmitter<string>();
  @Input() assessmentComments: CommentEntry[] = [];

  // Also shared with Final Decision (not yet extracted), which both displays and separately edits the
  // same underlying value — see the identical field on the parent.
  @Input() complaintStatusOnPortal = '';
  @Output() complaintStatusOnPortalChange = new EventEmitter<string>();

  @Output() persistCommentRequested = new EventEmitter<void>();
  @Output() forwardCompleted = new EventEmitter<ForwardCompleted>();
  /** Mirrors the parent's generic `assessmentSaving` flag while the meta-row Save icon's quick-save runs. */
  @Output() savingStateChange = new EventEmitter<boolean>();

  readonly complaintStatusOnPortalOptions = ['Draft', 'Pending', 'Rejected', 'Withdrawn', 'Closed'];

  forwardTarget = signal<'REGULATORY_BODIES' | 'RBI_DEPARTMENT' | 'OFFICE' | ''>('');
  // The reviewer's only forwarding route is another RBI department; the regulator and office routes
  // stay with the incharge and closing authority.
  canForwardTo = computed(() => {
    const reviewerOnly = this.userRole === 'REVIEWER';
    return {
      REGULATORY_BODIES: !reviewerOnly,
      RBI_DEPARTMENT: true,
      OFFICE: !reviewerOnly
    };
  });
  forwardRegulatorName = '';
  forwardRegulatorEmail = '';
  forwardDepartmentName = '';
  forwardDepartmentEmail = '';
  forwardOfficeCode = '';
  forwardOfficeName = '';
  // Transfer Office: which layout's offices the "RBIO Offices" picker below shows (UST563).
  forwardOfficeLayout = signal<'RBIO' | 'CEPC'>('RBIO');
  forwardTransferReason = '';
  forwardOfficeComments = '';
  officeList = signal<{ officeCode: string; officeName: string; officeType: string }[]>([]);
  showForwardConfirm = signal(false);
  forwardSubmitting = signal(false);
  forwardFieldErrors = signal<Record<string, string>>({});

  regulatorResults = signal<ForwardRecipient[]>([]);
  regulatorSearchLoading = signal(false);
  showRegulatorDropdown = signal(false);
  departmentResults = signal<ForwardRecipient[]>([]);
  departmentSearchLoading = signal(false);
  showDepartmentDropdown = signal(false);
  private regulatorSearchTimeout: any = null;
  private departmentSearchTimeout: any = null;

  ngOnInit() {
    this.loadOfficeList();
  }

  ngOnChanges(changes: SimpleChanges) {
    // @Input()s are not bound yet inside the constructor, and a later /summary re-read (e.g. saving the
    // shared left-column Basic Details form) can arrive while this tab is already mounted — both cases
    // land here.
    if (changes['initialDraft']) {
      this.applyForwardDraft(this.initialDraft);
    }
  }

  loadOfficeList() {
    const layout = this.forwardOfficeLayout();
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/masters/transfer-offices`, {
      params: { layout }
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.officeList.set(res?.data || []);
        // A draft restored before this list arrived has the code but not the name the confirm dialog shows.
        this.resolveForwardOfficeName();
      },
      error: () => this.officeList.set([])
    });
  }

  /** "Transfer office" toggle: re-fetches the destination list for the newly chosen layout. */
  onForwardOfficeLayoutChange(layout: 'RBIO' | 'CEPC') {
    this.forwardOfficeLayout.set(layout);
    this.forwardOfficeCode = '';
    this.forwardOfficeName = '';
    this.officeList.set([]);
    this.loadOfficeList();
  }

  // ═══ Forward recipient lookups ═══
  // Both masters are searched server-side. MasterDataController returns a bare array rather than the
  // {data} envelope the complaint controllers use, hence the fallback on each unwrap.

  onRegulatorSearchInput(value: string) {
    this.forwardRegulatorName = value;
    // A hand-typed name no longer corresponds to the picked row's address, so drop it and make the
    // officer pick again rather than mailing the previous regulator under a new name.
    this.forwardRegulatorEmail = '';
    if (this.regulatorSearchTimeout) clearTimeout(this.regulatorSearchTimeout);
    this.regulatorSearchTimeout = setTimeout(() => this.searchRegulators(value), 300);
  }

  private searchRegulators(query: string) {
    this.regulatorSearchLoading.set(true);
    this.showRegulatorDropdown.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/masters/regulators`, {
      params: query ? { q: query } : {}
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.regulatorResults.set(res?.data ?? res ?? []);
        this.regulatorSearchLoading.set(false);
      },
      error: () => {
        this.regulatorResults.set([]);
        this.regulatorSearchLoading.set(false);
      }
    });
  }

  openRegulatorDropdown() {
    if (this.regulatorResults().length === 0) this.searchRegulators('');
    else this.showRegulatorDropdown.set(true);
  }

  selectRegulator(regulator: ForwardRecipient) {
    this.forwardRegulatorName = regulator.name;
    this.forwardRegulatorEmail = regulator.email || '';
    this.showRegulatorDropdown.set(false);
    this.clearForwardError('regulator');
  }

  onRegulatorBlur() {
    setTimeout(() => this.showRegulatorDropdown.set(false), 200);
  }

  onDepartmentSearchInput(value: string) {
    this.forwardDepartmentName = value;
    this.forwardDepartmentEmail = '';
    if (this.departmentSearchTimeout) clearTimeout(this.departmentSearchTimeout);
    this.departmentSearchTimeout = setTimeout(() => this.searchDepartments(value), 300);
  }

  private searchDepartments(query: string) {
    this.departmentSearchLoading.set(true);
    this.showDepartmentDropdown.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/masters/rbi-departments`, {
      params: query ? { q: query } : {}
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.departmentResults.set(res?.data ?? res ?? []);
        this.departmentSearchLoading.set(false);
      },
      error: () => {
        this.departmentResults.set([]);
        this.departmentSearchLoading.set(false);
      }
    });
  }

  openDepartmentDropdown() {
    if (this.departmentResults().length === 0) this.searchDepartments('');
    else this.showDepartmentDropdown.set(true);
  }

  selectDepartment(department: ForwardRecipient) {
    this.forwardDepartmentName = department.name;
    this.forwardDepartmentEmail = department.email || '';
    this.showDepartmentDropdown.set(false);
    this.clearForwardError('department');
  }

  onDepartmentBlur() {
    setTimeout(() => this.showDepartmentDropdown.set(false), 200);
  }

  private clearForwardError(key: string) {
    const errors = { ...this.forwardFieldErrors() };
    delete errors[key];
    delete errors['target'];
    this.forwardFieldErrors.set(errors);
  }

  onForwardOfficeSelected(officeCode: string) {
    this.forwardOfficeCode = officeCode;
    const office = this.officeList().find(o => o.officeCode === officeCode);
    this.forwardOfficeName = office?.officeName || '';
    this.clearForwardError('office');
  }

  get forwardTargetLabel(): string {
    switch (this.forwardTarget()) {
      case 'REGULATORY_BODIES': return 'Other Regulatory Bodies';
      case 'RBI_DEPARTMENT': return 'Other RBI Department';
      case 'OFFICE': return 'Other Office';
      default: return '';
    }
  }

  /**
   * Strict visibility: switching the routing option must not carry any other option's values into
   * a submission it never showed — so every option-specific field is reset here, not just errors.
   */
  selectForwardTarget(target: 'REGULATORY_BODIES' | 'RBI_DEPARTMENT' | 'OFFICE') {
    this.forwardTarget.set(target);
    this.forwardFieldErrors.set({});

    this.forwardRegulatorName = '';
    this.forwardRegulatorEmail = '';
    this.forwardDepartmentName = '';
    this.forwardDepartmentEmail = '';
    this.forwardOfficeCode = '';
    this.forwardOfficeName = '';
    this.forwardOfficeLayout.set('RBIO');
    this.forwardTransferReason = '';
    this.forwardOfficeComments = '';
    this.setComplaintStatusOnPortal('');
    this.setAssessmentComment('');

    if (target === 'OFFICE') {
      this.loadOfficeList();
    }
  }

  /**
   * The regulator and department branches close the complaint on submit, so an unaddressed forward
   * would close it having notified nobody. Every branch is checked, not just OFFICE.
   */
  private validateForward(): boolean {
    const errors: Record<string, string> = {};
    const target = this.forwardTarget();

    if (!target) {
      errors['target'] = 'Select where to forward this complaint.';
    } else if (target === 'OFFICE') {
      if (!this.forwardOfficeCode) errors['office'] = 'Select an office.';
      if (!this.forwardTransferReason.trim()) errors['transferReason'] = 'Enter a reason for transfer.';
      if (!this.forwardOfficeComments.trim()) errors['officeComments'] = 'Enter the sent-from-office comments.';
    } else if (target === 'REGULATORY_BODIES') {
      if (!this.forwardRegulatorName.trim()) errors['regulator'] = 'Search for and select a regulator.';
      else if (!this.forwardRegulatorEmail.trim()) errors['regulatorEmail'] = 'This regulator has no email on record. Pick another or ask an admin to add one.';
      else if (!this.isValidEmail(this.forwardRegulatorEmail)) errors['regulatorEmail'] = 'Enter a valid email address.';
      if (!this.complaintStatusOnPortal.trim()) errors['complaintStatusOnPortal'] = 'Select the complaint status on portal.';
    } else if (target === 'RBI_DEPARTMENT') {
      if (!this.forwardDepartmentName.trim()) errors['department'] = 'Search for and select a department.';
      else if (!this.forwardDepartmentEmail.trim()) errors['departmentEmail'] = 'This department has no email on record. Pick another or ask an admin to add one.';
      else if (!this.isValidEmail(this.forwardDepartmentEmail)) errors['departmentEmail'] = 'Enter a valid email address.';
      if (!this.complaintStatusOnPortal.trim()) errors['complaintStatusOnPortal'] = 'Select the complaint status on portal.';
    }

    this.forwardFieldErrors.set(errors);
    return Object.keys(errors).length === 0;
  }

  private isValidEmail(value: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim());
  }

  /** Bridged from the parent's header Forward button via @ViewChild — must stay public. */
  openForwardConfirm() {
    if (!this.validateForward()) return;
    this.showForwardConfirm.set(true);
  }

  cancelForwardConfirm() {
    this.showForwardConfirm.set(false);
  }

  confirmForward() {
    if (this.forwardSubmitting()) return;
    this.forwardSubmitting.set(true);

    this.persistCommentRequested.emit();

    const target = this.forwardTarget();

    if (target === 'OFFICE') {
      const fromOffice = this.complaintOffice || '';
      const toOffice = this.forwardOfficeCode;
      const deptOf = (code: string) => (code || '').split('-')[0] || this.dept.cfg().code;
      const payload = {
        complaintNumber: this.complaintNumber,
        fromOffice,
        toOffice,
        transferType: `${deptOf(fromOffice)}_${deptOf(toOffice)}`,
        reason: this.forwardTransferReason || '',
        comments: this.forwardOfficeComments || '',
        requestedBy: this.auth.currentUser()?.username || ''
      };
      this.http.post(`${environment.apiBaseUrl}/api/v1/crpc/head/transfers/request`, payload)
        .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
        next: () => {
          this.forwardSubmitting.set(false);
          this.showForwardConfirm.set(false);
          this.forwardCompleted.emit({ approvalSentTo: this.forwardOfficeName, complaintStatus: 'SENT_TO_OFFICE' });
          this.messageService.add({
            severity: 'success',
            summary: 'Transfer Requested',
            detail: `Transfer to ${this.forwardOfficeName || 'the selected office'} is awaiting the office head's decision.`
          });
        },
        error: (err) => {
          this.forwardSubmitting.set(false);
          this.showForwardConfirm.set(false);
          this.messageService.add({
            severity: 'error',
            summary: 'Transfer Request Failed',
            detail: err?.error?.message || 'Could not submit the transfer request. Please try again.'
          });
        }
      });
      return;
    }

    // Other Regulatory Bodies / Other RBI Department: closes the complaint immediately.
    let assignedToName = '';
    let assignedToEmail = '';
    if (target === 'REGULATORY_BODIES') {
      assignedToName = this.forwardRegulatorName;
      assignedToEmail = this.forwardRegulatorEmail;
    } else if (target === 'RBI_DEPARTMENT') {
      assignedToName = this.forwardDepartmentName;
      assignedToEmail = this.forwardDepartmentEmail;
    }

    const payload = {
      target: 'OTHER_' + target,
      assignedTo: assignedToEmail || assignedToName,
      assignedToName: assignedToName || this.forwardTargetLabel,
      assignmentMode: 'MANUAL',
      remarks: this.assessmentComment || null,
      complaintStatusOnPortal: this.complaintStatusOnPortal || null,
      performedBy: this.auth.currentUser()?.username || '',
      performedByRole: this.userRole
    };

    this.http.post(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/send-for-approval`, payload)
      .pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.forwardSubmitting.set(false);
        this.showForwardConfirm.set(false);
        this.forwardCompleted.emit({ approvalSentTo: this.forwardTargetLabel, complaintStatus: 'CLOSED' });
        this.messageService.add({
          severity: 'success',
          summary: 'Complaint Forwarded',
          detail: `Forwarded to ${this.forwardTargetLabel || 'the selected recipient'}. Status: ${this.complaintStatusOnPortal || 'Closed'}.`
        });
      },
      error: (err) => {
        // This used to be a copy of the success handler, so a failed forward still told the officer
        // the complaint had closed while it stayed open in the database. The status is left alone.
        this.forwardSubmitting.set(false);
        this.showForwardConfirm.set(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Forward Failed',
          detail: err?.error?.message
            || 'Could not forward this complaint. It has not been closed — please try again.'
        });
      }
    });
  }

  /** Restores the Forward tab from a saved draft. Target first: it decides which sub-panel renders. */
  private applyForwardDraft(fw: any) {
    if (!fw) return;

    // A draft saved by another rung can name a target this user has no card for; leaving it selected
    // would render a sub-panel with no way to see or change which target it belongs to.
    const target = fw.target || '';
    this.forwardTarget.set((this.canForwardTo() as Record<string, boolean>)[target] ? target : '');
    this.forwardRegulatorName = fw.regulatorName || '';
    this.forwardRegulatorEmail = fw.regulatorEmail || '';
    this.forwardDepartmentName = fw.departmentName || '';
    this.forwardDepartmentEmail = fw.departmentEmail || '';
    this.forwardOfficeCode = fw.officeCode || '';
    this.forwardOfficeLayout.set(fw.officeLayout === 'CEPC' ? 'CEPC' : 'RBIO');
    this.forwardTransferReason = fw.transferReason || '';
    this.forwardOfficeComments = fw.officeComments || '';
    if (target === 'RBI_DEPARTMENT' || target === 'REGULATORY_BODIES') {
      this.setComplaintStatusOnPortal(fw.complaintStatusOnPortal || '');
    }
    this.resolveForwardOfficeName();
  }

  /** The office name is derived from the code via the master list, which may not have loaded yet. */
  private resolveForwardOfficeName() {
    if (!this.forwardOfficeCode) {
      this.forwardOfficeName = '';
      return;
    }
    const office = this.officeList().find(o => o.officeCode === this.forwardOfficeCode);
    if (office) this.forwardOfficeName = office.officeName;
  }

  private forwardDraftPayload() {
    return {
      target: this.forwardTarget() || null,
      regulatorName: this.forwardRegulatorName || null,
      regulatorEmail: this.forwardRegulatorEmail || null,
      departmentName: this.forwardDepartmentName || null,
      departmentEmail: this.forwardDepartmentEmail || null,
      officeCode: this.forwardOfficeCode || null,
      officeLayout: this.forwardOfficeLayout(),
      transferReason: this.forwardTransferReason || null,
      officeComments: this.forwardOfficeComments || null,
      complaintStatusOnPortal: this.complaintStatusOnPortal || null,
    };
  }

  /**
   * The meta row's generic Save icon, for the Forward tab specifically.
   *
   * <p>PUTs just the forwardDraftDto block of the summary patch — the endpoint patches on key PRESENCE, so
   * sending this one block cannot blank what the other tabs have saved.
   */
  saveForwardDraft() {
    this.persistCommentRequested.emit();
    this.savingStateChange.emit(true);

    this.http.put<any>(
      `${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/summary`)}`,
      { forwardDraftDto: this.forwardDraftPayload() }
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.savingStateChange.emit(false);
        // Only this block is re-read and re-applied; the response carries every block, but re-hydrating
        // the rest here would overwrite other tabs' not-yet-saved edits with their last-saved values.
        if (res?.data) this.applyForwardDraft(res.data.forwardDraftDto);
        this.messageService.add({ severity: 'success', summary: 'Saved', detail: 'Forward details saved.' });
      },
      error: (err) => {
        this.savingStateChange.emit(false);
        this.messageService.add({
          severity: 'error',
          summary: 'Save Failed',
          detail: err?.error?.message || 'Could not save. Check the fields and try again.'
        });
      }
    });
  }

  setComplaintStatusOnPortal(value: string) {
    this.complaintStatusOnPortal = value;
    this.complaintStatusOnPortalChange.emit(value);
  }

  setAssessmentComment(value: string) {
    this.assessmentComment = value;
    this.assessmentCommentChange.emit(value);
  }

  formatCommentDate(dateStr: string): string {
    if (!dateStr) return '';
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return dateStr;
    const day = d.getDate().toString().padStart(2, '0');
    const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    const mon = months[d.getMonth()];
    const year = d.getFullYear();
    const hrs = d.getHours().toString().padStart(2, '0');
    const mins = d.getMinutes().toString().padStart(2, '0');
    return `${day} ${mon} ${year}, ${hrs}:${mins}`;
  }

  formatStatusLabel(status: string): string {
    if (!status) return '';
    return status
      .toLowerCase()
      .split('_')
      .map(word => word.charAt(0).toUpperCase() + word.slice(1))
      .join(' ');
  }
}
