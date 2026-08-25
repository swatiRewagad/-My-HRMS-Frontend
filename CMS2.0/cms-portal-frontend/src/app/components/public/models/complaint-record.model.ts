export interface ComplaintRecord {
  complaintId: string;
  entityName: string;
  complaintDate: string;
  status: string;
  comments: string;
  isDraft?: boolean;
  draftId?: string;
}
