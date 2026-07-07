import { APIRequestContext } from '@playwright/test';

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8082';

export async function getComplaints(request: APIRequestContext, params?: Record<string, string>) {
  const query = params ? '?' + new URLSearchParams(params).toString() : '';
  return request.get(`${API_BASE}/api/v1/complaints${query}`);
}

export async function getComplaintById(request: APIRequestContext, id: string) {
  return request.get(`${API_BASE}/api/v1/complaints/${id}`);
}

export async function getMreAssessment(request: APIRequestContext, complaintId: string) {
  return request.get(`${API_BASE}/api/v1/copilot/maintainability/${complaintId}`);
}

export async function getRbioTasks(request: APIRequestContext, officer: string) {
  return request.get(`${API_BASE}/api/v1/workflow/rbio/all-tasks?officer=${officer}`);
}

export async function getCepcTasks(request: APIRequestContext, officer: string) {
  return request.get(`${API_BASE}/api/v1/workflow/cepc/tasks?officer=${officer}`);
}

export async function fileComplaint(request: APIRequestContext, payload: Record<string, unknown>) {
  return request.post(`${API_BASE}/api/v1/complaints/file`, { data: payload });
}

export async function recordDecision(
  request: APIRequestContext,
  complaintId: number,
  decision: { determination: string; officer: string; rationale: string }
) {
  return request.post(`${API_BASE}/api/v1/copilot/maintainability/${complaintId}/decision`, {
    data: decision,
  });
}
