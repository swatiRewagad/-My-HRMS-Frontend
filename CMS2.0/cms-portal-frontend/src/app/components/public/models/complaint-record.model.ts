export interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  closureClause?: string;
  closureDate?: string;
  acknowledgementLetterUrl?: string;
  closureLetterUrl?: string;
  isDraft?: boolean;
  draftId?: string;
}
