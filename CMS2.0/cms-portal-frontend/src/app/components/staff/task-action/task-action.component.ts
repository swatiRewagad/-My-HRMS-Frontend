import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-task-action',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <div class="action-container">
      <header class="topbar">
        <div class="topbar-left">
          <button class="back-btn" (click)="goBack()">&larr;</button>
          <h2>Complaint Action</h2>
        </div>
        <div class="topbar-right">
          @if (auth.currentUser(); as user) {
            <span class="role-badge">{{ user.roles[0] }}</span>
            <span>{{ user.firstName }} {{ user.lastName }}</span>
          }
        </div>
      </header>

      <main class="content">
        @if (loading()) {
          <div class="loading">Loading complaint details...</div>
        } @else if (complaint()) {
          <div class="complaint-detail">
            <div class="detail-header">
              <h3>{{ complaint()!.complaintNumber }}</h3>
              <span class="priority-badge" [class]="complaint()!.priority?.toLowerCase()">{{ complaint()!.priority }}</span>
              <span class="status-badge">{{ complaint()!.status }}</span>
            </div>

            <div class="detail-grid">
              <div class="detail-item">
                <label>Subject</label>
                <p>{{ complaint()!.subject }}</p>
              </div>
              <div class="detail-item">
                <label>Description</label>
                <p>{{ complaint()!.description }}</p>
              </div>
              <div class="detail-item">
                <label>Complainant</label>
                <p>{{ complaint()!.complainantName }} ({{ complaint()!.complainantEmail }})</p>
              </div>
              <div class="detail-item">
                <label>Phone</label>
                <p>{{ complaint()!.complainantPhone || 'N/A' }}</p>
              </div>
              <div class="detail-item">
                <label>Entity</label>
                <p>{{ complaint()!.entityName || 'N/A' }}</p>
              </div>
              <div class="detail-item">
                <label>Registered At</label>
                <p>{{ complaint()!.registeredAt }}</p>
              </div>
              <div class="detail-item">
                <label>SLA Due Date</label>
                <p>{{ complaint()!.slaDueDate }}</p>
              </div>
              <div class="detail-item">
                <label>Assigned To</label>
                <p>{{ complaint()!.assignedTo || 'Unassigned' }}</p>
              </div>
            </div>

            <!-- Timeline -->
            @if (complaint()!.timeline?.length) {
              <div class="timeline-section">
                <h4>Timeline</h4>
                @for (entry of complaint()!.timeline; track entry.timestamp) {
                  <div class="timeline-entry">
                    <span class="timeline-action">{{ entry.action }}</span>
                    <span class="timeline-flow">{{ entry.fromStatus }} &rarr; {{ entry.toStatus }}</span>
                    <span class="timeline-time">{{ entry.timestamp }}</span>
                    @if (entry.remarks) {
                      <span class="timeline-remarks">{{ entry.remarks }}</span>
                    }
                  </div>
                }
              </div>
            }
          </div>

          <!-- Action Panel -->
          <div class="action-panel">
            @if (isTerminalState()) {
              <div class="closed-banner">
                <span class="closed-icon">&#10003;</span>
                <h4>Complaint {{ complaint()!.status }}</h4>
                <p>No further actions available. This complaint has been processed.</p>
              </div>
            } @else {
            <h4>Take Action</h4>

            <div class="action-buttons">
              @for (action of availableActions(); track action.value) {
                <button
                  class="action-btn"
                  [class]="action.style"
                  [disabled]="processing()"
                  (click)="selectAction(action.value)">
                  {{ action.label }}
                </button>
              }
            </div>

            @if (selectedAction()) {
              <div class="remarks-section">
                <label>Remarks for "{{ selectedAction() }}"</label>
                <textarea [(ngModel)]="remarks" rows="3" placeholder="Enter remarks (required)..."></textarea>
                <div class="confirm-actions">
                  <button class="confirm-btn" [disabled]="!remarks.trim() || processing()" (click)="submitAction()">
                    @if (processing()) {
                      Processing...
                    } @else {
                      Confirm {{ selectedAction() }}
                    }
                  </button>
                  <button class="cancel-btn" (click)="cancelAction()">Cancel</button>
                </div>
              </div>
            }

            @if (actionResult()) {
              <div class="result-msg" [class.success]="actionSuccess()" [class.error]="!actionSuccess()">
                {{ actionResult() }}
              </div>
            }
            }
          </div>
        } @else {
          <div class="error-state">Complaint not found.</div>
        }
      </main>
    </div>
  `,
  styles: [`
    .action-container { min-height: 100vh; background: #f4f6f9; }
    .topbar { background: #1b5e20; color: white; padding: 12px 24px; display: flex; justify-content: space-between; align-items: center; }
    .topbar-left { display: flex; align-items: center; gap: 12px; }
    .topbar h2 { margin: 0; font-size: 18px; }
    .back-btn { background: none; border: none; color: white; font-size: 20px; cursor: pointer; }
    .topbar-right { display: flex; align-items: center; gap: 10px; font-size: 14px; }
    .role-badge { background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 4px; font-size: 11px; }
    .content { max-width: 900px; margin: 0 auto; padding: 24px 20px; }
    .loading, .error-state { text-align: center; padding: 40px; color: #666; }
    .complaint-detail { background: white; border-radius: 8px; padding: 24px; margin-bottom: 20px; }
    .detail-header { display: flex; align-items: center; gap: 12px; margin-bottom: 20px; }
    .detail-header h3 { margin: 0; color: #1b5e20; font-size: 18px; }
    .priority-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; color: white; }
    .priority-badge.high { background: #d32f2f; }
    .priority-badge.medium { background: #f57c00; }
    .priority-badge.low { background: #388e3c; }
    .status-badge { padding: 2px 8px; border-radius: 4px; font-size: 11px; background: #e3f2fd; color: #1565c0; }
    .detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .detail-item label { font-size: 11px; text-transform: uppercase; color: #888; font-weight: 600; }
    .detail-item p { margin: 4px 0 0; font-size: 14px; color: #333; }
    .timeline-section { margin-top: 20px; border-top: 1px solid #eee; padding-top: 16px; }
    .timeline-section h4 { margin: 0 0 12px; font-size: 14px; color: #444; }
    .timeline-entry { display: flex; align-items: center; gap: 12px; padding: 8px 0; border-bottom: 1px solid #f5f5f5; font-size: 13px; }
    .timeline-action { font-weight: 600; color: #1b5e20; min-width: 80px; }
    .timeline-flow { color: #666; }
    .timeline-time { color: #999; margin-left: auto; font-size: 12px; }
    .timeline-remarks { color: #555; font-style: italic; }
    .action-panel { background: white; border-radius: 8px; padding: 24px; border: 2px solid #e8f5e9; }
    .action-panel h4 { margin: 0 0 16px; color: #1b5e20; }
    .action-buttons { display: flex; gap: 12px; flex-wrap: wrap; }
    .action-btn { padding: 10px 20px; border: 2px solid #ccc; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; background: white; transition: all 0.2s; }
    .action-btn:hover:not(:disabled) { transform: translateY(-1px); }
    .action-btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .action-btn.approve { border-color: #2e7d32; color: #2e7d32; }
    .action-btn.approve:hover:not(:disabled) { background: #e8f5e9; }
    .action-btn.escalate { border-color: #f57c00; color: #f57c00; }
    .action-btn.escalate:hover:not(:disabled) { background: #fff3e0; }
    .action-btn.reject { border-color: #d32f2f; color: #d32f2f; }
    .action-btn.reject:hover:not(:disabled) { background: #fbe9e7; }
    .action-btn.resolve { border-color: #1565c0; color: #1565c0; }
    .action-btn.resolve:hover:not(:disabled) { background: #e3f2fd; }
    .action-btn.return { border-color: #6a1b9a; color: #6a1b9a; }
    .action-btn.return:hover:not(:disabled) { background: #f3e5f5; }
    .remarks-section { margin-top: 16px; padding-top: 16px; border-top: 1px solid #eee; }
    .remarks-section label { font-size: 13px; font-weight: 600; color: #444; display: block; margin-bottom: 8px; }
    .remarks-section textarea { width: 100%; padding: 10px; border: 1px solid #ddd; border-radius: 6px; font-size: 14px; resize: vertical; font-family: inherit; }
    .confirm-actions { display: flex; gap: 12px; margin-top: 12px; }
    .confirm-btn { padding: 10px 24px; background: #1b5e20; color: white; border: none; border-radius: 6px; cursor: pointer; font-size: 14px; font-weight: 600; }
    .confirm-btn:disabled { opacity: 0.5; cursor: not-allowed; }
    .cancel-btn { padding: 10px 24px; background: white; border: 1px solid #ccc; border-radius: 6px; cursor: pointer; font-size: 14px; }
    .result-msg { margin-top: 16px; padding: 12px; border-radius: 6px; font-size: 14px; }
    .result-msg.success { background: #e8f5e9; color: #2e7d32; }
    .result-msg.error { background: #fbe9e7; color: #d32f2f; }
    .closed-banner { text-align: center; padding: 24px; }
    .closed-banner .closed-icon { font-size: 40px; color: #2e7d32; display: block; margin-bottom: 8px; }
    .closed-banner h4 { margin: 0 0 8px; color: #2e7d32; font-size: 18px; text-transform: uppercase; }
    .closed-banner p { margin: 0; color: #666; font-size: 14px; }
  `]
})
export class TaskActionComponent implements OnInit {
  auth = inject(KeycloakAuthService);
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private http = inject(HttpClient);

  complaint = signal<any>(null);
  loading = signal(true);
  processing = signal(false);
  selectedAction = signal<string>('');
  actionResult = signal<string>('');
  actionSuccess = signal(false);
  availableActions = signal<{label: string; value: string; style: string}[]>([]);
  remarks = '';

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const id = this.route.snapshot.params['id'];
    this.loadComplaint(id);
  }

  private loadComplaint(complaintNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}`)
      .subscribe({
        next: (res) => {
          this.complaint.set(res.data);
          this.determineActions();
          this.loading.set(false);
        },
        error: () => {
          this.complaint.set(null);
          this.loading.set(false);
        }
      });
  }

  private determineActions() {
    const roles = this.auth.getRoles();
    const status = (this.complaint()?.status || '').toLowerCase();
    const actions: {label: string; value: string; style: string}[] = [];

    if (this.isTerminalState()) {
      // Allow reopen for Closing Authority / Admin on closed complaints
      if (roles.includes('CEPC_CLOSING_AUTHORITY') || roles.includes('CEPC_ADMIN') || roles.includes('ADMIN')) {
        if (status === 'closed' || status === 'resolved') {
          actions.push({ label: 'Reopen Complaint', value: 'REOPEN', style: 'escalate' });
        }
      }
      this.availableActions.set(actions);
      return;
    }

    // ═══ CEPC DO (Dealing Official) ═══
    if (roles.includes('CEPC_DO')) {
      if (status === 'assigned' || status === 'in_progress' || status === 'sent_back') {
        actions.push({ label: 'Forward to Reviewer', value: 'SUBMIT_FOR_REVIEW', style: 'approve' });
        actions.push({ label: 'Forward to In-Charge', value: 'FORWARD_TO_INCHARGE', style: 'approve' });
        actions.push({ label: 'Request Info', value: 'REQUEST_INFO', style: 'escalate' });
        actions.push({ label: 'Forward to Contact Person', value: 'FORWARD_TO_CONTACT', style: 'resolve' });
      }
    }

    // ═══ CEPC Reviewer ═══
    if (roles.includes('CEPC_REVIEWER')) {
      if (status === 'reviewer_review' || status === 'in_progress') {
        actions.push({ label: 'Forward to In-Charge', value: 'APPROVE_REVIEW', style: 'approve' });
        actions.push({ label: 'Forward to Closing Authority', value: 'FORWARD_TO_CLOSING_AUTHORITY', style: 'approve' });
        actions.push({ label: 'Send Back to DO', value: 'SEND_BACK_DO', style: 'return' });
      }
    }

    // ═══ CEPC In-Charge ═══
    if (roles.includes('CEPC_INCHARGE')) {
      if (status === 'incharge_review' || status === 'in_progress') {
        actions.push({ label: 'Forward to Closing Authority', value: 'APPROVE_CLOSURE', style: 'approve' });
        actions.push({ label: 'Send Back to Reviewer', value: 'SEND_BACK_REVIEWER', style: 'return' });
        actions.push({ label: 'Send Back to DO', value: 'SEND_BACK_DO', style: 'return' });
      }
    }

    // ═══ CEPC Closing Authority ═══
    if (roles.includes('CEPC_CLOSING_AUTHORITY')) {
      if (status === 'awaiting_closure' || status === 'in_progress') {
        actions.push({ label: 'Close Complaint', value: 'CLOSE_COMPLAINT', style: 'approve' });
        actions.push({ label: 'Send Back to In-Charge', value: 'SEND_BACK_INCHARGE', style: 'return' });
        actions.push({ label: 'Forward to Other Office', value: 'FORWARD_TO_OTHER_OFFICE', style: 'escalate' });
        actions.push({ label: 'Forward to Regulatory Body', value: 'FORWARD_TO_REGULATORY_BODY', style: 'escalate' });
        actions.push({ label: 'Forward to Other RBI Dept', value: 'FORWARD_TO_OTHER_RBI_DEPT', style: 'escalate' });
      }
    }

    // ═══ RBIO Officer ═══
    if (roles.includes('RBIO_OFFICER')) {
      if (status === 'pending' || status === 'assigned' || status === 'in_progress') {
        actions.push({ label: 'Escalate', value: 'ESCALATE', style: 'escalate' });
        actions.push({ label: 'Resolve', value: 'RESOLVE', style: 'resolve' });
        actions.push({ label: 'Reject', value: 'REJECT', style: 'reject' });
      }
    }

    // ═══ RBIO Supervisor ═══
    if (roles.includes('RBIO_SUPERVISOR')) {
      if (status === 'escalated' || status === 'in_progress') {
        actions.push({ label: 'Approve & Escalate', value: 'APPROVE', style: 'approve' });
        actions.push({ label: 'Return to Officer', value: 'RETURN_TO_OFFICER', style: 'return' });
        actions.push({ label: 'Resolve', value: 'RESOLVE', style: 'resolve' });
      }
    }

    // ═══ RBIO Conciliator ═══
    if (roles.includes('RBIO_CONCILIATOR')) {
      if (status === 'escalated' || status === 'conciliation') {
        actions.push({ label: 'Conciliation Success', value: 'CONCILIATION_SUCCESS', style: 'approve' });
        actions.push({ label: 'Conciliation Failed', value: 'CONCILIATION_FAILED', style: 'escalate' });
      }
    }

    // ═══ RBIO Adjudicator ═══
    if (roles.includes('RBIO_ADJUDICATOR')) {
      if (status === 'escalated' || status === 'adjudication') {
        actions.push({ label: 'Award (Adjudication)', value: 'ADJUDICATION_AWARD', style: 'approve' });
        actions.push({ label: 'Reject', value: 'REJECT', style: 'reject' });
      }
    }

    // ═══ CEPC Admin ═══
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
    const dept = this.auth.currentUser()?.department || 'RBIO';
    const complaintNumber = this.complaint()?.complaintId || this.complaint()?.complaintNumber;
    const actor = this.auth.currentUser()?.username || '';

    this.processing.set(true);
    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/${dept.toLowerCase()}/action/${complaintNumber}`,
      {
        action: this.selectedAction(),
        remarks: this.remarks,
        actor: actor
      }
    ).subscribe({
      next: (res) => {
        this.actionSuccess.set(true);
        this.actionResult.set(`Action "${this.selectedAction()}" completed. New status: ${res.data?.newStatus}`);
        this.processing.set(false);
        this.selectedAction.set('');
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
    return ['resolved', 'closed', 'rejected', 'withdrawn', 'adjudicated', 'conciliated', 'approved'].includes(status);
  }

  goBack() {
    const dept = this.auth.currentUser()?.department?.toLowerCase() || 'rbio';
    this.router.navigateByUrl('/', { skipLocationChange: true }).then(() => {
      this.router.navigate([`/staff/${dept}/tasks`]);
    });
  }
}
