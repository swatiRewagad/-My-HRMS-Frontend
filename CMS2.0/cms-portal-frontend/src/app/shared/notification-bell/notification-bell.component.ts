import { Component, inject, OnInit, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { BadgeModule } from 'primeng/badge';
import { PopoverModule } from 'primeng/popover'; // ✅ Replaces primeng/overlaypanel
import { Popover } from 'primeng/popover';       // ✅ Optional: If you need explicit typing for #op
import { NotificationService, InAppNotification } from '../../services/notification.service';

@Component({
  selector: 'app-notification-bell',
  standalone: true,
  imports: [
    CommonModule,
    ButtonModule,
    BadgeModule,
    PopoverModule // ✅ Modernized module import
  ],
  template: `
    <div class="notification-container">
      <button 
        pButton 
        type="button"
        icon="pi pi-bell" 
        [text]="true" 
        [rounded]="true" 
        severity="secondary"
        (click)="op.toggle($event)"
        title="Notifications">
        
        @if (notificationService.hasUnread()) {
          <p-badge 
            [value]="notificationService.unreadCount()" 
            severity="danger" 
            styleClass="notification-badge">
          </p-badge>
        }
      </button>

      <!-- ✅ Changed from <p-overlayPanel> to <p-popover> -->
      <p-popover #op styleClass="notification-overlay-panel" (onShow)="onDropdownOpen()" (onHide)="isOpen.set(false)">
        <div class="dropdown-header">
          <h4>Notifications</h4>
          @if (notificationService.hasUnread()) {
            <button pButton [text]="true" label="Mark all read" class="mark-all-btn" (click)="markAllRead()"></button>
          }
        </div>
        
        <div class="dropdown-body">
          @for (n of notificationService.notifications(); track n.id) {
            <div class="notification-item" [class.unread]="!n.isRead" (click)="onNotificationClick(n, op)">
              <div class="notif-icon-wrapper" [attr.data-type]="n.type">
                <i [class]="getIconClass(n.type)"></i>
              </div>
              <div class="notif-content">
                <p class="notif-title">{{ n.title }}</p>
                <span class="notif-type-badge">{{ formatType(n.type) }}</span>
                <p class="notif-message" [title]="n.message">{{ n.message }}</p>
                <span class="notif-time">{{ formatTime(n.createdAt) }}</span>
              </div>
            </div>
          } @empty {
            <div class="empty-state">
              <i class="pi pi-bell-slash"></i>
              <p>No notifications</p>
            </div>
          }
        </div>
      </p-popover>
    </div>
  `,
  styles: [`
    :host { display: inline-block; }
    .notification-container { position: relative; display: flex; align-items: center; }
    ::ng-deep .notification-badge { position: absolute; top: 2px; right: 2px; font-size: 0.65rem; min-width: 1.15rem; height: 1.15rem; line-height: 1.15rem; padding: 0; }

    // ✅ Target global class name structural shift from .p-overlaypanel to .p-popover
    ::ng-deep .notification-overlay-panel.p-popover {
      width: 360px !important;
      padding: 0 !important;
      background-color: var(--p-content-background);
      border: 1px solid var(--p-content-border-color);
      box-shadow: var(--p-overlay-navigation-shadow);
      border-radius: var(--p-content-border-radius);
      overflow: hidden;

      .p-popover-content { // ✅ Inner selector name change matching PrimeNG 21
        padding: 0 !important;
      }
    }

    .dropdown-header { display: flex; align-items: center; justify-content: space-between; padding: 1rem; border-bottom: 1px solid var(--p-content-border-color); background-color: var(--p-content-background); }
    .dropdown-header h4 { margin: 0; font-size: 1rem; font-weight: 700; color: var(--p-text-color); }
    .dropdown-header .mark-all-btn { padding: 0.25rem 0.5rem; font-size: 0.8125rem; font-weight: 600; }
    .dropdown-body { overflow-y: auto; max-height: 400px; background-color: var(--p-content-background); }
    .notification-item { display: flex; gap: 0.875rem; padding: 1rem; cursor: pointer; border-bottom: 1px solid var(--p-content-border-color); transition: background-color 0.15s ease; }
    .notification-item:last-child { border-bottom: none; }
    .notification-item:hover { background-color: var(--p-content-hover-background); }
    .notification-item.unread { background-color: rgba(var(--p-primary-50-rgb, 59, 130, 246), 0.08); }
    .notification-item.unread .notif-title { font-weight: 700; }
    .notif-icon-wrapper { width: 2.25rem; height: 2.25rem; border-radius: 50%; background-color: var(--p-content-hover-background); display: flex; align-items: center; justify-content: center; flex-shrink: 0; border: 1px solid var(--p-content-border-color); }
    .notif-icon-wrapper i { font-size: 1rem; color: var(--p-primary-color); }
    .notif-icon-wrapper[data-type*="CLOSED"] i, .notif-icon-wrapper[data-type*="COMPLI"] i { color: var(--p-emerald-500, #10b981); }
    .notif-icon-wrapper[data-type*="DUPLICATE"] i, .notif-icon-wrapper[data-type*="STALE"] i { color: var(--p-red-500, #ef4444); }
    .notif-icon-wrapper[data-type*="PENDING"] i { color: var(--p-orange-500, #f97316); }
    .notif-content { flex: 1; min-width: 0; }
    .notif-title { margin: 0; font-size: 0.8125rem; font-weight: 600; color: var(--p-text-color); line-height: 1.3; }
    .notif-type-badge { display: inline-block; margin-top: 0.25rem; padding: 0.125rem 0.375rem; font-size: 0.625rem; font-weight: 600; color: var(--p-text-muted-color); background-color: var(--p-content-hover-background); border: 1px solid var(--p-content-border-color); border-radius: 4px; text-transform: capitalize; }
    .notif-message { margin: 0.375rem 0 0; font-size: 0.75rem; color: var(--p-text-muted-color); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .notif-time { display: block; margin-top: 0.25rem; font-size: 0.6875rem; color: var(--p-text-muted-color); }
    .empty-state { padding: 3rem 1rem; text-align: center; color: var(--p-text-muted-color); display: flex; flex-direction: column; align-items: center; gap: 0.5rem; }
    .empty-state i { font-size: 2rem; }
    .empty-state p { margin: 0; font-size: 0.875rem; }
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

  onDropdownOpen(): void {
    this.isOpen.set(true);
    this.notificationService.getUnread().subscribe();
  }

  markAllRead(): void {
    this.notificationService.markAllRead().subscribe();
  }

  // ✅ Adjusted the type parameter to clear any internal explicit layout errors
  onNotificationClick(n: InAppNotification, popover: Popover): void {
    if (!n.isRead) {
      this.notificationService.markRead([n.id]).subscribe();
    }
    if (n.actionUrl) {
      this.router.navigateByUrl(n.actionUrl);
    }
    popover.hide();
  }

  getIconClass(type: string): string {
    const classMap: Record<string, string> = {
      ASSIGNMENT: 'pi pi-user-plus',
      REASSIGNMENT: 'pi pi-user-edit',
      TRANSFER_IN: 'pi pi-arrow-right-arrow-left',
      TRANSFER_PENDING: 'pi pi-hourglass',
      PENDING_3DAY: 'pi pi-clock',
      PENDING_5DAY: 'pi pi-stopwatch',
      DUPLICATE_DETECTED: 'pi pi-exclamation-triangle',
      SENT_BACK: 'pi pi-reply',
      BULK_CLOSE: 'pi pi-folder-open',
      ON_LEAVE_PENDING: 'pi pi-calendar-minus',
      NO_RECORD_ASSIGNED: 'pi pi-file',
      NO_REASSIGNED_TO_RBI: 'pi pi-building',
      NO_STATUS_STALE: 'pi pi-exclamation-circle',
      RE_RESPONSE: 'pi pi-comments',
      RE_UPDATE: 'pi pi-sync',
      COMPLAINT_CLOSED: 'pi pi-check-circle',
      MEETING_SCHEDULED: 'pi pi-calendar',
      AWARD_PASSED: 'pi pi-shield',
      DECISION: 'pi pi-sliders-h',
      ADVISORY_COMPLIED: 'pi pi-verified',
      DOCUMENT_UPLOADED: 'pi pi-cloud-upload',
      UPLOAD_LINK_SENT: 'pi pi-link',
      CRPC_TOLL_FREE_REMINDER: 'pi pi-phone',
      RIA_LEGAL_UPDATE: 'pi pi-info-circle',
    };
    return classMap[type] || 'pi pi-bell';
  }

  formatType(type: string): string {
    return type.replace(/_/g, ' ').toLowerCase();
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
