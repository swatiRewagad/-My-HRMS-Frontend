import { Component, inject, input, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import {
  ComplaintQueryService, QueryThread, QueryMessage, ChecklistItem, MeetingSlot,
  QueryType, SlotInput
} from '../../../services/complaint-query.service';

/**
 * Query/correspondence thread panel shared by the RE portal and the RBI side
 * (UST853, UST854, UST855, UST856, UST857).
 *
 * The panel renders what the server says: `awaitingMe`, `unread` and the extension decision all
 * come from the API rather than being inferred here, so the browser cannot disagree with the
 * server about who owes a reply.
 */
@Component({
  selector: 'app-query-thread',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './query-thread.component.html',
  styleUrl: './query-thread.component.scss'
})
export class QueryThreadComponent implements OnInit {
  private queryService = inject(ComplaintQueryService);

  complaintNumber = input.required<string>();
  /** RE for the entity portal, RBI for CEPC/RBIOS. Controls which actions are offered. */
  side = input<'RE' | 'RBI'>('RE');
  entityCode = input<string | null>(null);

  loading = signal(true);
  threads = signal<QueryThread[]>([]);
  error = signal('');

  expandedId = signal<number | null>(null);
  messages = signal<QueryMessage[]>([]);
  checklist = signal<ChecklistItem[]>([]);
  slots = signal<MeetingSlot[]>([]);
  threadLoading = signal(false);

  replyText = signal('');
  posting = signal(false);

  showComposer = signal(false);
  newType = signal<QueryType>('CLARIFICATION');
  newSubject = signal('');
  newBody = signal('');
  proposedDeadline = signal('');
  extensionReason = signal('');
  meetingPurpose = signal('');
  slotInputs = signal<string[]>(['']);
  checklistInputs = signal<string[]>(['']);

  declineReason = signal('');
  decisionReason = signal('');
  grantedDeadline = signal('');

  awaitingOnly = signal(false);

  visibleThreads = computed(() =>
    this.awaitingOnly() ? this.threads().filter(t => t.awaitingMe) : this.threads());

  awaitingCount = computed(() => this.threads().filter(t => t.awaitingMe).length);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.queryService.listThreads(this.complaintNumber(), this.entityCode()).subscribe({
      next: res => {
        this.threads.set(res.threads ?? []);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('query.error.load_failed');
        this.loading.set(false);
      }
    });
  }

  toggleThread(thread: QueryThread): void {
    if (this.expandedId() === thread.id) {
      this.expandedId.set(null);
      return;
    }
    this.expandedId.set(thread.id);
    this.threadLoading.set(true);
    this.queryService.getThread(thread.id, this.entityCode()).subscribe({
      next: res => {
        this.messages.set(res.messages ?? []);
        this.checklist.set(res.checklist ?? []);
        this.slots.set(res.slots ?? []);
        this.threadLoading.set(false);
        // The server cleared the unread flag when it served the thread; mirror that locally so the
        // badge disappears without a second round trip.
        this.threads.update(list =>
          list.map(t => (t.id === thread.id ? { ...t, unread: false } : t)));
      },
      error: () => {
        this.error.set('query.error.load_failed');
        this.threadLoading.set(false);
      }
    });
  }

  postReply(threadId: number): void {
    if (!this.replyText().trim()) {
      this.error.set('query.error.body_required');
      return;
    }
    this.posting.set(true);
    this.queryService.reply(threadId, this.replyText(), this.entityCode()).subscribe({
      next: () => {
        this.replyText.set('');
        this.posting.set(false);
        this.reloadThread(threadId);
        this.load();
      },
      error: err => {
        this.posting.set(false);
        this.error.set(err.error?.message || 'query.error.send_failed');
      }
    });
  }

  submitNewThread(): void {
    if (!this.newSubject().trim()) {
      this.error.set('query.error.subject_required');
      return;
    }
    if (!this.newBody().trim()) {
      this.error.set('query.error.body_required');
      return;
    }

    const payload: Record<string, unknown> = {
      queryType: this.newType(),
      subject: this.newSubject(),
      body: this.newBody()
    };

    if (this.newType() === 'EXTENSION_REQUEST') {
      payload['proposedDeadline'] = this.proposedDeadline();
      payload['extensionReason'] = this.extensionReason();
    }
    if (this.newType() === 'MEETING_REQUEST') {
      payload['meetingPurpose'] = this.meetingPurpose();
      payload['proposedSlots'] = this.slotInputs()
        .filter(s => s.trim())
        .map<SlotInput>(s => ({ start: s }));
    }
    if (this.newType() === 'DOCUMENT_REQUEST') {
      payload['checklistItems'] = this.checklistInputs()
        .filter(s => s.trim())
        .map(label => ({ label }));
    }

    this.posting.set(true);
    this.queryService.raiseThread(this.complaintNumber(), payload, this.entityCode()).subscribe({
      next: () => {
        this.posting.set(false);
        this.resetComposer();
        this.load();
      },
      error: err => {
        this.posting.set(false);
        this.error.set(err.error?.message || 'query.error.send_failed');
      }
    });
  }

  decideExtension(threadId: number, approve: boolean): void {
    this.posting.set(true);
    this.queryService.decideExtension(
      threadId, approve,
      this.grantedDeadline() || null,
      this.decisionReason() || null
    ).subscribe({
      next: () => {
        this.posting.set(false);
        this.decisionReason.set('');
        this.grantedDeadline.set('');
        this.reloadThread(threadId);
        this.load();
      },
      error: err => {
        this.posting.set(false);
        this.error.set(err.error?.message || 'query.error.send_failed');
      }
    });
  }

  acceptSlot(threadId: number, slotId: number): void {
    this.posting.set(true);
    this.queryService.respondToMeeting(threadId, 'ACCEPT', { acceptedSlotId: slotId }, this.entityCode())
      .subscribe({
        next: () => {
          this.posting.set(false);
          this.reloadThread(threadId);
          this.load();
        },
        error: err => {
          this.posting.set(false);
          this.error.set(err.error?.message || 'query.error.send_failed');
        }
      });
  }

  declineMeeting(threadId: number): void {
    if (!this.declineReason().trim()) {
      this.error.set('query.meeting.decline_reason_required');
      return;
    }
    this.posting.set(true);
    this.queryService.respondToMeeting(
      threadId, 'DECLINE', { declineReason: this.declineReason() }, this.entityCode()
    ).subscribe({
      next: () => {
        this.posting.set(false);
        this.declineReason.set('');
        this.reloadThread(threadId);
        this.load();
      },
      error: err => {
        this.posting.set(false);
        this.error.set(err.error?.message || 'query.error.send_failed');
      }
    });
  }

  private reloadThread(threadId: number): void {
    this.queryService.getThread(threadId, this.entityCode()).subscribe({
      next: res => {
        this.messages.set(res.messages ?? []);
        this.checklist.set(res.checklist ?? []);
        this.slots.set(res.slots ?? []);
      }
    });
  }

  addSlotInput(): void {
    this.slotInputs.update(list => [...list, '']);
  }

  updateSlotInput(index: number, value: string): void {
    this.slotInputs.update(list => list.map((v, i) => (i === index ? value : v)));
  }

  addChecklistInput(): void {
    this.checklistInputs.update(list => [...list, '']);
  }

  updateChecklistInput(index: number, value: string): void {
    this.checklistInputs.update(list => list.map((v, i) => (i === index ? value : v)));
  }

  typeLabelKey(type: QueryType): string {
    return `query.type.${type.toLowerCase()}`;
  }

  outstandingCount(): number {
    return this.checklist().filter(i => !i.resolved).length;
  }

  proposedSlots(): MeetingSlot[] {
    return this.slots().filter(s => s.slotStatus === 'PROPOSED');
  }

  private resetComposer(): void {
    this.showComposer.set(false);
    this.newSubject.set('');
    this.newBody.set('');
    this.proposedDeadline.set('');
    this.extensionReason.set('');
    this.meetingPurpose.set('');
    this.slotInputs.set(['']);
    this.checklistInputs.set(['']);
    this.error.set('');
  }
}
