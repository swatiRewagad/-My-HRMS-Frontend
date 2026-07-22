import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { EmailSyndicationService } from '../../../services/email-syndication.service';
import { EmailDraft, EmailDraftUpdateRequest, DeoUser } from '../../../models/email-syndication.model';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { highlightEmailText, escapeHtml } from '../../../utils/highlight-text.util';

@Component({
  selector: 'app-draft-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './draft-detail.component.html',
  styleUrl: './draft-detail.component.scss'
})
export class DraftDetailComponent implements OnInit {

  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private emailService = inject(EmailSyndicationService);

  draft = signal<EmailDraft | null>(null);
  deos = signal<DeoUser[]>([]);
  loading = signal(false);
  saving = signal(false);
  error = signal('');
  success = signal('');

  form = signal<EmailDraftUpdateRequest>({});
  showReassign = signal(false);
  selectedDeo = signal('');

  focusedFieldValue = signal<string>('');

  highlightedEmailBody = computed(() => {
    const d = this.draft();
    if (!d?.body) return '';
    const fieldVal = this.focusedFieldValue();
    if (!fieldVal) return escapeHtml(d.body);
    return highlightEmailText(d.body, fieldVal);
  });

  isEmailDraft = computed(() => {
    const d = this.draft();
    return !!(d?.body);
  });

  onFieldFocus(fieldValue: string | undefined | null) {
    if (!this.isEmailDraft()) return;
    this.focusedFieldValue.set(fieldValue?.trim() || '');
  }

  onFieldBlur() {
    this.focusedFieldValue.set('');
  }

  ngOnInit() {
    const draftId = this.route.snapshot.params['draftId'];
    this.loadDraft(draftId);
    this.loadDeos();
  }

  private loadDraft(draftId: string) {
    this.loading.set(true);
    this.emailService.getDraft(draftId).subscribe({
      next: (data) => {
        this.draft.set(data);
        this.form.set({
          complainantName: data.complainantName || '',
          complainantPhone: data.complainantPhone || '',
          cpgramsNumber: data.cpgramsNumber || '',
          complaintSummary: data.complaintSummary || '',
          category: data.category || '',
          subject: data.subject || ''
        });
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Failed to load draft');
        this.loading.set(false);
      }
    });
  }

  private loadDeos() {
    this.emailService.getDeos().subscribe({
      next: (data) => this.deos.set(data)
    });
  }

  updateField(field: string, value: string) {
    this.form.update(prev => ({ ...prev, [field]: value }));
  }

  saveDraft() {
    const draftId = this.draft()?.draftId;
    if (!draftId) return;

    this.saving.set(true);
    this.error.set('');
    this.emailService.updateDraft(draftId, this.form()).subscribe({
      next: (updated) => {
        this.draft.set(updated);
        this.success.set('Draft saved successfully');
        this.saving.set(false);
        setTimeout(() => this.success.set(''), 3000);
      },
      error: () => {
        this.error.set('Failed to save draft');
        this.saving.set(false);
      }
    });
  }

  assignToDeo() {
    const draftId = this.draft()?.draftId;
    const targetDeo = this.selectedDeo();
    if (!draftId || !targetDeo) return;

    this.saving.set(true);
    this.emailService.reassignDraft(draftId, targetDeo).subscribe({
      next: () => {
        this.success.set(`Assigned to DEO: ${targetDeo}. DEO will assess and route to Reviewer.`);
        this.saving.set(false);
        this.loadDraft(draftId);
      },
      error: () => {
        this.error.set('Assignment failed');
        this.saving.set(false);
      }
    });
  }

  reassignDraft() {
    const draftId = this.draft()?.draftId;
    const targetDeo = this.selectedDeo();
    if (!draftId || !targetDeo) return;

    this.emailService.reassignDraft(draftId, targetDeo).subscribe({
      next: () => {
        this.success.set(`Reassigned to ${targetDeo}`);
        this.showReassign.set(false);
        this.loadDraft(draftId);
      },
      error: () => this.error.set('Reassignment failed')
    });
  }

  goBack() {
    this.router.navigate(['/email-syndication']);
  }

  categories = ['ATM', 'UPI', 'NEFT_RTGS', 'LOAN', 'CREDIT_CARD', 'DEPOSIT', 'INSURANCE', 'GENERAL'];
}
