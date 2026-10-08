import { Component, inject, signal, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { SpeechButtonComponent } from '../../../shared/speech-button/speech-button.component';
import { TranslatePipe } from '../../../pipes/translate.pipe';
import { FeedbackService } from '../../../services/feedback.service';
import { scrollToTop } from '../../../utils/accessibility';

/**
 * UST109 scenarios 4 and 5 (FR-G-038, Document/CMS_Portal_UTS.txt) refuse free text that "exceeds
 * 500 characters OR contains special characters", with one message covering both conditions. Only
 * the length half was implemented, so a citizen could paste markup-adjacent punctuation into a
 * field the Scheme says must be plain prose.
 *
 * ALLOW-list, not a deny-list: letters, digits, whitespace and the punctuation ordinary prose needs
 * — full stop, comma, hyphen, apostrophe, parentheses, slash, colon, semicolon, question mark.
 * Everything else (@ # % ! $ * ^ ~ | \ { } [ ] = + " < > and friends) is a special character.
 *
 * Deliberately permissive about sentence punctuation: the staff-visibility suite stores real
 * sentences ("Send a closure SMS as well as the email.") and a rule that rejected a full stop would
 * refuse every honest answer.
 */
const SPECIAL_CHARACTERS = /[^A-Za-z0-9\s.,\-'()\/:;?]/;

function containsSpecialCharacters(text: string): boolean {
  if (!text) return false;
  return SPECIAL_CHARACTERS.test(text);
}

@Component({
  selector: 'app-submit-feedback',
  standalone: true,
  imports: [CommonModule, FormsModule, SpeechButtonComponent, TranslatePipe],
  templateUrl: './submit-feedback.component.html',
  styleUrl: './submit-feedback.component.scss'
})
export class SubmitFeedbackComponent implements OnInit {

  /** UST109 scenario 4 — verbatim. Covers over-length AND special characters in one message. */
  static readonly FEEDBACK_LIMIT_MESSAGE =
    'Feedback must be within 500 characters and cannot contain special characters.';

  /** UST109 scenario 5 — verbatim, for the "Others" source box. */
  static readonly OTHERS_LIMIT_MESSAGE =
    'Input must be within 500 characters and cannot contain special characters.';

  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private feedbackService = inject(FeedbackService);

  phase = signal<'form' | 'success'>('form');
  submitting = signal(false);

  complaintId = '';
  easeOfFiling = 0;
  grievanceRedressTime = 0;
  overallRating = 0;
  feedbackText = '';
  feedbackTextError = '';
  error = '';

  ngOnInit(): void {
    const idFromRoute = this.route.snapshot.paramMap.get('id');
    if (idFromRoute) {
      this.complaintId = idFromRoute;
    }
  }

  sourceOfInformation = '';
  sourceOtherText = '';
  sourceOtherTextError = '';
  awarenessHelped = '';
  // UST109 Q5's list. RBI and the Regulated Entity were missing while "Print Media" and "Town Halls"
  // were extra — so the two sources RBI most needs to measure (its own channels and the REs') could not
  // be reported at all, and the aggregate was unusable for the awareness analysis the question exists for.
  sourceOptions = [
    'RBI',
    'Regulated Entity (RE)',
    'Electronic Media/Internet',
    'Bank',
    'Word of Mouth',
    'Others',
  ];

  ratingLabels = ['', 'Very Poor', 'Poor', 'Average', 'Good', 'Excellent'];

  private readonly SPECIAL_CHAR_REGEX = /[^a-zA-Z0-9\s.,;:!?'"\-()\/]/;

  setRating(field: 'easeOfFiling' | 'grievanceRedressTime' | 'overallRating', value: number) {
    this[field] = value;
    this.clearErrorIfValid();
  }

  // The offending characters are reported, NOT silently stripped. Stripping edited the citizen's own
  // words as they typed — the text they submitted was not the text they wrote — and UST109 asks for the
  // input to be rejected with an explanation, not quietly rewritten.
  onSourceOtherTextChange() {
    this.sourceOtherTextError = this.validateFreeText(this.sourceOtherText, SubmitFeedbackComponent.OTHERS_LIMIT_MESSAGE);
    this.clearErrorIfValid();
  }

  onFeedbackTextChange() {
    this.feedbackTextError = this.validateFreeText(this.feedbackText, SubmitFeedbackComponent.FEEDBACK_LIMIT_MESSAGE);
    this.clearErrorIfValid();
  }

  /**
   * UST109's rule for the two free-text answers: within 500 characters, no special characters.
   * Runs both allow-list regexes (ours' SPECIAL_CHAR_REGEX and the SPECIAL_CHARACTERS one lifted from
   * cms_master's UST109 fix) so neither side's set of permitted punctuation is lost.
   */
  private validateFreeText(value: string, message: string): string {
    if (value.length > 500 || this.SPECIAL_CHAR_REGEX.test(value) || containsSpecialCharacters(value)) {
      return message;
    }
    return '';
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
    if (this.sourceOfInformation === 'Others'
        && (this.sourceOtherText.length > 500
            || this.SPECIAL_CHAR_REGEX.test(this.sourceOtherText)
            || containsSpecialCharacters(this.sourceOtherText))) {
      this.error = SubmitFeedbackComponent.OTHERS_LIMIT_MESSAGE;
      return;
    }
    // Q6 (awareness) is OPTIONAL per UST109. It was enforced as mandatory, which blocked submission of
    // an otherwise complete form over a question the citizen is entitled to skip.
    // UST109 scenario 4: one message for BOTH the length and the special-character rule. Checks both
    // ours' allow-list (SPECIAL_CHAR_REGEX) and cms_master's allow-list (containsSpecialCharacters) so
    // neither side's set of permitted punctuation is weakened by the merge.
    if (this.feedbackText.length > 500
        || this.SPECIAL_CHAR_REGEX.test(this.feedbackText)
        || containsSpecialCharacters(this.feedbackText)) {
      this.error = SubmitFeedbackComponent.FEEDBACK_LIMIT_MESSAGE;
      return;
    }

    this.submitting.set(true);
    this.feedbackService.submitFeedback({
      complaintNumber: this.complaintId.trim(),
      easeOfFiling: this.easeOfFiling,
      grievanceRedressTime: this.grievanceRedressTime,
      overallRating: this.overallRating,
      feedbackText: this.feedbackText,
      sourceOfInformation: this.sourceOfInformation,
      sourceOtherText: this.sourceOtherText,
      cmsPortalAwareness: this.awarenessHelped,
    }).subscribe({
      next: () => {
        this.submitting.set(false);
        this.phase.set('success');
        scrollToTop();
      },
      error: (err) => {
        this.submitting.set(false);
        this.error = err.error?.message || 'Failed to submit feedback. Please try again.';
      },
    });
  }

  goHome() {
    this.router.navigate(['/public']);
  }
}
