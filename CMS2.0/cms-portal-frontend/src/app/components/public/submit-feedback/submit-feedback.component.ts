import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';

@Component({
  selector: 'app-submit-feedback',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent],
  templateUrl: './submit-feedback.component.html',
  styleUrl: './submit-feedback.component.scss'
})
export class SubmitFeedbackComponent {

  private router = inject(Router);

  phase = signal<'form' | 'success'>('form');
  submitting = signal(false);

  // FR-G-030: Feedback form
  complaintId = '';
  overallRating = 0;
  timelinessRating = 0;
  communicationRating = 0;
  satisfactionRating = 0;
  feedbackText = '';
  suggestions = '';
  error = '';

  // FR-G-031: Rating labels
  ratingLabels = ['', 'Very Poor', 'Poor', 'Average', 'Good', 'Excellent'];

  setRating(field: 'overallRating' | 'timelinessRating' | 'communicationRating' | 'satisfactionRating', value: number) {
    this[field] = value;
  }

  submit() {
    this.error = '';
    if (!this.complaintId.trim()) {
      this.error = 'Complaint reference number is required.';
      return;
    }
    if (this.overallRating === 0) {
      this.error = 'Please provide an overall rating.';
      return;
    }

    this.submitting.set(true);
    setTimeout(() => {
      this.submitting.set(false);
      this.phase.set('success');
    }, 1000);
  }

  goHome() {
    this.router.navigate(['/public']);
  }
}
