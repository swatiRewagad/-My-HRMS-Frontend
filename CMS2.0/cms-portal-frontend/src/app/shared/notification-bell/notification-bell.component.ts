import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { NotificationService, InAppNotification } from '../../services/notification.service';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div class="notification-bell" (click)="toggleDropdown()">
      <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"
           stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/>
        <path d="M13.73 21a2 2 0 0 1-3.46 0"/>
      </svg>
      @if (notificationService.hasUnread()) {
        <span class="badge">{{ notificationService.unreadCount() }}</span>
      }
    </div>

    @if (isOpen()) {
      <div class="dropdown-overlay" (click)="isOpen.set(false)"></div>
      <div class="dropdown">
        <div class="dropdown-header">
          <h4>Notifications</h4>
          @if (notificationService.hasUnread()) {
            <button class="mark-all-btn" (click)="markAllRead()">Mark all read</button>
          }
        </div>
        <div class="dropdown-body">
          @for (n of notificationService.notifications(); track n.id) {
            <div class="notification-item" [class.unread]="!n.isRead" (click)="onNotificationClick(n)">
              <div class="notif-icon" [attr.data-type]="n.type">
                {{ getIcon(n.type) }}
              </div>
              <div class="notif-content">
                <p class="notif-title">{{ n.title }}</p>
                <p class="notif-message">{{ n.message }}</p>
                <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
              </div>
            </div>
          } @empty {
            <div class="empty-state">No notifications</div>
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
    .dropdown-body { overflow-y: auto; max-height: 400px; }
    .notification-item { display: flex; gap: 12px; padding: 12px 16px; cursor: pointer; transition: background 0.15s; }
    .notification-item:hover { background: #f8fafc; }
    .notification-item.unread { background: #eff6ff; }
    .notif-icon { width: 36px; height: 36px; border-radius: 50%; background: #e2e8f0; display: flex;
                  align-items: center; justify-content: center; font-size: 16px; flex-shrink: 0; }
    .notif-content { flex: 1; min-width: 0; }
    .notif-title { margin: 0; font-size: 13px; font-weight: 600; color: #1e293b; }
    .notif-message { margin: 2px 0 0; font-size: 12px; color: #64748b; overflow: hidden;
                     text-overflow: ellipsis; white-space: nowrap; }
    .notif-time { font-size: 11px; color: #94a3b8; }
    .empty-state { padding: 40px 16px; text-align: center; color: #94a3b8; }
  `]
})
export class NotificationBellComponent implements OnInit, OnDestroy {
  readonly notificationService = inject(NotificationService);
  private router = inject(Router);

  isOpen = signal(false);

  ngOnInit(): void {
    this.notificationService.loadUnreadCount();
    this.notificationService.getUnread().subscribe();
  }

  ngOnDestroy(): void {
    this.notificationService.disconnectWebSocket();
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
      ASSIGNMENT: '\u{1F4CB}',
      TRANSFER_PENDING: '\u{1F504}',
      PENDING_3DAY: '\u{23F0}',
      DUPLICATE_ACTIVITY: '\u{1F4CB}',
      SENT_BACK: '\u{21A9}\uFE0F',
      BULK_CLOSE: '\u{2705}',
    };
    return icons[type] || '\u{1F514}';
  }

  formatTime(dateStr: string): string {
    const date = new Date(dateStr);
    const now = new Date();
    const diff = now.getTime() - date.getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'Just now';
    if (minutes < 60) return `${minutes}m ago`;
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return `${hours}h ago`;
    const days = Math.floor(hours / 24);
    return `${days}d ago`;
  }
}
