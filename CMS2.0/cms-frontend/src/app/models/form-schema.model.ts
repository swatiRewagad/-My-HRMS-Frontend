export interface FormSchema {
  formTitle: string;
  steps: FormStep[];
  preFilingModal: PreFilingModal;
}

export interface FormStep {
  stepNumber: number;
  title: string;
  description: string;
  helpText?: string;
  fields: FormField[];
}

export interface FormField {
  key: string;
  label: string;
  type: 'text' | 'email' | 'tel' | 'select' | 'textarea' | 'radio' | 'file' | 'date';
  required?: boolean;
  fullWidth?: boolean;
  placeholder?: string;
  prefix?: string;
  hasVerify?: boolean;
  hasVoice?: boolean;
  maxLength?: number;
  rows?: number;
  options?: FieldOption[];
  optionsSource?: string;
  dependsOn?: string;
  accept?: string;
  multiple?: boolean;
  maxSize?: number;
  hint?: string;
}

export interface FieldOption {
  label: string;
  value: any;
}

export interface PreFilingModal {
  title: string;
  subtitle: string;
  options: PreFilingOption[];
}

export interface PreFilingOption {
  id: string;
  number: number;
  title: string;
  description: string;
  conditionalFields?: FormField[];
}
