export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  correlationId: string;
  timestamp: string;
}
