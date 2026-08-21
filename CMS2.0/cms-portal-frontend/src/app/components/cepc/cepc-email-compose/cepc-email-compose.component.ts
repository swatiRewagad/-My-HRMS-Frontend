import { Component, Input, Output, EventEmitter, OnChanges, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../../environments/environment';

interface EmailTemplate {
  id: string;
  label: string;
  subject: string;
  body: string;
}

@Component({
  selector: 'app-cepc-email-compose',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './cepc-email-compose.component.html',
  styleUrl: './cepc-email-compose.component.scss'
})
export class CepcEmailComposeComponent implements OnChanges {
  @Input() complaintNumber = '';
  @Input() complainantEmail = '';
  @Input() entityEmail = '';
  @Output() sent = new EventEmitter<void>();

  private http = inject(HttpClient);

  sending = signal(false);
  resultMessage = signal('');
  resultSuccess = signal(false);

  toEmail = '';
  ccEmails = '';
  subject = '';
  body = '';
  selectedTemplate = '';
  attachments: File[] = [];
  attachmentError = '';

  readonly maxFileSize = 2 * 1024 * 1024; // 2MB

  readonly templates: EmailTemplate[] = [
    {
      id: 'acknowledgement',
      label: 'Acknowledgement',
      subject: 'Acknowledgement - Complaint #{complaintNumber}',
      body: 'Dear Sir/Madam,\n\nThis is to acknowledge receipt of your complaint bearing number {complaintNumber}. Your complaint is under examination and you will be informed of the outcome in due course.\n\nRegards,\nCEPC Office'
    },
    {
      id: 'information_request',
      label: 'Information Request',
      subject: 'Information Required - Complaint #{complaintNumber}',
      body: 'Dear Sir/Madam,\n\nWith reference to your complaint bearing number {complaintNumber}, we require the following additional information/documents to proceed with the examination:\n\n1. \n2. \n3. \n\nKindly provide the above at the earliest.\n\nRegards,\nCEPC Office'
    },
    {
      id: 'closure_notification',
      label: 'Closure Notification',
      subject: 'Closure of Complaint #{complaintNumber}',
      body: 'Dear Sir/Madam,\n\nThis is to inform you that your complaint bearing number {complaintNumber} has been examined and a decision has been taken. The complaint stands closed.\n\nPlease refer to the attached closure letter for details.\n\nRegards,\nCEPC Office'
    },
    {
      id: 'conciliation_meeting',
      label: 'Conciliation Meeting Invitation',
      subject: 'Conciliation Meeting - Complaint #{complaintNumber}',
      body: 'Dear Sir/Madam,\n\nWith reference to complaint bearing number {complaintNumber}, a conciliation meeting has been scheduled.\n\nDate: \nTime: \nVenue/Link: \n\nYour presence/participation is requested. Please confirm your availability.\n\nRegards,\nCEPC Office'
    }
  ];

  ngOnChanges() {
    if (this.complaintNumber && !this.subject) {
      this.subject = `Re: Complaint #${this.complaintNumber}`;
    }
  }

  onTemplateSelect() {
    const tpl = this.templates.find(t => t.id === this.selectedTemplate);
    if (!tpl) return;

    this.subject = tpl.subject.replace(/{complaintNumber}/g, this.complaintNumber);
    this.body = tpl.body.replace(/{complaintNumber}/g, this.complaintNumber);
  }

  prefillComplainant() {
    if (this.complainantEmail) {
      this.toEmail = this.complainantEmail;
    }
  }

  prefillEntity() {
    if (this.entityEmail) {
      this.toEmail = this.entityEmail;
    }
  }

  onFileSelect(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;

    this.attachmentError = '';
    const newFiles = Array.from(input.files);

    for (const file of newFiles) {
      if (file.size > this.maxFileSize) {
        this.attachmentError = `File "${file.name}" exceeds 2MB limit.`;
        input.value = '';
        return;
      }
    }

    this.attachments = [...this.attachments, ...newFiles];
    input.value = '';
  }

  removeAttachment(index: number) {
    this.attachments = this.attachments.filter((_, i) => i !== index);
  }

  formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  send() {
    if (!this.toEmail || !this.subject || !this.body) return;
    if (this.sending()) return;

    this.sending.set(true);
    this.resultMessage.set('');

    const formData = new FormData();
    formData.append('to', this.toEmail.trim());
    formData.append('cc', this.ccEmails.trim());
    formData.append('subject', this.subject.trim());
    formData.append('body', this.body.trim());

    for (const file of this.attachments) {
      formData.append('attachments', file, file.name);
    }

    this.http.post(
      `${environment.apiBaseUrl}/api/v1/complaints/${this.complaintNumber}/emails/send`,
      formData
    ).subscribe({
      next: () => {
        this.resultSuccess.set(true);
        this.resultMessage.set('Email sent successfully.');
        this.sending.set(false);
        this.resetForm();
        this.sent.emit();
        setTimeout(() => this.resultMessage.set(''), 4000);
      },
      error: (err) => {
        this.resultSuccess.set(false);
        this.resultMessage.set(`Failed to send: ${err.error?.message || err.message || 'Unknown error'}`);
        this.sending.set(false);
        setTimeout(() => this.resultMessage.set(''), 5000);
      }
    });
  }

  private resetForm() {
    this.toEmail = '';
    this.ccEmails = '';
    this.subject = `Re: Complaint #${this.complaintNumber}`;
    this.body = '';
    this.selectedTemplate = '';
    this.attachments = [];
    this.attachmentError = '';
  }
}
