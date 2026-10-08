export interface Attachment {
  id: string;
  name: string;
  size: string;
  type: string;
  uploadedAt: string;
  uploadedBy: string;
  url?: string;
}

export interface HistoryEntry {
  id: string;
  status: string;
  statusLabel: string;
  statusColor: string;
  modifiedOn: string;
  modifiedBy: string;
  assignedOfficer: string;
  officerAvatarColor: string;
  description: string;
}

export interface LegalCase {
  id: number | null;
  complaintNumber: string;
  caseNumber: string | null;
  courtName: string | null;
  partiesOfCase: string | null;
  regionOfLegalTeam: string | null;
  rbiFirstRespondent: boolean | null;
  appearanceRequired: boolean | null;
  subjectMatter: string | null;
  advocateName: string | null;
  assistantLegalAdvisor: string | null;
  /** ISO {@code yyyy-MM-dd}, matching an {@code <input type="date">}'s own value format. */
  nextHearingDate: string | null;
  presentStatus: string | null;
  actionTakenSoFar: string | null;
  actionToBeTaken: string | null;
  monetaryClaimDetails: string | null;
  createdBy: string | null;
  lastModifiedBy: string | null;
  createdAt: string | null;
  lastModifiedAt: string | null;
}

export interface LegalCaseFormShape {
  caseNumber: string;
  courtName: string;
  partiesOfCase: string;
  regionOfLegalTeam: string;
  rbiFirstRespondent: boolean | null;
  appearanceRequired: boolean | null;
  subjectMatter: string;
  advocateName: string;
  assistantLegalAdvisor: string;
  nextHearingDate: string;
  presentStatus: string;
  actionTakenSoFar: string;
  actionToBeTaken: string;
  monetaryClaimDetails: string;
}

export type RightSidePanelKey = 'pastComplaints' | 'attachments' | 'history' | 'legalCase' | null;
