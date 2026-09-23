import { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';

export class CustomValidators {

  static alphaNumeric(): ValidatorFn {
    return (control: AbstractControl): ValidationErrors | null => {
      if (!control.value) return null;
      const valid = /^[a-zA-Z0-9\s]*$/.test(control.value);
      return valid ? null : { alphaNumeric: true };
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
