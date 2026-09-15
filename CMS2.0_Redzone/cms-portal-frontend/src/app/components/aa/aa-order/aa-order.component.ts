import { Component, Input, Output, EventEmitter, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

type OrderOutcome = 'UPHELD' | 'MODIFIED' | 'SET_ASIDE' | 'REMANDED' | 'DISMISSED';

@Component({
  selector: 'app-aa-order',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './aa-order.component.html',
  styleUrl: './aa-order.component.scss'
})
export class AaOrderComponent {
  @Input() appeal: any;
  @Output() orderPassed = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  submitting = signal(false);
  error = signal('');
  success = signal('');
  showPreview = signal(false);

  // Form fields
  outcome: OrderOutcome | '' = '';
  modifiedAmount: number | null = null;
  orderSummary = '';

  outcomes: { value: OrderOutcome; label: string; description: string }[] = [
    { value: 'UPHELD', label: 'Upheld', description: 'Appeal upheld - original order stands' },
    { value: 'MODIFIED', label: 'Modified', description: 'Award modified with changes' },
    { value: 'SET_ASIDE', label: 'Set Aside', description: 'Original order set aside' },
    { value: 'REMANDED', label: 'Remanded', description: 'Remanded back for fresh consideration' },
    { value: 'DISMISSED', label: 'Dismissed', description: 'Appeal dismissed - no merit found' },
  ];

  get originalAwardAmount(): string {
    return this.appeal?.originalAwardAmount ? '\u20B9' + this.appeal.originalAwardAmount : 'Not specified';
  }

  get isModified(): boolean {
    return this.outcome === 'MODIFIED';
  }

  previewOrder() {
    this.error.set('');
    if (!this.outcome) {
      this.error.set('Please select an outcome.');
      return;
    }
    if (!this.orderSummary.trim()) {
      this.error.set('Please provide an order summary.');
      return;
    }
    if (this.isModified && (this.modifiedAmount === null || this.modifiedAmount < 0)) {
      this.error.set('Please enter a valid modified amount.');
      return;
    }
    this.showPreview.set(true);
  }

  submitOrder() {
    this.error.set('');
    this.submitting.set(true);

    const appealNumber = this.appeal?.appealNumber;
    const body = {
      action: 'PASS_ORDER',
      outcome: this.outcome,
      modifiedAmount: this.isModified ? this.modifiedAmount : null,
      orderSummary: this.orderSummary,
      actor: this.auth.currentUser()?.username || '',
      remarks: `Order passed: ${this.outcome}`
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/action`,
      body
    ).subscribe({
      next: () => {
        this.success.set('Order passed successfully.');
        this.submitting.set(false);
        setTimeout(() => this.orderPassed.emit(), 1500);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Failed to pass order.');
        this.submitting.set(false);
      }
    });
  }

  cancel() {
    this.cancelled.emit();
  }
}
