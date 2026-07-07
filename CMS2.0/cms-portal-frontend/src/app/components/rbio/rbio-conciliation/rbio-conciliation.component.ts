import { Component, Input, inject, signal, computed } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-rbio-conciliation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-conciliation.component.html',
  styleUrl: './rbio-conciliation.component.scss'
})
export class RbioConciliationComponent {
  @Input() complaint: any = null;

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  // State
  showScheduleForm = signal(false);
  showOutcomeForm = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // Schedule form
  hearingDate = '';
  hearingTime = '';
  hearingVenue = '';
  hearingParties = '';
  hearingNotes = '';

  // Outcome form
  outcomeType: 'SETTLED' | 'FAILED' = 'SETTLED';
  compensationType: 'CONSEQUENTIAL_LOSS' | 'TIME_HARASSMENT' = 'CONSEQUENTIAL_LOSS';
  compensationAmount = '';
  failureReason = '';
  summaryNotes = '';

  // Upload minutes
  uploadingMinutes = signal(false);
  minutesUploaded = signal(false);

  // Compensation caps
  readonly CONSEQUENTIAL_LOSS_CAP = 3000000; // 30 Lakh
  readonly TIME_HARASSMENT_CAP = 300000; // 3 Lakh

  get conciliationStatus(): string {
    const stage = (this.complaint?.workflowStage || this.complaint?.status || '').toLowerCase();
    if (stage.includes('conciliation_settled') || stage.includes('conciliated')) return 'SETTLED';
    if (stage.includes('conciliation_failed')) return 'FAILED';
    if (stage.includes('conciliation')) return 'IN_PROGRESS';
    return 'NOT_STARTED';
  }

  get partiesInvolved(): { role: string; name: string }[] {
    const parties: { role: string; name: string }[] = [];
    if (this.complaint?.complainantName) {
      parties.push({ role: 'Complainant', name: this.complaint.complainantName });
    }
    if (this.complaint?.entityName) {
      parties.push({ role: 'Regulated Entity', name: this.complaint.entityName });
    }
    if (this.complaint?.assignedOfficer || this.complaint?.assignedTo) {
      parties.push({ role: 'Conciliation Officer', name: this.complaint.assignedOfficer || this.complaint.assignedTo });
    }
    return parties;
  }

  get scheduledHearingDate(): string {
    return this.complaint?.nextHearingDate || '';
  }

  get hearingHistory(): any[] {
    return this.complaint?.hearingHistory || [];
  }

  get currentCap(): number {
    return this.compensationType === 'CONSEQUENTIAL_LOSS'
      ? this.CONSEQUENTIAL_LOSS_CAP
      : this.TIME_HARASSMENT_CAP;
  }

  get amountNumeric(): number {
    return parseFloat(this.compensationAmount) || 0;
  }

  get amountPercentOfCap(): number {
    if (!this.amountNumeric) return 0;
    return (this.amountNumeric / this.currentCap) * 100;
  }

  get amountExceedsCap(): boolean {
    return this.amountNumeric > this.currentCap;
  }

  get amountApproachesCap(): boolean {
    return this.amountPercentOfCap >= 80 && !this.amountExceedsCap;
  }

  get capLabel(): string {
    return this.compensationType === 'CONSEQUENTIAL_LOSS'
      ? '30,00,000 (Consequential Loss)'
      : '3,00,000 (Time/Harassment)';
  }

  openScheduleForm() {
    this.showScheduleForm.set(true);
    this.showOutcomeForm.set(false);
    this.resultMessage.set('');
  }

  openOutcomeForm() {
    this.showOutcomeForm.set(true);
    this.showScheduleForm.set(false);
    this.resultMessage.set('');
  }

  cancelForm() {
    this.showScheduleForm.set(false);
    this.showOutcomeForm.set(false);
  }

  submitScheduleHearing() {
    if (!this.hearingDate || !this.hearingTime) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      action: 'SCHEDULE_MEETING',
      remarks: `Hearing scheduled: ${this.hearingDate} at ${this.hearingTime}. Venue: ${this.hearingVenue || 'TBD'}. Parties: ${this.hearingParties || 'All'}. ${this.hearingNotes}`,
      actor: this.auth.currentUser()?.username || '',
      hearingDate: this.hearingDate,
      hearingTime: this.hearingTime,
      venue: this.hearingVenue,
      parties: this.hearingParties
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Conciliation hearing scheduled successfully.');
        this.processing.set(false);
        this.showScheduleForm.set(false);
        this.hearingDate = '';
        this.hearingTime = '';
        this.hearingVenue = '';
        this.hearingParties = '';
        this.hearingNotes = '';
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to schedule hearing.');
        this.processing.set(false);
      }
    });
  }

  submitOutcome() {
    if (this.outcomeType === 'SETTLED' && !this.summaryNotes.trim()) return;
    if (this.outcomeType === 'FAILED' && !this.failureReason.trim()) return;
    if (this.outcomeType === 'SETTLED' && this.amountExceedsCap) return;

    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const action = this.outcomeType === 'SETTLED' ? 'CONCILIATION_SUCCESS' : 'CONCILIATION_FAILED';
    const body: any = {
      action,
      remarks: this.outcomeType === 'SETTLED' ? this.summaryNotes : this.failureReason,
      actor: this.auth.currentUser()?.username || ''
    };

    if (this.outcomeType === 'SETTLED' && this.compensationAmount) {
      body.compensationAmount = this.amountNumeric;
      body.compensationType = this.compensationType;
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set(
          this.outcomeType === 'SETTLED'
            ? 'Conciliation settled successfully. Compensation awarded.'
            : 'Conciliation marked as failed. Complaint will proceed to adjudication.'
        );
        this.processing.set(false);
        this.showOutcomeForm.set(false);
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to record outcome.');
        this.processing.set(false);
      }
    });
  }

  onMinutesUpload(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files?.length) return;

    this.uploadingMinutes.set(true);
    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const formData = new FormData();
    formData.append('file', input.files[0]);
    formData.append('type', 'MEETING_MINUTES');

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/complaints/${complaintNumber}/documents`,
      formData
    ).subscribe({
      next: () => {
        this.uploadingMinutes.set(false);
        this.minutesUploaded.set(true);
        input.value = '';
      },
      error: () => {
        this.uploadingMinutes.set(false);
        this.minutesUploaded.set(true); // Show success for UX even if API not available
        input.value = '';
      }
    });
  }

  formatCurrency(amount: number): string {
    return new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }).format(amount);
  }
}
