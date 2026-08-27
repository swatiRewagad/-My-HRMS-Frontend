import { Component, Input, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-cepc-sla-indicator',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cepc-sla-indicator.component.html',
  styleUrl: './cepc-sla-indicator.component.scss'
})
export class CepcSlaIndicatorComponent {
  @Input() set slaDueDate(value: string) { this._slaDueDate.set(value); }
  @Input() set status(value: string) { this._status.set(value); }
  @Input() showEscalationLevel = true;

  private _slaDueDate = signal<string>('');
  private _status = signal<string>('');

  private terminalStates = ['closed', 'resolved', 'rejected', 'withdrawn'];

  isTerminal = computed(() => {
    const s = (this._status() || '').toLowerCase();
    return this.terminalStates.includes(s);
  });

  daysRemaining = computed(() => {
    if (this.isTerminal() || !this._slaDueDate()) return null;
    const due = new Date(this._slaDueDate());
    const now = new Date();
    const diffMs = due.getTime() - now.getTime();
    return Math.ceil(diffMs / (1000 * 60 * 60 * 24));
  });

  isOverdue = computed(() => {
    const days = this.daysRemaining();
    return days !== null && days < 0;
  });

  daysOverdue = computed(() => {
    const days = this.daysRemaining();
    if (days === null || days >= 0) return 0;
    return Math.abs(days);
  });

  escalationLevel = computed<number>(() => {
    const overdue = this.daysOverdue();
    if (overdue <= 0) return 0;
    if (overdue <= 2) return 1;
    if (overdue <= 5) return 2;
    return 3;
  });

  escalationLabel = computed<string>(() => {
    const level = this.escalationLevel();
    if (level === 0) return '';
    if (level === 1) return 'L1';
    if (level === 2) return 'L2';
    return 'L3';
  });

  escalationText = computed<string>(() => {
    const level = this.escalationLevel();
    if (level === 0) return '';
    if (level === 1) return 'Escalated to DO (L1)';
    if (level === 2) return 'Escalated to Incharge (L2)';
    return 'Escalated to Closing Authority (L3)';
  });

  colorClass = computed(() => {
    if (this.isTerminal()) return 'terminal';
    const days = this.daysRemaining();
    if (days === null) return 'unknown';
    if (days < 0) return 'red';
    if (days < 2) return 'orange';
    if (days <= 5) return 'yellow';
    return 'green';
  });

  progressPercent = computed(() => {
    if (this.isTerminal()) return 100;
    const days = this.daysRemaining();
    if (days === null) return 0;
    // Assume 30-day SLA window for progress bar
    const totalDays = 30;
    const elapsed = totalDays - days;
    return Math.min(100, Math.max(0, (elapsed / totalDays) * 100));
  });

  daysText = computed(() => {
    if (this.isTerminal()) return 'Completed';
    const days = this.daysRemaining();
    if (days === null) return '—';
    if (days < 0) return `${Math.abs(days)} day${Math.abs(days) !== 1 ? 's' : ''} overdue`;
    if (days === 0) return 'Due today';
    return `${days} day${days !== 1 ? 's' : ''} remaining`;
  });
}
