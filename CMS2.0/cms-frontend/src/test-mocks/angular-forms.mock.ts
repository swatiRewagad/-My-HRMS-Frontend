export class AbstractControl {
  value: any;
  constructor(value?: any) { this.value = value; }
}

export class FormControl extends AbstractControl {
  constructor(value?: any) { super(value); }
}

export class FormGroup {
  controls: Record<string, any> = {};
  constructor(controls?: any) { this.controls = controls || {}; }
  get(key: string) { return this.controls[key]; }
}

export class Validators {
  static required(control: any) { return control.value ? null : { required: true }; }
}

export type ValidatorFn = (control: AbstractControl) => ValidationErrors | null;
export type ValidationErrors = Record<string, any>;

export class ReactiveFormsModule {}
export class FormsModule {}
