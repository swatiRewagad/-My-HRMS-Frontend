import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

@Component({
  selector: 'app-file-appeal',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './file-appeal.component.html',
  styleUrl: './file-appeal.component.scss'
})
export class FileAppealComponent {

  private router = inject(Router);

  // FR-G-032: Appeal phases
  phase = signal<'search' | 'form' | 'success'>('search');
  submitting = signal(false);
  error = '';

  // Search
  complaintId = '';
  complaintFound = false;

  // FR-G-033: Appeal details
  appealGround = '';
  appealGrounds = [
    'The complaint was not resolved within 30 days',
    'Dissatisfied with the resolution/award',
    'The complaint was rejected without valid reason',
    'Partial relief was granted',
    'Non-implementation of the award by Regulated Entity',
    'Other',
  ];
  appealDetails = '';
  reliefSought = '';
  appealAttachments: File[] = [];
  declarationChecked = false;

  // Success
  appealRefNumber = '';

  searchComplaint() {
    if (!this.complaintId.trim()) {
      this.error = 'Please enter your complaint reference number.';
      return;
    }
    this.error = '';
    this.complaintFound = true;
    this.phase.set('form');
  }

  onFilesSelected(event: Event) {
    const input = event.target as HTMLInputElement;
    if (!input.files) return;
    for (let i = 0; i < input.files.length; i++) {
      const file = input.files[i];
      if (file.size > 5 * 1024 * 1024) continue;
      this.appealAttachments.push(file);
    }
    input.value = '';
  }

  removeFile(index: number) {
    this.appealAttachments.splice(index, 1);
  }

  // FR-G-034: Submit appeal
  submitAppeal() {
    this.error = '';
    if (!this.appealGround) {
      this.error = 'Please select a ground for appeal.';
      return;
    }
    if (!this.appealDetails.trim()) {
      this.error = 'Please provide details supporting your appeal.';
      return;
    }
    if (!this.declarationChecked) {
      this.error = 'Please accept the declaration before submitting.';
      return;
    }

    this.submitting.set(true);
    setTimeout(() => {
      this.appealRefNumber = 'APL-' + Date.now().toString().slice(-10);
      this.submitting.set(false);
      this.phase.set('success');
    }, 1200);
  }

  goHome() {
    this.router.navigate(['/public']);
  }

  trackAppeal() {
    this.router.navigate(['/public/track', this.appealRefNumber]);
  }
}
