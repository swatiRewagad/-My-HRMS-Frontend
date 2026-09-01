export interface EligibilityQuestion {
  key: string;
  question: string;
  translationKey?: string;
  type: 'select' | 'radio';
  options: EligibilityOption[];
  blockOn: string | null;
  blockMessage: string;
  blockMessageKey?: string;
  nonMaintainable?: boolean;
  closureLetterPara2?: string;
  cepcBlockMessage?: string;
  cepcClosureLetter?: string;
  simplifiedText?: string;
  simplifiedTextKey?: string;
}

export interface EligibilityOption {
  label: string;
  value: string;
  translationKey?: string;
}

export interface BankEntity {
  id: number;
  name: string;
  department?: string;
  entityType?: string;
}

export interface AttachmentPreview {
  name: string;
  url: string;
  type: string;
  size: number;
}

export interface AccountType {
  label: string;
  value: string;
  checked: boolean;
}

export interface SelectOption {
  label: string;
  value: string;
  entityType?: string;
}

export interface DraftPayload {
  phone: string;
  entityName: string;
  formData: Record<string, any>;
  eligibilityAnswers: Record<string, string>;
  currentStep: number;
  highestStepReached?: number;
  eligibilityStep?: number;
  phase: string;
  checkedAccountTypes?: string[];
  dateDisplay?: Record<string, string>;
}

export interface ComplaintPayload {
  filingType: string;
  category: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  complainantAddress: string;
  complainantState?: string;
  complainantDistrict?: string;
  entityName: string;
  entityType: string;
  regulatedEntityId?: number;
  subject: string;
  description: string;
  amountInvolved?: number;
  transactionDate?: string;
  priorReComplaint: boolean;
  reComplaintDate?: string;
  reComplaintReference?: string;
  reRepliedAndDissatisfied: boolean;
}
