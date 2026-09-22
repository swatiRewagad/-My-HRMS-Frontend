import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificationService, InAppNotification } from '../../services/notification.service';
import { TranslationService } from '../../services/translation.service';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    <div class="notification-bell" (click)="toggleDropdown()"
         data-testid="notification-bell"
         [attr.data-connected]="notificationService.connected()"
         [attr.data-unread-count]="notificationService.unreadCount()"
         [attr.aria-label]="'notifications.title' | translate">
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      @if (notificationService.hasUnread()) {
        <span class="badge" data-testid="notification-badge">{{ notificationService.unreadCount() }}</span>
      }
    </div>

    @if (isOpen()) {
      <div class="dropdown-overlay" (click)="isOpen.set(false)"></div>
      <div class="dropdown" data-testid="notification-dropdown">
        <div class="dropdown-header">
          <h4>{{ 'notifications.title' | translate }}</h4>
          @if (notificationService.hasUnread()) {
            <button class="mark-all-btn" data-testid="notification-mark-all"
                    (click)="markAllRead()">{{ 'notifications.mark_all_read' | translate }}</button>
          }
        </div>
        <!-- Shown only while the live channel is down, so a stale badge is never mistaken for
             "no new work". -->
        @if (!notificationService.connected()) {
          <div class="offline-notice" data-testid="notification-offline">
            {{ 'notifications.live_updates_unavailable' | translate }}
          </div>
        }
        <div class="dropdown-body">
          @for (n of notificationService.notifications(); track n.id) {
            <div class="notification-item" [class.unread]="!n.isRead"
                 [attr.data-testid]="'notification-item-' + n.id"
                 (click)="onNotificationClick(n)">
              <div class="notif-icon" [attr.data-type]="n.type">
                {{ getIcon(n.type) }}
              </div>
              <div class="notif-content">
                <p class="notif-title">{{ resolveText(n.title) }}</p>
                <span class="notif-type-badge">{{ formatType(n.type) }}</span>
                <p class="notif-message">{{ resolveText(n.message) }}</p>
                <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
              </div>
            </div>
          } @empty {
            <div class="empty-state" data-testid="notification-empty">
              {{ 'notifications.empty' | translate }}
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: [`
    :host { position: relative; display: inline-block; }
    .notification-bell { cursor: pointer; position: relative; padding: 8px; border-radius: 50%; transition: background 0.2s; }
    .notification-bell:hover { background: rgba(0,0,0,0.05); }
    .badge { position: absolute; top: 2px; right: 2px; background: #ef4444; color: #fff; font-size: 11px;
             min-width: 18px; height: 18px; border-radius: 9px; display: flex; align-items: center;
             justify-content: center; font-weight: 600; }
    .dropdown-overlay { position: fixed; inset: 0; z-index: 999; }
    .dropdown { position: absolute; top: 100%; right: 0; width: 360px; max-height: 480px; background: #fff;
                border-radius: 12px; box-shadow: 0 10px 40px rgba(0,0,0,0.15); z-index: 1000; overflow: hidden; }
    .dropdown-header { display: flex; align-items: center; justify-content: space-between; padding: 16px; border-bottom: 1px solid #f1f5f9; }
    .dropdown-header h4 { margin: 0; font-size: 16px; font-weight: 600; }
    .mark-all-btn { background: none; border: none; color: #3b82f6; cursor: pointer; font-size: 13px; font-weight: 500; }
    .offline-notice { padding: 8px 16px; font-size: 11px; color: #92400e; background: #fef3c7;
                      border-bottom: 1px solid #fde68a; }
    .dropdown-body { overflow-y: auto; max-height: 400px; }
    .notification-item { display: flex; gap: 12px; padding: 12px 16px; cursor: pointer; transition: background 0.15s; }
    .notification-item:hover { background: #f8fafc; }
    .notification-item.unread { background: #eff6ff; }
    .notif-icon { width: 36px; height: 36px; border-radius: 50%; background: #e2e8f0; display: flex;
                  align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
    .notif-content { flex: 1; min-width: 0; }
    .notif-title { margin: 0; font-size: 13px; font-weight: 600; color: #1e293b; }
    .notif-type-badge { display: inline-block; margin-top: 3px; padding: 1px 6px; font-size: 10px;
                        font-weight: 500; color: #475569; background: #e2e8f0; border-radius: 4px;
                        letter-spacing: 0.2px; text-transform: capitalize; }
    .notif-message { margin: 4px 0 0; font-size: 12px; color: #64748b; overflow: hidden;
                     text-overflow: ellipsis; white-space: nowrap; }
    .notif-time { font-size: 11px; color: #94a3b8; }
    .empty-state { padding: 40px 16px; text-align: center; color: #94a3b8; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  readonly notificationService = inject(NotificationService);
  private translation = inject(TranslationService);
  private router = inject(Router);

  isOpen = signal(false);

  ngOnInit(): void {
    this.notificationService.refresh();
    // The live channel had no caller at all before this, which is why the badge only ever moved on a
    // page load. ngOnDestroy already tore a connection down; nothing ever opened one.
    this.notificationService.connectWebSocket();
  }

  ngOnDestroy(): void {
    this.notificationService.disconnectWebSocket();
  }

  /**
   * Notification title/message are a mix: newer producers write translation keys
   * (`aa.assignment.assigned`, `notification.reassign.in`) while older ones still write English
   * prose. translate() returns the key unchanged when it is not a known key, so passing everything
   * through resolves the keys and leaves legacy prose readable.
   */
  resolveText(value: string | null | undefined): string {
    return value ? this.translation.translate(value) : '';
  }

  toggleDropdown(): void {
    this.isOpen.update(v => !v);
    if (this.isOpen()) {
      this.notificationService.getUnread().subscribe();
    }
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe();
  }

  onNotificationClick(n: InAppNotification): void {
    if (!n.isRead) {
      this.notificationService.markRead([n.id]).subscribe();
    }
    if (n.actionUrl) {
      this.router.navigateByUrl(n.actionUrl);
    }
    this.isOpen.set(false);
  }

  getIcon(type: string): string {
    const icons: Record<string, string> = {
      ASSIGNMENT: '\u{1F464}',
      REASSIGNMENT: '\u{1F464}',
      TRANSFER_IN: '\u{1F504}',
      TRANSFER_PENDING: '\u{1F504}',
      PENDING_3DAY: '\u{23F0}',
      PENDING_5DAY: '\u{23F0}',
      DUPLICATE_DETECTED: '\u{26A0}\uFE0F',
      SENT_BACK: '\u{21A9}\uFE0F',
      BULK_CLOSE: '\u{1F5C2}\uFE0F',
      ON_LEAVE_PENDING: '\u{1F3D6}\uFE0F',
      NO_RECORD_ASSIGNED: '\u{1F4CB}',
      NO_REASSIGNED_TO_RBI: '\u{1F4CB}',
      NO_STATUS_STALE: '\u{1F534}',
      RE_RESPONSE: '\u{1F3E6}',
      RE_UPDATE: '\u{1F3E6}',
      COMPLAINT_CLOSED: '\u{2705}',
      MEETING_SCHEDULED: '\u{1F4C5}',
      AWARD_PASSED: '\u{2696}\uFE0F',
      DECISION: '\u{2696}\uFE0F',
      ADVISORY_COMPLIED: '\u{2696}\uFE0F',
      DOCUMENT_UPLOADED: '\u{1F4C4}',
      UPLOAD_LINK_SENT: '\u{1F517}',
      CRPC_TOLL_FREE_REMINDER: '\u{1F4DE}',
      RIA_LEGAL_UPDATE: '\u{1F4DD}',
    };
    return icons[type] || '\u{1F514}';
  }

  /**
   * The type badge resolves `notifications.type.<lowercased type>` and falls back to the humanised
   * enum, so a type added by the backend before its key is seeded still renders something legible
   * rather than a raw key.
   */
  formatType(type: string): string {
    const key = `notifications.type.${type.toLowerCase()}`;
    const resolved = this.translation.translate(key);
    return resolved === key ? type.replace(/_/g, ' ').toLowerCase() : resolved;
  }

  formatTime(dateStr: string): string {
    const diff = Date.now() - new Date(dateStr).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return this.translation.translate('notifications.time.just_now');
    if (minutes < 60) return this.translation.translate('notifications.time.minutes_ago', { count: String(minutes) });
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return this.translation.translate('notifications.time.hours_ago', { count: String(hours) });
    const days = Math.floor(hours / 24);
    return this.translation.translate('notifications.time.days_ago', { count: String(days) });
  }
}
