import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { FeedbackService, FeedbackPayload } from '../../../services/feedback.service';
import { HttpErrorResponse } from '@angular/common/http';

/** Reject strings containing HTML tags or script content. */
function containsUnsafeContent(text: string): boolean {
  if (!text) return false;
  const pattern = /<\s*\/?\s*(script|iframe|object|embed|form|link|style|img|svg|on\w+)\b[^>]*>|<[^>]+>/i;
  return pattern.test(text);
}

@Component({
  selector: 'app-submit-feedback',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslatePipe],
  templateUrl: './submit-feedback.component.html',
  styleUrl: './submit-feedback.component.scss'
})
export class SubmitFeedbackComponent {

  private router = inject(Router);
  private feedbackService = inject(FeedbackService);

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
    if (this.suggestions.length > 500) {
      this.error = 'Suggestions must be within 500 characters.';
      return;
    }

    // Validate for HTML/script injection
    if (containsUnsafeContent(this.feedbackText)) {
      this.error = 'Feedback text contains disallowed content. Please remove any HTML tags.';
      return;
    }
    if (containsUnsafeContent(this.sourceOtherText)) {
      this.error = 'Source text contains disallowed content. Please remove any HTML tags.';
      return;
    }
    if (containsUnsafeContent(this.suggestions)) {
      this.error = 'Suggestions contain disallowed content. Please remove any HTML tags.';
      return;
    }

    const payload: FeedbackPayload = {
      complaintNumber: this.complaintId.trim(),
      overallRating: this.overallRating,
      easeOfFiling: this.easeOfFiling,
      grievanceRedressTime: this.grievanceRedressTime,
      sourceOfInformation: this.sourceOfInformation,
      cmsPortalAwareness: this.cmsPortalAwareness,
      feedbackText: this.feedbackText || undefined,
      suggestions: this.suggestions || undefined,
      sourceOtherText: this.sourceOtherText || undefined,
      timelinessRating: this.timelinessRating || undefined,
      communicationRating: this.communicationRating || undefined,
      satisfactionRating: this.satisfactionRating || undefined,
    };

    this.submitting.set(true);
    this.feedbackService.submitFeedback(payload).subscribe({
      next: () => {
        this.submitting.set(false);
        this.phase.set('success');
      },
      error: (err: HttpErrorResponse) => {
        this.submitting.set(false);
        if (err.status === 409) {
          this.error = 'Feedback already submitted for this complaint.';
        } else if (err.status === 400) {
          this.error = err.error?.message || 'Validation error. Please check your input.';
        } else if (err.status === 404) {
          this.error = err.error?.message || 'Complaint not found.';
        } else {
          this.error = 'An unexpected error occurred. Please try again later.';
        }
      },
    });
  }

  goHome() {
    this.router.navigate(['/public']);
  }
}
