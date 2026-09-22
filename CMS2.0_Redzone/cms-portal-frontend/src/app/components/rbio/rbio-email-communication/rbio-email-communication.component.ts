import { Component, Input, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import {
  ComplaintCorrespondenceService, ComplaintEmail
} from '../../../services/complaint-correspondence.service';
import { CommunicationTemplateService } from '../../../services/communication-template.service';

/**
 * The Email Communication tab (UST590-593, UST656).
 *
 * <p>THIS TAB PREVIOUSLY RENDERED NOTHING. It was registered in the RBIO tab list, but the template had
 * no matching block at all — clicking it toggled a CSS class and left the panel blank. There was also no
 * backend: no query read the {@code COMPLAINT_ID} column on SIMULATED_EMAILS, and the only compose UI in
 * the product resolved its "send" with a {@code setTimeout}, persisting nothing.
 *
 * <p>Recipient-domain enforcement is deliberately NOT duplicated here as the authority. The client shows
 * a hint early for usability, but the server refuses at send time (UST656) — a browser-side check is not
 * a control, and the previous implementation's client-side check was the only one that ran.
 */
@Component({
  selector: 'app-rbio-email-communication',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './rbio-email-communication.component.html',
  styleUrl: './rbio-email-communication.component.scss'
})
export class RbioEmailCommunicationComponent implements OnInit {

  @Input() complaintNumber: string | null = null;

  private service = inject(ComplaintCorrespondenceService);
  private templates = inject(CommunicationTemplateService);

  emails = signal<ComplaintEmail[]>([]);
  loading = signal(false);
  error = signal<string | null>(null);

  // ═══ Compose state — every field a signal, so computed() actually re-evaluates ═══
  composing = signal(false);
  sending = signal(false);
  sendError = signal<string | null>(null);
  rejectedRecipients = signal<string[]>([]);

  recipients = signal('');
  subject = signal('');
  body = signal('');
  inReplyToId = signal<number | null>(null);
  selectedTemplateId = signal<number | null>(null);

  /** UST590: template search. A signal because the filtered list is a computed over it. */
  templateSearch = signal('');
  allTemplates = signal<any[]>([]);

  filteredTemplates = computed(() => {
    const q = this.templateSearch().trim().toLowerCase();
    const emailOnly = this.allTemplates().filter(t => (t.mode || '').toUpperCase() === 'EMAIL');
    if (!q) return emailOnly;
    return emailOnly.filter(t =>
      (t.templateName || '').toLowerCase().includes(q) ||
      (t.category || '').toLowerCase().includes(q) ||
      (t.subjectTemplate || '').toLowerCase().includes(q));
  });

  ngOnInit() {
    this.load();
    this.templates.getActive().subscribe({
      next: rows => this.allTemplates.set(rows || []),
      // A template list that fails to load must not break the log below it; compose degrades to
      // free-text, which is still usable.
      error: () => this.allTemplates.set([])
    });
  }

  load() {
    if (!this.complaintNumber) return;
    this.loading.set(true);
    this.error.set(null);

    this.service.getEmails(this.complaintNumber).subscribe({
      next: rows => { this.emails.set(rows); this.loading.set(false); },
      error: () => { this.error.set('email.error_load_failed'); this.loading.set(false); }
    });
  }

  isInbound(e: ComplaintEmail): boolean {
    return (e.direction || '').toUpperCase() === 'INBOUND';
  }

  startCompose() {
    this.resetCompose();
    this.composing.set(true);
  }

  /** UST591: Reply keeps the thread; the server quotes the original into the body. */
  startReply(e: ComplaintEmail) {
    this.resetCompose();
    this.recipients.set(this.isInbound(e) ? e.from : e.to);
    this.subject.set(this.prefix(e.subject, 'Re: '));
    this.inReplyToId.set(e.id);
    this.composing.set(true);
  }

  /** UST591: Reply All adds the CC list alongside the original correspondents. */
  startReplyAll(e: ComplaintEmail) {
    this.startReply(e);
    const extra = [e.to, e.cc].filter(v => !!v).join(', ');
    const combined = [this.recipients(), extra].filter(v => !!v).join(', ');
    this.recipients.set(this.dedupe(combined));
  }

  /** UST591: Forward preserves the thread content but takes a fresh recipient. */
  startForward(e: ComplaintEmail) {
    this.resetCompose();
    this.subject.set(this.prefix(e.subject, 'Fwd: '));
    this.inReplyToId.set(e.id);
    this.composing.set(true);
  }

  applyTemplate(templateId: number) {
    this.selectedTemplateId.set(templateId);
    const chosen = this.allTemplates().find(t => t.id === templateId);
    if (chosen) {
      // Prefilled for visibility, but the SERVER re-renders from the template id, so what is stored is
      // what was actually sent rather than whatever the browser claimed.
      if (!this.subject()) this.subject.set(chosen.subjectTemplate || '');
      if (!this.body()) this.body.set(chosen.bodyTemplate || '');
    }
  }

  send() {
    if (!this.complaintNumber) return;
    const list = this.recipientList();
    if (list.length === 0) {
      this.sendError.set('email.error_no_recipients');
      return;
    }

    this.sending.set(true);
    this.sendError.set(null);
    this.rejectedRecipients.set([]);

    this.service.sendEmail(this.complaintNumber, {
      recipients: list,
      subject: this.subject(),
      body: this.body(),
      templateId: this.selectedTemplateId(),
      inReplyToId: this.inReplyToId()
    }).subscribe({
      next: () => {
        this.sending.set(false);
        this.composing.set(false);
        this.resetCompose();
        this.load();
      },
      error: err => {
        this.sending.set(false);
        // The server's refusal reason is shown as-is, including which recipients were rejected — masked
        // server-side, so this cannot leak an address the officer was not entitled to see.
        this.sendError.set(err?.error?.messageKey || 'email.error_send_failed');
        this.rejectedRecipients.set(err?.error?.rejectedRecipients || []);
      }
    });
  }

  cancelCompose() {
    this.composing.set(false);
    this.resetCompose();
  }

  recipientList(): string[] {
    return this.recipients().split(',').map(r => r.trim()).filter(r => !!r);
  }

  private resetCompose() {
    this.recipients.set('');
    this.subject.set('');
    this.body.set('');
    this.inReplyToId.set(null);
    this.selectedTemplateId.set(null);
    this.templateSearch.set('');
    this.sendError.set(null);
    this.rejectedRecipients.set([]);
  }

  private prefix(subject: string, p: string): string {
    const s = subject || '';
    return s.toLowerCase().startsWith(p.toLowerCase()) ? s : p + s;
  }

  private dedupe(csv: string): string {
    const seen = new Set<string>();
    return csv.split(',').map(v => v.trim()).filter(v => {
      if (!v || seen.has(v.toLowerCase())) return false;
      seen.add(v.toLowerCase());
      return true;
    }).join(', ');
  }
}
