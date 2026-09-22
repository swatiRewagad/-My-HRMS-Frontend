import { Injectable, inject, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { Client, IMessage, StompHeaders } from '@stomp/stompjs';
import { environment } from '../../environments/environment';
import { KeycloakAuthService } from './keycloak-auth.service';

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

/**
 * What the backend actually pushes over STOMP. Deliberately not InAppNotification: both producers
 * (NotificationService.send and ReassignmentNotificationService.pushBestEffort) send a small Map, not
 * the persisted entity, so the frame has no isRead/createdAt/actionUrl. The bell therefore treats a
 * push as a signal to re-read from REST rather than as a row to render.
 */
export interface NotificationPush {
  id: number;
  type: string;
  title?: string;
  titleKey?: string;
  message?: string;
  complaintNumber?: string;
}

/** Where the server publishes per-user notifications. Expanded from convertAndSendToUser(userId, "/queue/notifications", …). */
export const NOTIFICATION_QUEUE_DESTINATION = '/user/queue/notifications';

/** CONNECT header carrying the bearer token, matching StompIdentityChannelInterceptor. */
const CONNECT_AUTH_HEADER = 'Authorization';

/** CONNECT header carrying a dev identity. The server ignores it unless allow-dev-identity-headers is true. */
const CONNECT_DEV_USER_HEADER = 'X-User-Id';

/** Set by the E2E suites to drive the socket without a Keycloak session. Never populated in production. */
const DEV_USER_STORAGE_KEY = 'cms_dev_user_id';

@Injectable({ providedIn: 'root' })
export class NotificationService {

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);
  private baseUrl = `${environment.apiBaseUrl}/api/v1/notifications`;

  readonly unreadCount = signal(0);
  readonly notifications = signal<InAppNotification[]>([]);
  readonly hasUnread = computed(() => this.unreadCount() > 0);

  /** True while a STOMP session is established. Drives the bell's live/offline affordance. */
  readonly connected = signal(false);

  private client: Client | null = null;

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

  /**
   * Opens the live notification channel, so the bell updates without a page refresh.
   *
   * The previous implementation opened a raw `new WebSocket()` and JSON.parse'd the frames. The
   * server endpoint speaks STOMP, so the transport connected and then sat there: no CONNECT frame
   * was ever sent, no SUBSCRIBE was ever issued, and the only frames the server would have sent are
   * STOMP-framed rather than bare JSON. Nothing arrived, and nothing called this method either.
   *
   * Idempotent — the bell renders in several shells and may be constructed more than once per
   * session, and a second socket would double-count every push.
   */
  connectWebSocket(): void {
    if (this.client) {
      return;
    }

    const connectHeaders = this.buildConnectHeaders();
    if (!connectHeaders) {
      // No identity means the server would reject CONNECT anyway. Staying on the REST-only path is
      // the honest outcome: the count is still correct on load, it just is not live.
      return;
    }

    this.client = new Client({
      brokerURL: this.brokerUrl(),
      connectHeaders,
      // Reconnect and heartbeats are the point of using a real STOMP client: an officer's laptop
      // sleeps, and a socket that dies silently is indistinguishable from "no work assigned".
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        this.connected.set(true);
        this.client?.subscribe(NOTIFICATION_QUEUE_DESTINATION, (frame: IMessage) =>
          this.onPush(frame)
        );
        // A dropped socket may have missed pushes. Re-reading on every (re)connect closes that gap.
        this.refresh();
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: frame => {
        // The server sends ERROR then closes; CONNECT rejection lands here. Surfaced rather than
        // swallowed, because the silent failure is exactly the bug being fixed.
        this.connected.set(false);
        console.error('[notifications] STOMP error:', frame.headers['message'], frame.body);
      },
      onWebSocketError: event => {
        this.connected.set(false);
        console.error('[notifications] websocket error:', event);
      },
    });

    this.client.activate();
  }

  disconnectWebSocket(): void {
    this.connected.set(false);
    const client = this.client;
    this.client = null;
    // deactivate() also stops the reconnect timer; close() alone would let it reconnect after the
    // component was destroyed.
    client?.deactivate().catch(() => { /* already gone */ });
  }

  /** Re-reads count and list from REST. The single place both the initial load and a push converge. */
  refresh(): void {
    this.loadUnreadCount();
    this.getUnread().subscribe({ error: () => { /* transport already logged it */ } });
  }

  /**
   * A push carries only id/type/title, so the row is not rendered from the frame. The list and count
   * are re-read instead, which keeps one source of truth for isRead/actionUrl and means a push that
   * arrives twice (reconnect overlap) cannot inflate the badge.
   */
  private onPush(frame: IMessage): void {
    try {
      const push = JSON.parse(frame.body) as NotificationPush;
      if (!push || push.id == null) {
        return;
      }
    } catch {
      // An unparseable frame still means something happened server-side; refresh anyway.
    }
    this.refresh();
  }

  private brokerUrl(): string {
    return environment.apiBaseUrl.replace(/^http/, 'ws') + '/ws/notifications';
  }

  /**
   * The token is preferred; the dev header is a local-only fallback that the server honours only
   * while cms.security.allow-dev-identity-headers is true. Returns null when neither is available,
   * so no socket is opened that the server would only reject.
   */
  private buildConnectHeaders(): StompHeaders | null {
    const token = this.auth.isAuthenticated() ? this.auth.getToken() : '';
    if (token) {
      return { [CONNECT_AUTH_HEADER]: `Bearer ${token}` };
    }

    const devUserId = this.devUserId();
    return devUserId ? { [CONNECT_DEV_USER_HEADER]: devUserId } : null;
  }

  private devUserId(): string {
    if (environment.production) {
      return '';
    }
    try {
      return sessionStorage.getItem(DEV_USER_STORAGE_KEY)
        || localStorage.getItem(DEV_USER_STORAGE_KEY)
        || '';
    } catch {
      return '';
    }
  }
}
