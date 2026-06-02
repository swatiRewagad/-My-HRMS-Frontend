export interface ComplaintRegistrationRequest {
  channel: string;
  category: string;
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  entityName: string;
  entityType: string;
  subject: string;
  description: string;
  amountInvolved?: number;
  transactionDate?: string;
  jurisdictionCode?: string;
}

export interface ComplaintAcknowledgement {
  complaintId: string;
  status: string;
  registeredAt: string;
  slaDueDate: string;
  acknowledgementMessage: string;
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
