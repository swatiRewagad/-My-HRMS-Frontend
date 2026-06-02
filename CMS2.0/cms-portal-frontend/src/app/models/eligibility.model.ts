export interface EligibilityQuestion {
  questionCode: string;
  questionText: string;
  questionType: 'YES_NO' | 'SINGLE_CHOICE' | 'TEXT';
  category: string;
  mandatory: boolean;
  displayOrder: number;
  options: string[];
}

export interface EligibilityCheckRequest {
  channel: string;
  sessionId: string;
  answers: Record<string, string>;
}

export interface EligibilityCheckResponse {
  outcome: 'ELIGIBLE' | 'NOT_ELIGIBLE';
  reasonCode: string;
  standardMessage: string;
  nextAction: 'PROCEED_TO_REGISTRATION' | 'SHOW_ADVISORY';
  reference: string;
}
