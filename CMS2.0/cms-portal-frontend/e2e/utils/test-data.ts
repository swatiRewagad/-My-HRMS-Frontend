import { APIRequestContext } from '@playwright/test';

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8082';

/**
 * Default complaint payload for CEPC workflow tests.
 */
export interface CreateComplaintPayload {
  complainantName: string;
  complainantEmail: string;
  complainantPhone: string;
  complainantAddress: string;
  subject: string;
  description: string;
  entityName: string;
  priority: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  filingType: 'CEPC_MANUAL' | 'PHYSICAL_LETTER' | 'EMAIL';
  createdBy: string;
}

/**
 * Generates a unique test complaint payload with sensible defaults.
 * Override any field by passing partial data.
 */
export function buildComplaint(overrides: Partial<CreateComplaintPayload> = {}): CreateComplaintPayload {
  const suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  return {
    complainantName: `Test Complainant ${suffix}`,
    complainantEmail: `test_${suffix}@example.com`,
    complainantPhone: '9876543210',
    complainantAddress: '123 Test Street, Mumbai',
    subject: `E2E Test Complaint ${suffix}`,
    description: `Automated end-to-end test complaint created at ${new Date().toISOString()}`,
    entityName: 'Test Bank Ltd',
    priority: 'MEDIUM',
    filingType: 'CEPC_MANUAL',
    createdBy: 'cepc_do',
    ...overrides,
  };
}

/**
 * Creates a complaint via the backend API directly.
 * Returns the created complaint data (including complaintNumber).
 */
export async function createTestComplaint(
  request: APIRequestContext,
  overrides: Partial<CreateComplaintPayload> = {},
  token?: string
): Promise<{ complaintNumber: string; complaintId: string; status: string; [key: string]: unknown }> {
  const payload = buildComplaint(overrides);
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(`${API_BASE}/api/v1/workflow/cepc/create-complaint`, {
    data: payload,
    headers,
  });

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Failed to create test complaint: ${response.status()} - ${body}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Performs a workflow action on a complaint via the API.
 */
export async function performAction(
  request: APIRequestContext,
  complaintNumber: string,
  action: string,
  actor: string,
  remarks = 'E2E automated test action',
  extras: Record<string, string> = {},
  token?: string
): Promise<{ newStatus: string; [key: string]: unknown }> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const body = {
    action,
    remarks,
    actor,
    ...extras,
  };

  const response = await request.post(
    `${API_BASE}/api/v1/workflow/cepc/action/${complaintNumber}`,
    { data: body, headers }
  );

  if (!response.ok()) {
    const text = await response.text();
    throw new Error(`Action ${action} failed: ${response.status()} - ${text}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Fetches a complaint by number from the API.
 */
export async function getComplaint(
  request: APIRequestContext,
  complaintNumber: string,
  token?: string
): Promise<Record<string, unknown>> {
  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`, {
    headers,
  });

  if (!response.ok()) {
    throw new Error(`Failed to fetch complaint ${complaintNumber}: ${response.status()}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Cleanup: Closes a test complaint (admin force-close) so it does not pollute future test runs.
 */
export async function cleanupComplaint(
  request: APIRequestContext,
  complaintNumber: string,
  token?: string
): Promise<void> {
  try {
    await performAction(
      request,
      complaintNumber,
      'CLOSE_COMPLAINT',
      'cepc_admin',
      'E2E cleanup — auto-close',
      {},
      token
    );
  } catch {
    // Best-effort cleanup — ignore errors
  }
}

/**
 * Utility to advance a complaint through the workflow to a target status.
 * Useful for setting up preconditions in tests.
 */
export async function advanceToStatus(
  request: APIRequestContext,
  complaintNumber: string,
  targetStatus: string,
  token?: string
): Promise<void> {
  const transitions: Record<string, { action: string; actor: string }[]> = {
    in_progress: [
      { action: 'ACCEPT', actor: 'cepc_do' },
    ],
    reviewer_review: [
      { action: 'ACCEPT', actor: 'cepc_do' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do' },
    ],
    incharge_review: [
      { action: 'ACCEPT', actor: 'cepc_do' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer' },
    ],
    awaiting_closure: [
      { action: 'ACCEPT', actor: 'cepc_do' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer' },
      { action: 'APPROVE_CLOSURE', actor: 'cepc_incharge' },
    ],
    closed: [
      { action: 'ACCEPT', actor: 'cepc_do' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer' },
      { action: 'APPROVE_CLOSURE', actor: 'cepc_incharge' },
      { action: 'CLOSE_COMPLAINT', actor: 'cepc_ca' },
    ],
  };

  const steps = transitions[targetStatus];
  if (!steps) {
    throw new Error(`No predefined transition path to status: ${targetStatus}`);
  }

  for (const step of steps) {
    await performAction(request, complaintNumber, step.action, step.actor, 'E2E advance', {}, token);
  }
}

// ────────────────────────────────────────────────────────────────────────────
// RBIO Test Data Helpers
// ────────────────────────────────────────────────────────────────────────────

/**
 * Creates an RBIO complaint via the backend API directly.
 * Returns the created complaint data (including complaintNumber).
 */
export async function createRbioComplaint(
  request: APIRequestContext,
  overrides: Partial<CreateComplaintPayload> = {},
  token?: string
): Promise<{ complaintNumber: string; complaintId: string; status: string; [key: string]: unknown }> {
  const suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const payload = {
    complainantName: `RBIO Test Complainant ${suffix}`,
    complainantEmail: `rbio_test_${suffix}@example.com`,
    complainantPhone: '9876543210',
    complainantAddress: '456 Test Avenue, Mumbai',
    subject: `RBIO E2E Test Complaint ${suffix}`,
    description: `Automated RBIO end-to-end test complaint created at ${new Date().toISOString()}`,
    entityName: 'Test Bank Ltd',
    priority: 'MEDIUM' as const,
    department: 'RBIO',
    filingType: 'CEPC_MANUAL' as const,
    createdBy: 'rbio.officer',
    ...overrides,
  };

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(`${API_BASE}/api/v1/workflow/rbio/create-complaint`, {
    data: payload,
    headers,
  });

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Failed to create RBIO test complaint: ${response.status()} - ${body}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Performs a workflow action on an RBIO complaint via the API.
 */
export async function performRbioAction(
  request: APIRequestContext,
  complaintNumber: string,
  action: string,
  actor: string,
  remarks = 'E2E automated RBIO test action',
  extras: Record<string, unknown> = {},
  token?: string
): Promise<{ newStatus: string; [key: string]: unknown }> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const body = {
    action,
    remarks,
    actor,
    ...extras,
  };

  const response = await request.post(
    `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
    { data: body, headers }
  );

  if (!response.ok()) {
    const text = await response.text();
    throw new Error(`RBIO Action ${action} failed: ${response.status()} - ${text}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Cleanup: Closes an RBIO test complaint via admin force-close.
 */
export async function cleanupRbioComplaint(
  request: APIRequestContext,
  complaintNumber: string,
  token?: string
): Promise<void> {
  try {
    await performRbioAction(
      request,
      complaintNumber,
      'CLOSE_COMPLAINT',
      'rbio.admin',
      'E2E cleanup — auto-close',
      {},
      token
    );
  } catch {
    // Best-effort cleanup — ignore errors
  }
}

// ────────────────────────────────────────────────────────────────────────────
// RE (Regulated Entity) Test Data Helpers
// ────────────────────────────────────────────────────────────────────────────

/**
 * Creates a complaint and forwards it to a Regulated Entity.
 * Returns the complaint data with status forwarded_to_re.
 */
export async function createForwardedComplaint(
  request: APIRequestContext,
  entityCode: string,
  overrides: Partial<CreateComplaintPayload> = {},
  token?: string
): Promise<{ complaintNumber: string; complaintId: string; status: string; [key: string]: unknown }> {
  // Step 1: Create the complaint
  const result = await createTestComplaint(request, {
    entityName: entityCode,
    subject: `RE Forwarded Complaint ${Date.now().toString(36)}`,
    ...overrides,
  }, token);

  // Step 2: Forward it to the RE
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(
    `${API_BASE}/api/v1/workflow/cepc/action/${result.complaintNumber}`,
    {
      data: {
        action: 'FORWARD_TO_RE',
        remarks: 'E2E test — forwarding to RE',
        actor: 'cepc_do',
        entityCode,
      },
      headers,
    }
  );

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Failed to forward complaint to RE: ${response.status()} - ${body}`);
  }

  const json = await response.json();
  return { ...result, status: 'forwarded_to_re', ...(json.data || json) };
}

/**
 * Submits an RE response to a forwarded complaint via the API.
 */
export async function respondToComplaint(
  request: APIRequestContext,
  complaintNumber: string,
  responseText: string,
  token?: string
): Promise<{ newStatus: string; [key: string]: unknown }> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(
    `${API_BASE}/api/v1/workflow/re/respond/${complaintNumber}`,
    {
      data: {
        response: responseText,
        actor: 're.nodal',
        remarks: 'RE response submitted via E2E test',
      },
      headers,
    }
  );

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Failed to submit RE response: ${response.status()} - ${body}`);
  }

  const json = await response.json();
  return json.data || json;
}

// ────────────────────────────────────────────────────────────────────────────
// AA (Appellate Authority) Test Data Helpers
// ────────────────────────────────────────────────────────────────────────────

export interface FileAppealPayload {
  originalComplaintNumber: string;
  appealType: 'APPEAL' | 'REPRESENTATION';
  grounds: string;
  reliefSought: string;
  appellantName: string;
  appellantEmail: string;
  appellantPhone: string;
  compensationClaimed?: number;
}

/**
 * Files an appeal via the AA API.
 * Returns the appeal data including appealNumber.
 */
export async function fileAppeal(
  request: APIRequestContext,
  originalComplaintNumber: string,
  overrides: Partial<FileAppealPayload> = {},
  token?: string
): Promise<{ appealNumber: string; appealId: string; status: string; [key: string]: unknown }> {
  const suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const payload: FileAppealPayload = {
    originalComplaintNumber,
    appealType: 'APPEAL',
    grounds: `E2E test appeal grounds ${suffix}`,
    reliefSought: `Compensation and corrective action ${suffix}`,
    appellantName: `Test Appellant ${suffix}`,
    appellantEmail: `appellant_${suffix}@example.com`,
    appellantPhone: '9876543210',
    ...overrides,
  };

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(`${API_BASE}/api/v1/aa/appeals/file`, {
    data: payload,
    headers,
  });

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Failed to file appeal: ${response.status()} - ${body}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Performs an action on an appeal in the AA workflow.
 */
export async function performAppealAction(
  request: APIRequestContext,
  appealNumber: string,
  action: string,
  params: Record<string, unknown> = {},
  token?: string
): Promise<{ newStatus: string; [key: string]: unknown }> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const body = {
    action,
    remarks: `E2E automated AA action: ${action}`,
    ...params,
  };

  const response = await request.post(
    `${API_BASE}/api/v1/aa/appeals/action/${appealNumber}`,
    { data: body, headers }
  );

  if (!response.ok()) {
    const text = await response.text();
    throw new Error(`AA Action ${action} failed: ${response.status()} - ${text}`);
  }

  const json = await response.json();
  return json.data || json;
}

/**
 * Advance an RBIO complaint through the workflow to a target status.
 */
export async function advanceRbioToStatus(
  request: APIRequestContext,
  complaintNumber: string,
  targetStatus: string,
  token?: string
): Promise<void> {
  const transitions: Record<string, { action: string; actor: string; extras?: Record<string, unknown> }[]> = {
    in_progress: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
    ],
    escalated: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'ESCALATE', actor: 'rbio.officer' },
    ],
    conciliation: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'FORWARD_TO_CONCILIATION', actor: 'rbio.officer' },
    ],
    adjudication: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'ESCALATE', actor: 'rbio.officer' },
      { action: 'FORWARD_TO_ADJUDICATION', actor: 'rbio.supervisor' },
    ],
    resolved: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'RESOLVE', actor: 'rbio.officer' },
    ],
    closed: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'RESOLVE', actor: 'rbio.officer' },
      { action: 'CLOSE_COMPLAINT', actor: 'rbio.admin' },
    ],
    conciliated: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'FORWARD_TO_CONCILIATION', actor: 'rbio.officer' },
      { action: 'CONCILIATION_SUCCESS', actor: 'rbio.conciliator', extras: { compensationAmount: 50000 } },
    ],
    adjudicated: [
      { action: 'ACCEPT', actor: 'rbio.officer' },
      { action: 'ESCALATE', actor: 'rbio.officer' },
      { action: 'FORWARD_TO_ADJUDICATION', actor: 'rbio.supervisor' },
      { action: 'ADJUDICATION_AWARD', actor: 'rbio.adjudicator', extras: { awardAmount: 100000 } },
    ],
  };

  const steps = transitions[targetStatus];
  if (!steps) {
    throw new Error(`No predefined RBIO transition path to status: ${targetStatus}`);
  }

  for (const step of steps) {
    await performRbioAction(
      request,
      complaintNumber,
      step.action,
      step.actor,
      'E2E advance',
      step.extras || {},
      token
    );
  }
}
