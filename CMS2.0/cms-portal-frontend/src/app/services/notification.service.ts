import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

export const MOCK_NOTIFICATIONS: InAppNotification[] = [
  {
    id: 'notif-001',
    targetUserId: 'USR-9021',
    type: 'ASSIGNMENT',
    title: 'New Complaint Assigned',
    message: 'Grievance case #RBI-2026-88401 has been dispatched to your queue for primary evaluation.',
    relatedEntityId: 'RBI-2026-88401',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/complaints/RBI-2026-88401',
    isRead: false,
    readAt: null,
    createdAt: new Date().toISOString() // Just now
  },
  {
    id: 'notif-002',
    targetUserId: 'USR-9021',
    type: 'REASSIGNMENT',
    title: 'Case Reassigned',
    message: 'Reviewer transferred complaint panel context #RBI-2026-11029 back to your tracking view.',
    relatedEntityId: 'RBI-2026-11029',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/complaints/RBI-2026-11029',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 15 * 60000).toISOString() // 15m ago
  },
  {
    id: 'notif-003',
    targetUserId: 'USR-9021',
    type: 'TRANSFER_IN',
    title: 'Inter-Departmental Transfer In',
    message: 'Nodal desk safely routed banking case file parameters down to your sub-region interface.',
    relatedEntityId: 'TXF-88301',
    relatedEntityType: 'TRANSFER_RECORD',
    actionUrl: '/workspace/transfers/inbound',
    isRead: true,
    readAt: new Date(Date.now() - 30 * 60000).toISOString(),
    createdAt: new Date(Date.now() - 45 * 60000).toISOString() // 45m ago
  },
  {
    id: 'notif-004',
    targetUserId: 'USR-9021',
    type: 'TRANSFER_PENDING',
    title: 'Transfer Approval Pending',
    message: 'Outgoing transfer authority review is awaiting formal signature parameters from management block.',
    relatedEntityId: 'TXF-88390',
    relatedEntityType: 'TRANSFER_RECORD',
    actionUrl: '/workspace/transfers/pending',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 2 * 3600000).toISOString() // 2h ago
  },
  {
    id: 'notif-005',
    targetUserId: 'USR-9021',
    type: 'PENDING_3DAY',
    title: 'SLA Escalation Window: 3-Day Milestone',
    message: 'Urgent notice: Complaint file record #RBI-2026-00492 has passed its initial resolution deadline.',
    relatedEntityId: 'RBI-2026-00492',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/complaints/RBI-2026-00492',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 5 * 3600000).toISOString() // 5h ago
  },
  {
    id: 'notif-006',
    targetUserId: 'USR-9021',
    type: 'PENDING_5DAY',
    title: 'Critical SLA Constraint: 5-Day Violation',
    message: 'Breach window reached: File reference context is flagged for mandatory ombudsman visibility reporting.',
    relatedEntityId: 'RBI-2026-00492',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/escalations/crit-5',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 20 * 3600000).toISOString() // 20h ago
  },
  {
    id: 'notif-007',
    targetUserId: 'USR-9021',
    type: 'DUPLICATE_DETECTED',
    title: 'Duplicate Record Signatures Detected',
    message: 'System tracking engines caught matching pan-card hash values across concurrently open files.',
    relatedEntityId: 'RBI-2026-99301',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/dedup/review',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 25 * 3600000).toISOString() // 1d ago
  },
  {
    id: 'notif-008',
    targetUserId: 'USR-9021',
    type: 'SENT_BACK',
    title: 'File Sent Back by Review Board',
    message: 'Audit team requested immediate corrections regarding missing compliance proof layout attachments.',
    relatedEntityId: 'RBI-2026-00492',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/complaints/RBI-2026-00492/edit',
    isRead: true,
    readAt: new Date(Date.now() - 28 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 30 * 3600000).toISOString() // 1d ago
  },
  {
    id: 'notif-009',
    targetUserId: 'USR-9021',
    type: 'BULK_CLOSE',
    title: 'Bulk Batch Operation Concluded',
    message: 'Successfully updated status metadata profiles across 45 systemic consumer complaint instances.',
    relatedEntityId: 'BATCH-89102',
    relatedEntityType: 'SYSTEM_BATCH',
    actionUrl: '/workspace/batches/logs/b-89102',
    isRead: true,
    readAt: new Date(Date.now() - 48 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 50 * 3600000).toISOString() // 2d ago
  },
  {
    id: 'notif-010',
    targetUserId: 'USR-9021',
    type: 'ON_LEAVE_PENDING',
    title: 'Temporary Leave Assignment Delayed',
    message: 'Backup officer endorsement step is outstanding for regional supervisor absence requests.',
    relatedEntityId: 'LR-00491',
    relatedEntityType: 'HR_ROSTER',
    actionUrl: '/workspace/hr/leave-roster',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 72 * 3600000).toISOString() // 3d ago
  },
  {
    id: 'notif-011',
    targetUserId: 'USR-9021',
    type: 'NO_RECORD_ASSIGNED',
    title: 'Unassigned Base Record Queue Notification',
    message: 'New digital portal intake files are unmapped to active duty handling officers at this station.',
    relatedEntityId: 'POOL-CENTRAL',
    relatedEntityType: 'QUEUE_POOL',
    actionUrl: '/workspace/unassigned-pool',
    isRead: true,
    readAt: new Date(Date.now() - 90 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 96 * 3600000).toISOString() // 4d ago
  },
  {
    id: 'notif-012',
    targetUserId: 'USR-9021',
    type: 'NO_REASSIGNED_TO_RBI',
    title: 'Corporate Escalation Returned to Central RBI',
    message: 'External nodal compliance team safely passed processing context back inside the primary grid.',
    relatedEntityId: 'RBI-2026-44910',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/central-intake',
    isRead: true,
    readAt: new Date(Date.now() - 110 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 120 * 3600000).toISOString() // 5d ago
  },
  {
    id: 'notif-013',
    targetUserId: 'USR-9021',
    type: 'NO_STATUS_STALE',
    title: 'Stale Application Status Alert',
    message: 'File record #RBI-2026-77301 has logged no procedural status updates over the last 14 business days.',
    relatedEntityId: 'RBI-2026-77301',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/stale-monitor',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 144 * 3600000).toISOString() // 6d ago
  },
  {
    id: 'notif-014',
    targetUserId: 'USR-9021',
    type: 'RE_RESPONSE',
    title: 'Commercial Banking Institution Response',
    message: 'Target commercial entity uploaded formal defense response statements directly against file claim parameters.',
    relatedEntityId: 'REP-003921',
    relatedEntityType: 'BANK_RESPONSE',
    actionUrl: '/workspace/responses/view/notif-014',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 168 * 3600000).toISOString() // 7d ago
  },
  {
    id: 'notif-015',
    targetUserId: 'USR-9021',
    type: 'RE_UPDATE',
    title: 'Systemic Operational Record Core Update',
    message: 'Central ledger adjusted default clearing window timelines matching modified regulatory guidelines.',
    relatedEntityId: 'SYS-REG-02',
    relatedEntityType: 'SYSTEM_CONFIG',
    actionUrl: '/workspace/system/logs',
    isRead: true,
    readAt: new Date(Date.now() - 190 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 192 * 3600000).toISOString() // 8d ago
  },
  {
    id: 'notif-016',
    targetUserId: 'USR-9021',
    type: 'COMPLAINT_CLOSED',
    title: 'Complaint Formally Resolved and Closed',
    message: 'Consumer claim instance verification complete. Satisfactory remedy successfully deployed and finalized.',
    relatedEntityId: 'RBI-2026-11204',
    relatedEntityType: 'COMPLAINT',
    actionUrl: '/workspace/archive/complaints',
    isRead: true,
    readAt: new Date(Date.now() - 210 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 216 * 3600000).toISOString() // 9d ago
  },
  {
    id: 'notif-017',
    targetUserId: 'USR-9021',
    type: 'MEETING_SCHEDULED',
    title: 'Conciliation Meeting Confirmed',
    message: 'Digital mediation assembly scheduled for Tuesday afternoon with alternative dispute resolution board.',
    relatedEntityId: 'MTG-4402',
    relatedEntityType: 'HEARING_MEETING',
    actionUrl: '/workspace/calendar/mediations',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 240 * 3600000).toISOString() // 10d ago
  },
  {
    id: 'notif-018',
    targetUserId: 'USR-9021',
    type: 'AWARD_PASSED',
    title: 'Formal Compensation Award Issued',
    message: 'Arbitration framework verified penalty payout requirements onto corresponding account profiles.',
    relatedEntityId: 'AWD-00392',
    relatedEntityType: 'ARBITRATION_AWARD',
    actionUrl: '/workspace/awards/registry',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 264 * 3600000).toISOString() // 11d ago
  },
  {
    id: 'notif-019',
    targetUserId: 'USR-9021',
    type: 'DECISION',
    title: 'Final Appellate Ruling Passed',
    message: 'Ombudsman office locked final decree processing constraints regarding dispute profile updates.',
    relatedEntityId: 'DEC-99401',
    relatedEntityType: 'OMBUDSMAN_DECISION',
    actionUrl: '/workspace/decisions/final',
    isRead: true,
    readAt: new Date(Date.now() - 280 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 288 * 3600000).toISOString() // 12d ago
  },
  {
    id: 'notif-020',
    targetUserId: 'USR-9021',
    type: 'ADVISORY_COMPLIED',
    title: 'Advisory Complied Validation Signal',
    message: 'Target credit enterprise dispatched matching validation codes indicating absolute execution of directives.',
    relatedEntityId: 'ADV-44021',
    relatedEntityType: 'COMPLIANCE_ADVISORY',
    actionUrl: '/workspace/compliance/advisories',
    isRead: true,
    readAt: new Date(Date.now() - 300 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 312 * 3600000).toISOString() // 13d ago
  }, {
    id: 'notif-021', targetUserId: 'USR-9021',
    type: 'DOCUMENT_UPLOADED',
    title: 'Legal Proof Document Uploaded',
    message: 'Complainant appended secondary structural notarized declarations onto running dashboard files.',
    relatedEntityId: 'DOC-883012',
    relatedEntityType: 'ATTACHMENT_FILE',
    actionUrl: '/workspace/documents/viewer',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 336 * 3600000).toISOString() // 14d ago
  }, {
    id: 'notif-022',
    targetUserId: 'USR-9021',
    type: 'UPLOAD_LINK_SENT',
    title: 'Secure Upload Endpoint Link Shared',
    message: 'Digital processing hub delivered encrypted external link token out to the client identity profiles.',
    relatedEntityId: 'LNK-99204',
    relatedEntityType: 'OUTBOUND_LINK',
    actionUrl: '/workspace/system/outbox',
    isRead: true,
    readAt: new Date(Date.now() - 350 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 360 * 3600000).toISOString() // 15d ago
  }, {
    id: 'notif-023',
    targetUserId: 'USR-9021',
    type: 'CRPC_TOLL_FREE_REMINDER',
    title: 'CRPC Toll-Free Call Reminder Action',
    message: 'Follow-up system checklist prompt: Outbound confirmation contact parameter due for consumer file review.',
    relatedEntityId: 'CALL-49201',
    relatedEntityType: 'CRM_LOG',
    actionUrl: '/workspace/crm/calls-log',
    isRead: false,
    readAt: null,
    createdAt: new Date(Date.now() - 384 * 3600000).toISOString() // 16d ago
  },
  {
    id: 'notif-024',
    targetUserId: 'USR-9021',
    type: 'RIA_LEGAL_UPDATE',
    title: 'RIA Statutory Legal Update Dispatched',
    message: 'Right to Information reference sub-packet compiled and matched into central legal archive layer.',
    relatedEntityId: 'RIA-2026-004',
    relatedEntityType: 'LEGAL_PACKET',
    actionUrl: '/workspace/legal/ria-packets',
    isRead: true,
    readAt: new Date(Date.now() - 400 * 3600000).toISOString(),
    createdAt: new Date(Date.now() - 408 * 3600000).toISOString() // 17d ago
  }];

/**
 * All supported notification types in the CMS system.
 */
export type NotificationType =
  | 'ASSIGNMENT'
  | 'REASSIGNMENT'
  | 'TRANSFER_IN'
  | 'TRANSFER_PENDING'
  | 'PENDING_3DAY'
  | 'PENDING_5DAY'
  | 'DUPLICATE_DETECTED'
  | 'SENT_BACK'
  | 'BULK_CLOSE'
  | 'ON_LEAVE_PENDING'
  | 'NO_RECORD_ASSIGNED'
  | 'NO_REASSIGNED_TO_RBI'
  | 'NO_STATUS_STALE'
  | 'RE_RESPONSE'
  | 'COMPLAINT_CLOSED'
  | 'RE_UPDATE'
  | 'MEETING_SCHEDULED'
  | 'AWARD_PASSED'
  | 'DECISION'
  | 'ADVISORY_COMPLIED'
  | 'DOCUMENT_UPLOADED'
  | 'UPLOAD_LINK_SENT'
  | 'CRPC_TOLL_FREE_REMINDER'
  | 'RIA_LEGAL_UPDATE';

export interface InAppNotification {
  id: number | string;
  targetUserId: string;
  type: NotificationType | string;
  title: string;
  message: string;
  relatedEntityId: string;
  relatedEntityType: string;
  actionUrl: string;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPage {
  content: InAppNotification[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private http = inject(HttpClient);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/notifications`;

  readonly unreadCount = signal(0);
  readonly notifications = signal<InAppNotification[]>(MOCK_NOTIFICATIONS);
  readonly hasUnread = computed(() => this.unreadCount() > 0);

  private ws: WebSocket | null = null;

  loadUnreadCount(): void {
    this.http.get<{ count: number }>(`${this.baseUrl}/unread-count`).subscribe(res => {
      this.unreadCount.set(res.count);
    });
  }

  getNotifications(page = 0, size = 20): Observable<NotificationPage> {
    return this.http.get<NotificationPage>(`${this.baseUrl}`, { params: { page, size } });
  }

  getUnread(): Observable<InAppNotification[]> {
    return this.http.get<InAppNotification[]>(`${this.baseUrl}/unread`).pipe(
      tap(list => this.notifications.set(list))
    );
  }

  markAllRead(): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>(`${this.baseUrl}/mark-all-read`, {}).pipe(
      tap(() => {
        this.unreadCount.set(0);
        this.notifications.update(list => list.map(n => ({ ...n, isRead: true })));
      })
    );
  }

  markRead(ids: (number | string)[]): Observable<{ updated: number }> {
    return this.http.post<{ updated: number }>(`${this.baseUrl}/mark-read`, ids).pipe(
      tap(() => {
        this.unreadCount.update(c => Math.max(0, c - ids.length));
        this.notifications.update(list =>
          list.map(n => ids.includes(n.id) ? { ...n, isRead: true } : n)
        );
      })
    );
  }

  connectWebSocket(userId: string): void {
    const wsUrl = environment.apiBaseUrl.replace(/^http/, 'ws') + '/ws/notifications';
    this.ws = new WebSocket(wsUrl);
    this.ws.onmessage = (event) => {
      const notification = JSON.parse(event.data) as InAppNotification;
      this.notifications.update(list => [notification, ...list]);
      this.unreadCount.update(c => c + 1);
    };
  }

  disconnectWebSocket(): void {
    this.ws?.close();
    this.ws = null;
  }
}
