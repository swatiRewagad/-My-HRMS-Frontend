import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ComplaintService } from '../../services/complaint.service';
import { ComplaintRegistrationRequest, ComplaintAcknowledgement, ComplaintCategory } from '../../models/complaint.model';
import { SpeechButtonComponent } from '../../shared/speech-button/speech-button.component';

@Component({
  selector: 'app-complaint-form',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './complaint-form.component.html',
  styleUrl: './complaint-form.component.scss'
})
export class ComplaintFormComponent {

  private complaintService = inject(ComplaintService);
  private router = inject(Router);

  categories: ComplaintCategory[] = ['ATM', 'UPI', 'NEFT_RTGS', 'LOAN', 'CREDIT_CARD', 'DEPOSIT', 'INSURANCE', 'GENERAL'];
  entityTypes = ['BANK', 'NBFC', 'COOPERATIVE_BANK'];

  form = signal<ComplaintRegistrationRequest>({
    filingType: 'ONLINE',
    category: '',
    complainantName: '',
    complainantEmail: '',
    complainantPhone: '',
    entityName: '',
    entityType: '',
    subject: '',
    description: '',
    amountInvolved: undefined,
    transactionDate: undefined,
    jurisdictionCode: undefined
  });

  attachments = signal<File[]>([]);
  submitting = signal(false);
  acknowledgement = signal<ComplaintAcknowledgement | null>(null);
  error = signal('');

  updateField(field: string, value: any) {
    this.form.update(prev => ({ ...prev, [field]: value }));
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (input.files) {
      this.attachments.set(Array.from(input.files));
    }
  }

  submit() {
    this.submitting.set(true);
    this.error.set('');

    this.complaintService.registerComplaint(this.form()).subscribe({
      next: (ack) => {
        if (this.attachments().length > 0) {
          this.complaintService.uploadAttachments(ack.complaintId, this.attachments()).subscribe({
            next: () => {
              this.acknowledgement.set(ack);
              this.submitting.set(false);
            },
            error: () => {
              this.acknowledgement.set(ack);
              this.submitting.set(false);
            }
          });
        } else {
          this.acknowledgement.set(ack);
          this.submitting.set(false);
        }
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Failed to submit complaint. Please try again.');
        this.submitting.set(false);
      }
    });
  }

  trackComplaint() {
    const id = this.acknowledgement()?.complaintId;
    if (id) {
      this.router.navigate(['/track', id]);
    }
  }

  get isValid(): boolean {
    const f = this.form();
    return !!(f.category && f.complainantName && f.entityName && f.subject && f.description
      && (f.complainantEmail || f.complainantPhone));
  }
}
