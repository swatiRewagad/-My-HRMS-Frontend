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

  // FR-G-038: Full questionnaire (UST109)
  easeOfFiling = 0;
  grievanceRedressTime = 0;
  sourceOfInformation = '';
  sourceOtherText = '';
  cmsPortalAwareness = '';
  sourceOptions = [
    'RBI Website',
    'Bank/NBFC Branch',
    'News/Media',
    'Social Media',
    'Word of Mouth',
    'Government Portal',
    'Others',
  ];
  awarenessOptions = [
    'Very Aware',
    'Somewhat Aware',
    'Not Aware (first time user)',
  ];

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
    if (this.easeOfFiling === 0) {
      this.error = 'Please rate the ease of filing.';
      return;
    }
    if (this.grievanceRedressTime === 0) {
      this.error = 'Please rate the grievance redress time.';
      return;
    }
    if (!this.sourceOfInformation) {
      this.error = 'Please select source of information.';
      return;
    }
    if (this.sourceOfInformation === 'Others' && !this.sourceOtherText.trim()) {
      this.error = 'Please specify the source.';
      return;
    }
    if (!this.cmsPortalAwareness) {
      this.error = 'Please select CMS Portal awareness level.';
      return;
    }
    if (this.feedbackText.length > 500) {
      this.error = 'Feedback must be within 500 characters.';
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
