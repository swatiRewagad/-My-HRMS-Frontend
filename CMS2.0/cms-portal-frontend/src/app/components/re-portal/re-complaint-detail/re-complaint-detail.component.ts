import { Component, inject, signal, computed, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

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
  imports: [CommonModule, FormsModule],
  templateUrl: './re-complaint-detail.component.html',
  styleUrl: './re-complaint-detail.component.scss'
})
export class ReComplaintDetailComponent implements OnInit {
  private route = inject(ActivatedRoute);
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

  // Query form
  showQueryForm = signal(false);
  queryType = signal<'clarification' | 'extension'>('clarification');
  queryText = signal('');
  submittingQuery = signal(false);
  querySuccess = signal('');
  queryError = signal('');

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

  toggleQueryForm() {
    this.showQueryForm.set(!this.showQueryForm());
    this.querySuccess.set('');
    this.queryError.set('');
  }

  submitQuery() {
    if (!this.queryText().trim()) {
      this.queryError.set('Please enter your query details.');
      return;
    }

    this.submittingQuery.set(true);
    this.queryError.set('');
    this.querySuccess.set('');

    const complaintNumber = this.complaint()?.complaintNumber;
    const payload = {
      type: this.queryType(),
      text: this.queryText()
    };

    this.http.post<any>(`${environment.apiBaseUrl}/api/v1/re-portal/complaints/${complaintNumber}/query`, payload).subscribe({
      next: () => {
        this.submittingQuery.set(false);
        this.querySuccess.set(`${this.queryType() === 'extension' ? 'Extension' : 'Clarification'} request submitted.`);
        this.queryText.set('');
        if (complaintNumber) this.loadComplaint(complaintNumber);
      },
      error: (err) => {
        this.submittingQuery.set(false);
        this.queryError.set(err.error?.message || 'Failed to submit query.');
      }
    });
  }

  goBack() {
    this.router.navigate(['/re-portal/dashboard']);
  }
}
