import { Component, Input, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { ComplaintCorrespondenceService } from '../../../services/complaint-correspondence.service';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { environment } from '../../../../environments/environment';

/** One row of RBIO_MEETING, as the meetings endpoint returns it. */
export interface MeetingEvent {
  id: number;
  sequenceNo: number | null;
  eventType: 'SCHEDULED' | 'RESCHEDULED' | 'COMPLETED' | 'CANCELLED';
  meetingDate: string | null;
  meetingTime: string | null;
  participants: string | null;
  meetingMode: string | null;
  meetingVenue: string | null;
  rescheduleReason: string | null;
  entityAccepted: string | null;
  minutesOfMeeting: string | null;
  operative: boolean;
  supersededAt: string | null;
  performedBy: string | null;
  performedByRole: string | null;
  performedAt: string | null;
}

export interface MeetingParticipant {
  id: number;
  participantType: string;
  participantName: string;
  participantEmail: string | null;
  participantConfirmed: boolean;
}

/**
 * Conciliation meetings (UST496-503, 643-651).
 *
 * <p>WHAT THIS REPLACES, and why each change matters:
 *
 * <ul>
 *   <li><b>The schedule persisted nothing.</b> It sent `hearingDate` while the server read `meetingDate`,
 *       so every "hearing scheduled successfully" recorded no date at all. The canonical names are now sent.</li>
 *   <li><b>The MOM upload was a phantom with a faked success.</b> It POSTed to
 *       `/api/v1/complaints/{n}/documents`, which no controller implements, and its error handler set
 *       `minutesUploaded(true)` with the comment "Show success for UX even if API not available". A signed MOM
 *       is evidence of what the parties agreed; it was discarded behind a green tick. It now uses the real
 *       `/api/files/upload` endpoint and an upload failure reports failure.</li>
 *   <li><b>A refusal rendered as a success.</b> The `next` branch never checked `res.success`, and RBIO
 *       refusals answer 200 with `success:false`. Both are checked now.</li>
 *   <li><b>There was no Meeting Status at all</b> — no Scheduled/Rescheduled/Complete, no reschedule reason,
 *       no entity acceptance, no MOM text field, and no participants enum. All are present and the save button
 *       is blocked until the mandatory fields for the chosen status are complete.</li>
 *   <li><b>Meeting history had no source.</b> It read `complaint.hearingHistory`, a field no server response
 *       has ever set, so the block was permanently empty. It now reads the meetings endpoint, which returns
 *       superseded rows too — that is what makes UST502's retained history visible.</li>
 * </ul>
 *
 * <p>The client-side mandatory-field checks here are an AFFORDANCE, not the control. The server enforces the
 * same rules in `RbioMeetingService`, because a disabled button is bypassed by any direct POST.
 */
@Component({
  selector: 'app-rbio-conciliation',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './rbio-conciliation.component.html',
  styleUrl: './rbio-conciliation.component.scss'
})
export class RbioConciliationComponent implements OnInit {
  @Input() complaint: any = null;

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);
  private attachments = inject(ComplaintCorrespondenceService);

  // ── State ──
  showMeetingForm = signal(false);
  showOutcomeForm = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  /** Signals, not plain fields: a computed() that reads a plain field never re-evaluates. */
  meetings = signal<MeetingEvent[]>([]);
  participants = signal<MeetingParticipant[]>([]);
  loadingMeetings = signal(false);

  /**
   * Meeting Status drives which fields are mandatory (UST496, 499, 502).
   * A signal because the template's mandatory-field computeds depend on it.
   */
  meetingStatus = signal<'SCHEDULED' | 'RESCHEDULED' | 'COMPLETED'>('SCHEDULED');

  // ── Meeting form. Bound with ngModel, read by the computeds below, so they are signals. ──
  meetingDate = signal('');
  meetingTime = signal('');
  meetingVenue = signal('');
  meetingMode = signal('IN_PERSON');
  meetingParticipants = signal<'' | 'ENTITY' | 'COMPLAINANT' | 'BOTH'>('');
  rescheduleReason = signal('');
  entityAccepted = signal<'' | 'Y' | 'N'>('');
  minutesOfMeeting = signal('');
  meetingNotes = signal('');

  // ── Additional participant ──
  newParticipantName = signal('');
  newParticipantEmail = signal('');
  addingParticipant = signal(false);

  // ── Outcome form (unchanged behaviour) ──
  outcomeType: 'SETTLED' | 'FAILED' = 'SETTLED';
  compensationType: 'CONSEQUENTIAL_LOSS' | 'TIME_HARASSMENT' = 'CONSEQUENTIAL_LOSS';
  compensationAmount = '';
  failureReason = '';
  summaryNotes = '';

  uploadingMinutes = signal(false);
  minutesUploaded = signal(false);

  /**
   * Compensation caps.
   *
   * NOTE: these mirror the server's configured caps for display only. The server validates the award in
   * RbioCompensationService against SYSTEM_CONFIG; this is an affordance, not the limit.
   */
  readonly CONSEQUENTIAL_LOSS_CAP = 3000000;
  readonly TIME_HARASSMENT_CAP = 300000;

  /**
   * UST497: the six statuses that exclude a meeting.
   *
   * Kept as a client-side hint so the button can be disabled, but it is NOT the control: the server reads
   * RBIO_STATUS_MASTER.BLOCKS_MEETING and refuses a direct POST. This list existing in the browser alone was
   * the whole enforcement before.
   */
  private readonly MEETING_STATUS_EXCLUDED_STATUSES = [
    'advisory_complied', 'settled', 'withdrawn', 'rejected', 'award_passed', 'ombudsman_decision',
    'conciliated', 'adjudicated'
  ];

  ngOnInit(): void {
    this.loadMeetings();
  }

  get complaintNumber(): string {
    return this.complaint?.complaintNumber || this.complaint?.complaintId || '';
  }

  get conciliationStatus(): string {
    const stage = (this.complaint?.workflowStage || this.complaint?.status || '').toLowerCase();
    if (stage.includes('conciliation_settled') || stage.includes('conciliated')) return 'SETTLED';
    if (stage.includes('conciliation_failed')) return 'FAILED';
    if (stage.includes('conciliation') || stage.includes('meeting')) return 'IN_PROGRESS';
    return 'NOT_STARTED';
  }

  get isMeetingStatusExcluded(): boolean {
    const status = (this.complaint?.status || '').toLowerCase();
    return this.MEETING_STATUS_EXCLUDED_STATUSES.some(excluded => status === excluded);
  }

  /** The operative meeting, or null. Derived from the loaded rows rather than a second request. */
  currentMeeting = computed(() => this.meetings().find(m => m.operative) ?? null);

  /** The full history including superseded rows — the point of UST502. */
  meetingHistory = computed(() => this.meetings());

  confirmedParticipants = computed(() => this.participants().filter(p => p.participantConfirmed));

  /** UST498: additional ENTITY participants are capped at six. */
  additionalEntityCount = computed(() =>
    this.participants().filter(p => p.participantType === 'ENTITY').length);

  canAddParticipant = computed(() => this.additionalEntityCount() < 6);

  /**
   * Whether the mandatory fields for the chosen Meeting Status are complete (UST496, 499, 502).
   *
   * A computed over signals, so it re-evaluates as the user types. Reading plain class fields here would
   * register no dependency and the button would never enable — a bug class already found twice in this
   * codebase.
   */
  canSaveMeeting = computed(() => {
    const status = this.meetingStatus();
    if (status === 'COMPLETED') {
      // UST499: entity acceptance AND a non-blank MOM.
      return this.entityAccepted() !== '' && this.minutesOfMeeting().trim().length > 0;
    }
    // UST496: date, time and participants for SCHEDULED; UST502 adds the reason for RESCHEDULED.
    const base = this.meetingDate() !== '' && this.meetingTime() !== ''
      && this.meetingParticipants() !== '';
    return status === 'RESCHEDULED'
      ? base && this.rescheduleReason().trim().length > 0
      : base;
  });

  get currentCap(): number {
    return this.compensationType === 'CONSEQUENTIAL_LOSS'
      ? this.CONSEQUENTIAL_LOSS_CAP : this.TIME_HARASSMENT_CAP;
  }

  get amountNumeric(): number {
    return parseFloat(this.compensationAmount) || 0;
  }

  get amountPercentOfCap(): number {
    if (!this.amountNumeric) return 0;
    return (this.amountNumeric / this.currentCap) * 100;
  }

  get amountExceedsCap(): boolean {
    return this.amountNumeric > this.currentCap;
  }

  get amountApproachesCap(): boolean {
    return this.amountPercentOfCap >= 80 && !this.amountExceedsCap;
  }

  get capLabel(): string {
    return this.compensationType === 'CONSEQUENTIAL_LOSS'
      ? '30,00,000 (Consequential Loss)' : '3,00,000 (Time/Harassment)';
  }

  // ══════════════════════════ Loading ══════════════════════════

  /**
   * Loads the meeting history and participants.
   *
   * Deliberately NO catchError-to-default: a failure here must be visible. A swallowed error is how the old
   * screen showed an empty history for a complaint that had meetings.
   */
  loadMeetings(): void {
    const n = this.complaintNumber;
    if (!n) return;
    this.loadingMeetings.set(true);

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${n}/meetings`)
      .subscribe({
        next: res => {
          this.meetings.set(res?.data ?? []);
          this.loadingMeetings.set(false);
        },
        error: () => {
          this.meetings.set([]);
          this.loadingMeetings.set(false);
          this.resultSuccess.set(false);
          this.resultMessage.set('rbio.meeting.error.unavailable');
        }
      });

    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${n}/meetings/participants`)
      .subscribe({
        next: res => this.participants.set(res?.data ?? []),
        error: () => this.participants.set([])
      });
  }

  // ══════════════════════════ Forms ══════════════════════════

  openMeetingForm(status: 'SCHEDULED' | 'RESCHEDULED' | 'COMPLETED'): void {
    this.meetingStatus.set(status);
    this.showMeetingForm.set(true);
    this.showOutcomeForm.set(false);
    this.resultMessage.set('');

    // A reschedule pre-fills from the operative meeting so the officer edits rather than retypes, but the
    // NEW values are what is sent — the previous row is never mutated.
    const current = this.currentMeeting();
    if (status === 'RESCHEDULED' && current) {
      this.meetingDate.set(current.meetingDate ?? '');
      this.meetingTime.set(current.meetingTime ?? '');
      this.meetingParticipants.set((current.participants as any) ?? '');
      this.meetingVenue.set(current.meetingVenue ?? '');
      this.meetingMode.set(current.meetingMode ?? 'IN_PERSON');
      this.rescheduleReason.set('');
    }
  }

  openOutcomeForm(): void {
    this.showOutcomeForm.set(true);
    this.showMeetingForm.set(false);
    this.resultMessage.set('');
  }

  cancelForm(): void {
    this.showMeetingForm.set(false);
    this.showOutcomeForm.set(false);
  }

  private resetMeetingForm(): void {
    this.meetingDate.set('');
    this.meetingTime.set('');
    this.meetingVenue.set('');
    this.meetingParticipants.set('');
    this.rescheduleReason.set('');
    this.entityAccepted.set('');
    this.minutesOfMeeting.set('');
    this.meetingNotes.set('');
  }

  // ══════════════════════════ Saving ══════════════════════════

  /**
   * Saves the meeting event.
   *
   * <p>Sends the CANONICAL param names the server reads. The old body sent `hearingDate`/`hearingTime`/
   * `venue`/`parties`, none of which had a server reader — so a successful-looking save recorded nothing.
   */
  submitMeeting(): void {
    if (!this.canSaveMeeting()) return;
    this.processing.set(true);

    const status = this.meetingStatus();
    const action = status === 'SCHEDULED' ? 'SCHEDULE_MEETING'
      : status === 'RESCHEDULED' ? 'RESCHEDULE_MEETING'
      : 'COMPLETE_MEETING';

    const body: Record<string, unknown> = {
      action,
      actor: this.auth.currentUser()?.username || '',
      remarks: this.meetingNotes() || `Meeting ${status.toLowerCase()}`
    };

    if (status === 'COMPLETED') {
      body['entityAccepted'] = this.entityAccepted();
      body['minutesOfMeeting'] = this.minutesOfMeeting();
    } else {
      body['meetingDate'] = this.meetingDate();
      body['meetingTime'] = this.meetingTime();
      body['participants'] = this.meetingParticipants();
      body['meetingVenue'] = this.meetingVenue();
      body['meetingMode'] = this.meetingMode();
      if (status === 'RESCHEDULED') {
        body['rescheduleReason'] = this.rescheduleReason();
      }
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${this.complaintNumber}`, body
    ).subscribe({
      next: res => {
        // RBIO refusals answer HTTP 200 with success:false. The old code's next branch ignored that, so a
        // refused action printed "scheduled successfully".
        if (res && res.success === false) {
          this.resultSuccess.set(false);
          this.resultMessage.set(res.message || 'rbio.meeting.error.unavailable');
          this.processing.set(false);
          return;
        }
        this.resultSuccess.set(true);
        this.resultMessage.set(
          status === 'SCHEDULED' ? 'rbio.meeting.saved.scheduled'
            : status === 'RESCHEDULED' ? 'rbio.meeting.saved.rescheduled'
            : 'rbio.meeting.saved.completed');
        this.processing.set(false);
        this.showMeetingForm.set(false);
        this.resetMeetingForm();
        this.loadMeetings();
      },
      error: err => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'rbio.meeting.error.unavailable');
        this.processing.set(false);
      }
    });
  }

  submitOutcome(): void {
    if (this.outcomeType === 'SETTLED' && !this.summaryNotes.trim()) return;
    if (this.outcomeType === 'FAILED' && !this.failureReason.trim()) return;
    if (this.outcomeType === 'SETTLED' && this.amountExceedsCap) return;

    this.processing.set(true);
    const action = this.outcomeType === 'SETTLED' ? 'CONCILIATION_SUCCESS' : 'CONCILIATION_FAILED';
    const body: any = {
      action,
      remarks: this.outcomeType === 'SETTLED' ? this.summaryNotes : this.failureReason,
      actor: this.auth.currentUser()?.username || ''
    };

    if (this.outcomeType === 'SETTLED' && this.compensationAmount) {
      // BOTH names are sent: the server accepts awardAmount and compensationAmount, and sending only the
      // latter is what once recorded a citizen's statutory award as 0.00.
      body.compensationAmount = this.amountNumeric;
      body.awardAmount = this.amountNumeric;
      body.compensationType = this.compensationType;
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${this.complaintNumber}`, body
    ).subscribe({
      next: res => {
        if (res && res.success === false) {
          this.resultSuccess.set(false);
          this.resultMessage.set(res.message || 'Failed to record outcome.');
          this.processing.set(false);
          return;
        }
        this.resultSuccess.set(true);
        this.resultMessage.set(
          this.outcomeType === 'SETTLED'
            ? 'Conciliation settled successfully. Compensation awarded.'
            : 'Conciliation marked as failed. Complaint will proceed to adjudication.');
        this.processing.set(false);
        this.showOutcomeForm.set(false);
      },
      error: err => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to record outcome.');
        this.processing.set(false);
      }
    });
  }

  // ══════════════════════════ Participants (UST498) ══════════════════════════

  addParticipant(): void {
    const name = this.newParticipantName().trim();
    if (!name || !this.canAddParticipant()) return;
    this.addingParticipant.set(true);

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/meetings/participants`,
      { participantName: name, participantType: 'ENTITY', participantEmail: this.newParticipantEmail() }
    ).subscribe({
      next: () => {
        this.newParticipantName.set('');
        this.newParticipantEmail.set('');
        this.addingParticipant.set(false);
        this.loadMeetings();
      },
      error: err => {
        this.addingParticipant.set(false);
        this.resultSuccess.set(false);
        // A 409 here is the six-cap. Surfaced rather than swallowed, so the officer learns why.
        this.resultMessage.set(err.error?.message || 'rbio.meeting.participant.cap_reached');
      }
    });
  }

  toggleParticipantConfirmed(p: MeetingParticipant): void {
    this.http.put<any>(
      `${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}`
      + `/meetings/participants/${p.id}/confirm`,
      { confirmed: !p.participantConfirmed }
    ).subscribe({
      next: () => this.loadMeetings(),
      error: err => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'rbio.meeting.error.unavailable');
      }
    });
  }

  // ══════════════════════════ The MOM letter (UST500, 501) ══════════════════════════

  /** The generated MOM letter. Server-rendered from the editable template, opened for print/download. */
  momLetterUrl(): string {
    return `${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}`
      + `/meetings/mom-letter?schemeVersion=RBIOS_2021`;
  }

  /**
   * Uploads the SCANNED SIGNED MOM letter (UST501).
   *
   * <p>Uses the real attachment endpoint with `documentType=MEETING_MINUTES`, so the file appears under the
   * Attachments tab. The previous implementation POSTed to a route that does not exist AND reported success
   * from its error handler — so a signed MOM was silently discarded behind a green tick.
   */
  onMinutesUpload(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    const complaintId = this.complaint?.id ?? this.complaint?.complaintId ?? this.complaintNumber;
    this.uploadingMinutes.set(true);
    this.minutesUploaded.set(false);

    this.attachments.uploadAttachment(
      this.complaintNumber, complaintId, input.files[0], 'MEETING_MINUTES'
    ).subscribe({
      next: () => {
        this.uploadingMinutes.set(false);
        this.minutesUploaded.set(true);
        this.resultSuccess.set(true);
        this.resultMessage.set('rbio.meeting.saved.uploaded');
        input.value = '';
      },
      error: err => {
        // Reports FAILURE. The old handler set minutesUploaded(true) here.
        this.uploadingMinutes.set(false);
        this.minutesUploaded.set(false);
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'rbio.meeting.error.upload_failed');
        input.value = '';
      }
    });
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN',
      { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }

  /** Label key for a meeting event type, so the template never interpolates a raw code. */
  eventTypeKey(eventType: string): string {
    switch (eventType) {
      case 'SCHEDULED': return 'rbio.meeting.status.scheduled';
      case 'RESCHEDULED': return 'rbio.meeting.status.rescheduled';
      case 'COMPLETED': return 'rbio.meeting.status.completed';
      default: return 'rbio.meeting.status.scheduled';
    }
  }

  participantsKey(participants: string | null): string {
    switch (participants) {
      case 'ENTITY': return 'rbio.meeting.participants.entity';
      case 'COMPLAINANT': return 'rbio.meeting.participants.complainant';
      case 'BOTH': return 'rbio.meeting.participants.both';
      default: return 'rbio.meeting.participants';
    }
  }
}
