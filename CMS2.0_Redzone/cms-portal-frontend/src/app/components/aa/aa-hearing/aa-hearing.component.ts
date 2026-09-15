import { Component, Input, Output, EventEmitter, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-aa-hearing',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './aa-hearing.component.html',
  styleUrl: './aa-hearing.component.scss'
})
export class AaHearingComponent {
  @Input() appeal: any;
  @Output() hearingScheduled = new EventEmitter<void>();
  @Output() cancelled = new EventEmitter<void>();

  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  scheduling = signal(false);
  error = signal('');
  success = signal('');
  showPreview = signal(false);

  // Form fields
  hearingDate = '';
  hearingTime = '';
  hearingVenue = '';
  partiesToNotify: string[] = ['appellant', 'respondent'];
  additionalNotes = '';

  venueOptions = [
    'RBI Head Office, Mumbai - Conference Room A',
    'RBI Regional Office - Hearing Room 1',
    'RBI Regional Office - Hearing Room 2',
    'Virtual (Video Conference)',
    'Other'
  ];

  get hearingHistory(): any[] {
    return this.appeal?.hearingHistory || [];
  }

  toggleParty(party: string) {
    const index = this.partiesToNotify.indexOf(party);
    if (index > -1) {
      this.partiesToNotify.splice(index, 1);
    } else {
      this.partiesToNotify.push(party);
    }
  }

  isPartySelected(party: string): boolean {
    return this.partiesToNotify.includes(party);
  }

  previewNotice() {
    if (!this.hearingDate || !this.hearingTime || !this.hearingVenue) {
      this.error.set('Please fill in date, time, and venue before previewing.');
      return;
    }
    this.error.set('');
    this.showPreview.set(true);
  }

  scheduleHearing() {
    this.error.set('');
    if (!this.hearingDate) {
      this.error.set('Please select a hearing date.');
      return;
    }
    if (!this.hearingTime) {
      this.error.set('Please select a hearing time.');
      return;
    }
    if (!this.hearingVenue) {
      this.error.set('Please select a venue.');
      return;
    }

    this.scheduling.set(true);
    const appealNumber = this.appeal?.appealNumber;

    const body = {
      action: 'SCHEDULE_HEARING',
      hearingDate: `${this.hearingDate}T${this.hearingTime}:00`,
      hearingVenue: this.hearingVenue,
      partiesToNotify: this.partiesToNotify,
      remarks: this.additionalNotes,
      actor: this.auth.currentUser()?.username || ''
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/appeals/${appealNumber}/action`,
      body
    ).subscribe({
      next: () => {
        this.success.set('Hearing scheduled successfully. Notices will be sent to selected parties.');
        this.scheduling.set(false);
        setTimeout(() => this.hearingScheduled.emit(), 1500);
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Failed to schedule hearing.');
        this.scheduling.set(false);
      }
    });
  }

  cancel() {
    this.cancelled.emit();
  }
}
