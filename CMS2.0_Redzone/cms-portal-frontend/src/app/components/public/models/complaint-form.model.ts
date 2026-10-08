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
  clauseCode?: string;
  statusCode?: 'PORTAL_REJECTION';
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
  fileId?: string;
  viewUrl?: string;
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
  /**
   * Date the RE's reply was received (UST12). The post-reply filing window runs from this date, not
   * the original RE-complaint date, so the server cannot enforce that window without it.
   */
  reReplyDate?: string;
  complainantPincode?: string;
  entityState?: string;
  entityDistrict?: string;
  entityBranchName?: string;
  repName?: string;
  repEmail?: string;
  repPhone?: string;
  // Verbatim wizard answers so the review/withdraw screen can show every question answered,
  // including the ones with no dedicated column.
  eligibilityAnswers?: Record<string, any>;
  wizardFormData?: Record<string, any>;
  /**
   * The complaint number this filing duplicates, set only when the citizen was warned and chose
   * "Proceed Anyway" (UST87 AC5). Lets the officer see the two complaints as related.
   */
  duplicateOfComplaintNumber?: string;
  /** DPDP Act 2023 consent declaration (UST5/UST79). Gated server-side for online filing. */
  declarationAccepted?: boolean;
}
