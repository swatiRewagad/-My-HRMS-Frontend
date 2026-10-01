import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { QueryThreadComponent } from '../../shared/query-thread/query-thread.component';
import { InternalNotesComponent } from '../../shared/internal-notes/internal-notes.component';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { ComplaintSummaryComponent } from '../../shared/complaint-summary/complaint-summary.component';
import { ComplaintSummaryItem } from '../../shared/complaint-summary/complaint-summary.types';
import { ContextRailComponent } from '../../shared/context-rail/context-rail.component';
import { ContextRailPanel } from '../../shared/context-rail/context-rail.types';
import { UploadLimitsService } from '../../../services/upload-limits.service';

interface ComplaintDetail {
  complaintNumber: string;
  subject: string;
  description: string;
  complainantName: string;
  status: string;
  entityCode?: string;

  /**
   * The server's own answer to "may this entity still respond", computed from the response tracker
   * (RePortalController.getComplaintDetail → rePortalService.isWithinResponseWindow). It is the
   * AUTHORITY on the window, and the only window field this endpoint actually emits.
   */
  withinResponseWindow?: boolean;

  // Served by the detail endpoint, so these render real values.
  priority?: string;
  filingType?: string;
  reliefSought?: string | null;
  createdAt?: string;

  /**
   * NOT emitted by GET /re-portal/complaints/{n} today. Kept optional so the fields populate the day
   * the server adds them, and so nothing here renders a date it does not have. `responseDeadline` in
   * particular used to be read as the window gate — see `isResponseWindowOpen`.
   */
  category?: string;
  filedDate?: string;
  forwardedDate?: string;
  responseDeadline?: string;
  entityName?: string;

  // UST846: derived from the entity's own actions. Displayed here but never settable — the server
  // refuses a direct write, so this is presentation only.
  reActivityStatus?: string;
  reActivityStatusKey?: string;
  reActivityChangedAt?: string | null;
}

/**
 * A timeline row.
 *
 * <p>Both namings are accepted because the endpoint emits `performedBy` / `performedAt` while this
 * component was written against `actor` / `timestamp`. The mapper in loadComplaint() normalises them;
 * reading only the original pair rendered a timeline with no author and no date.
 */
interface TimelineEntry {
  action: string;
  actor: string;
  timestamp: string;
  remarks: string;
}

/** The right rail's panels. One for now — see `railPanels` for why there is no attachments panel. */
type RailKey = 'history';

/**
 * The entity's view of one complaint, on the canonical three-region layout.
 *
 * <h2>Why this screen moved</h2>
 * It was a single 900px column with six stacked blocks, so an entity reading the complaint had to
 * scroll the response form out of sight, and reading the activity timeline scrolled both away. Every
 * RBI-side detail screen (RBIO, CEPC, AA) is now facts-left / work-centre / consult-rail-right; an RE
 * nodal officer met a different shape than the officer they correspond with. The regions here are the
 * same three, using the same shared components.
 *
 * <h2>What an RE is, and what it therefore does NOT get</h2>
 * An RE is a RESPONDENT, not a driver of the complaint's state machine. There is no
 * app-workflow-action-bar on this screen and that is deliberate: the entity's only mutations are
 * respond, save-draft and raise-query. PUT /re-portal/complaints/{n}/activity-status exists purely to
 * return 403 — "RE Activity Status is derived from your actions and cannot be set directly" — so the
 * activity ladder is an OUTCOME of those three actions, never a transition the entity selects. An
 * action bar here would offer state changes the server has no handler for.
 *
 * <h2>And no app-comment-thread</h2>
 * ComplaintCommentService.isStaff() is a staff allowlist; RE_PNO and RE_NODAL_OFFICER are refused 403.
 * The entity's correspondence surfaces are app-query-thread (with the RBI side) and
 * app-internal-notes (entity-private). Both stay in the CENTRE region rather than the rail, because
 * the entity composes in them — the rail holds material one reads.
 */
@Component({
  selector: 'app-re-complaint-detail',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    QueryThreadComponent,
    InternalNotesComponent,
    StatusBadgeComponent,
    ComplaintSummaryComponent,
    ContextRailComponent
  ],
  templateUrl: './re-complaint-detail.component.html',
  styleUrl: './re-complaint-detail.component.scss'
})
export class ReComplaintDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  // Public: the template renders the configured limit in its upload hint.
  uploadLimits = inject(UploadLimitsService);
  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  loading = signal(true);
  complaint = signal<ComplaintDetail | null>(null);
  timeline = signal<TimelineEntry[]>([]);

  // Response form
  responseText = signal('');
  selectedFiles = signal<File[]>([]);
  submittingResponse = signal(false);
  responseSuccess = signal('');
  responseError = signal('');

  /**
   * Passed to the query and notes panels so their requests carry the entity scope. The server
   * prefers the JWT's entity_code claim over this, so it is a convenience for the dev-header mode
   * rather than the access control itself.
   */
  entityCode = computed(() => this.complaint()?.entityCode ?? null);

  // ═══ Summary strip and right rail ═══════════════════════════════════════════════════════════════

  /**
   * The identifying band, on the shared app-complaint-summary.
   *
   * <p>Captions are keys from the shared `ui.col.*` vocabulary, already seeded in ten locales, so the
   * entity reads the same words an RBIO officer does. Items whose source the detail endpoint does not
   * emit are OMITTED rather than rendered blank — a caption over an empty value reads as data loss.
   */
  readonly summaryItems = computed<readonly ComplaintSummaryItem[]>(() => {
    const c = this.complaint();
    if (!c) return [];
    const items: ComplaintSummaryItem[] = [
      { labelKey: 'ui.col.complaint_number', value: c.complaintNumber, icon: 'pi-file' },
      // Masked, exactly as the left panel masks it. The strip must not be the one place that leaks a
      // complainant's full name to a respondent entity.
      { labelKey: 'ui.col.complainant_name', value: this.maskName(c.complainantName), icon: 'pi-user', tone: 'owner' },
      { labelKey: 'ui.col.status', value: c.status, kind: 'status', icon: 'pi-flag' }
    ];
    if (c.priority) {
      items.push({ labelKey: 'ui.col.priority', value: c.priority, icon: 'pi-exclamation-circle' });
    }
    if (c.createdAt) {
      items.push({ labelKey: 'ui.col.created_at', value: this.shortDate(c.createdAt), icon: 'pi-calendar' });
    }
    // The window, as an SLA chip: it is the one fact that decides whether this entity can still act,
    // which is what the 'sla' kind exists to make unmissable.
    //
    // A shut window is only a DANGER when it shut on the entity. Having answered also closes it, and
    // that is the entity having done what was asked of it — painting it red would read as a breach.
    items.push({
      labelKey: 'ui.col.deadline',
      value: this.windowLabel(),
      kind: 'sla',
      severity: this.isResponseWindowOpen() || this.alreadyAnswered() ? 'ok' : 'danger',
      icon: 'pi-clock'
    });
    return items;
  });

  railOpen = signal<RailKey | null>(null);

  /**
   * ONE panel, not three.
   *
   * <p>The reference screen's rail carries history, attachments and email. Only history has a source
   * here: the timeline comes from GET /re-portal/complaints/{n}/timeline, which is served.
   *
   * <p>No ATTACHMENTS panel. The only served attachment list is GET /api/files/complaint/{id}, keyed
   * by the numeric complaint id — and the RE detail payload emits `complaintNumber` only, no id. A
   * panel built on a key this screen does not hold would be a phantom: it would render an empty list
   * on every complaint and tell the entity there are no documents when there may be several.
   *
   * <p>No EMAIL panel: RE correspondence is the query thread, which is a working surface (the entity
   * composes in it) and therefore belongs in the centre region, not behind a consult rail.
   *
   * <p>`label` is already-resolved text, not a key — that is the rail's contract, and the
   * homogenisation spec asserts the exact English heading.
   */
  readonly railPanels: readonly ContextRailPanel<RailKey>[] = [
    { key: 'history', label: 'Activity Timeline', icon: 'pi-history' }
  ];

  // ═══ The response window ════════════════════════════════════════════════════════════════════════

  /**
   * Statuses that mean this entity has already filed its statutory response.
   *
   * <p>This is the SERVER'S OWN definition, mirrored rather than invented:
   * ReResponseDeadlineService.hasResponded() is exactly these three, compared with
   * equalsIgnoreCase, and it is what suppresses the overdue flag on the dashboard. Keeping a
   * different list here would make the detail screen disagree with the row the entity clicked
   * through from. `rejected` is deliberately NOT in it — a rejected complaint was never answered.
   *
   * <p>Compared case-insensitively for the same reason the server does: the detail endpoint emits
   * the lowercase workflow code (`re_responded`) while other payloads carry other casings.
   */
  private static readonly ANSWERED_STATUSES = new Set([
    're_responded',
    'resolved',
    'closed'
  ]);

  /**
   * Whether this entity has already answered, i.e. whether a SECOND response would be a duplicate.
   *
   * <p>This is a SEPARATE question from the deadline, and the server treats it separately too:
   * RePortalService.submitResponse refuses with 409 "Complaint already responded to on: …" when
   * `tracker.getRespondedAt() != null`, and only then goes on to look at the window. `respondedAt`
   * itself is not in the detail payload (RePortalController exposes it on the respond RESPONSE only),
   * so `status` is the signal available here — and RePortalService:157 sets it to `re_responded` on
   * a successful response, which is what makes this readable at all.
   */
  alreadyAnswered = computed(() => {
    const status = this.complaint()?.status;
    return !!status && ReComplaintDetailComponent.ANSWERED_STATUSES.has(status.toLowerCase());
  });

  /**
   * Whether this entity may still submit its response.
   *
   * <p>TWO server-side rules gate a response, and this must reflect BOTH:
   * <ol>
   *   <li>the response has not already been filed — a 409 refusal, see `alreadyAnswered`;</li>
   *   <li>the window has not closed — `withinResponseWindow`.</li>
   * </ol>
   * Reading only the second one left the form open on a complaint that had already been answered,
   * offering a submit control whose only possible outcome was a 409. There is one statutory response
   * per forwarded complaint, so inviting a second is inviting the entity to try to overwrite its own
   * answer of record.
   *
   * <p>THE SECOND RULE IS ALSO WHERE THE BUG WAS THAT MADE THIS SCREEN UNUSABLE. The gate used to be
   * derived from `responseDeadline`, a field GET /re-portal/complaints/{n} has never emitted. With it
   * undefined `deadlineCountdown()` reported `expired: true` for every complaint, so the textarea,
   * the file input and the submit button were all latched off — no regulated entity could file a
   * response through the portal at all, however many days were left. The server was answering the
   * question correctly the whole time, in `withinResponseWindow`, and nothing read it.
   *
   * <p>So the server's own answer is now the authority. The date-arithmetic fallback below is kept
   * ONLY for the case where a deadline is present (the dashboard's row tint has the same shape), and
   * the final fallback is OPEN rather than closed: a missing window field must not silently revoke a
   * statutory right to respond. The server refuses a late response regardless — that refusal is the
   * real enforcement, and it is covered by respond.spec.ts.
   */
  isResponseWindowOpen = computed(() => {
    const c = this.complaint();
    if (!c) return false;
    if (this.alreadyAnswered()) return false;
    if (typeof c.withinResponseWindow === 'boolean') {
      return c.withinResponseWindow;
    }
    if (c.responseDeadline) {
      return new Date(c.responseDeadline).getTime() > Date.now();
    }
    return true;
  });

  /**
   * Days and hours left, when a deadline is known.
   *
   * <p>`expired` is NO LONGER the window gate — it is only whether a countdown can be rendered. With
   * no deadline emitted there is nothing to count down, which is a display gap, not a closed window.
   */
  deadlineCountdown = computed(() => {
    const c = this.complaint();
    if (!c?.responseDeadline) return { days: 0, hours: 0, known: false };
    const diff = new Date(c.responseDeadline).getTime() - Date.now();
    if (diff <= 0) return { days: 0, hours: 0, known: true };
    return {
      days: Math.floor(diff / (1000 * 60 * 60 * 24)),
      hours: Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60)),
      known: true
    };
  });

  /**
   * The strip's window chip. States the honest thing in each case: "Responded" when the entity has
   * already answered (the window being shut is a CONSEQUENCE of that, not an expiry), a real
   * countdown when a deadline is known, "Open"/"Closed" when only the server's boolean is.
   */
  private windowLabel(): string {
    if (this.alreadyAnswered()) return 'Responded';
    const countdown = this.deadlineCountdown();
    if (countdown.known && this.isResponseWindowOpen()) {
      return `${countdown.days}d ${countdown.hours}h`;
    }
    return this.isResponseWindowOpen() ? 'Open' : 'Closed';
  }

  private shortDate(value: string): string {
    const d = new Date(value);
    return isNaN(d.getTime())
      ? value
      : d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
  }

  ngOnInit() {
    const complaintNumber = this.route.snapshot.paramMap.get('complaintNumber');
    if (complaintNumber) {
      this.loadComplaint(complaintNumber);
    }
  }

  loadComplaint(complaintNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints/${complaintNumber}`).subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.complaint.set(data.complaint || data);
        // The detail payload carries no timeline, so `data.timeline` was always undefined and the rail
        // would always read "No activity recorded yet". The timeline is its own endpoint.
        this.timeline.set(this.normaliseTimeline(data.timeline));
        this.loading.set(false);
        this.loadTimeline(complaintNumber);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  /**
   * The activity timeline, from GET /re-portal/complaints/{n}/timeline.
   *
   * <p>Fetched separately because the detail endpoint does not include it. Its failure is swallowed
   * deliberately: the timeline is consult material behind the rail, and a complaint whose history
   * cannot be read is still a complaint the entity must be able to respond to.
   */
  private loadTimeline(complaintNumber: string) {
    this.http
      .get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints/${complaintNumber}/timeline`)
      .subscribe({
        next: (res) => this.timeline.set(this.normaliseTimeline(res?.data ?? res)),
        error: () => this.timeline.set([])
      });
  }

  /**
   * Maps the server's `performedBy` / `performedAt` onto the `actor` / `timestamp` this component
   * renders. Without it every row showed an author-less, date-less entry — the fields simply did not
   * line up, and `track entry.timestamp` collapsed all of them onto one undefined key.
   */
  private normaliseTimeline(raw: unknown): TimelineEntry[] {
    if (!Array.isArray(raw)) return [];
    return raw.map((e: any) => ({
      action: e?.action ?? '',
      actor: e?.actor ?? e?.performedBy ?? '',
      timestamp: e?.timestamp ?? e?.performedAt ?? '',
      remarks: e?.remarks ?? ''
    }));
  }

  maskName(name: string): string {
    if (!name || name.length <= 4) return name;
    const first = name.substring(0, 2);
    const last = name.substring(name.length - 2);
    return `${first}${'*'.repeat(name.length - 4)}${last}`;
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      const files = Array.from(input.files);
      this.selectedFiles.set(files);
    }
  }

  removeFile(index: number) {
    const files = [...this.selectedFiles()];
    files.splice(index, 1);
    this.selectedFiles.set(files);
  }

  submitResponse() {
    if (!this.responseText().trim()) {
      this.responseError.set('Please enter a response.');
      return;
    }

    this.submittingResponse.set(true);
    this.responseError.set('');
    this.responseSuccess.set('');

    const formData = new FormData();
    formData.append('responseText', this.responseText());
    this.selectedFiles().forEach(file => {
      formData.append('documents', file);
    });

    const complaintNumber = this.complaint()?.complaintNumber;
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints/${complaintNumber}/respond`, formData).subscribe({
      next: (res) => {
        this.submittingResponse.set(false);
        this.responseSuccess.set('Response submitted successfully.');
        this.responseText.set('');
        this.selectedFiles.set([]);
        // Reload complaint to refresh timeline
        if (complaintNumber) this.loadComplaint(complaintNumber);
      },
      error: (err) => {
        this.submittingResponse.set(false);
        this.responseError.set(err.error?.message || 'Failed to submit response.');
      }
    });
  }

  goBack() {
    this.router.navigate(['/re-portal/dashboard']);
  }
}
