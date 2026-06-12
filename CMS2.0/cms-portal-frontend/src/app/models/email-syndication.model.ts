export interface EmailDraft {
  id: number;
  draftId: string;
  displayId: string;
  messageId: string;
  senderEmail: string;
  subject: string;
  body: string;
  originalBody?: string | null;
  complainantName: string;
  complainantPhone: string;
  cpgramsNumber: string;
  complaintSummary: string;
  category: string;
  modeOfReceipt: string;
  status: EmailDraftStatus;
  assignedTo: string;
  parentComplaintId: string;
  isDuplicate: boolean;
  ocrProcessed: boolean;
  ocrConfidence: number;
  receivedAt: string;
  createdAt: string;
  processedBy: string;
  convertedComplaintId: string;
  attachments: EmailAttachment[];
  suggestedRelated: EmailDraft[];
  detectedLanguage?: string;
  languageName?: string;
  isVernacular?: boolean;
  translationConfidence?: number;
}

export type EmailDraftStatus = 'PENDING' | 'ASSIGNED' | 'IN_PROGRESS' | 'CONVERTED' | 'DUPLICATE' | 'IGNORED' | 'REJECTED';

export interface EmailAttachment {
  id: number;
  fileName: string;
  fileType: string;
  fileSize: number;
  ocrText: string;
  ocrConfidence: number;
  createdAt: string;
}

export interface EmailIngestRequest {
  senderEmail: string;
  subject: string;
  body: string;
  messageId?: string;
  attachmentPaths?: string[];
}

export interface EmailDraftUpdateRequest {
  complainantName?: string;
  complainantPhone?: string;
  cpgramsNumber?: string;
  complaintSummary?: string;
  category?: string;
  subject?: string;
  body?: string;
  entityName?: string;
  entityType?: string;
}

export interface IgnoreListEntry {
  id: number;
  emailPattern: string;
  patternType: 'EXACT' | 'DOMAIN' | 'WILDCARD';
  reason: string;
  addedBy: string;
  isActive: boolean;
  createdAt: string;
}

export interface IgnoreListRequest {
  emailPattern: string;
  patternType?: 'EXACT' | 'DOMAIN' | 'WILDCARD';
  reason?: string;
}

export interface DeoUser {
  id: number;
  userId: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  maxThreshold: number;
  currentAssignedCount: number;
  sortOrder: number;
}

export interface EmailQueueStats {
  totalDrafts: number;
  pendingCount: number;
  assignedCount: number;
  inProgressCount: number;
  convertedCount: number;
  duplicateCount: number;
  ignoredCount: number;
  activeDeoCount: number;
}
