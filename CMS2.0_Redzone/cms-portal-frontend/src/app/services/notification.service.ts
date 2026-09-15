import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { environment } from '../../environments/environment';

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
  id: number;
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
  readonly notifications = signal<InAppNotification[]>([]);
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

  markRead(ids: number[]): Observable<{ updated: number }> {
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
