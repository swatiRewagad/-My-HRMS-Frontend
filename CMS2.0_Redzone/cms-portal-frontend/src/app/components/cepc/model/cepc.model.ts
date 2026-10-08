export interface EligibilityQuestionItem {
  key: string;
  label: string;
  type: 'radio' | 'date';
  answer: boolean | null;
  dateValue: string | null;
}

export interface PanelState {
  form: boolean;
  attachments: boolean;
  history: boolean;
  settings: boolean;
}

// Shape of GET /api/v1/complaints/nodal-records. Only the nodal officer contact fields are stored on
// the record itself; the rest is joined off the complaint server-side, so everything here is
// read-only as far as this screen is concerned.
export interface NodalRecord {
  id: number;
  recordNumber: string;
  complaintNumber: string;
  status: string;
  statusLabel: string;
  assignedTo: string;
  slaDays: number | null;
  receiptDate: string;
  subject: string;
  complainant: string;
  mobile: string;
  email: string;
  bankName: string;
  bankCategory: string;
  branchCategory: string;
  branchName: string;
  pincode: string;
  city: string;
  district: string;
  state: string;
  country: string;
  moduleName: string;
  atmComplaint: string;
  designatedOffice: string;
  processingOffice: string;
  noName: string;
  noMobile: string;
  noEmail: string;
  noDesignation: string;
  pnoName: string;
  pnoMobile: string;
  pnoEmail: string;
  // The officer's saved assessment. Dates arrive ISO because the form binds them to native date
  // inputs; notice131ComplyDate is the exception — it is display-only, so the backend sends it
  // already formatted.
  advisoryComplianceDate: string | null;
  disputeAmount: number | null;
  compensationLoss: number | null;
  compensationMental: number | null;
  awardImplementationDate: string | null;
  awardAcceptanceDate: string | null;
  notice131ComplyDate: string | null;
  forwardedToReAt: string | null;
}

/**
 * A person at the entity this office has been dealing with on the complaint.
 *
 * <p>Not a nodal officer. The nodal officer is who the entity has designated, comes from master data and
 * is snapshotted onto the nodal record; these are typed in by the dealing officer, there may be several,
 * and they live in their own table so nothing in RBIO or the dashboard sees them.
 */
export interface ContactPerson {
  id: number;
  complaintNumber: string;
  name: string;
  designation: string | null;
  email: string | null;
  phone: string | null;
  remarks: string | null;
  createdBy: string | null;
  lastModifiedBy: string | null;
  createdAt: string | null;
  lastModifiedAt: string | null;
}

// Shape of GET /api/v1/email-syndication/deo. Keycloak owns the roster; OFFICER_AVAILABILITY owns
// leave state and the per-officer threshold, and currentLoad is a live count of drafts already sitting
// with that DEO — which is what automatic assignment balances on.
export interface DeoUser {
  id: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  leaveReason: string;
  officeCode: string;
  currentLoad: number;
  maxLoad: number;
}

/** A row from the entity typeahead, GET /api/v1/routing/entities/list. */
export interface EntitySearchResult {
  id: number;
  name: string;
  department: string;
  /** The entity's category as the master stores it, e.g. "Public Sector Bank". */
  entityType: string;
  /** The category as the complaint screens label it, e.g. "Nationalised Bank". */
  entityCategory?: string | null;
  /** RBI's sub-classification below the category, e.g. "Loan Company". NBFCs only. */
  entityTypeDetail?: string | null;
  /** What Entity Details shows as Entity Type: the sub-classification, or the category if there is none. */
  entityTypeDisplay?: string | null;
  city?: string | null;
  state?: string | null;
}

/** One entity in full, GET /api/v1/routing/entities/{id}. */
export interface EntityDetail extends EntitySearchResult {
  status?: string | null;
  portalEnabled?: boolean | null;
  nodalOfficerName?: string | null;
  nodalOfficerEmail?: string | null;
  nodalOfficerPhone?: string | null;
  nodalOfficerDesignation?: string | null;
  pnoName?: string | null;
  pnoEmail?: string | null;
  pnoPhone?: string | null;
}

/** One mail on a complaint, GET /api/v1/complaints/{complaintNumber}/emails. */
export interface ComplaintEmail {
  id: number;
  messageId?: string | null;
  threadId?: string | null;
  complaintNumber?: string | null;
  subject: string;
  from: string;
  to: string;
  cc?: string | null;
  bcc?: string | null;
  body?: string | null;
  /** Display-formatted dd-MM-yyyy; sentAt carries the full timestamp. */
  date: string;
  status: string;
  direction?: string | null;
  assignedTo?: string | null;
  sentAt?: string | null;
  updatedAt?: string | null;
  /** Why the last dispatch attempt failed; only populated while FAILED. */
  lastError?: string | null;
  /** The server's word on whether this mail can be replied to, i.e. whether it has actually gone out. */
  canReply: boolean;
  editable: boolean;
  /** True when a failed dispatch can be queued again. */
  canRetry: boolean;
  attachments?: { id?: number; name: string; size: string; url?: string; previewUrl?: string }[];
}

/** The full conversation behind one activity row, GET .../emails/{emailId}. */
export interface ComplaintEmailThread {
  threadId?: string | null;
  complaintNumber?: string | null;
  subject?: string | null;
  status?: string | null;
  messageCount?: number;
  email: ComplaintEmail;
  messages?: ComplaintEmail[];
}
