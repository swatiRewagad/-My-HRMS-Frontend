import { execFileSync } from 'node:child_process';
import { APIRequestContext, Page } from '@playwright/test';

const API_BASE = process.env.API_BASE_URL || 'http://localhost:8082';

/**
 * Dev identity headers for an actor, derived from the actor's own name.
 *
 * The AOP role guards used to fail open — a request with no roles was allowed straight through — so
 * these helpers never had to say who they were. Now that a roleless request is correctly rejected,
 * each call has to identify itself.
 *
 * The role is derived from the actor rather than hardcoded so a step run as `cepc_reviewer_001`
 * still authenticates AS a reviewer. Hardcoding one privileged role would let a test pass while
 * silently exercising the wrong identity, which would hide real authorization regressions.
 *
 * The backend honours X-User-* only under the dev-local profile; the enforcing profile ignores them.
 */
export type WorkflowScope = 'CEPC' | 'RBIO' | 'AA' | 'RE';

export function identityHeadersFor(actor: string, scope: WorkflowScope = 'CEPC'): Record<string, string> {
  const a = (actor || '').toLowerCase();

  // The actor name alone is ambiguous: `admin_001` drives both CEPC and RBIO workflows, and each
  // office's guard accepts only its own roles. The calling helper knows which endpoint it is hitting,
  // so the scope disambiguates. Specific names still win over the scope default.
  let role: string;
  if (a.includes('re_pno') || a.includes('pno')) role = 'RE_PNO';
  else if (a.startsWith('re_') || a.startsWith('re.') || a.includes('nodal')) role = 'RE_NODAL_OFFICER';
  // AA is matched before the generic `reviewer`/`authority`/`admin` arms below, because the real
  // accounts are aa_reviewer_001 / aa_secretariat_001 / aa_admin_001: `aa_reviewer_001` would
  // otherwise fall through to CEPC_REVIEWER and be rejected by the AA guard, and the failure would
  // look like a broken assertion rather than a wrong identity.
  else if (a.includes('aa_secretariat') || a.includes('secretariat') || a.includes('authority')) role = 'AA_SECRETARIAT';
  else if (a.includes('aa_reviewer') || a.includes('bench')) role = 'AA_REVIEWER';
  else if (a.includes('aa_admin')) role = 'AA_ADMIN';
  else if (a.includes('aa_do') || a.includes('registrar')) role = 'AA_DO';
  // ORBIO is the RBIO-module Ombudsman, so ORBIO actors carry RBIO roles by design.
  else if (a.includes('orbio_admin')) role = 'RBIO_ADMIN';
  else if (a.includes('orbio')) role = 'RBIO_OFFICER';
  else if (a.includes('conciliator')) role = scope === 'CEPC' ? 'CEPC_CONCILIATOR' : 'RBIO_CONCILIATOR';
  else if (a.includes('adjudicator')) role = scope === 'CEPC' ? 'CEPC_ADJUDICATOR' : 'RBIO_ADJUDICATOR';
  else if (a.includes('supervisor')) role = scope === 'CEPC' ? 'CEPC_SUPERVISOR' : 'RBIO_SUPERVISOR';
  else if (a.includes('reviewer')) role = 'CEPC_REVIEWER';
  else if (a.includes('incharge')) role = 'CEPC_INCHARGE';
  else if (a.includes('closing')) role = 'CEPC_CLOSING_AUTHORITY';
  else if (a.includes('contact')) role = 'CEPC_CONTACT_PERSON';
  else if (a.includes('rbio')) role = 'RBIO_OFFICER';
  else if (a.includes('admin')) role = `${scope}_ADMIN`;
  else role = scope === 'CEPC' ? 'CEPC_DO' : `${scope}_OFFICER`;

  return {
    'X-User-Id': actor,
    'X-User-Name': actor,
    'X-User-Roles': role,
  };
}

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
    createdBy: 'cepc_do_001',
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
  // The CEPC role guard used to fail open: a request carrying no roles was allowed through. It now
  // rejects one, which is the whole point of the control — so seeding has to identify itself the way
  // a real CEPC user would. A caller supplying `token` overrides this with a real JWT.
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-User-Id': 'cepc_do1',
    'X-User-Name': 'CEPC Dealing Officer',
    'X-User-Roles': 'CEPC_DO',
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
 * Obtains a citizen session token by solving the CAPTCHA and OTP through the real
 * /api/v1/citizen/auth/* endpoints. Returns null when the backend does not expose
 * devOtp (i.e. cms.auth.otp.dev-auto-populate is false), so callers can test.skip().
 */
export async function loginCitizen(
  request: APIRequestContext,
  mobile: string
): Promise<string | null> {
  const captchaRes = await request.get(`${API_BASE}/api/v1/citizen/auth/captcha?type=MATH`);
  if (!captchaRes.ok()) return null;
  const captcha = await captchaRes.json();

  // CaptchaService.generateMathCaptcha emits word form only, e.g. "What is 13 plus 10?"
  const match = /(\d+)\s*(plus|minus)\s*(\d+)/i.exec(captcha.audioQuestion || '');
  if (!match) return null;
  const [, a, op, b] = match;
  const x = Number(a);
  const y = Number(b);
  const answer = op.toLowerCase() === 'plus' ? x + y : x - y;

  const otpRes = await request.post(`${API_BASE}/api/v1/citizen/auth/send-otp`, {
    data: { mobile, captchaToken: captcha.token, captchaAnswer: String(answer) },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!otpRes.ok()) return null;
  const otpBody = await otpRes.json();
  if (!otpBody.devOtp) return null;

  const verifyRes = await request.post(`${API_BASE}/api/v1/citizen/auth/verify-otp`, {
    data: { mobile, otp: otpBody.devOtp, sessionId: otpBody.sessionId },
    headers: { 'Content-Type': 'application/json' },
  });
  if (!verifyRes.ok()) return null;
  return (await verifyRes.json()).token || null;
}

/**
 * Seeds a citizen session into sessionStorage so guarded public routes
 * (/withdraw, /feedback, /file-complaint) render instead of redirecting to login.
 * Must run on a page already served from the app origin.
 */
export async function seedCitizenSession(
  page: Page,
  mobile: string,
  token: string
): Promise<void> {
  await page.evaluate(([identifier, tok]) => {
    sessionStorage.setItem('cms_public_session', JSON.stringify({
      identifier, token: tok, startedAt: Date.now(),
    }));
    sessionStorage.setItem('cms_public_last_activity', String(Date.now()));
  }, [mobile, token]);
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
    ...identityHeadersFor(actor),
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
      'cepc_admin_001',
      'E2E cleanup — auto-close',
      {},
      token
    );
  } catch {
    // Best-effort cleanup — ignore errors
  }
}

/**
 * The clause a CEPC closure is recorded under when a test does not care which one.
 *
 * 15(1)(a) exists in CLOSURE_CLAUSE_MASTER and is appealable by a complainant, so a complaint closed
 * through this helper can actually be appealed afterwards.
 *
 * WHY THIS IS NEEDED: CepcWorkflowService treats `closureClause` as OPTIONAL on CLOSE_COMPLAINT
 * (CepcWorkflowService.java:386-389 only sets it when non-empty), so a complaint closed without one
 * has closure_clause = NULL. AA then correctly FAILS CLOSED — the clause is the entire input to
 * deciding Appeal vs Representation — and POST /appeals/file answers 503
 * appeal.error_clause_not_configured. Every test that closed a complaint here and then filed an
 * appeal was failing for this SETUP reason, not for the behaviour it was asserting.
 *
 * This helper fixes the TESTS. The same gap in real data (1646 of 1647 closed complaints carry no
 * clause and therefore cannot be appealed at all) is a PRODUCT defect, escalated separately: which
 * clause is mandatory at closure is a legal question, not one for a test fixture to settle.
 */
export const DEFAULT_CLOSURE_CLAUSE = '15(1)(a)';

/**
 * Utility to advance a complaint through the workflow to a target status.
 * Useful for setting up preconditions in tests.
 *
 * `extras` is merged into the FINAL step's params, so a caller can override the closure clause (or
 * pass anything else that action accepts) without restating the transition path.
 */
export async function advanceToStatus(
  request: APIRequestContext,
  complaintNumber: string,
  targetStatus: string,
  token?: string,
  extras: Record<string, string> = {}
): Promise<void> {
  const transitions: Record<string, { action: string; actor: string }[]> = {
    in_progress: [
      { action: 'ACCEPT', actor: 'cepc_do_001' },
    ],
    reviewer_review: [
      { action: 'ACCEPT', actor: 'cepc_do_001' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do_001' },
    ],
    incharge_review: [
      { action: 'ACCEPT', actor: 'cepc_do_001' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do_001' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer_001' },
    ],
    awaiting_closure: [
      { action: 'ACCEPT', actor: 'cepc_do_001' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do_001' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer_001' },
      { action: 'APPROVE_CLOSURE', actor: 'cepc_incharge_001' },
    ],
    closed: [
      { action: 'ACCEPT', actor: 'cepc_do_001' },
      { action: 'SUBMIT_FOR_REVIEW', actor: 'cepc_do_001' },
      { action: 'APPROVE_REVIEW', actor: 'cepc_reviewer_001' },
      { action: 'APPROVE_CLOSURE', actor: 'cepc_incharge_001' },
      { action: 'CLOSE_COMPLAINT', actor: 'cepc_closing_001' },
    ],
  };

  const steps = transitions[targetStatus];
  if (!steps) {
    throw new Error(`No predefined transition path to status: ${targetStatus}`);
  }

  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];

    const params: Record<string, string> = {};
    if (step.action === 'CLOSE_COMPLAINT') {
      params['closureClause'] = DEFAULT_CLOSURE_CLAUSE;
    }
    if (i === steps.length - 1) {
      Object.assign(params, extras);
    }

    await performAction(request, complaintNumber, step.action, step.actor, 'E2E advance', params, token);
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
    createdBy: 'rbio_officer_001',
    ...overrides,
  };

  // Same reason as createTestComplaint: the RBIO guard no longer fails open on a roleless request.
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...identityHeadersFor('rbio.officer', 'RBIO'),
    'X-User-Id': 'rbio.officer',
    'X-User-Name': 'RBIO Officer',
    'X-User-Roles': 'RBIO_OFFICER',
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
    ...identityHeadersFor(actor, 'RBIO'),
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
      'admin_001',
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
 * Resolves a REGULATED_ENTITIES row id from its name via the RE portal profile endpoint.
 *
 * The name → id mapping is not exposed by any admin/lookup endpoint, but /re-portal/profile
 * already performs exactly this lookup (findByNameNormalized) and returns the row, so it doubles
 * as the resolver. Dev identity headers are used because seeding has no JWT of its own.
 */
export async function resolveRegulatedEntityId(
  request: APIRequestContext,
  entityCode: string,
  token?: string
): Promise<number | null> {
  const headers: Record<string, string> = {
    ...identityHeadersFor('re_pno_001', 'RE'),
    'X-Entity-Code': entityCode,
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await request.get(`${API_BASE}/api/v1/re-portal/profile`, { headers });
  if (!res.ok()) return null;
  const json = await res.json();
  const id = (json.data || json)?.id;
  return typeof id === 'number' ? id : null;
}

/**
 * Creates the ReResponseTracker row that every RE-portal read and write depends on.
 *
 * No CEPC/RBIO workflow action creates this row — FORWARD_DEPT only flips status to 'forwarded'.
 * The only writer in the codebase is ReResponsivenessService.trackForwarding, reachable solely via
 * POST /api/v1/triage/re-track/{complaintId}. Without it, GET /re-portal/complaints/{n} 404s with
 * "No response tracker for complaint", the detail page renders "Complaint not found", and the
 * respond endpoint 404s too. Seeding it here reproduces what a production forward is expected to do.
 */
export async function seedReResponseTracker(
  request: APIRequestContext,
  complaintId: number | string,
  entityCode: string,
  token?: string
): Promise<boolean> {
  const entityId = await resolveRegulatedEntityId(request, entityCode, token);
  if (entityId == null) return false;

  const headers: Record<string, string> = {};
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const res = await request.post(
    `${API_BASE}/api/v1/triage/re-track/${complaintId}?regulatedEntityId=${entityId}`,
    { headers }
  );
  return res.ok();
}

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
  // Create a complaint that's already in "forwarded" state for the entity
  // First accept, then forward via FORWARD_DEPT action
  const result = await createTestComplaint(request, {
    entityName: entityCode,
    subject: `RE Forwarded Complaint ${Date.now().toString(36)}`,
    ...overrides,
  }, token);

  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...identityHeadersFor('cepc_do_001'),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  // Accept the complaint first
  await request.post(
    `${API_BASE}/api/v1/workflow/cepc/action/${result.complaintNumber}`,
    {
      data: { action: 'ACCEPT', remarks: 'E2E setup', actor: 'cepc_do_001' },
      headers,
    }
  );

  // Forward to RE via FORWARD_DEPT (sets status to forwarded)
  const response = await request.post(
    `${API_BASE}/api/v1/workflow/cepc/action/${result.complaintNumber}`,
    {
      data: {
        action: 'FORWARD_DEPT',
        remarks: 'E2E test — forwarding to RE for response',
        actor: 'cepc_do_001',
        targetDepartment: entityCode,
      },
      headers,
    }
  );

  if (!response.ok()) {
    const body = await response.text();
    throw new Error(`Failed to forward complaint to RE: ${response.status()} - ${body}`);
  }

  // FORWARD_DEPT sets status='forwarded' but does NOT create the ReResponseTracker that the whole
  // RE portal is keyed on, so the complaint would be invisible to the entity it was just forwarded
  // to. Seeded explicitly here so RE-side tests exercise the portal rather than that gap.
  if (result.complaintId != null) {
    await seedReResponseTracker(request, result.complaintId as string | number, entityCode, token);
  }

  const json = await response.json();
  return { ...result, status: json.data?.newStatus || 'forwarded', ...(json.data || json) };
}

/**
 * Submits an RE response to a forwarded complaint via the API.
 */
export async function respondToComplaint(
  request: APIRequestContext,
  complaintNumber: string,
  responseText: string,
  token?: string,
  entityCode?: string
): Promise<{ newStatus: string; [key: string]: unknown }> {
  // RE callers are entity-scoped, and the portal correctly refuses a complaint belonging to a
  // different entity — so the code has to match the complaint. It cannot be discovered from the API
  // (the complaint detail response does not expose entityCode), so the caller passes it; the default
  // matches createForwardedComplaint's own default entity.
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...identityHeadersFor('re_nodal_001', 'RE'),
    'X-Entity-Code': entityCode || process.env['RE_ENTITY_CODE'] || 'TEST_BANK_001',
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(
    `${API_BASE}/api/v1/re-portal/complaints/${complaintNumber}/respond`,
    {
      data: {
        response: responseText,
        actor: 're_nodal_001',
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
  classificationType: 'APPEAL' | 'REPRESENTATION';
  appealGround: string;
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
    classificationType: 'APPEAL',
    appealGround: `E2E test appeal grounds ${suffix}`,
    reliefSought: `Compensation and corrective action ${suffix}`,
    appellantName: `Test Appellant ${suffix}`,
    appellantEmail: `appellant_${suffix}@example.com`,
    appellantPhone: '9876543210',
    ...overrides,
  };

  // POST /api/v1/appeals/file is a multipart endpoint: text fields bind via @RequestParam and files
  // via @RequestPart. Posting a JSON body meant `complaintNumber` was never bound, so the request
  // failed argument resolution before reaching the controller. Sent as form fields instead.
  const headers: Record<string, string> = {
    ...identityHeadersFor('aa_registrar_001', 'AA'),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await request.post(`${API_BASE}/api/v1/appeals/file`, {
    multipart: {
      complaintNumber: payload.originalComplaintNumber,
      ground: payload.appealGround,
      details: payload.reliefSought,
      reliefSought: payload.reliefSought,
      classification: payload.classificationType,
    },
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
  // This helper takes no actor argument, so the identity comes from the caller's own `params.actor`
  // when supplied, falling back to the registrar who owns most AA transitions.
  const appealActor = typeof params['actor'] === 'string' && params['actor']
    ? (params['actor'] as string)
    : 'aa_registrar_001';
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...identityHeadersFor(appealActor, 'AA'),
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
    `${API_BASE}/api/v1/appeals/${appealNumber}/action`,
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
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
    ],
    escalated: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'ESCALATE', actor: 'rbio_officer_001' },
    ],
    conciliation: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'FORWARD_TO_CONCILIATION', actor: 'rbio_officer_001' },
    ],
    adjudication: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'ESCALATE', actor: 'rbio_officer_001' },
      { action: 'FORWARD_TO_ADJUDICATION', actor: 'rbio_supervisor_001' },
    ],
    resolved: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'RESOLVE', actor: 'rbio_officer_001' },
    ],
    closed: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'RESOLVE', actor: 'rbio_officer_001' },
      { action: 'CLOSE_COMPLAINT', actor: 'admin_001' },
    ],
    conciliated: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'FORWARD_TO_CONCILIATION', actor: 'rbio_officer_001' },
      { action: 'CONCILIATION_SUCCESS', actor: 'rbio_conciliator_001', extras: { compensationAmount: 50000 } },
    ],
    adjudicated: [
      { action: 'ACCEPT', actor: 'rbio_officer_001' },
      { action: 'ESCALATE', actor: 'rbio_officer_001' },
      { action: 'FORWARD_TO_ADJUDICATION', actor: 'rbio_supervisor_001' },
      { action: 'ADJUDICATION_AWARD', actor: 'rbio_adjudicator_001', extras: { awardAmount: 100000 } },
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

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// S3 — RBIO ladder, send-backs, additional entities, legal case, reopen.
//
// Appended, per the parallel-session contract: identityHeadersFor above is NOT rewritten. It cannot
// serve these tests anyway, and the reason is a trap worth stating.
//
// identityHeadersFor('rbio_reviewer_001', 'RBIO') returns CEPC_REVIEWER, because the `reviewer` arm
// is matched before any RBIO arm. The RBIO guard then answers 403, which looks exactly like a broken
// feature. The ladder roles (RBIO_DEALING_OFFICIAL, RBIO_REVIEWER, RBIO_DEPUTY_OMBUDSMAN,
// RBIO_OMBUDSMAN) are therefore stated EXPLICITLY here rather than derived from a username.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

/** The RBIO ladder roles, as the transition table names them. */
export type RbioLadderRole =
  | 'RBIO_DEALING_OFFICIAL'
  | 'RBIO_REVIEWER'
  | 'RBIO_DEPUTY_OMBUDSMAN'
  | 'RBIO_OMBUDSMAN'
  | 'RBIO_ADMIN'
  | 'RBIO_ADJUDICATOR';

/**
 * Real Keycloak accounts for each ladder role.
 *
 * These usernames EXIST in realm `cms` — verified against the admin API. That matters for send-backs:
 * the previous-holder lookup asks Keycloak whether the officer is still enabled and treats an unknown
 * username as inactive (it fails closed). A fabricated name therefore makes an auto-send-back refuse
 * with 422 and the test looks like a product bug when it is a fixture bug.
 */
export const RBIO_LADDER_ACTORS: Record<RbioLadderRole, string> = {
  RBIO_DEALING_OFFICIAL: 'rbio_do_001',
  RBIO_REVIEWER: 'rbio_reviewer_001',
  RBIO_DEPUTY_OMBUDSMAN: 'rbio_dyombudsman_001',
  RBIO_OMBUDSMAN: 'rbio_ombudsman_001',
  RBIO_ADMIN: 'admin_001',
  RBIO_ADJUDICATOR: 'rbio.adjudicator',
};

/** Dev identity headers naming the role explicitly. */
export function rbioLadderHeaders(role: RbioLadderRole, actor?: string): Record<string, string> {
  const user = actor || RBIO_LADDER_ACTORS[role];
  return {
    'Content-Type': 'application/json',
    'X-User-Id': user,
    'X-User-Name': user,
    'X-User-Roles': role,
  };
}

/**
 * Performs an RBIO action as a named ladder role and returns the raw status plus parsed body.
 *
 * Unlike {@link performRbioAction} this does NOT throw on a non-2xx. The S3 tests assert on refusals —
 * 400 for a missing comment, 422 for an unresolvable send-back, 403 for an unauthorised reopen — so a
 * helper that threw would make the interesting cases unassertable.
 *
 * It also surfaces `success` from the envelope, because WorkflowController answers HTTP 200 with
 * `success:false` for IllegalArgumentException paths. A test that checked only the status code would
 * pass on those.
 */
export async function rbioLadderAction(
  request: APIRequestContext,
  complaintNumber: string,
  action: string,
  role: RbioLadderRole,
  body: Record<string, unknown> = {},
  actor?: string
): Promise<{ status: number; success: boolean; message: string; data: any }> {
  const user = actor || RBIO_LADDER_ACTORS[role];
  const response = await request.post(
    `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
    {
      data: { action, actor: user, userRole: role, ...body },
      headers: rbioLadderHeaders(role, user),
    }
  );

  const status = response.status();
  let json: any = {};
  try {
    json = await response.json();
  } catch {
    json = {};
  }

  return {
    status,
    // A refusal that arrives as HTTP 200 + success:false is still a refusal.
    success: status >= 200 && status < 300 && json.success !== false,
    message: json.message || json.error || '',
    data: json.data ?? null,
  };
}

/** GET helper for the case-file endpoints, returning status alongside the body. */
export async function rbioCaseFileGet(
  request: APIRequestContext,
  complaintNumber: string,
  path: string,
  role: RbioLadderRole = 'RBIO_DEALING_OFFICIAL',
  query = ''
): Promise<{ status: number; data: any; meta: any }> {
  const response = await request.get(
    `${API_BASE}/api/v1/complaints/${complaintNumber}/${path}${query}`,
    { headers: rbioLadderHeaders(role) }
  );
  let json: any = {};
  try {
    json = await response.json();
  } catch {
    json = {};
  }
  return { status: response.status(), data: json.data ?? null, meta: json.meta ?? null };
}

/** POST helper for the case-file endpoints. Does not throw, so refusals stay assertable. */
export async function rbioCaseFilePost(
  request: APIRequestContext,
  complaintNumber: string,
  path: string,
  body: Record<string, unknown>,
  role: RbioLadderRole = 'RBIO_DEALING_OFFICIAL'
): Promise<{ status: number; data: any; message: string }> {
  const response = await request.post(
    `${API_BASE}/api/v1/complaints/${complaintNumber}/${path}`,
    { data: body, headers: rbioLadderHeaders(role) }
  );
  let json: any = {};
  try {
    json = await response.json();
  } catch {
    json = {};
  }
  return { status: response.status(), data: json.data ?? null, message: json.message || '' };
}

/**
 * Establishes custody: the dealing official accepts the complaint, then hands it to a reviewer.
 *
 * A send-back can only be tested once somebody has actually HELD the target role, because the
 * previous-holder lookup keys on a RELEASED custody row. Skipping this and calling SEND_BACK_DO
 * directly yields a legitimate 422 that proves nothing about the happy path.
 */
export async function rbioEstablishLadderCustody(
  request: APIRequestContext,
  complaintNumber: string
): Promise<void> {
  await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');
  const submitted = await rbioLadderAction(
    request,
    complaintNumber,
    'SUBMIT_FOR_REVIEW',
    'RBIO_DEALING_OFFICIAL',
    { remarks: 'E2E: assessment complete, forwarding for review' }
  );
  if (!submitted.success) {
    throw new Error(
      `Could not establish ladder custody on ${complaintNumber}: ${submitted.status} ${submitted.message}`
    );
  }
}

// ── Office capacity routing helpers (S3: UST464-467, UST755) ─────────────────────────────────────

export interface OfficeThresholdRow {
  officeId: string;
  officeName: string;
  department: string;
  maxThreshold: number;
  currentCount: number;
  overflowTargetOffice: string | null;
  active: boolean;
}

/**
 * Files a complaint through the PUBLIC portal endpoint, which is the only path that resolves a
 * territorial office from (state, district).
 *
 * The CEPC/RBIO create-complaint endpoints used by {@link createTestComplaint} take no state or
 * district, so they cannot exercise office routing at all — a spec built on them would pass while
 * routing was broken.
 */
export async function fileComplaintForOffice(
  request: APIRequestContext,
  opts: { state: string; district: string; entityName?: string; email?: string }
): Promise<{ complaintNumber: string; email: string; status: number; message: string }> {
  const suffix = Date.now().toString(36) + Math.random().toString(36).slice(2, 6);
  const email = opts.email || `s3_office_${suffix}@example.com`;

  // AntiAutomationFilter counts requests per client IP (velocity-threshold 100 / 60s) and answers
  // 429 SUSPICIOUS_ACTIVITY past that. A spec that files several complaints trips it, and every
  // later filing then fails for a reason that has nothing to do with office routing.
  //
  // Each filing therefore presents its own synthetic X-Forwarded-For, which is the header the filter
  // keys its counter on. This does NOT disable or soften the control: the per-client threshold is
  // still enforced exactly as configured, and a real client hammering the endpoint is still blocked.
  // It only stops the whole suite from being counted as one abusive client. The alternative —
  // raising velocity-threshold in application-dev-local.yml — would weaken a live control for every
  // session sharing this backend.
  const octet = () => Math.floor(Math.random() * 254) + 1;
  const syntheticIp = `10.${octet()}.${octet()}.${octet()}`;

  const response = await request.post(`${API_BASE}/api/v1/complaints`, {
    data: {
      complainantName: `S3 Office Routing ${suffix}`,
      complainantEmail: email,
      complainantPhone: '9876543210',
      complainantState: opts.state,
      complainantDistrict: opts.district,
      subject: `Office routing ${suffix}`,
      description: 'Automated check that the territorial office is resolved and persisted.',
      priority: 'medium',
      // Must be one of ONLINE|PHYSICAL_LETTER|EMAIL|WALK_IN — FileComplaintRequest rejects others.
      filingType: 'ONLINE',
      entityName: opts.entityName || 'State Bank of India',
      categoryId: 1,
      declarationAccepted: true,
    },
    headers: {
      'Content-Type': 'application/json',
      'X-Forwarded-For': syntheticIp,
    },
  });

  let json: any = {};
  try {
    json = await response.json();
  } catch {
    json = {};
  }
  const data = json.data ?? json;
  return {
    // The public filing response names the reference complaintId, not complaintNumber.
    complaintNumber: data?.complaintId || data?.complaintNumber || '',
    email,
    status: response.status(),
    message: json?.message || '',
  };
}

/** Reads the office capacity table through the CRPC head admin endpoint. */
export async function getOfficeThresholds(
  request: APIRequestContext
): Promise<OfficeThresholdRow[]> {
  const response = await request.get(`${API_BASE}/api/v1/crpc/head/office-thresholds`, {
    headers: identityHeadersFor('crpc_head1', 'CEPC'),
  });
  if (!response.ok()) return [];
  const json = await response.json();
  const rows = json.data ?? json;
  return Array.isArray(rows) ? rows : [];
}

/**
 * Sets one office's capacity, so an overflow can be provoked without filing 500 complaints.
 *
 * The query parameter is `threshold` (CrpcHeadController.updateThreshold). That endpoint reads
 * `jwt.getSubject()` for the audit trail, so it needs a real bearer token — with header-only dev
 * identity it fails rather than recording an unattributed change. Callers should treat a non-200 as
 * "cannot provoke overflow through this API" and skip, NOT as a routing failure.
 */
export async function setOfficeThreshold(
  request: APIRequestContext,
  officeId: string,
  threshold: number,
  token?: string
): Promise<number> {
  const headers: Record<string, string> = identityHeadersFor('crpc_head1', 'CEPC');
  if (token) headers['Authorization'] = `Bearer ${token}`;
  const response = await request.put(
    `${API_BASE}/api/v1/crpc/head/office-thresholds/${officeId}?threshold=${threshold}`,
    { headers }
  );
  return response.status();
}

/** Resets every office counter for a department. */
export async function resetOfficeCounters(
  request: APIRequestContext,
  department: string
): Promise<number> {
  const response = await request.post(
    `${API_BASE}/api/v1/crpc/head/office-thresholds/reset?department=${department}`,
    { headers: identityHeadersFor('crpc_head1', 'CEPC') }
  );
  return response.status();
}

/**
 * Reads a complaint as an RBIO role, returning status alongside the body.
 *
 * Distinct from {@link getComplaint}, which sends no RBIO identity: these S3 assertions need to read a
 * complaint AS a specific ladder role, and they assert on persisted fields
 * (maintainabilityDetermination, impleadedParties, assignedOfficer) rather than on the response of the
 * action that wrote them — a 200 does not prove the value landed.
 */
export async function rbioGetComplaint(
  request: APIRequestContext,
  complaintNumber: string,
  role: RbioLadderRole = 'RBIO_ADMIN'
): Promise<{ status: number; data: any }> {
  const response = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`, {
    headers: rbioLadderHeaders(role),
  });
  let json: any = {};
  try {
    json = await response.json();
  } catch {
    json = {};
  }
  return { status: response.status(), data: json.data ?? json ?? null };
}

/**
 * Reads columns straight from cms_db for one complaint.
 *
 * ── Why the database and not the API ────────────────────────────────────────────────────────────
 * GET /api/v1/complaints/{n} returns a deliberate PROJECTION for the citizen portal: it exposes
 * `assignedTo` and `status` but NOT assigned_officer, maintainability_determination, impleaded_parties,
 * deputy_decision or reopen_reason. Those are exactly the columns the param-name defect class corrupts.
 *
 * Widening that citizen-facing response so a test could see them would be the wrong fix — it would leak
 * internal workflow state to the public API to satisfy a test. And asserting on the ACTION's response
 * body instead would defeat the purpose: the whole point of this defect class is that the action reports
 * success while the column holds the wrong value.
 *
 * So the assertion reads the column. Only a SELECT, only one named complaint.
 */
export function rbioReadComplaintColumns(
  complaintNumber: string,
  columns: string[]
): Record<string, string> {
  if (!/^[A-Za-z0-9-]+$/.test(complaintNumber)) {
    throw new Error(`Refusing to use an unexpected complaint number in SQL: ${complaintNumber}`);
  }
  for (const column of columns) {
    if (!/^[a-z_]+$/.test(column)) {
      throw new Error(`Refusing to use an unexpected column name in SQL: ${column}`);
    }
  }

  const sql =
    `SELECT ${columns.join(', ')} FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}';`;
  const out = execFileSync(
    process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe',
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  );

  // -N -B gives one tab-separated line with no header. MySQL prints NULL as the literal "NULL", which is
  // normalised to '' so a caller can treat "absent" uniformly.
  const values = out.trim().split('\t');
  const row: Record<string, string> = {};
  columns.forEach((column, i) => {
    row[column] = values[i] === 'NULL' || values[i] === undefined ? '' : values[i];
  });
  return row;
}
