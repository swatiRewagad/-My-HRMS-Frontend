import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';
import { QueryThreadComponent } from '../../shared/query-thread/query-thread.component';
import { InternalNotesComponent } from '../../shared/internal-notes/internal-notes.component';
import { StatusBadgeComponent } from '../../shared/status-badge/status-badge.component';
import { UploadLimitsService } from '../../../services/upload-limits.service';

interface ComplaintDetail {
  complaintNumber: string;
  subject: string;
  description: string;
  complainantName: string;
  category: string;
  filedDate: string;
  forwardedDate: string;
  responseDeadline: string;
  status: string;
  entityName: string;
  entityCode?: string;
  // UST846: derived from the entity's own actions. Displayed here but never settable — the server
  // refuses a direct write, so this is presentation only.
  reActivityStatus?: string;
  reActivityStatusKey?: string;
  reActivityChangedAt?: string | null;
}

interface TimelineEntry {
  action: string;
  actor: string;
  timestamp: string;
  remarks: string;
}

@Component({
  selector: 'app-re-complaint-detail',
  standalone: true,
  imports: [CommonModule, FormsModule, QueryThreadComponent, InternalNotesComponent, StatusBadgeComponent],
  templateUrl: './re-complaint-detail.component.html',
  styleUrl: './re-complaint-detail.component.scss'
})
export class ReComplaintDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
  // Public: the template renders the configured limit in its upload hint.
  uploadLimits = inject(UploadLimitsService);
  private router = inject(Router);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  loading = signal(true);
  complaint = signal<ComplaintDetail | null>(null);
  timeline = signal<TimelineEntry[]>([]);

  // Response form
  responseText = signal('');
  selectedFiles = signal<File[]>([]);
  submittingResponse = signal(false);
  responseSuccess = signal('');
  responseError = signal('');

  /**
   * Passed to the query and notes panels so their requests carry the entity scope. The server
   * prefers the JWT's entity_code claim over this, so it is a convenience for the dev-header mode
   * rather than the access control itself.
   */
  entityCode = computed(() => this.complaint()?.entityCode ?? null);

  // Computed
  deadlineCountdown = computed(() => {
    const c = this.complaint();
    if (!c?.responseDeadline) return { days: 0, hours: 0, expired: true };
    const deadline = new Date(c.responseDeadline);
    const now = new Date();
    const diff = deadline.getTime() - now.getTime();
    if (diff <= 0) return { days: 0, hours: 0, expired: true };
    const days = Math.floor(diff / (1000 * 60 * 60 * 24));
    const hours = Math.floor((diff % (1000 * 60 * 60 * 24)) / (1000 * 60 * 60));
    return { days, hours, expired: false };
  });

  isResponseWindowOpen = computed(() => {
    return !this.deadlineCountdown().expired;
  });

  ngOnInit() {
    const complaintNumber = this.route.snapshot.paramMap.get('complaintNumber');
    if (complaintNumber) {
      this.loadComplaint(complaintNumber);
    }
  }

  loadComplaint(complaintNumber: string) {
    this.loading.set(true);
    this.http.get<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints/${complaintNumber}`).subscribe({
      next: (res) => {
        const data = res?.data || res;
        this.complaint.set(data.complaint || data);
        this.timeline.set(data.timeline || []);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
      }
    });
  }

  maskName(name: string): string {
    if (!name || name.length <= 4) return name;
    const first = name.substring(0, 2);
    const last = name.substring(name.length - 2);
    return `${first}${'*'.repeat(name.length - 4)}${last}`;
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      const files = Array.from(input.files);
      this.selectedFiles.set(files);
    }
  }

  removeFile(index: number) {
    const files = [...this.selectedFiles()];
    files.splice(index, 1);
    this.selectedFiles.set(files);
  }

  submitResponse() {
    if (!this.responseText().trim()) {
      this.responseError.set('Please enter a response.');
      return;
    }

    this.submittingResponse.set(true);
    this.responseError.set('');
    this.responseSuccess.set('');

    const formData = new FormData();
    formData.append('responseText', this.responseText());
    this.selectedFiles().forEach(file => {
      formData.append('documents', file);
    });

    const complaintNumber = this.complaint()?.complaintNumber;
    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints/${complaintNumber}/respond`, formData).subscribe({
      next: (res) => {
        this.submittingResponse.set(false);
        this.responseSuccess.set('Response submitted successfully.');
        this.responseText.set('');
        this.selectedFiles.set([]);
        // Reload complaint to refresh timeline
        if (complaintNumber) this.loadComplaint(complaintNumber);
      },
      error: (err) => {
        this.submittingResponse.set(false);
        this.responseError.set(err.error?.message || 'Failed to submit response.');
      }
    });
  }

  goBack() {
    this.router.navigate(['/re-portal/dashboard']);
  }
}
