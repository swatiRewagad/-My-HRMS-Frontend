/**
 * The registry of every reviewable screen in the portal.
 *
 * Screen IDs are STABLE: they are derived from the area + slug written here, never from run order,
 * so a review spreadsheet keyed on them stays valid across runs. Never renumber an existing entry —
 * add new ones at the end of their area.
 *
 * `actor` decides how the crawler authenticates before visiting `route`:
 *   PUBLIC    — no session
 *   CITIZEN   — seeded public session (mobile+OTP gate bypassed via sessionStorage)
 *   CEPC, RBIO, AA, RE and ADMIN roles — Keycloak SSO as that role
 *
 * `params` supplies values for :placeholders. A route whose param cannot be resolved at runtime is
 * reported as BLOCKED rather than silently captured as an empty page.
 */

export type Actor =
  | 'PUBLIC'
  | 'CITIZEN'
  | 'CEPC_DO'
  | 'CEPC_REVIEWER'
  | 'CEPC_INCHARGE'
  | 'CEPC_CA'
  | 'RBIO_OFFICER'
  | 'RBIO_SUPERVISOR'
  | 'RBIO_ADMIN'
  | 'AA_DO'
  | 'AA_ADMIN'
  | 'RE_PNO'
  | 'ADMIN';

export interface ScreenDef {
  id: string;
  area: string;
  name: string;
  route: string;
  actor: Actor;
  /** Param names this route needs resolved, e.g. ['complaintNumber']. */
  needs?: ('complaintNumber' | 'appealNumber' | 'draftId' | 'token' | 'ruleId' | 'closedComplaintNumber')[];
  /** Extra settle time in ms for screens known to load slowly. */
  settle?: number;
  notes?: string;
  /**
   * A selector to click before the screenshot, for panels that are COLLAPSED by default.
   *
   * Without this the deck can only show a screen's resting state, so a BA reviewing the RBIO context
   * rail would see a 48px icon strip and nothing else — and would reasonably mark the rail as missing.
   * A screen needing this gets a SECOND registry entry rather than a replaced one: the resting state is
   * what an officer sees first and is still worth reviewing.
   *
   * A selector that does not match is recorded on the capture, never thrown.
   */
  openFirst?: string;
}

export const SCREENS: ScreenDef[] = [
  // ── Public / citizen-facing ──
  { id: 'PUB-001', area: 'Public', name: 'Landing page', route: '/', actor: 'PUBLIC' },
  { id: 'PUB-002', area: 'Public', name: 'Public home', route: '/public', actor: 'PUBLIC' },
  { id: 'PUB-003', area: 'Public', name: 'Citizen login (mobile + CAPTCHA + OTP)', route: '/public/login', actor: 'PUBLIC' },
  { id: 'PUB-004', area: 'Public', name: 'Eligibility wizard', route: '/public/eligibility-wizard', actor: 'PUBLIC' },
  { id: 'PUB-005', area: 'Public', name: 'Track complaint (entry form)', route: '/public/track', actor: 'PUBLIC' },
  { id: 'PUB-006', area: 'Public', name: 'Track complaint (result)', route: '/public/track/:complaintNumber', actor: 'PUBLIC', needs: ['complaintNumber'] },
  { id: 'PUB-007', area: 'Public', name: 'FAQ', route: '/public/faq', actor: 'PUBLIC' },
  { id: 'PUB-008', area: 'Public', name: 'File complaint wizard (step 1)', route: '/public/file-complaint', actor: 'CITIZEN' },
  { id: 'PUB-009', area: 'Public', name: 'Withdraw complaint (picker)', route: '/public/withdraw', actor: 'CITIZEN' },
  { id: 'PUB-010', area: 'Public', name: 'Withdraw complaint (specific)', route: '/public/withdraw/:complaintNumber', actor: 'CITIZEN', needs: ['complaintNumber'] },
  { id: 'PUB-011', area: 'Public', name: 'Submit feedback', route: '/public/feedback', actor: 'CITIZEN' },
  { id: 'PUB-012', area: 'Public', name: 'File appeal', route: '/public/appeal', actor: 'CITIZEN' },
  { id: 'PUB-013', area: 'Public', name: 'Complaint history', route: '/public/history', actor: 'CITIZEN' },
  { id: 'PUB-014', area: 'Public', name: 'Complaint detail', route: '/public/complaint/:complaintNumber', actor: 'CITIZEN', needs: ['complaintNumber'] },
  { id: 'PUB-015', area: 'Public', name: 'Legacy file-complaint route', route: '/file-complaint', actor: 'PUBLIC' },
  { id: 'PUB-016', area: 'Public', name: 'Legacy track route', route: '/track', actor: 'PUBLIC' },
  { id: 'PUB-017', area: 'Public', name: 'Global search', route: '/search', actor: 'PUBLIC' },
  { id: 'PUB-018', area: 'Public', name: 'Complainant upload link (OTP-gated)', route: '/public/upload/:token', actor: 'PUBLIC', needs: ['token'] },
  { id: 'PUB-019', area: 'Public', name: 'Public eligibility wizard (unauthenticated entry)', route: '/public/eligibility-wizard', actor: 'CITIZEN' },
  { id: 'PUB-020', area: 'Public', name: 'Track a CLOSED complaint (citizen stage projection)', route: '/public/track/:closedComplaintNumber', actor: 'PUBLIC', needs: ['closedComplaintNumber'] },

  // ── Staff shell ──
  { id: 'STF-001', area: 'Staff', name: 'Staff login (SSO entry)', route: '/staff/login', actor: 'PUBLIC' },
  { id: 'STF-002', area: 'Staff', name: 'Staff unauthorized', route: '/staff/unauthorized', actor: 'PUBLIC' },
  { id: 'STF-003', area: 'Staff', name: 'Staff dashboard', route: '/staff/dashboard', actor: 'CEPC_DO' },
  { id: 'STF-004', area: 'Staff', name: 'Staff reports', route: '/staff/reports', actor: 'CEPC_INCHARGE' },
  { id: 'STF-005', area: 'Staff', name: 'Senior dashboard', route: '/staff/senior-dashboard', actor: 'CEPC_INCHARGE' },

  // ── CEPC ──
  { id: 'CEPC-001', area: 'CEPC', name: 'CEPC dashboard', route: '/cepc/dashboard', actor: 'CEPC_DO' },
  { id: 'CEPC-002', area: 'CEPC', name: 'CEPC complaint detail', route: '/cepc/complaint/:complaintNumber', actor: 'CEPC_DO', needs: ['complaintNumber'] },
  { id: 'CEPC-003', area: 'CEPC', name: 'CEPC SLA dashboard', route: '/cepc/sla-dashboard', actor: 'CEPC_INCHARGE' },
  { id: 'CEPC-004', area: 'CEPC', name: 'CEPC task list', route: '/staff/cepc/tasks', actor: 'CEPC_DO' },
  { id: 'CEPC-005', area: 'CEPC', name: 'CEPC task detail', route: '/staff/cepc/task/:complaintNumber', actor: 'CEPC_DO', needs: ['complaintNumber'] },
  { id: 'CEPC-006', area: 'CEPC', name: 'CEPC history', route: '/staff/cepc/history', actor: 'CEPC_DO' },
  { id: 'CEPC-007', area: 'CEPC', name: 'CEPC escalations', route: '/staff/cepc/escalations', actor: 'CEPC_INCHARGE' },

  // ── RBIO ──
  { id: 'RBIO-001', area: 'RBIO', name: 'RBIO home', route: '/rbio', actor: 'RBIO_OFFICER' },
  { id: 'RBIO-002', area: 'RBIO', name: 'RBIO create complaint', route: '/rbio/create-complaint', actor: 'RBIO_OFFICER' },
  { id: 'RBIO-003', area: 'RBIO', name: 'RBIO complaint detail', route: '/rbio/complaint/:complaintNumber', actor: 'RBIO_OFFICER', needs: ['complaintNumber'] },
  { id: 'RBIO-004', area: 'RBIO', name: 'RBIO supervisor dashboard', route: '/rbio/supervisor-dashboard', actor: 'RBIO_SUPERVISOR' },
  { id: 'RBIO-005', area: 'RBIO', name: 'RBIO task list', route: '/staff/rbio/tasks', actor: 'RBIO_OFFICER' },
  { id: 'RBIO-006', area: 'RBIO', name: 'RBIO task detail', route: '/staff/rbio/task/:complaintNumber', actor: 'RBIO_OFFICER', needs: ['complaintNumber'] },
  { id: 'RBIO-007', area: 'RBIO', name: 'RBIO history', route: '/staff/rbio/history', actor: 'RBIO_OFFICER' },
  { id: 'RBIO-008', area: 'RBIO', name: 'RBIO escalations', route: '/staff/rbio/escalations', actor: 'RBIO_SUPERVISOR' },
  { id: 'RBIO-009', area: 'RBIO', name: 'Officer list (legacy)', route: '/officer', actor: 'RBIO_OFFICER' },
  { id: 'RBIO-010', area: 'RBIO', name: 'Officer complaint action (legacy)', route: '/officer/complaint/:complaintNumber', actor: 'RBIO_OFFICER', needs: ['complaintNumber'] },

  // ── Appellate Authority ──
  { id: 'AA-001', area: 'AA', name: 'AA dashboard', route: '/aa/dashboard', actor: 'AA_DO' },
  { id: 'AA-002', area: 'AA', name: 'AA appeal detail', route: '/aa/appeal/:appealNumber', actor: 'AA_DO', needs: ['appealNumber'] },
  { id: 'AA-003', area: 'AA', name: 'AA search', route: '/aa/search', actor: 'AA_DO' },
  { id: 'AA-004', area: 'AA', name: 'AA register appeal', route: '/aa/register/:complaintNumber', actor: 'AA_DO', needs: ['complaintNumber'] },
  { id: 'AA-005', area: 'AA', name: 'AA admin', route: '/aa/admin', actor: 'AA_ADMIN' },
  { id: 'AA-006', area: 'AA', name: 'AA draft assessment (email/letter origin)', route: '/aa/draft/:draftId', actor: 'AA_DO', needs: ['draftId'] },

  // ── Regulated Entity portal ──
  { id: 'RE-001', area: 'RE Portal', name: 'RE login', route: '/re-portal/login', actor: 'PUBLIC' },
  { id: 'RE-002', area: 'RE Portal', name: 'RE dashboard', route: '/re-portal/dashboard', actor: 'RE_PNO' },
  { id: 'RE-003', area: 'RE Portal', name: 'RE complaint detail', route: '/re-portal/complaints/:complaintNumber', actor: 'RE_PNO', needs: ['complaintNumber'] },
  { id: 'RE-004', area: 'RE Portal', name: 'RE profile', route: '/re-portal/profile', actor: 'RE_PNO' },
  { id: 'RE-005', area: 'RE Portal', name: 'RE reassignment — my requests', route: '/re-portal/reassignment/my-requests', actor: 'RE_PNO' },
  { id: 'RE-006', area: 'RE Portal', name: 'RE reassignment — PNO approvals', route: '/re-portal/reassignment/approvals', actor: 'RE_PNO' },
  { id: 'RE-007', area: 'RE Portal', name: 'RE PNO dashboard', route: '/re-portal/pno-dashboard', actor: 'RE_PNO' },

  // ── CRPC ──
  { id: 'CRPC-001', area: 'CRPC', name: 'CRPC login', route: '/crpc/login', actor: 'PUBLIC' },
  { id: 'CRPC-002', area: 'CRPC', name: 'CRPC home', route: '/crpc/home', actor: 'CEPC_DO' },
  { id: 'CRPC-003', area: 'CRPC', name: 'CRPC physical letter intake', route: '/crpc/physical-letter', actor: 'CEPC_DO' },
  { id: 'CRPC-004', area: 'CRPC', name: 'CRPC ops head', route: '/crpc/ops-head', actor: 'CEPC_INCHARGE' },
  { id: 'CRPC-005', area: 'CRPC', name: 'CRPC reviewer', route: '/crpc/reviewer', actor: 'CEPC_REVIEWER' },
  { id: 'CRPC-006', area: 'CRPC', name: 'CRPC help desk', route: '/crpc/help-desk', actor: 'CEPC_DO' },
  { id: 'CRPC-007', area: 'CRPC', name: 'CRPC in-charge', route: '/crpc/in-charge', actor: 'CEPC_INCHARGE' },
  { id: 'CRPC-008', area: 'CRPC', name: 'CRPC reports', route: '/crpc/reports', actor: 'CEPC_INCHARGE' },
  { id: 'CRPC-009', area: 'CRPC', name: 'CRPC draft assessment (DEO)', route: '/crpc/draft/:draftId', actor: 'CEPC_DO', needs: ['draftId'] },
  { id: 'CRPC-010', area: 'CRPC', name: 'CRPC reviewer draft assessment', route: '/crpc/reviewer/draft/:draftId', actor: 'CEPC_REVIEWER', needs: ['draftId'] },

  // ── Admin ──
  { id: 'ADM-001', area: 'Admin', name: 'Admin dashboard', route: '/admin/dashboard', actor: 'ADMIN' },
  { id: 'ADM-002', area: 'Admin', name: 'Admin rules list', route: '/admin/rules', actor: 'ADMIN' },
  { id: 'ADM-003', area: 'Admin', name: 'Admin rule — new', route: '/admin/rules/new', actor: 'ADMIN' },
  { id: 'ADM-004', area: 'Admin', name: 'Admin rule tester', route: '/admin/rules/test', actor: 'ADMIN' },
  { id: 'ADM-005', area: 'Admin', name: 'Admin extraction rules', route: '/admin/extraction-rules', actor: 'ADMIN' },
  { id: 'ADM-006', area: 'Admin', name: 'Admin report access', route: '/admin/report-access', actor: 'ADMIN' },
  { id: 'ADM-007', area: 'Admin', name: 'Admin team management', route: '/admin/team-management', actor: 'ADMIN' },
  { id: 'ADM-008', area: 'Admin', name: 'Admin security', route: '/admin/security', actor: 'ADMIN' },
  { id: 'ADM-009', area: 'Admin', name: 'Admin comment templates', route: '/admin/comment-templates', actor: 'ADMIN' },
  { id: 'ADM-010', area: 'Admin', name: 'Admin communication templates', route: '/admin/communication-templates', actor: 'ADMIN' },
  { id: 'ADM-011', area: 'Admin', name: 'Admin master data', route: '/admin/master-data', actor: 'ADMIN' },
  { id: 'ADM-012', area: 'Admin', name: 'Admin rule — edit existing', route: '/admin/rules/edit/:ruleId', actor: 'ADMIN', needs: ['ruleId'] },

  // ── Email syndication ──
  { id: 'EML-001', area: 'Email Syndication', name: 'Email syndication inbox', route: '/email-syndication', actor: 'ADMIN' },
  { id: 'EML-002', area: 'Email Syndication', name: 'Ignore list', route: '/email-syndication/ignore-list', actor: 'ADMIN' },
  { id: 'EML-003', area: 'Email Syndication', name: 'Simulator', route: '/email-syndication/simulator', actor: 'ADMIN' },
  { id: 'EML-004', area: 'Email Syndication', name: 'Email draft detail', route: '/email-syndication/draft/:draftId', actor: 'ADMIN', needs: ['draftId'] },

  // ── Cross-cutting: the same screen as a DIFFERENT rank. The transition matrix is a 3-tuple
  // (status, action, ROLE), so a screen reviewed as only one role hides most of its action bar.
  { id: 'XRL-001', area: 'Cross-role', name: 'RBIO task detail as REVIEWER (ladder actions)', route: '/staff/rbio/task/:complaintNumber', actor: 'RBIO_SUPERVISOR', needs: ['complaintNumber'] },
  { id: 'XRL-002', area: 'Cross-role', name: 'RBIO complaint detail as ADMIN', route: '/rbio/complaint/:complaintNumber', actor: 'RBIO_ADMIN', needs: ['complaintNumber'] },
  { id: 'XRL-003', area: 'Cross-role', name: 'CEPC task detail as REVIEWER', route: '/staff/cepc/task/:complaintNumber', actor: 'CEPC_REVIEWER', needs: ['complaintNumber'] },
  { id: 'XRL-004', area: 'Cross-role', name: 'Staff dashboard as RBIO officer', route: '/staff/dashboard', actor: 'RBIO_OFFICER' },
  { id: 'XRL-005', area: 'Cross-role', name: 'Staff dashboard as AA DO', route: '/staff/dashboard', actor: 'AA_DO' },
  { id: 'XRL-006', area: 'Cross-role', name: 'Staff dashboard as RE PNO', route: '/staff/dashboard', actor: 'RE_PNO' },

  // ── Collapsed-by-default panels, captured OPEN. These are additional states of screens already in
  // the registry above, not replacements: reviewing only the open state would hide what an officer
  // actually lands on, and reviewing only the closed state hides the panel's entire content.
  { id: 'STA-001', area: 'Panel states', name: 'RBIO complaint detail — context rail open (history)', route: '/rbio/complaint/:complaintNumber', actor: 'RBIO_OFFICER', needs: ['complaintNumber'], openFirst: '.context-rail .rail-icon' },
  { id: 'STA-002', area: 'Panel states', name: 'RBIO complaint detail — attachments rail open', route: '/rbio/complaint/:complaintNumber', actor: 'RBIO_OFFICER', needs: ['complaintNumber'], openFirst: '.tab-bar .tab-add' },
  // CEPC and AA now render the same shared rail, so their open states need capturing too — the whole
  // point of the review is whether an officer meets the same screen in every module.
  { id: 'STA-003', area: 'Panel states', name: 'CEPC complaint detail — context rail open', route: '/cepc/complaint/:complaintNumber', actor: 'CEPC_DO', needs: ['complaintNumber'], openFirst: '.context-rail .rail-icon' },
  { id: 'STA-004', area: 'Panel states', name: 'AA appeal detail — context rail open', route: '/aa/appeal/:appealNumber', actor: 'AA_DO', needs: ['appealNumber'], openFirst: '.context-rail .rail-icon' },
];
