import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-rbio-adjudication',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-adjudication.component.html',
  styleUrl: './rbio-adjudication.component.scss'
})
export class RbioAdjudicationComponent {
  @Input() complaint: any = null;

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  // State
  activeForm = signal<'NOTICE' | 'IMPLEAD' | 'AWARD' | 'REJECT' | null>(null);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // 13-1 Notice form
  noticeContent = '';
  noticeTargetParty = '';

  // Implead Party form
  impleadPartyName = '';
  impleadPartyType = '';

  // Award form
  awardCompensationType: 'CONSEQUENTIAL_LOSS' | 'TIME_HARASSMENT' = 'CONSEQUENTIAL_LOSS';
  awardAmount = '';
  awardSummary = '';

  // Reject form
  rejectionGrounds = '';

  // Compensation caps
  readonly CONSEQUENTIAL_LOSS_CAP = 3000000; // 30 Lakh
  readonly TIME_HARASSMENT_CAP = 300000; // 3 Lakh

  get caseSummary(): string {
    return this.complaint?.description || this.complaint?.subject || 'No case summary available.';
  }

  get hearingDates(): string[] {
    return this.complaint?.hearingDates || [];
  }

  get partiesInvolved(): { role: string; name: string }[] {
    const parties: { role: string; name: string }[] = [];
    if (this.complaint?.complainantName) {
      parties.push({ role: 'Complainant', name: this.complaint.complainantName });
    }
    if (this.complaint?.entityName) {
      parties.push({ role: 'Regulated Entity', name: this.complaint.entityName });
    }
    return parties;
  }

  get impleadedParties(): { name: string; type: string }[] {
    return this.complaint?.impleadedParties || [];
  }

  get currentCap(): number {
    return this.awardCompensationType === 'CONSEQUENTIAL_LOSS'
      ? this.CONSEQUENTIAL_LOSS_CAP
      : this.TIME_HARASSMENT_CAP;
  }

  get awardAmountNumeric(): number {
    return parseFloat(this.awardAmount) || 0;
  }

  get awardPercentOfCap(): number {
    if (!this.awardAmountNumeric) return 0;
    return (this.awardAmountNumeric / this.currentCap) * 100;
  }

  get awardExceedsCap(): boolean {
    return this.awardAmountNumeric > this.currentCap;
  }

  get awardApproachesCap(): boolean {
    return this.awardPercentOfCap >= 80 && !this.awardExceedsCap;
  }

  get capLabel(): string {
    return this.awardCompensationType === 'CONSEQUENTIAL_LOSS'
      ? '30,00,000 (Consequential Loss)'
      : '3,00,000 (Time/Harassment)';
  }

  openForm(form: 'NOTICE' | 'IMPLEAD' | 'AWARD' | 'REJECT') {
    this.activeForm.set(form);
    this.resultMessage.set('');
  }

  cancelForm() {
    this.activeForm.set(null);
  }

  submitNotice() {
    if (!this.noticeContent.trim() || !this.noticeTargetParty.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      action: 'ISSUE_13_1_NOTICE',
      remarks: this.noticeContent,
      actor: this.auth.currentUser()?.username || '',
      targetParty: this.noticeTargetParty
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('13-1 Notice issued successfully.');
        this.processing.set(false);
        this.activeForm.set(null);
        this.noticeContent = '';
        this.noticeTargetParty = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to issue notice.');
        this.processing.set(false);
      }
    });
  }

  submitImplead() {
    if (!this.impleadPartyName.trim() || !this.impleadPartyType.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      action: 'IMPLEAD_PARTY',
      remarks: `Impleading party: ${this.impleadPartyName} (${this.impleadPartyType})`,
      actor: this.auth.currentUser()?.username || '',
      impleadPartyName: this.impleadPartyName,
      impleadPartyType: this.impleadPartyType
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set(`Party "${this.impleadPartyName}" impleaded successfully.`);
        this.processing.set(false);
        this.activeForm.set(null);
        this.impleadPartyName = '';
        this.impleadPartyType = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to implead party.');
        this.processing.set(false);
      }
    });
  }

  submitAward() {
    if (!this.awardSummary.trim() || this.awardExceedsCap) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body: any = {
      action: 'ADJUDICATION_AWARD',
      remarks: this.awardSummary,
      actor: this.auth.currentUser()?.username || '',
      compensationType: this.awardCompensationType,
      compensationAmount: this.awardAmountNumeric
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Adjudication award passed successfully.');
        this.processing.set(false);
        this.activeForm.set(null);
        this.awardAmount = '';
        this.awardSummary = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to pass award.');
        this.processing.set(false);
      }
    });
  }

  submitReject() {
    if (!this.rejectionGrounds.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      action: 'REJECT',
      remarks: this.rejectionGrounds,
      actor: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Complaint rejected with detailed grounds.');
        this.processing.set(false);
        this.activeForm.set(null);
        this.rejectionGrounds = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to reject complaint.');
        this.processing.set(false);
      }
    });
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }
}
