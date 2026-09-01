import { Component, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';

@Component({
  selector: 'app-submit-feedback',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslatePipe],
  templateUrl: './submit-feedback.component.html',
  styleUrl: './submit-feedback.component.scss'
})
export class SubmitFeedbackComponent {

  private router = inject(Router);

  phase = signal<'form' | 'success'>('form');
  submitting = signal(false);

  complaintId = '';
  easeOfFiling = 0;
  grievanceRedressTime = 0;
  overallRating = 0;
  feedbackText = '';
  feedbackTextError = '';
  error = '';

  sourceOfInformation = '';
  sourceOtherText = '';
  sourceOtherTextError = '';
  awarenessHelped = '';
  sourceOptions = [
    'Print Media',
    'Town Halls/Awareness Campaign by RBI',
    'Electronic Media/Internet',
    'Banks',
    'Word of Mouth',
    'Others',
  ];

  ratingLabels = ['', 'Very Poor', 'Poor', 'Average', 'Good', 'Excellent'];

  private readonly SPECIAL_CHAR_REGEX = /[^a-zA-Z0-9\s.,;:!?'"\-()\/]/;

  setRating(field: 'easeOfFiling' | 'grievanceRedressTime' | 'overallRating', value: number) {
    this[field] = value;
    this.clearErrorIfValid();
  }

  onSourceOtherTextChange() {
    if (this.SPECIAL_CHAR_REGEX.test(this.sourceOtherText)) {
      this.sourceOtherTextError = 'Special characters are not allowed.';
      this.sourceOtherText = this.sourceOtherText.replace(this.SPECIAL_CHAR_REGEX, '');
    } else {
      this.sourceOtherTextError = '';
    }
    this.clearErrorIfValid();
  }

  onFeedbackTextChange() {
    if (this.SPECIAL_CHAR_REGEX.test(this.feedbackText)) {
      this.feedbackTextError = 'Special characters are not allowed.';
      this.feedbackText = this.feedbackText.replace(this.SPECIAL_CHAR_REGEX, '');
    } else {
      this.feedbackTextError = '';
    }
    this.clearErrorIfValid();
  }

  clearErrorIfValid() {
    if (!this.error) return;
    if (!this.complaintId.trim()) return;
    if (this.easeOfFiling === 0) return;
    if (this.grievanceRedressTime === 0) return;
    if (this.overallRating === 0) return;
    if (!this.sourceOfInformation) return;
    if (this.sourceOfInformation === 'Others' && !this.sourceOtherText.trim()) return;
    this.error = '';
  }

  submit() {
    this.error = '';
    if (!this.complaintId.trim()) {
      this.error = 'Complaint reference number is required.';
      return;
    }
    if (this.easeOfFiling === 0) {
      this.error = 'Please rate the ease of filing and tracking.';
      return;
    }
    if (this.grievanceRedressTime === 0) {
      this.error = 'Please rate grievance redressed within a reasonable time.';
      return;
    }
    if (this.overallRating === 0) {
      this.error = 'Please rate the overall experience with the resolution provided.';
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
    if (this.sourceOfInformation === 'Others' && this.SPECIAL_CHAR_REGEX.test(this.sourceOtherText)) {
      this.error = 'Special characters are not allowed in source specification.';
      return;
    }
    if (this.sourceOfInformation === 'Others' && this.sourceOtherText.length > 500) {
      this.error = 'Source specification must be within 500 characters.';
      return;
    }
    if (this.feedbackText.length > 500) {
      this.error = 'Feedback must be within 500 characters.';
      return;
    }
    if (this.SPECIAL_CHAR_REGEX.test(this.feedbackText)) {
      this.error = 'Special characters are not allowed in feedback.';
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
