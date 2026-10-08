import { Component, DestroyRef, EventEmitter, Input, OnInit, Output, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MessageService } from 'primeng/api';
import { environment } from '../../../../../environments/environment';
import { CepcContextService } from '../../../../services/cepc-context.service';
import { SpeechButtonComponent } from '../../../../shared/speech-button/speech-button.component';
import { TranslateOrPipe } from '../../../../pipes/translate-or.pipe';

/** A row from the entity typeahead, GET /api/v1/routing/entities/list. */
interface EntitySearchResult {
  id: number;
  name: string;
  department: string;
  entityType: string;
  entityCategory?: string | null;
  entityTypeDetail?: string | null;
  entityTypeDisplay?: string | null;
  city?: string | null;
  state?: string | null;
}

/** One of the Conciliation tab's six "Entity Name" dropdowns, and its own typeahead state. */
interface OtherEntitySlot {
  id: number | null;
  name: string;
  searchText: string;
  results: EntitySearchResult[];
  showDropdown: boolean;
  loading: boolean;
}

// One CONCILIATION_MEETINGS row, as returned by /api/complaints/{department}/{id}/conciliation. The newest row
// is the live meeting; the earlier rows are the reschedule trail and are read-only.
interface ConciliationMeeting {
  id: number | null;
  meetingStatus: string;
  meetingDate: string | null;
  meetingTime: string | null;
  acceptedByComplainant: boolean | null;
  acceptedByEntity: boolean | null;
  conductedThroughVc: boolean | null;
  meetingComments: string | null;
  comments: string | null;
  wantOtherEntities: boolean | null;
  otherEntities: { id: number; name: string | null }[] | null;
  createdBy: string | null;
  createdAt: string | null;
  updatedBy: string | null;
  updatedAt: string | null;
}

/** What a successful conciliation save changes on the parent-owned header/status chrome. */
export interface ConciliationStatusChange {
  workflowStage?: string;
  statusNav?: string;
  statusNavLabel?: string;
}

@Component({
  selector: 'app-cepc-conciliation-workflow',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslateOrPipe],
  templateUrl: './cepc-conciliation-workflow.component.html',
  styleUrl: './cepc-conciliation-workflow.component.scss',
})
export class CepcConciliationWorkflowComponent implements OnInit {
  private http = inject(HttpClient);
  readonly dept = inject(CepcContextService);
  private destroyRef = inject(DestroyRef);
  // Resolves to the SAME instance the parent provides at component level (providers: [MessageService]
  // on CepcComplaintDetailsView) — Angular DI walks up the component tree, so this child's toasts land
  // on the parent's <p-toast> without anything needing to be passed down.
  private messageService = inject(MessageService);

  @Input() complaintId: any;
  @Input() complaintNumber = '';
  @Input() isReadOnlyViewer = false;
  @Input() canMutate = false;
  @Input() summaryLoadState: 'loading' | 'ready' | 'failed' = 'loading';
  @Input() complaintStatus = '';
  @Input() complaintStatusNav = '';
  @Input() complaintStatusNavLabel = '';

  /** Emitted after a successful save so the parent can update the header status chip/tab gating it owns. */
  @Output() statusChanged = new EventEmitter<ConciliationStatusChange>();
  /** Mirrors the parent's generic `assessmentSaving` flag while the meta-row Save icon's quick-save runs. */
  @Output() savingStateChange = new EventEmitter<boolean>();

  readonly conciliationStatuses = [
    { value: 'SCHEDULED', label: 'Scheduled' },
    { value: 'RESCHEDULED', label: 'Rescheduled' },
    { value: 'COMPLETED', label: 'Completed' },
    { value: 'CANCELLED', label: 'Cancelled' },
  ];
  meetingStatus = 'SCHEDULED';
  meetingDate = '';
  meetingTime = '';
  acceptedByComplainant: boolean | null = null;
  acceptedByEntity: boolean | null = null;
  conductedThroughVc: boolean | null = null;
  meetingComments = '';
  conciliationComments = '';
  /** Whether the officer wants to name entities beyond the complaint's own regulated entity. */
  wantOtherEntities: boolean | null = null;
  /** The six Entity Name dropdowns, shown together once wantOtherEntities is Yes. */
  otherEntities: OtherEntitySlot[] = this.createOtherEntitySlots();
  private otherEntitySearchTimeouts: (ReturnType<typeof setTimeout> | null)[] = new Array(6).fill(null);
  conciliationCurrent = signal<ConciliationMeeting | null>(null);
  conciliationHistory = signal<ConciliationMeeting[]>([]);
  conciliationLoading = signal(false);
  conciliationSaving = signal(false);
  conciliationError = signal('');
  conciliationSaved = signal(false);
  conciliationFieldErrors = signal<Record<string, string>>({});
  showConciliationDialog = signal(false);
  showMeetingScheduledSuccess = signal(false);
  private conciliationLoadedFor = '';

  ngOnInit() {
    // @Input()s are not yet bound inside the constructor, so complaintId would still read as undefined
    // there. The parent only ever renders this component while its Conciliation tab is active, so
    // ngOnInit — the first hook to see bound inputs — is the "tab just opened" signal that used to be
    // an effect() on assessmentTab().
    this.loadConciliation();
  }

  // Getters, not computed(): meetingStatus is an ngModel field rather than a signal.

  /** Picking RESCHEDULED opens a new meeting server-side, preserving the current one's minutes. */
  get conciliationOpensNewMeeting(): boolean {
    const current = this.conciliationCurrent();
    if (!current) return true;
    if (this.meetingStatus === 'RESCHEDULED') return true;
    return current.meetingStatus === 'COMPLETED' || current.meetingStatus === 'CANCELLED';
  }

  /** The backend requires a date and time while the meeting is still live. */
  get conciliationRequiresSchedule(): boolean {
    return this.meetingStatus === 'SCHEDULED' || this.meetingStatus === 'RESCHEDULED';
  }

  loadConciliation(force = false) {
    const id = this.complaintId;
    if (!id) return;
    if (!force && this.conciliationLoadedFor === String(id)) return;

    this.conciliationLoading.set(true);
    this.conciliationError.set('');
    this.http.get<any>(`${environment.apiBaseUrl}${this.dept.cx(`${id}/conciliation`)}`)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.conciliationLoadedFor = String(id);
          this.applyConciliation(res?.data || {});
          this.conciliationLoading.set(false);
        },
        error: (err) => {
          this.conciliationLoading.set(false);
          this.conciliationError.set(err?.status === 404
            ? 'This complaint could not be found.'
            : 'Could not load the conciliation details. Retry.');
        }
      });
  }

  private applyConciliation(data: any) {
    const current: ConciliationMeeting | null = data.current || null;
    this.conciliationCurrent.set(current);
    // Newest first, and the live meeting is edited in the form above rather than listed as history.
    this.conciliationHistory.set(((data.history || []) as ConciliationMeeting[])
      .filter(m => !current || m.id !== current.id)
      .reverse());

    this.meetingStatus = current?.meetingStatus || 'SCHEDULED';
    this.meetingDate = (current?.meetingDate || '').substring(0, 10);
    this.meetingTime = (current?.meetingTime || '').substring(0, 5);
    this.acceptedByComplainant = current?.acceptedByComplainant ?? null;
    this.acceptedByEntity = current?.acceptedByEntity ?? null;
    this.conductedThroughVc = current?.conductedThroughVc ?? null;
    this.meetingComments = current?.meetingComments || '';
    this.conciliationComments = current?.comments || '';
    this.wantOtherEntities = current?.wantOtherEntities ?? null;
    this.otherEntities = this.createOtherEntitySlots();
    (current?.otherEntities || []).slice(0, 6).forEach((entity, index) => {
      this.otherEntities[index].id = entity.id;
      this.otherEntities[index].name = entity.name || '';
      this.otherEntities[index].searchText = entity.name || '';
    });
    this.conciliationFieldErrors.set({});
  }

  private createOtherEntitySlots(): OtherEntitySlot[] {
    return Array.from({ length: 6 }, () => (
      { id: null, name: '', searchText: '', results: [], showDropdown: false, loading: false }
    ));
  }

  onOtherEntitySearchInput(index: number, value: string) {
    const slot = this.otherEntities[index];
    slot.searchText = value;
    slot.id = null;
    slot.name = '';
    const pending = this.otherEntitySearchTimeouts[index];
    if (pending) clearTimeout(pending);
    if (!value || value.length < 2) {
      slot.showDropdown = false;
      slot.results = [];
      return;
    }
    this.otherEntitySearchTimeouts[index] = setTimeout(() => this.searchOtherEntity(index, value), 300);
  }

  private searchOtherEntity(index: number, query: string) {
    const slot = this.otherEntities[index];
    slot.loading = true;
    slot.showDropdown = true;
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/routing/entities/list`, {
      params: { search: query }
    }).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        slot.results = res?.data || [];
        slot.loading = false;
      },
      error: () => {
        slot.results = [];
        slot.loading = false;
      }
    });
  }

  selectOtherEntity(index: number, entity: EntitySearchResult) {
    const slot = this.otherEntities[index];
    slot.id = entity.id;
    slot.name = entity.name;
    slot.searchText = entity.name;
    slot.showDropdown = false;
  }

  onOtherEntityBlur(index: number) {
    setTimeout(() => { this.otherEntities[index].showDropdown = false; }, 200);
  }

  /** What confirmConciliationDialog sends: only the slots an entity was actually picked for. */
  private otherEntitiesPayload(): { id: number; name: string }[] {
    if (!this.wantOtherEntities) return [];
    return this.otherEntities
      .filter(slot => slot.id !== null)
      .map(slot => ({ id: slot.id as number, name: slot.name }));
  }

  private validateConciliation(): boolean {
    const errors: Record<string, string> = {};
    if (this.conciliationRequiresSchedule) {
      if (!this.meetingDate) errors['meetingDate'] = 'Meeting date is required while the meeting is open.';
      if (!this.meetingTime) errors['meetingTime'] = 'Meeting time is required while the meeting is open.';
    }
    if (this.meetingComments.length > 4000) errors['meetingComments'] = 'Maximum 4000 characters.';
    if (this.conciliationComments.length > 4000) errors['comments'] = 'Maximum 4000 characters.';
    this.conciliationFieldErrors.set(errors);
    return Object.keys(errors).length === 0;
  }

  /** Bridged from the parent's header Confirm button via @ViewChild — must stay public. */
  openConciliationDialog() {
    this.conciliationSaved.set(false);
    this.conciliationError.set('');

    // Say why instead of returning silently. A Confirm that does nothing at all is indistinguishable from
    // scheduling being broken, and this is reachable whenever the complaint is held by another officer —
    // the same rule the server enforces, which would answer 403 here.
    if (!this.complaintId || !this.canMutate) {
      this.conciliationError.set(this.summaryLoadState === 'ready'
        ? 'This complaint is not open for you to edit, so its meeting cannot be saved. It is assigned to another officer.'
        : 'The complaint is still loading. Try again in a moment.');
      return;
    }

    if (!this.validateConciliation()) return;
    this.showConciliationDialog.set(true);
  }

  cancelConciliationDialog() {
    this.showConciliationDialog.set(false);
  }

  closeMeetingScheduledSuccess() {
    this.showMeetingScheduledSuccess.set(false);
  }

  confirmConciliationDialog() {
    if (this.conciliationSaving()) return;

    const payload = {
      meetingStatus: this.meetingStatus,
      meetingDate: this.meetingDate || null,
      meetingTime: this.meetingTime || null,
      acceptedByComplainant: this.acceptedByComplainant,
      acceptedByEntity: this.acceptedByEntity,
      conductedThroughVc: this.conductedThroughVc,
      meetingComments: this.meetingComments || null,
      comments: this.conciliationComments || null,
      wantOtherEntities: this.wantOtherEntities,
      otherEntities: this.otherEntitiesPayload(),
    };

    this.conciliationSaving.set(true);
    this.http.put<any>(`${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/conciliation`)}`, payload)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (res) => {
          this.applyConciliation(res?.data || {});
          this.conciliationSaving.set(false);
          this.conciliationSaved.set(true);
          this.showConciliationDialog.set(false);
          this.showMeetingScheduledSuccess.set(true);
          // The stage the server computed, not an assumed MEETING_SCHEDULED: a meeting the officer marked
          // COMPLETED or CANCELLED has no meeting ahead of it, and claiming otherwise left the screen — and
          // the dashboard tab it drives — reporting work that no longer existed.
          if (res?.data?.workflowStage) this.statusChanged.emit({ workflowStage: res.data.workflowStage });
          this.applyConciliationStatus(res?.data);
        },
        error: (err) => {
          this.conciliationSaving.set(false);
          this.conciliationError.set(err?.error?.message
            || 'Could not save the conciliation details. Check the fields and try again.');
          this.showConciliationDialog.set(false);
        }
      });
  }

  /**
   * Repoints the header status chip at the status the conciliation save just persisted.
   *
   * <p>The chip renders `complaintStatusNavLabel`, which the parent fills from the summary's navBarDto —
   * not the `complaintStatus` input above it, which gates the tabs. Without this the officer confirms a
   * meeting, the complaint is stored as MEETING_SCHEDULED, and the chip keeps reading its old status until
   * the page is reloaded.
   */
  private applyConciliationStatus(data: any) {
    if (!data?.status) return;
    this.complaintStatusNav = data.status;
    this.complaintStatusNavLabel = data.statusLabel || this.formatStatusLabel(data.status);
    this.statusChanged.emit({ statusNav: this.complaintStatusNav, statusNavLabel: this.complaintStatusNavLabel });
  }

  /**
   * The meta row's generic Save icon, for the Conciliation tab specifically.
   *
   * <p>Bridged from the parent's `saveAssessment()` dispatcher via @ViewChild — must stay public. Reuses the
   * same endpoint Confirm does, because that endpoint already persists all eight fields — there is no
   * separate draft to keep. It still validates first: the backend rejects an open meeting with no date or
   * time, so skipping the check would turn a save into a 400.
   */
  saveConciliationDraft() {
    if (!this.complaintId) return;

    // A reschedule is the one save that is not idempotent: the backend opens a NEW meeting row for it, so
    // that the original date survives in the history panel. Saving repeatedly while drafting would leave a
    // trail of meetings that never happened, and there is no way to withdraw them.
    if (this.conciliationOpensNewMeeting) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Use Confirm',
        detail: 'This opens a new meeting in the history rather than updating the current one. '
          + 'Use Confirm so it is recorded once.'
      });
      return;
    }

    this.conciliationError.set('');
    this.conciliationSaved.set(false);
    if (!this.validateConciliation()) return;

    this.savingStateChange.emit(true);
    this.http.put<any>(
      `${environment.apiBaseUrl}${this.dept.cx(`${this.complaintId}/conciliation`)}`,
      {
        meetingStatus: this.meetingStatus,
        meetingDate: this.meetingDate || null,
        meetingTime: this.meetingTime || null,
        acceptedByComplainant: this.acceptedByComplainant,
        acceptedByEntity: this.acceptedByEntity,
        conductedThroughVc: this.conductedThroughVc,
        meetingComments: this.meetingComments || null,
        comments: this.conciliationComments || null,
        wantOtherEntities: this.wantOtherEntities,
        otherEntities: this.otherEntitiesPayload(),
      }
    ).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (res) => {
        this.savingStateChange.emit(false);
        this.applyConciliation(res?.data || {});
        this.conciliationSaved.set(true);
        if (res?.data?.workflowStage) this.statusChanged.emit({ workflowStage: res.data.workflowStage });
        this.applyConciliationStatus(res?.data);
        this.messageService.add({
          severity: 'success', summary: 'Saved', detail: 'Conciliation details saved.'
        });
      },
      error: (err) => {
        this.savingStateChange.emit(false);
        const detail = err?.error?.message
          || 'Could not save the conciliation details. Check the fields and try again.';
        this.conciliationError.set(detail);
        this.messageService.add({ severity: 'error', summary: 'Save Failed', detail });
      }
    });
  }

  resetConciliation() {
    this.applyConciliation({
      current: this.conciliationCurrent(),
      history: this.conciliationHistory(),
    });
    this.conciliationError.set('');
    this.conciliationSaved.set(false);
  }

  /** Appends dictated text without the stray leading space an inline concat binding leaves behind. */
  appendSpeech(field: 'meetingComments' | 'conciliationComments', text: string) {
    const spoken = (text || '').trim();
    if (!spoken) return;
    const existing = this[field];
    this[field] = existing ? `${existing} ${spoken}` : spoken;
  }

  conciliationStatusLabel(status: string | null | undefined): string {
    return this.conciliationStatuses.find(s => s.value === status)?.label || status || '—';
  }

  meetingStatusBadgeLabel(status: string | null | undefined): string {
    return `Meeting ${this.conciliationStatusLabel(status)}`;
  }

  meetingActionPhrase(status: string | null | undefined): string {
    switch (status) {
      case 'RESCHEDULED': return 'reschedule the meeting';
      case 'CANCELLED': return 'cancel the meeting';
      case 'COMPLETED': return 'mark the meeting as completed';
      default: return 'schedule the meeting';
    }
  }

  yesNoLabel(value: boolean | null | undefined): string {
    if (value === null || value === undefined) return 'Not answered';
    return value ? 'Yes' : 'No';
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
