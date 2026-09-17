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
  toRecipients?: string;
  ccRecipients?: string;
  bccRecipients?: string;
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

export type IgnorePatternType = 'EXACT' | 'DOMAIN' | 'WILDCARD' | 'CONTAINS';
export type IgnoreMatchField = 'FROM' | 'TO' | 'CC' | 'BCC' | 'SUBJECT';

export interface IgnoreListEntry {
  id: number;
  emailPattern: string;
  patternType: IgnorePatternType;
  /** Which header emailPattern is matched against. */
  matchField: IgnoreMatchField;
  toPattern?: string | null;
  ccPattern?: string | null;
  bccPattern?: string | null;
  subjectPattern?: string | null;
  /** Counter-rule: when this matches, the ignore rule is overridden and the draft IS created. */
  exceptionPattern?: string | null;
  reason: string;
  addedBy: string;
  isActive: boolean;
  /** How many emails this rule has suppressed. */
  suppressedCount?: number;
  createdAt: string;
}

export interface IgnoreListRequest {
  emailPattern: string;
  patternType?: IgnorePatternType;
  matchField?: IgnoreMatchField;
  toPattern?: string;
  ccPattern?: string;
  bccPattern?: string;
  subjectPattern?: string;
  exceptionPattern?: string;
  reason?: string;
  isActive?: boolean;
}

export interface IgnoredEmailEntry {
  id: number;
  senderEmail: string;
  subject: string;
  toRecipients?: string | null;
  ccRecipients?: string | null;
  bccRecipients?: string | null;
  messageId?: string | null;
  matchedRuleId: number | null;
  matchedRulePattern: string | null;
  matchedRuleField: string | null;
  matchedRuleType: string | null;
  matchedRuleReason: string | null;
  receivedAt: string;
}

export interface IgnoredEmailReport {
  total: number;
  entries: IgnoredEmailEntry[];
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
