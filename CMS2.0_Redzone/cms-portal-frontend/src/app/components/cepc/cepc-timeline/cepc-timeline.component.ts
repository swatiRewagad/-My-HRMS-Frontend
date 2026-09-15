import { Component, Input, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface TimelineEntry {
  action: string;
  fromStatus: string;
  toStatus: string;
  timestamp: string;
  remarks: string;
  performedBy?: string;
  documents?: { id: string; name: string; url?: string }[];
}

@Component({
  selector: 'app-cepc-timeline',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './cepc-timeline.component.html',
  styleUrl: './cepc-timeline.component.scss'
})
export class CepcTimelineComponent implements OnInit, OnDestroy {
  @Input() complaintNumber: string = '';

  private http = inject(HttpClient);
  private refreshInterval: any = null;

  entries = signal<TimelineEntry[]>([]);
  loading = signal(true);
  expandedIndex = signal<number | null>(null);

  ngOnInit() {
    if (this.complaintNumber) {
      this.loadTimeline();
      // Auto-refresh every 30 seconds
      this.refreshInterval = setInterval(() => this.loadTimeline(), 30000);
    }
  }

  ngOnDestroy() {
    if (this.refreshInterval) {
      clearInterval(this.refreshInterval);
    }
  }

  loadTimeline() {
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/timeline`).subscribe({
      next: (res) => {
        this.entries.set(res?.data || []);
        this.loading.set(false);
      },
      error: () => {
        this.entries.set([]);
        this.loading.set(false);
      }
    });
  }

  toggleExpand(index: number) {
    this.expandedIndex.set(this.expandedIndex() === index ? null : index);
  }

  getActionIcon(action: string): string {
    const icons: Record<string, string> = {
      'ACCEPT': '\u2705',
      'SUBMIT_FOR_REVIEW': '\u{1F4E4}',
      'APPROVE_REVIEW': '\u2714\uFE0F',
      'SEND_BACK_DO': '\u21A9\uFE0F',
      'SEND_BACK_REVIEWER': '\u21A9\uFE0F',
      'SEND_BACK_INCHARGE': '\u21A9\uFE0F',
      'CLOSE_COMPLAINT': '\u{1F512}',
      'ESCALATE': '\u26A0\uFE0F',
      'REASSIGN': '\u{1F501}',
      'REQUEST_INFO': '\u2753',
      'INFO_RECEIVED': '\u{1F4E5}',
      'FORWARD_DEPT': '\u27A1\uFE0F',
      'FORWARD_TO_CONTACT': '\u{1F4E8}',
      'CONTACT_RESPONSE': '\u{1F4AC}',
      'REOPEN': '\u{1F504}',
      'CONCILIATION_SUCCESS': '\u{1F91D}',
      'CONCILIATION_FAILED': '\u274C',
    };
    return icons[action] || '\u{1F4CB}';
  }

  getActionLabel(action: string): string {
    const labels: Record<string, string> = {
      'ACCEPT': 'Accepted & Started Examination',
      'SUBMIT_FOR_REVIEW': 'Forwarded to Reviewer',
      'APPROVE_REVIEW': 'Forwarded to In-Charge',
      'APPROVE_CLOSURE': 'Approved for Closure',
      'FORWARD_TO_CLOSING_AUTHORITY': 'Forwarded to Closing Authority',
      'SEND_BACK_DO': 'Sent Back to Dealing Officer',
      'SEND_BACK_REVIEWER': 'Sent Back to Reviewer',
      'SEND_BACK_INCHARGE': 'Sent Back to In-Charge',
      'CLOSE_COMPLAINT': 'Complaint Closed',
      'ESCALATE': 'Escalated',
      'REASSIGN': 'Reassigned',
      'REQUEST_INFO': 'Information Requested',
      'INFO_RECEIVED': 'Information Received',
      'FORWARD_DEPT': 'Forwarded to Department',
      'FORWARD_TO_CONTACT': 'Forwarded to Contact Person',
      'CONTACT_RESPONSE': 'Contact Person Response',
      'SCHEDULE_MEETING': 'Meeting Scheduled',
      'REOPEN': 'Complaint Reopened',
      'CONCILIATION_SUCCESS': 'Conciliation Settled',
      'CONCILIATION_FAILED': 'Conciliation Failed',
    };
    return labels[action] || action;
  }
}
