import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-cepc-conciliation',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cepc-conciliation.component.html',
  styleUrl: './cepc-conciliation.component.scss'
})
export class CepcConciliationComponent {
  @Input() complaint: any = null;

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  // Conciliation state
  showScheduleForm = signal(false);
  showOutcomeForm = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // Schedule form
  hearingDate = '';
  hearingTime = '';
  hearingVenue = '';
  hearingNotes = '';

  // Outcome form
  outcomeType: 'SETTLED' | 'FAILED' = 'SETTLED';
  compensationAmount = '';
  summaryNotes = '';

  // Upload minutes
  uploadingMinutes = signal(false);
  minutesUploaded = signal(false);

  get conciliationStatus(): string {
    const stage = (this.complaint?.workflowStage || '').toLowerCase();
    if (stage.includes('conciliation_settled')) return 'SETTLED';
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
      parties.push({ role: 'Entity', name: this.complaint.entityName });
    }
    if (this.complaint?.assignedOfficer || this.complaint?.assignedTo) {
      parties.push({ role: 'Conciliation Officer', name: this.complaint.assignedOfficer || this.complaint.assignedTo });
    }
    return parties;
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
      action: 'SCHEDULE_CONCILIATION',
      remarks: `Hearing scheduled: ${this.hearingDate} at ${this.hearingTime}. Venue: ${this.hearingVenue || 'TBD'}. ${this.hearingNotes}`,
      actor: this.auth.currentUser()?.username || '',
      hearingDate: this.hearingDate,
      hearingTime: this.hearingTime,
      venue: this.hearingVenue
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Conciliation hearing scheduled successfully.');
        this.processing.set(false);
        this.showScheduleForm.set(false);
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to schedule hearing.');
        this.processing.set(false);
      }
    });
  }

  submitOutcome() {
    if (!this.summaryNotes.trim()) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const action = this.outcomeType === 'SETTLED' ? 'CONCILIATION_SUCCESS' : 'CONCILIATION_FAILED';
    const body: any = {
      action,
      remarks: this.summaryNotes,
      actor: this.auth.currentUser()?.username || '',
    };

    if (this.outcomeType === 'SETTLED' && this.compensationAmount) {
      body.compensationAmount = parseFloat(this.compensationAmount);
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/cepc/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set(
          this.outcomeType === 'SETTLED'
            ? 'Conciliation settled successfully.'
            : 'Conciliation marked as failed. Complaint will proceed to next stage.'
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
    // Simulate upload — in production this would POST to document API
    setTimeout(() => {
      this.uploadingMinutes.set(false);
      this.minutesUploaded.set(true);
      input.value = '';
    }, 1000);
  }
}
