import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { CepcSlaIndicatorComponent } from '../cepc-sla-indicator/cepc-sla-indicator.component';
import { CepcTimelineComponent } from '../cepc-timeline/cepc-timeline.component';
import { CepcConciliationComponent } from '../cepc-conciliation/cepc-conciliation.component';

interface TimelineEntry {
  action: string;
  fromStatus: string;
  toStatus: string;
  timestamp: string;
  remarks: string;
  performedBy?: string;
}

type CepcRole = 'CEPC_DO' | 'CEPC_REVIEWER' | 'CEPC_INCHARGE' | 'CEPC_CLOSING_AUTHORITY' | 'CEPC_ADMIN' | 'CEPC_CONTACT_PERSON';

interface ActionDef {
  id: string;
  label: string;
  description: string;
  style: string;
  requiresRemarks: boolean;
  requiresTarget?: boolean;
  targetType?: 'user' | 'department';
}

@Component({
  selector: 'app-cepc-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, CepcSlaIndicatorComponent, CepcTimelineComponent, CepcConciliationComponent],
  templateUrl: './cepc-complaint-detail.component.html',
  styleUrl: './cepc-complaint-detail.component.scss'
})
export class CepcComplaintDetailComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
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
    if (file.size > 10 * 1024 * 1024) return;

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

  getStatusLabel(status: string): string {
    const labels: Record<string, string> = {
      'assigned': 'Assigned', 'pending': 'Pending', 'new': 'New',
      'in_progress': 'Under Examination', 'under_review': 'Under Review',
      'reviewer_review': 'Reviewer Review', 'incharge_review': 'In Charge Review',
      'awaiting_closure': 'Awaiting Closure', 'escalated': 'Escalated',
      'sent_back': 'Sent Back', 'info_requested': 'Info Requested',
      'forwarded': 'Forwarded to Dept', 'forwarded_to_contact': 'With Contact Person',
      'closed': 'Closed', 'resolved': 'Resolved',
    };
    return labels[status?.toLowerCase()] || status;
  }

  goBack() {
    this.router.navigate(['/cepc/dashboard']);
  }
}
