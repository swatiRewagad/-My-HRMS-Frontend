export interface ComplaintRegistrationRequest {
  filingType: string;
  category: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  complainantAddress?: string;
  complainantState?: string;
  complainantDistrict?: string;
  entityName: string;
  entityType: string;
  regulatedEntityId?: number;
  subject: string;
  description: string;
  amountInvolved?: number;
  transactionDate?: string;
  jurisdictionCode?: string;
  priorReComplaint?: boolean;
  reComplaintDate?: string;
  reComplaintReference?: string;
  reRepliedAndDissatisfied?: boolean;
  /** UST5: required by the server for ONLINE filings — see FileComplaintRequest.isDeclarationAcceptedWhenRequired. */
  declarationAccepted?: boolean;
}

export interface NonMaintainableRegistrationRequest {
  clauseCode: string;
  statusCode: 'PORTAL_REJECTION';
  complainantName?: string;
  complainantEmail?: string;
  complainantPhone?: string;
  complainantState?: string;
  complainantDistrict?: string;
  entityName?: string;
}

export interface NonMaintainableRegistrationResponse {
  caseId: string;
  status: string;
}

export interface ComplaintAcknowledgement {
  complaintId: string;
  status: string;
  registeredAt: string;
  slaDueDate: string;
  acknowledgementMessage: string;
}

export interface StageInfo {
  stage: number;
  label: string;
  status: 'completed' | 'current' | 'pending';
  date: string | null;
}

export interface ComplaintStatus {
  complaintId: string;
  status: string;
  category: string;
  registeredAt: string;
  slaDueDate: string;
  assignedTeam: string;
  resolutionSummary: string;
  timeline: StatusTransition[];
  communications?: Communication[];
  documents?: ComplaintDocument[];
  entityName?: string;
  closureClause?: string;
  closedAt?: string;
  currentStage?: number;
  stages?: StageInfo[];
  priority?: string;
  /** UST111: true only when this closure's clause is appealable by the complainant (15(1)(a)/(b)). */
  appealable?: boolean;
  /**
   * Whether an appeal has already been filed against this complaint.
   *
   * Derived server-side from the complaint's own APPEAL_FILED timeline event, NOT from the APPEALS
   * table — the appeal record itself is Appellate Authority state behind a role guard, and this
   * payload is also served to anonymous track-by-reference callers. The complaint's `status` stays
   * `closed` after an appeal is filed (see AppealWorkflowService), so this flag is the only thing
   * that tells the tracker an appeal exists.
   */
  appealFiled?: boolean;
  appealNumber?: string | null;
  appealFiledAt?: string | null;
  /**
   * Whether the server would accept a withdrawal of this complaint (UST105 scenario 3).
   *
   * Computed server-side from the Scheme's exclusion list so the portal cannot drift from it; the
   * server still enforces the rule on the withdraw call itself. Optional because an older backend
   * omits the field — `undefined` means "unknown", and the withdrawal screen treats it as
   * permitted so a stale server cannot lock a citizen out of a right they have.
   */
  withdrawable?: boolean;
}

export interface StatusTransition {
  fromStatus: string;
  toStatus: string;
  action: string;
  timestamp: string;
}

export interface Communication {
  id: string;
  from: string;
  to: string;
  subject: string;
  message: string;
  timestamp: string;
  type: 'email' | 'sms' | 'system';
}

export interface ComplaintDocument {
  id: string;
  name: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
  url?: string;
}

export type ComplaintCategory = 'ATM' | 'UPI' | 'NEFT_RTGS' | 'LOAN' | 'CREDIT_CARD' | 'DEPOSIT' | 'INSURANCE' | 'GENERAL';
