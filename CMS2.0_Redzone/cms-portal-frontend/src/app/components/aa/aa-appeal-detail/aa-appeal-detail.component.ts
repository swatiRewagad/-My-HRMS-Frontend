import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { AaHearingComponent } from '../aa-hearing/aa-hearing.component';
import { AaOrderComponent } from '../aa-order/aa-order.component';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

type AaRole = 'AA_DO' | 'AA_REVIEWER' | 'AA_SECRETARIAT' | 'AA_ADMIN';

interface ActionDef {
  id: string;
  /** Translation keys, not text: every user-facing string in this module resolves through the API. */
  labelKey: string;
  descriptionKey: string;
  style: string;
  requiresRemarks: boolean;
  requiresTarget?: boolean;
  targetType?: 'user' | 'date';
}

/** Server-computed stage SLA, as returned on the appeal detail. */
interface AppealSla {
  stage: string;
  stageAllowedDays: number;
  tracked: boolean;
  deadline: string | null;
  /** Negative when overdue. */
  daysRemaining: number | null;
  breached: boolean;
  statusKey: string;
}

interface TimelineEntry {
  action: string;
  fromStatus: string;
  toStatus: string;
  timestamp: string;
  remarks: string;
  performedBy?: string;
}

@Component({
  selector: 'app-aa-appeal-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, AaHearingComponent, AaOrderComponent, StatusBadgeComponent, TranslatePipe],
  templateUrl: './aa-appeal-detail.component.html',
  styleUrl: './aa-appeal-detail.component.scss'
})
export class AaAppealDetailComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  appeal = signal<any>(null);
  loading = signal(true);
  processing = signal(false);
  userRole = signal<AaRole>('AA_DO');

  timeline = signal<TimelineEntry[]>([]);
  timelineLoading = signal(true);

  selectedAction = signal<ActionDef | null>(null);
  remarks = '';
  targetUser = '';
  hearingDate = '';
  hearingVenue = '';

  actionResult = signal('');
  actionSuccess = signal(false);

  // Sub-component panels
  showHearingPanel = signal(false);
  showOrderPanel = signal(false);

  // Officers for reassignment
  aaOfficers = signal<{ id: string; name: string }[]>([]);

  /**
   * Translation keys for the acting role, reusing the aa.role_* keys S1 already seeded in all ten
   * locales rather than the English literals that were here.
   */
  private static readonly ROLE_LABEL_KEYS: Record<AaRole, string> = {
    'AA_DO': 'aa.role_do',
    'AA_REVIEWER': 'aa.role_reviewer',
    'AA_SECRETARIAT': 'aa.role_secretariat',
    'AA_ADMIN': 'aa.role_admin'
  };

  roleLabelKey = computed(() => AaAppealDetailComponent.ROLE_LABEL_KEYS[this.userRole()]);

  /**
   * Presentation metadata ONLY. Whether an action is offered is decided by the SERVER; this map merely
   * says how to render one the server already offered.
   *
   * This replaced a client-side derivation that gated on six statuses the backend cannot produce
   * (accepted, assigned, hearing_completed, order_reserved, documents_requested, dismissed). The real
   * status after ACCEPT is under_review, so an AA_REVIEWER was shown ZERO actions and the workflow
   * dead-ended at step two. Any client-side action list can drift from what the server will accept;
   * this one had, silently, in production.
   */
  private static readonly ACTION_PRESENTATION: Record<string, Omit<ActionDef, 'id'>> = {
    ACCEPT: { labelKey: 'aa.action.accept', descriptionKey: 'aa.action.accept_desc', style: 'primary', requiresRemarks: false },
    REJECT: { labelKey: 'aa.action.reject', descriptionKey: 'aa.action.reject_desc', style: 'close', requiresRemarks: true },
    ASSIGN_TO_BENCH: { labelKey: 'aa.action.assign_to_bench', descriptionKey: 'aa.action.assign_to_bench_desc', style: 'forward', requiresRemarks: true },
    REQUEST_DOCUMENTS: { labelKey: 'aa.action.request_documents', descriptionKey: 'aa.action.request_documents_desc', style: 'info', requiresRemarks: true },
    PREPARE_BRIEF: { labelKey: 'aa.action.prepare_brief', descriptionKey: 'aa.action.prepare_brief_desc', style: 'info', requiresRemarks: true },
    ESCALATE_TO_TIER2: { labelKey: 'aa.action.escalate_to_tier2', descriptionKey: 'aa.action.escalate_to_tier2_desc', style: 'escalate', requiresRemarks: true },
    SCHEDULE_HEARING: { labelKey: 'aa.action.schedule_hearing', descriptionKey: 'aa.action.schedule_hearing_desc', style: 'primary', requiresRemarks: false },
    FORWARD_TO_AUTHORITY: { labelKey: 'aa.action.forward_to_authority', descriptionKey: 'aa.action.forward_to_authority_desc', style: 'forward', requiresRemarks: true },
    SEND_BACK_REGISTRAR: { labelKey: 'aa.action.send_back_registrar', descriptionKey: 'aa.action.send_back_registrar_desc', style: 'return', requiresRemarks: true },
    PASS_ORDER: { labelKey: 'aa.action.pass_order', descriptionKey: 'aa.action.pass_order_desc', style: 'primary', requiresRemarks: false },
    REMAND_TO_OMBUDSMAN: { labelKey: 'aa.action.remand_to_ombudsman', descriptionKey: 'aa.action.remand_to_ombudsman_desc', style: 'escalate', requiresRemarks: true },
    DISMISS: { labelKey: 'aa.action.dismiss', descriptionKey: 'aa.action.dismiss_desc', style: 'close', requiresRemarks: true },
    REASSIGN: { labelKey: 'aa.action.reassign', descriptionKey: 'aa.action.reassign_desc', style: 'info', requiresRemarks: true, requiresTarget: true, targetType: 'user' },
    CLOSE: { labelKey: 'aa.action.close', descriptionKey: 'aa.action.close_desc', style: 'close', requiresRemarks: true },
    REOPEN: { labelKey: 'aa.action.reopen', descriptionKey: 'aa.action.reopen_desc', style: 'escalate', requiresRemarks: true },
  };

  /**
   * Exactly what the server says this caller may do, in the server's order.
   *
   * An action the server offers but this map does not know how to render is still shown, labelled with
   * its raw id — failing visible beats hiding a legitimate action because the frontend is out of date.
   */
  availableActions = computed<ActionDef[]>(() =>
    ((this.appeal()?.availableActions as string[] | undefined) ?? []).map(id => ({
      id,
      ...(AaAppealDetailComponent.ACTION_PRESENTATION[id] ?? {
        labelKey: id,
        descriptionKey: '',
        style: 'info',
        requiresRemarks: true,
      }),
    })));

  /** Server-computed SLA. Never recalculated here: working-day maths belongs with the holiday master. */
  sla = computed<AppealSla | null>(() => this.appeal()?.sla ?? null);

  slaOverdue = computed(() => this.sla()?.breached === true);

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (roles.includes('AA_ADMIN')) this.userRole.set('AA_ADMIN');
    else if (roles.includes('AA_SECRETARIAT')) this.userRole.set('AA_SECRETARIAT');
    else if (roles.includes('AA_REVIEWER')) this.userRole.set('AA_REVIEWER');
    else this.userRole.set('AA_DO');

    const appealNumber = this.route.snapshot.params['appealNumber'];
    this.loadAppeal(appealNumber);
    this.loadTimeline(appealNumber);
    this.loadOfficers();
  }

  private loadAppeal(appealNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}`).subscribe({
      next: (res) => {
        this.appeal.set(res?.data || null);
        this.loading.set(false);
      },
      error: () => {
        this.appeal.set(null);
        this.loading.set(false);
      }
    });
  }

  private loadTimeline(appealNumber: string) {
    this.timelineLoading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/timeline`).subscribe({
      next: (res) => {
        this.timeline.set(res?.data || []);
        this.timelineLoading.set(false);
      },
      error: () => {
        this.timeline.set([]);
        this.timelineLoading.set(false);
      }
    });
  }

  private loadOfficers() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/by-role?role=AA_REVIEWER`).subscribe({
      next: (res) => {
        const users = (res || []).map((u: any) => ({ id: u.username || u.userId, name: u.displayName || `${u.firstName} ${u.lastName}` }));
        this.aaOfficers.set(users);
      },
      error: () => this.aaOfficers.set([])
    });
  }

  selectAction(action: ActionDef) {
    if (action.id === 'PASS_ORDER') {
      this.showOrderPanel.set(true);
      this.showHearingPanel.set(false);
      this.selectedAction.set(null);
      return;
    }
    if (action.id === 'SCHEDULE_HEARING') {
      this.showHearingPanel.set(true);
      this.showOrderPanel.set(false);
      this.selectedAction.set(null);
      return;
    }
    this.showHearingPanel.set(false);
    this.showOrderPanel.set(false);
    this.selectedAction.set(action);
    this.remarks = '';
    this.targetUser = '';
    this.actionResult.set('');
  }

  cancelAction() {
    this.selectedAction.set(null);
    this.showHearingPanel.set(false);
    this.showOrderPanel.set(false);
    this.remarks = '';
  }

  submitAction() {
    const action = this.selectedAction();
    if (!action) return;

    const appealNumber = this.appeal()?.appealNumber;
    if (!appealNumber) return;

    this.processing.set(true);

    const body: any = {
      action: action.id,
      remarks: this.remarks,
      actor: this.auth.currentUser()?.username || '',
      targetUser: this.targetUser,
      hearingDate: this.hearingDate,
      hearingVenue: this.hearingVenue
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/action`,
      body
    ).subscribe({
      next: (res) => {
        this.processing.set(false);

        // A REFUSED action arrives as HTTP 200 with success:false — that is this API's convention for a
        // rejected write, so Angular's error callback never fires. Treating any 200 as success is why
        // this screen reported "Order passed successfully" for orders the server had thrown away.
        if (res?.success === false) {
          this.actionSuccess.set(false);
          this.actionResult.set(res.messageKey || res.message || 'aa.action.failed');
          return;
        }

        this.actionSuccess.set(true);
        this.actionResult.set('aa.action.completed');
        this.selectedAction.set(null);
        this.loadAppeal(appealNumber);
        this.loadTimeline(appealNumber);
        this.restoreFocusToActions();
      },
      error: (err) => {
        this.actionSuccess.set(false);
        // 409 CONFLICT means the appeal moved on: the server returns the actions that ARE legal now, so
        // refreshing shows the caller the truth instead of leaving a stale card set on screen.
        if (err?.status === 409) {
          this.actionResult.set(err.error?.messageKey || 'aa.workflow.error_illegal_transition');
          this.loadAppeal(appealNumber);
        } else {
          this.actionResult.set(err.error?.messageKey || err.error?.message || 'aa.action.failed');
        }
        this.processing.set(false);
      }
    });
  }

  /**
   * Returns focus to the action list after a panel closes.
   *
   * The panels are @if-gated inline blocks, so when one is removed the focused element vanishes and focus
   * falls to the document body — a keyboard user loses their place entirely.
   */
  private restoreFocusToActions(): void {
    setTimeout(() => {
      const target = document.querySelector<HTMLElement>('[data-testid="action-list"] button');
      target?.focus();
    });
  }

  onHearingScheduled() {
    this.showHearingPanel.set(false);
    const appealNumber = this.appeal()?.appealNumber;
    if (appealNumber) {
      this.loadAppeal(appealNumber);
      this.loadTimeline(appealNumber);
    }
  }

  onOrderPassed() {
    this.showOrderPanel.set(false);
    const appealNumber = this.appeal()?.appealNumber;
    if (appealNumber) {
      this.loadAppeal(appealNumber);
      this.loadTimeline(appealNumber);
    }
  }

  /**
   * True when the server offers this caller nothing.
   *
   * Derived from the server's own answer rather than a hardcoded terminal-status list. The old list
   * included `dismissed`, which the backend never sets \u2014 DISMISS produces `closed` \u2014 so it was both
   * wrong and a second place the vocabulary could drift.
   */
  isTerminalState(): boolean {
    return this.availableActions().length === 0;
  }

  getTimelineIcon(action: string): string {
    const icons: Record<string, string> = {
      'FILED': '\u{1F4E5}',
      'ACCEPT': '\u2705',
      'REJECT': '\u274C',
      // Keyed on the real action ids from AaWorkflowTransition. These were ASSIGN_BENCH /
      // FORWARD_AUTHORITY / REMAND_OMBUDSMAN \u2014 names the server never emits \u2014 so those rows silently
      // fell through to the default icon and a raw action string.
      'ASSIGN_TO_BENCH': '\u{1F4E4}',
      'REQUEST_DOCUMENTS': '\u2753',
      'SCHEDULE_HEARING': '\u{1F4C5}',
      'PREPARE_BRIEF': '\u{1F4DD}',
      'ESCALATE_TO_TIER2': '\u2B06\uFE0F',
      'FORWARD_TO_AUTHORITY': '\u27A1\uFE0F',
      'SEND_BACK_REGISTRAR': '\u21A9\uFE0F',
      'PASS_ORDER': '\u{1F4DC}',
      'REMAND_TO_OMBUDSMAN': '\u{1F501}',
      'DISMISS': '\u{1F6AB}',
      'REASSIGN': '\u{1F501}',
      'CLOSE': '\u{1F512}',
      'REOPEN': '\u{1F504}',
    };
    return icons[action] || '\u{1F4CB}';
  }

  /** Translation key for a timeline action; the raw id is the fallback so a new action still reads. */
  getTimelineLabelKey(action: string): string {
    const known = [
      'FILED', 'ACCEPT', 'REJECT', 'ASSIGN_TO_BENCH', 'REQUEST_DOCUMENTS', 'SCHEDULE_HEARING',
      'PREPARE_BRIEF', 'ESCALATE_TO_TIER2', 'FORWARD_TO_AUTHORITY', 'SEND_BACK_REGISTRAR',
      'PASS_ORDER', 'REMAND_TO_OMBUDSMAN', 'DISMISS', 'REASSIGN', 'CLOSE', 'REOPEN',
    ];
    return known.includes(action) ? `aa.timeline.${action.toLowerCase()}` : action;
  }

  /**
   * The acting reviewer's tier, from the token claim.
   *
   * reviewer_tier is a real claim (aa_reviewer_001 = 1, aa_reviewer_002 = 2) that nothing in the UI has
   * ever surfaced, so a tier-1 reviewer had no way to know escalation was open to them.
   */
  reviewerTier = computed<string | null>(() => {
    if (this.userRole() !== 'AA_REVIEWER') {
      return null;
    }
    const claims = this.auth.currentUser() as Record<string, unknown> | null;
    const tier = claims?.['reviewer_tier'];
    return tier == null ? null : String(tier);
  });

  goBack() {
    this.router.navigate(['/aa/dashboard']);
  }
}
