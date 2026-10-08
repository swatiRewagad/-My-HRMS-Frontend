export interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  closureClause?: string;
  closureDate?: string;
  appealable?: boolean;
  acknowledgementLetterUrl?: string;
  closureLetterUrl?: string;
  isDraft?: boolean;
  draftId?: string;
}
