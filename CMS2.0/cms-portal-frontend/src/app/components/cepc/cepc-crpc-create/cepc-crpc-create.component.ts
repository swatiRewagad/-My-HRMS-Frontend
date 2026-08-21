import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { HttpClient } from '@angular/common/http';
import { KeycloakAuthService } from '../../../services/keycloak-auth.service';
import { environment } from '../../../../environments/environment';

@Component({
  selector: 'app-cepc-crpc-create',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cepc-crpc-create.component.html',
  styleUrl: './cepc-crpc-create.component.scss'
})
export class CepcCrpcCreateComponent implements OnInit {
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private http = inject(HttpClient);
  private auth = inject(KeycloakAuthService);

  complaintNumber = signal('');
  submitting = signal(false);
  errorMessage = signal('');

  // Form fields
  counterPartyName = '';
  counterPartyEmail = '';
  counterPartyPhone = '';
  counterSubject = '';
  counterDescription = '';
  priority = 'MEDIUM';

  // File upload
  selectedFiles: File[] = [];
  fileError = signal('');

  readonly maxFileSize = 2 * 1024 * 1024; // 2MB
  readonly maxFileCount = 5;

  async ngOnInit() {
    const authenticated = await this.auth.init();
    if (!authenticated) {
      this.router.navigate(['/staff/login']);
      return;
    }

    const roles = this.auth.getRoles();
    if (!roles.includes('DO') && !roles.includes('CEPC_DO')) {
      this.router.navigate(['/staff/unauthorized']);
      return;
    }

    const id = this.route.snapshot.params['id'];
    this.complaintNumber.set(id || '');
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;

    this.fileError.set('');
    const newFiles = Array.from(input.files);

    // Validate count
    if (this.selectedFiles.length + newFiles.length > this.maxFileCount) {
      this.fileError.set(`Maximum ${this.maxFileCount} files allowed.`);
      input.value = '';
      return;
    }

    // Validate size
    for (const file of newFiles) {
      if (file.size > this.maxFileSize) {
        this.fileError.set(`File "${file.name}" exceeds 2MB limit.`);
        input.value = '';
        return;
      }
    }

    this.selectedFiles = [...this.selectedFiles, ...newFiles];
    input.value = '';
  }

  removeFile(index: number) {
    this.selectedFiles = this.selectedFiles.filter((_, i) => i !== index);
  }

  submit() {
    if (!this.counterPartyName.trim() || !this.counterSubject.trim() || !this.counterDescription.trim()) {
      this.errorMessage.set('Please fill all required fields.');
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set('');

    const formData = new FormData();
    formData.append('complaintNumber', this.complaintNumber());
    formData.append('counterPartyName', this.counterPartyName.trim());
    formData.append('counterPartyEmail', this.counterPartyEmail.trim());
    formData.append('counterPartyPhone', this.counterPartyPhone.trim());
    formData.append('counterSubject', this.counterSubject.trim());
    formData.append('counterDescription', this.counterDescription.trim());
    formData.append('priority', this.priority);
    formData.append('createdBy', this.auth.currentUser()?.username || '');

    for (const file of this.selectedFiles) {
      formData.append('documents', file, file.name);
    }

    this.http.post<any>(
      `${environment.apiBaseUrl}/api/v1/workflow/cepc/crpc/create`,
      formData
    ).subscribe({
      next: () => {
        this.submitting.set(false);
        this.router.navigate(['/cepc/complaint', this.complaintNumber()]);
      },
      error: (err) => {
        this.submitting.set(false);
        this.errorMessage.set(err.error?.message || err.message || 'Failed to create CRPC. Please try again.');
      }
    });
  }

  cancel() {
    this.router.navigate(['/cepc/complaint', this.complaintNumber()]);
  }
}
