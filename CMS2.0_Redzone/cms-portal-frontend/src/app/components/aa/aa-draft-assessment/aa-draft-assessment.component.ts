import { Component, OnInit, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';

/** A related draft the server proposes, with its similarity score. */
interface SuggestedRelated {
  draftId: string;
  subject: string;
  category: string | null;
  status: string;
  parentComplaintId: string | null;
  convertedComplaintId: string | null;
  /** 0.0-1.0. Similarity, NOT a duplicate verdict. */
  similarityScore: number;
}

interface DraftAttachment {
  /** A STRING of the form "ATT-<id>", not a number. */
  id: string;
  fileName: string;
  fileType: string | null;
  fileSize: number | null;
  ocrText: string | null;
  ocrConfidence: number | null;
}

/**
 * Lets an AA Dealing Officer assess an inbound email/letter draft before it is routed onward.
 *
 * Replaces a placeholder that had no HTTP calls at all. It renders only what the intake API genuinely
 * exposes; several things a screen like this would want do not exist server-side and are surfaced as
 * visible limitations rather than mocked:
 *   - OCR confidence is ONE draft-level integer (0-100), not per-field. There is no per-field score to
 *     render, so the UI must not imply one.
 *   - `suggestedRelated` is same-sender SUBJECT SIMILARITY, not deduplication. Dedup runs only at ingest
 *     and exposes nothing queryable, so these are shown as "possibly related", never as confirmed
 *     duplicates.
 *   - There is no reject endpoint and no draft-to-APPEAL conversion. Routing a draft onward creates a
 *     COMPLAINT, so this screen says "route onward" rather than claiming to register an appeal.
 */
@Component({
  selector: 'app-aa-draft-assessment',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './aa-draft-assessment.component.html',
  styleUrl: './aa-draft-assessment.component.scss'
})
export class AaDraftAssessmentComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  private readonly base = `${environment.apiBaseUrl}/api/v1/email-syndication`;

  draft = signal<any>(null);
  loading = signal(true);
  saving = signal(false);
  errorKey = signal('');
  noticeKey = signal('');

  // Editable assessment fields. Only those the update endpoint actually accepts.
  complainantName = '';
  complainantPhone = '';
  category = '';
  entityName = '';
  complaintSummary = '';
  remarks = '';

  attachments = computed<DraftAttachment[]>(() => this.draft()?.attachments ?? []);
  suggestedRelated = computed<SuggestedRelated[]>(() => this.draft()?.suggestedRelated ?? []);

  /** Extracted OCR fields as name/value pairs. Absent unless OCR actually ran. */
  extractedFields = computed<{ key: string; value: string }[]>(() => {
    const fields = this.draft()?.ocrExtractedFields;
    if (!fields || typeof fields !== 'object') {
      return [];
    }
    return Object.entries(fields).map(([key, value]) => ({ key, value: String(value) }));
  });

  /**
   * Whether the OCR result is weak enough to warrant checking every field by hand.
   *
   * 70 mirrors the server's own prefill threshold. It is duplicated here only to colour a badge; the
   * server stays the authority on whether manual entry is REQUIRED, and `requiresManualEntry` is
   * rendered directly rather than inferred from the score.
   */
  ocrLowConfidence = computed(() => {
    const d = this.draft();
    return d?.ocrProcessed === true && (d?.ocrConfidence ?? 0) < 70;
  });

  ocrStatusKey = computed(() => {
    const d = this.draft();
    if (!d) return '';
    if (d.requiresManualEntry) return 'aa.draft.ocr_manual_entry_required';
    if (!d.ocrProcessed) return 'aa.draft.ocr_not_run';
    return this.ocrLowConfidence() ? 'aa.draft.ocr_low_confidence' : 'aa.draft.ocr_ok';
  });

  /** Terminal draft states: nothing further can be assessed. */
  readonly = computed(() => {
    const status = this.draft()?.status;
    return status === 'CONVERTED' || status === 'APPROVED_ROUTED' || status === 'REJECTED';
  });

  async ngOnInit(): Promise<void> {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }
    const draftId = this.route.snapshot.params['draftId'];
    this.load(draftId);
  }

  private load(draftId: string): void {
    this.loading.set(true);
    this.http.get<any>(`${this.base}/drafts/${draftId}`).subscribe({
      next: (res) => {
        const data = res?.data;
        // This endpoint answers HTTP 200 with {error: "..."} for a draft that does not exist, so a
        // missing record must be detected from the body, not the status code.
        if (!data || data.error) {
          this.draft.set(null);
          this.errorKey.set('aa.draft.error_not_found');
          this.loading.set(false);
          return;
        }
        this.draft.set(data);
        this.complainantName = data.complainantName ?? '';
        this.complainantPhone = data.complainantPhone ?? '';
        this.category = data.category ?? '';
        this.entityName = data.entityName ?? '';
        this.complaintSummary = data.complaintSummary ?? '';
        this.loading.set(false);
      },
      error: () => {
        // No mock fallback: a screen that looks like a real draft but is not would be worse than an error.
        this.draft.set(null);
        this.errorKey.set('aa.draft.error_load_failed');
        this.loading.set(false);
      }
    });
  }

  /** Saves the assessed fields without changing the draft's disposition. */
  saveAssessment(): void {
    this.submit({
      complainantName: this.complainantName,
      complainantPhone: this.complainantPhone,
      category: this.category,
      entityName: this.entityName,
      complaintSummary: this.complaintSummary,
      deoRemarks: this.remarks,
      status: 'IN_PROGRESS',
    }, 'aa.draft.saved', 'aa.draft.error_save_failed');
  }

  /**
   * Routes the draft onward for registration.
   *
   * Called "route onward", not "register appeal", because that is what the server does: it creates a
   * COMPLAINT and marks the draft APPROVED_ROUTED. There is no draft-to-appeal endpoint, so a label
   * promising one would be a claim the backend cannot honour.
   */
  routeOnward(): void {
    if (!this.remarks.trim()) {
      this.errorKey.set('aa.draft.error_remarks_required');
      return;
    }
    this.submit({
      complainantName: this.complainantName,
      complainantPhone: this.complainantPhone,
      category: this.category,
      entityName: this.entityName,
      complaintSummary: this.complaintSummary,
      deoRemarks: this.remarks,
      deoDecision: 'MAINTAINABLE',
      status: 'APPROVED_ROUTED',
    }, 'aa.draft.routed', 'aa.draft.error_route_failed');
  }

  /**
   * Marks the draft not maintainable.
   *
   * There is NO reject endpoint, so this writes the REJECTED status through the generic update. The
   * server validates nothing on that transition and records no timeline row — a real gap, and the reason
   * a written reason is enforced here at the very least.
   */
  markNotMaintainable(): void {
    if (!this.remarks.trim()) {
      this.errorKey.set('aa.draft.error_reason_required');
      return;
    }
    this.submit({
      deoRemarks: this.remarks,
      deoDecision: 'NOT_MAINTAINABLE',
      nonMaintainableReason: this.remarks,
      status: 'REJECTED',
    }, 'aa.draft.rejected', 'aa.draft.error_reject_failed');
  }

  /** One update path, so every disposition handles the 200-with-error convention identically. */
  private submit(body: Record<string, unknown>, successKey: string, failureKey: string): void {
    const draftId = this.draft()?.draftId;
    if (!draftId) return;

    this.saving.set(true);
    this.errorKey.set('');
    this.http.put<any>(`${this.base}/drafts/${draftId}`, body).subscribe({
      next: (res) => {
        this.saving.set(false);
        if (res?.success === false || res?.data?.error) {
          this.errorKey.set(failureKey);
          return;
        }
        this.noticeKey.set(successKey);
        this.draft.set(res?.data ?? this.draft());
      },
      error: () => {
        this.saving.set(false);
        this.errorKey.set(failureKey);
      }
    });
  }

  /** Attachment download. The id is "ATT-<n>"; the file route wants the bare numeric part. */
  attachmentUrl(attachment: DraftAttachment): string {
    const numericId = attachment.id?.replace(/^ATT-/, '') ?? '';
    return `${environment.apiBaseUrl}/api/files/email-draft/${numericId}`;
  }

  dismissMessages(): void {
    this.errorKey.set('');
    this.noticeKey.set('');
  }

  goBack(): void {
    this.router.navigate(['/aa/dashboard']);
  }
}
