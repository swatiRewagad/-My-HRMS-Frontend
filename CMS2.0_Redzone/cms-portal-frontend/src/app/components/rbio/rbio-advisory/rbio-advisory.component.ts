import { Component, Input, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-rbio-advisory',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './rbio-advisory.component.html',
  styleUrl: './rbio-advisory.component.scss'
})
export class RbioAdvisoryComponent {
  @Input() complaint: any = null;

  private http = inject(HttpClient);
  auth = inject(KeycloakAuthService);

  // State
  showForm = signal(true);
  showPreview = signal(false);
  processing = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  // Form fields
  advisorySubject = '';
  advisoryText = '';
  advisoryClassification: 'OPERATIONAL' | 'PROCEDURAL' | 'POLICY' = 'OPERATIONAL';

  get isFormValid(): boolean {
    return !!(this.advisorySubject.trim() && this.advisoryText.trim());
  }

  openPreview() {
    if (!this.isFormValid) return;
    this.showPreview.set(true);
    this.showForm.set(false);
  }

  editForm() {
    this.showPreview.set(false);
    this.showForm.set(true);
  }

  submitAdvisory() {
    if (!this.isFormValid) return;
    this.processing.set(true);

    const complaintNumber = this.complaint?.complaintNumber || this.complaint?.complaintId;
    const body = {
      action: 'ISSUE_ADVISORY',
      remarks: this.advisoryText,
      actor: this.auth.currentUser()?.username || '',
      advisorySubject: this.advisorySubject,
      advisoryClassification: this.advisoryClassification
    };

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/rbio/action/${complaintNumber}`,
      body
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Advisory opinion issued successfully.');
        this.processing.set(false);
        this.showPreview.set(false);
        this.showForm.set(false);
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(err.error?.message || 'Failed to issue advisory.');
        this.processing.set(false);
      }
    });
  }

  resetForm() {
    this.advisorySubject = '';
    this.advisoryText = '';
    this.advisoryClassification = 'OPERATIONAL';
    this.showForm.set(true);
    this.showPreview.set(false);
    this.resultMessage.set('');
  }
}
