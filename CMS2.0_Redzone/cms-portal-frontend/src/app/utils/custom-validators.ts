import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export class CustomValidators {

  static alphaNumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const valid = /^[a-zA-Z0-9\s]*$/.test(control.value);
      return valid ? null : { alphaNumeric: true };
    };
  }

  /**
   * Letters, spaces and the punctuation that occurs inside real names (apostrophe, hyphen, full stop).
   *
   * Name fields previously used alphaNumeric(), which permits 0-9 — so "John123" was accepted as a
   * first name. \p{L} rather than a-zA-Z so names in Indian scripts are not rejected as invalid.
   */
  static lettersOnly(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const valid = /^[\p{L}\s.'-]*$/u.test(control.value);
      return valid ? null : { lettersOnly: true };
    };
  }

  static numericOnly(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const valid = /^\d*$/.test(control.value);
      return valid ? null : { numericOnly: true };
    };
  }

  static maxCeiling(max: number, message: string): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const num = parseInt(control.value.replace(/,/g, ''), 10);
      return isNaN(num) || num <= max ? null : { maxCeiling: { max, message } };
    };
  }
}
