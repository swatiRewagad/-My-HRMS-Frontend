import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-ocr-api-test',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './ocr-api-test.component.html',
  styleUrl: './ocr-api-test.component.scss'
})
export class OcrApiTestComponent {
  baseUrl = signal(environment.ocrServiceUrl);
  selectedFile: File | null = null;
  sender = '';
  subject = '';
  synthetic = false;

  loading = signal(false);
  activeTab = signal<'submit' | 'job' | 'health' | 'stats'>('submit');

  submissionResult = signal<any>(null);
  jobResult = signal<any>(null);
  healthResult = signal<any>(null);
  statsResult = signal<any>(null);
  errorMessage = signal<string | null>(null);

  jobIdInput = '';
  lastJobId = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  onFileSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
    }
  }

  submitDocument() {
    if (!this.selectedFile) {
      this.errorMessage.set('Please select a file');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.submissionResult.set(null);

    const formData = new FormData();
    formData.append('file', this.selectedFile);
    if (this.sender) formData.append('sender', this.sender);
    if (this.subject) formData.append('subject', this.subject);
    if (this.synthetic) formData.append('synthetic', 'true');

    this.http.post(`${this.baseUrl()}/v1/documents`, formData).subscribe({
      next: (res: any) => {
        this.submissionResult.set(res);
        this.lastJobId.set(res.job_id);
        this.jobIdInput = res.job_id;
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.detail || err.message || 'Request failed');
        this.loading.set(false);
      }
    });
  }

  fetchJob() {
    const jobId = this.jobIdInput.trim();
    if (!jobId) {
      this.errorMessage.set('Please enter a job ID');
      return;
    }

    this.loading.set(true);
    this.errorMessage.set(null);
    this.jobResult.set(null);

    this.http.get(`${this.baseUrl()}/v1/jobs/${jobId}`).subscribe({
      next: (res) => {
        this.jobResult.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set(err.error?.detail || err.message || 'Job not found');
        this.loading.set(false);
      }
    });
  }

  fetchHealth() {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.healthResult.set(null);

    this.http.get(`${this.baseUrl()}/healthz`).subscribe({
      next: (res) => {
        this.healthResult.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Health check failed: ' + (err.message || 'Connection refused'));
        this.loading.set(false);
      }
    });
  }

  fetchStats() {
    this.loading.set(true);
    this.errorMessage.set(null);
    this.statsResult.set(null);

    this.http.get(`${this.baseUrl()}/v1/stats`).subscribe({
      next: (res) => {
        this.statsResult.set(res);
        this.loading.set(false);
      },
      error: (err) => {
        this.errorMessage.set('Stats fetch failed: ' + (err.message || 'Connection refused'));
        this.loading.set(false);
      }
    });
  }

  getStatusClass(status: string): string {
    switch (status) {
      case 'completed': return 'status-success';
      case 'bounced': return 'status-warning';
      case 'failed': return 'status-error';
      default: return 'status-info';
    }
  }

  getBounceClass(decision: string): string {
    switch (decision) {
      case 'accept': return 'badge-success';
      case 'repair': return 'badge-warning';
      case 'bounce': return 'badge-danger';
      default: return '';
    }
  }

  copyToClipboard(text: string) {
    navigator.clipboard.writeText(text);
  }

  formatJson(obj: any): string {
    return JSON.stringify(obj, null, 2);
  }
}
