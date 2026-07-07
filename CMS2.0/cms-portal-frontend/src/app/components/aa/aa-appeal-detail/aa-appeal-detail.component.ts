import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { AaHearingComponent } from '../aa-hearing/aa-hearing.component';
import { AaOrderComponent } from '../aa-order/aa-order.component';

type AaRole = 'AA_REGISTRAR' | 'AA_BENCH_OFFICER' | 'AA_AUTHORITY' | 'AA_ADMIN';

interface ActionDef {
  id: string;
  label: string;
  description: string;
  style: string;
  requiresRemarks: boolean;
  requiresTarget?: boolean;
  targetType?: 'user' | 'date';
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
  imports: [CommonModule, FormsModule, AaHearingComponent, AaOrderComponent],
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
  userRole = signal<AaRole>('AA_REGISTRAR');

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

  roleLabels: Record<AaRole, string> = {
    'AA_REGISTRAR': 'Registrar',
    'AA_BENCH_OFFICER': 'Bench Officer',
    'AA_AUTHORITY': 'Appellate Authority',
    'AA_ADMIN': 'AA Admin'
  };

  availableActions = computed<ActionDef[]>(() => {
    const role = this.userRole();
    const status = (this.appeal()?.status || '').toLowerCase();
    const actions: ActionDef[] = [];

    if (this.isTerminalState()) return [];

    if (role === 'AA_REGISTRAR') {
      if (['filed', 'under_review'].includes(status)) {
        actions.push({ id: 'ACCEPT', label: 'Accept Appeal', description: 'Accept and register the appeal for processing', style: 'primary', requiresRemarks: false });
        actions.push({ id: 'REJECT', label: 'Reject Appeal', description: 'Reject appeal (ineligible or out of time)', style: 'close', requiresRemarks: true });
        actions.push({ id: 'ASSIGN_BENCH', label: 'Assign to Bench', description: 'Assign to a Bench Officer for processing', style: 'forward', requiresRemarks: true, requiresTarget: true, targetType: 'user' });
        actions.push({ id: 'REQUEST_DOCUMENTS', label: 'Request Documents', description: 'Request additional documents from appellant', style: 'info', requiresRemarks: true });
      }
    }

    if (role === 'AA_BENCH_OFFICER') {
      if (['accepted', 'assigned', 'hearing_completed', 'documents_requested'].includes(status)) {
        actions.push({ id: 'SCHEDULE_HEARING', label: 'Schedule Hearing', description: 'Schedule a hearing date and venue', style: 'primary', requiresRemarks: true });
        actions.push({ id: 'PREPARE_BRIEF', label: 'Prepare Brief', description: 'Prepare case brief for the Authority', style: 'info', requiresRemarks: true });
        actions.push({ id: 'FORWARD_AUTHORITY', label: 'Forward to Authority', description: 'Forward case to Appellate Authority for decision', style: 'forward', requiresRemarks: true });
        actions.push({ id: 'SEND_BACK_REGISTRAR', label: 'Send Back to Registrar', description: 'Return to Registrar for additional processing', style: 'return', requiresRemarks: true });
      }
    }

    if (role === 'AA_AUTHORITY') {
      if (['order_reserved', 'hearing_completed', 'accepted', 'assigned'].includes(status)) {
        actions.push({ id: 'PASS_ORDER', label: 'Pass Order', description: 'Pass final order on the appeal', style: 'primary', requiresRemarks: false });
        actions.push({ id: 'SCHEDULE_HEARING', label: 'Schedule Hearing', description: 'Schedule a hearing date', style: 'info', requiresRemarks: true });
        actions.push({ id: 'REMAND_OMBUDSMAN', label: 'Remand to Ombudsman', description: 'Remand case back to Ombudsman for fresh consideration', style: 'escalate', requiresRemarks: true });
        actions.push({ id: 'DISMISS', label: 'Dismiss Appeal', description: 'Dismiss the appeal', style: 'close', requiresRemarks: true });
      }
    }

    if (role === 'AA_ADMIN') {
      if (!this.isTerminalState()) {
        actions.push({ id: 'REASSIGN', label: 'Reassign', description: 'Reassign to another officer', style: 'info', requiresRemarks: true, requiresTarget: true, targetType: 'user' });
        actions.push({ id: 'CLOSE', label: 'Close Appeal', description: 'Administratively close the appeal', style: 'close', requiresRemarks: true });
      }
      if (['closed', 'dismissed', 'rejected'].includes(status)) {
        actions.push({ id: 'REOPEN', label: 'Reopen Appeal', description: 'Reopen a closed/dismissed appeal', style: 'escalate', requiresRemarks: true });
      }
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
    if (roles.includes('AA_ADMIN')) this.userRole.set('AA_ADMIN');
    else if (roles.includes('AA_AUTHORITY')) this.userRole.set('AA_AUTHORITY');
    else if (roles.includes('AA_BENCH_OFFICER')) this.userRole.set('AA_BENCH_OFFICER');
    else this.userRole.set('AA_REGISTRAR');

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
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/keycloak/users/by-role?role=AA_BENCH_OFFICER`).subscribe({
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
        this.actionSuccess.set(true);
        this.actionResult.set(`Action "${action.label}" completed successfully. New status: ${res?.data?.newStatus || 'updated'}.`);
        this.processing.set(false);
        this.selectedAction.set(null);
        this.loadAppeal(appealNumber);
        this.loadTimeline(appealNumber);
      },
      error: (err) => {
        this.actionSuccess.set(false);
        this.actionResult.set(`Failed: ${err.error?.message || err.message || 'Unknown error'}`);
        this.processing.set(false);
      }
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

  isTerminalState(): boolean {
    const status = (this.appeal()?.status || '').toLowerCase();
    return ['closed', 'dismissed', 'rejected', 'order_passed'].includes(status);
  }

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'filed': 'Filed',
      'under_review': 'Under Review',
      'accepted': 'Accepted',
      'rejected': 'Rejected',
      'hearing_scheduled': 'Hearing Scheduled',
      'hearing_completed': 'Hearing Completed',
      'order_reserved': 'Order Reserved',
      'order_passed': 'Order Passed',
      'remanded': 'Remanded',
      'dismissed': 'Dismissed',
      'closed': 'Closed',
      'assigned': 'Assigned',
      'documents_requested': 'Documents Requested',
    };
    return labels[status] || status;
  }

  getTimelineIcon(action: string): string {
    const icons: Record<string, string> = {
      'ACCEPT': '\u2705',
      'REJECT': '\u274C',
      'ASSIGN_BENCH': '\u{1F4E4}',
      'REQUEST_DOCUMENTS': '\u2753',
      'SCHEDULE_HEARING': '\u{1F4C5}',
      'PREPARE_BRIEF': '\u{1F4DD}',
      'FORWARD_AUTHORITY': '\u27A1\uFE0F',
      'SEND_BACK_REGISTRAR': '\u21A9\uFE0F',
      'PASS_ORDER': '\u{1F4DC}',
      'REMAND_OMBUDSMAN': '\u{1F501}',
      'DISMISS': '\u{1F6AB}',
      'REASSIGN': '\u{1F501}',
      'CLOSE': '\u{1F512}',
      'REOPEN': '\u{1F504}',
    };
    return icons[action] || '\u{1F4CB}';
  }

  getTimelineLabel(action: string): string {
    const labels: Record<string, string> = {
      'ACCEPT': 'Appeal Accepted',
      'REJECT': 'Appeal Rejected',
      'ASSIGN_BENCH': 'Assigned to Bench Officer',
      'REQUEST_DOCUMENTS': 'Documents Requested',
      'SCHEDULE_HEARING': 'Hearing Scheduled',
      'PREPARE_BRIEF': 'Brief Prepared',
      'FORWARD_AUTHORITY': 'Forwarded to Authority',
      'SEND_BACK_REGISTRAR': 'Sent Back to Registrar',
      'PASS_ORDER': 'Order Passed',
      'REMAND_OMBUDSMAN': 'Remanded to Ombudsman',
      'DISMISS': 'Appeal Dismissed',
      'REASSIGN': 'Reassigned',
      'CLOSE': 'Appeal Closed',
      'REOPEN': 'Appeal Reopened',
      'FILE_APPEAL': 'Appeal Filed',
    };
    return labels[action] || action;
  }

  goBack() {
    this.router.navigate(['/aa/dashboard']);
  }
}
