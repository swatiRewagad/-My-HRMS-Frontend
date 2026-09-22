import { Component, Input, Output, EventEmitter, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';
import { TranslatePipe } from '../../../pipes/translate.pipe';

type OrderOutcome = 'UPHELD' | 'MODIFIED' | 'SET_ASIDE' | 'REMANDED' | 'DISMISSED';

/**
 * Issues the final order on an appeal.
 *
 * Posts to the dedicated order endpoint rather than the generic /action route. That matters for more
 * than tidiness: the order endpoint validates the outcome against the five permitted values, persists an
 * immutable order record with a revision number, and returns the stored order — whereas /action accepted
 * any string as an outcome and stored it unvalidated.
 *
 * The previous version sent `outcome`/`modifiedAmount` while /action read `orderOutcome`/
 * `awardModifiedAmount`, so every submission was rejected. Worse, the rejection arrived as HTTP 200 with
 * success:false, which Angular does not treat as an error — so this screen reported "Order passed
 * successfully" for orders the server had thrown away. Both are fixed here.
 */
@Component({
  selector: 'app-aa-order',
  standalone: true,
  imports: [CommonModule, FormsModule, TranslatePipe],
  templateUrl: './aa-order.component.html',
  styleUrl: './aa-order.component.scss'
})
export class AaOrderComponent {
  @Input() appeal: any;
  @Output() orderPassed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  private http = inject(HttpClient);

  submitting = signal(false);
  errorKey = signal('');
  successKey = signal('');
  showPreview = signal(false);

  outcome: OrderOutcome | '' = '';
  awardAmount: number | null = null;
  orderSummary = '';

  /** Labels are translation keys; the values are the server's vocabulary and must not be localised. */
  outcomes: { value: OrderOutcome; labelKey: string; descriptionKey: string }[] = [
    { value: 'UPHELD', labelKey: 'aa.order.outcome_upheld', descriptionKey: 'aa.order.outcome_upheld_desc' },
    { value: 'MODIFIED', labelKey: 'aa.order.outcome_modified', descriptionKey: 'aa.order.outcome_modified_desc' },
    { value: 'SET_ASIDE', labelKey: 'aa.order.outcome_set_aside', descriptionKey: 'aa.order.outcome_set_aside_desc' },
    { value: 'REMANDED', labelKey: 'aa.order.outcome_remanded', descriptionKey: 'aa.order.outcome_remanded_desc' },
    { value: 'DISMISSED', labelKey: 'aa.order.outcome_dismissed', descriptionKey: 'aa.order.outcome_dismissed_desc' },
  ];

  /**
   * Outcomes that can carry a monetary award.
   *
   * Mirrors the server's own rule: an award submitted with any other outcome is silently dropped, so
   * offering the field would invite an officer to enter a figure that never gets stored.
   */
  get awardBearing(): boolean {
    return this.outcome === 'MODIFIED' || this.outcome === 'UPHELD';
  }

  previewOrder() {
    this.errorKey.set('');
    if (!this.outcome) {
      this.errorKey.set('aa.order.error_outcome_required');
      return;
    }
    if (!this.orderSummary.trim()) {
      this.errorKey.set('aa.order.error_summary_required');
      return;
    }
    if (this.awardBearing && this.awardAmount !== null && this.awardAmount < 0) {
      this.errorKey.set('aa.order.error_amount_invalid');
      return;
    }
    this.showPreview.set(true);
  }

  submitOrder() {
    this.errorKey.set('');
    this.submitting.set(true);

    const appealNumber = this.appeal?.appealNumber;
    const body: Record<string, unknown> = {
      outcome: this.outcome,
      orderSummary: this.orderSummary,
    };
    // Only sent when the outcome can actually carry one.
    if (this.awardBearing && this.awardAmount !== null) {
      body['awardAmount'] = this.awardAmount;
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/order`,
      body
    ).subscribe({
      next: (res) => {
        this.submitting.set(false);

        // A refused write arrives as 200 with success:false. Reporting it as success is how a passed
        // order that was never stored looked like a completed one.
        if (res?.success === false) {
          this.errorKey.set(res.messageKey || 'aa.order.error_failed');
          this.showPreview.set(false);
          return;
        }

        this.successKey.set(res?.messageKey || 'aa.order.passed');
        setTimeout(() => this.orderPassed.emit(), 1200);
      },
      error: (err) => {
        this.submitting.set(false);
        this.showPreview.set(false);
        this.errorKey.set(err.error?.messageKey || err.error?.message || 'aa.order.error_failed');
      }
    });
  }

  cancel() {
    this.cancelled.emit();
  }
}
