export interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  isDraft?: boolean;
  draftId?: string;
}
