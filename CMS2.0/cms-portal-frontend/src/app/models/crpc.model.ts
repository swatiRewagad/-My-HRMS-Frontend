export interface ReviewerUser {
  id: string;
  displayName: string;
  email: string;
  isActive: boolean;
  isOnLeave: boolean;
  maxLoad: number;
  currentLoad: number;
  region: string;
  sortOrder: number;
}
