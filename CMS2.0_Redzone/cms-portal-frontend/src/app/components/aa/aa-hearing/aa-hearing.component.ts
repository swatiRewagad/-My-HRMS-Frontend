import { Component, Input, Output, EventEmitter, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/** One hearing event, as returned by the hearings endpoint. */
interface HearingRecord {
  id: number;
  sequenceNo: number;
  eventType: string;
  date: string;
  venue: string | null;
  mode: string | null;
  outcome: string | null;
  outcomeRemarks: string | null;
  reason: string | null;
  performedBy: string | null;
  performedByRole: string | null;
  performedAt: string;
  superseded: boolean;
}

/**
 * Schedules and reschedules hearings, and shows the hearing history.
 *
 * Talks to the dedicated hearings endpoint rather than the generic /action route. Three things follow
 * from that, none of them cosmetic:
 *   - History is real. Rescheduling APPENDS a record instead of overwriting Appeal.hearingDate, so a
 *     vacated sitting remains on the record — which for a statutory hearing is the point.
 *   - The server checks for an officer double-booking and answers 409, so a clash is refused rather
 *     than silently accepted.
 *   - Notices are RECORDED, not sent. There is no email or SMS gateway in this deployment, and the
 *     server says so explicitly (noticeStatus PENDING, gatewayAvailable false). The previous copy told
 *     the user "Notices will be sent to selected parties" while the server ignored the field entirely.
 */
@Component({
  selector: 'app-aa-hearing',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './aa-hearing.component.html',
  styleUrl: './aa-hearing.component.scss'
})
export class AaHearingComponent implements OnInit {
  @Input() appeal: any;
  @Output() hearingScheduled = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  private http = inject(HttpClient);

  scheduling = signal(false);
  errorKey = signal('');
  successKey = signal('');
  showPreview = signal(false);

  history = signal<HearingRecord[]>([]);
  historyLoading = signal(true);
  /** The sitting currently in force, or null when none is fixed. */
  operative = signal<HearingRecord | null>(null);

  hearingDate = '';
  hearingTime = '';
  hearingVenue = '';
  /** Sent separately from the venue: the server normalises mode, and a venue string cannot express it. */
  hearingMode = 'IN_PERSON';
  /** A reason is mandatory when a sitting already exists — vacating one without a reason is not auditable. */
  reason = '';
  partiesToNotify: string[] = ['appellant', 'respondent'];

  /** Venue labels are keys; 'Virtual' is deliberately NOT here — that is the MODE, not a venue. */
  venueOptions = [
    { value: 'RBI Head Office, Mumbai - Conference Room A', labelKey: 'aa.hearing.venue_ho_mumbai_a' },
    { value: 'RBI Regional Office - Hearing Room 1', labelKey: 'aa.hearing.venue_ro_1' },
    { value: 'RBI Regional Office - Hearing Room 2', labelKey: 'aa.hearing.venue_ro_2' },
    { value: 'Other', labelKey: 'aa.hearing.venue_other' },
  ];

  modeOptions = [
    { value: 'IN_PERSON', labelKey: 'aa.hearing.mode_in_person' },
    { value: 'VIDEO', labelKey: 'aa.hearing.mode_video' },
    { value: 'HYBRID', labelKey: 'aa.hearing.mode_hybrid' },
  ];

  ngOnInit(): void {
    this.loadHistory();
  }

  /** True when a sitting is already fixed, so this scheduling is a RESCHEDULE and needs a reason. */
  get isReschedule(): boolean {
    return this.operative() !== null;
  }

  private loadHistory(): void {
    const appealNumber = this.appeal?.appealNumber;
    if (!appealNumber) {
      this.historyLoading.set(false);
      return;
    }
    this.historyLoading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/hearings`)
      .subscribe({
        next: (res) => {
          this.history.set(res?.hearingHistory ?? []);
          this.operative.set(res?.operative ?? null);
          this.historyLoading.set(false);
        },
        error: () => {
          // No mock fallback: an empty history that is actually a failed fetch would read as "no
          // hearings ever fixed", which is a materially different and misleading statement.
          this.history.set([]);
          this.errorKey.set('aa.hearing.error_history_unavailable');
          this.historyLoading.set(false);
        }
      });
  }

  toggleParty(party: string) {
    const index = this.partiesToNotify.indexOf(party);
    if (index > -1) {
      this.partiesToNotify.splice(index, 1);
    } else {
      this.partiesToNotify.push(party);
    }
  }

  isPartySelected(party: string): boolean {
    return this.partiesToNotify.includes(party);
  }

  previewNotice() {
    if (!this.validate()) {
      return;
    }
    this.errorKey.set('');
    this.showPreview.set(true);
  }

  private validate(): boolean {
    if (!this.hearingDate) {
      this.errorKey.set('aa.hearing.error_date_required');
      return false;
    }
    if (!this.hearingTime) {
      this.errorKey.set('aa.hearing.error_time_required');
      return false;
    }
    if (!this.hearingVenue) {
      this.errorKey.set('aa.hearing.error_venue_required');
      return false;
    }
    if (this.isReschedule && !this.reason.trim()) {
      this.errorKey.set('aa.hearing.error_reason_required');
      return false;
    }
    this.errorKey.set('');
    return true;
  }

  scheduleHearing() {
    if (!this.validate()) {
      return;
    }
    this.scheduling.set(true);
    const appealNumber = this.appeal?.appealNumber;

    const body: Record<string, unknown> = {
      // A full ISO datetime. The endpoint tolerates date-only and defaults the time, but an unstated
      // default on a hearing time is exactly the sort of thing a party turns up wrong for.
      hearingDate: `${this.hearingDate}T${this.hearingTime}:00`,
      hearingVenue: this.hearingVenue,
      hearingMode: this.hearingMode,
      partiesToNotify: this.partiesToNotify,
    };
    if (this.isReschedule) {
      body['reason'] = this.reason.trim();
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/hearings`,
      body
    ).subscribe({
      next: (res) => {
        this.scheduling.set(false);
        if (res?.success === false) {
          this.errorKey.set(res.messageKey || 'aa.hearing.error_failed');
          this.showPreview.set(false);
          return;
        }
        // The server's own key, which says notices were RECORDED. Never substituted for a claim that
        // they were delivered.
        this.successKey.set(res?.messageKey || 'aa.hearing.scheduled_notices_recorded');
        this.loadHistory();
        setTimeout(() => this.hearingScheduled.emit(), 1200);
      },
      error: (err) => {
        this.scheduling.set(false);
        this.showPreview.set(false);
        // 409 is a double-booking of the presiding officer, which is a real conflict the officer must
        // resolve — not a validation slip.
        if (err?.status === 409) {
          this.errorKey.set(err.error?.messageKey || 'aa.hearing.error_conflict');
          return;
        }
        this.errorKey.set(err.error?.messageKey || err.error?.message || 'aa.hearing.error_failed');
      }
    });
  }

  cancel() {
    this.cancelled.emit();
  }
}
