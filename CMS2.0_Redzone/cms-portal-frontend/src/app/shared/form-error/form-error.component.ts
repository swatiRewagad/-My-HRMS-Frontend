import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { AbstractControl } from '@angular/forms';
import { TranslatePipe } from '../../pipes/translate.pipe';

@Component({
  selector: 'app-form-error',
  standalone: true,
  imports: [CommonModule, TranslatePipe],
  template: `
    @if (control && control.invalid && (control.touched || control.dirty)) {
      <span class="field-error">
        @if (control.hasError('required')) {
          {{ label ? label + ' is required' : ('validation.required' | translate) }}
        } @else if (control.hasError('minlength')) {
          {{ label || 'Field' }} must be at least {{ control.getError('minlength').requiredLength }} characters
        } @else if (control.hasError('maxlength')) {
          {{ label || 'Field' }} must not exceed {{ control.getError('maxlength').requiredLength }} characters
        } @else if (control.hasError('email')) {
          {{ 'validation.invalid_email' | translate }}
        } @else if (control.hasError('min')) {
          {{ label || 'Value' }} must be at least {{ control.getError('min').min }}
        } @else if (control.hasError('max')) {
          {{ label || 'Value' }} must not exceed {{ control.getError('max').max }}
        } @else if (control.hasError('alphaNumeric')) {
          {{ label || 'Field' }} must contain only letters and numbers
        } @else if (control.hasError('lettersOnly')) {
          Only letters are allowed.
        } @else if (control.hasError('numericOnly')) {
          Only numbers are allowed.
        } @else if (control.hasError('pattern')) {
          {{ label || 'Field' }} is not in the expected format
        } @else if (control.hasError('maxCeiling')) {
          {{ control.getError('maxCeiling').message }}
        }
      </span>
    }
  `,
  styles: [`
    .field-error {
      display: block;
      color: #e53935;
      font-size: 0.75rem;
      margin-top: 4px;
      line-height: 1.3;
    }
  `]
})
export class FormErrorComponent {
  @Input() control!: AbstractControl | null;
  @Input() label = '';
}
