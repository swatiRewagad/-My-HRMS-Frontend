/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * Shared drivers for the QA pass-4 Session C specs (complaint-details fields, amounts, uploads,
 * declaration, review, duplicates).
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * WHY A HELPER AT ALL. Six manual blocks (33 savings, 34 loan, 35 ATM/debit, 37 credit card,
 * 39 wallet name, 40 transaction reference) are the SAME ten assertions against six different
 * inputs, and three more (42 dispute amount, 43 consequential loss, 44 harassment) are the same
 * eleven against three amount inputs. Hand-writing ~90 near-identical tests produces a file no
 * reviewer reads, and every one of them would repeat the 6-screen wizard walk. Two table-driven
 * drivers here, nine tables in the specs.
 *
 * WHY THE DRAFT-RESUME ENTRY RATHER THAN CLICKING THROUGH. All of Session C's fields live on
 * step 3 or later of a six-step wizard whose earlier steps are master-data driven (pincode →
 * state/district lookups, entity search, category master). Clicking through costs ~25 network
 * round-trips per test and fails for reasons that have nothing to do with the field under test.
 * `?resume=true` reads sessionStorage `cms_complaint_draft` and lands directly on a chosen step
 * with a valid form, exercising the real component and the real validators.
 * Pattern cribbed from `duplicate-check.spec.ts:131-166`, which established it.
 *
 * DRAFT_VERSION MUST TRACK file-complaint.component.ts:1861. `loadDraft()` DISCARDS a draft whose
 * version differs (ts:1929) — silently, with no console error. A stale number here does not fail
 * loudly; it drops you on step 1 of an empty form and every locator times out. Paid for that once.
 *
 * ═══ THE STEP NUMBERS ARE NOT WHAT THE BRIEF SAYS ═══
 * The wizard is SIX steps, not seven. Complaint details is step 3 (not 5), declaration is step 5
 * (not 7), review is step 6. Verified against `totalSteps = 6` (ts:133) and the
 * `@if (currentStep() === N)` blocks in the template. Recorded in findings-QA-C.md §0.
 *
 * WHAT THESE HELPERS DELIBERATELY DO NOT DO:
 *  - They do not assert any size, cap or limit. Limits are CONFIGURATION (standing ruling 1);
 *    `fetchUploadLimits()` reads them from the server and callers compute boundaries from that.
 *  - They do not import from Session A's or Session B's files. Session B is building a wizard
 *    navigation helper for the same journey; cross-importing between concurrently-written files
 *    is a race. This duplication is intentional.
 */

import type { APIRequestContext, Page } from '@playwright/test';
import { seedCitizenSession } from '../utils/test-data';

export const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
export const APP_BASE =
  process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';

/** Must equal file-complaint.component.ts:1861, or every seeded draft is silently discarded. */
export const DRAFT_VERSION = 4;

/**
 * Session C owns the 98765_3____ mobile range (per the concurrency brief). OTP cooldowns, hourly
 * send caps and `invalidateActiveOtps` all key on the mobile number in a SHARED cms_db, and
 * duplicate detection keys on mobile + entity, so a collision with Session A or B would make the
 * duplicate specs lie in BOTH directions.
 */
export function sessionCMobile(): string {
  return '987653' + String(Math.floor(1000 + Math.random() * 9000));
}

export function uniqueEmail(tag: string): string {
  return `qa_c_${tag}_${Date.now().toString(36)}_${Math.floor(Math.random() * 9999)}@example.com`;
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// WIZARD ENTRY
// ═══════════════════════════════════════════════════════════════════════════════════════════════

export interface DraftSeed {
  step: number;
  formData?: Record<string, unknown>;
  eligibilityAnswers?: Record<string, unknown>;
  declarationChecked?: boolean;
  declaration2Checked?: boolean;
  attachmentMeta?: { name: string; type: string; size: number }[];
  entityName?: string;
}

/**
 * A form state that passes every validator on steps 1-4, so a seeded draft can land on any step
 * and `nextStep()` will actually advance instead of silently refusing.
 *
 * `filedWithRE: 'yes'` maps to priorReComplaint, which the SERVER requires a date and reference
 * for — omit them and registration 400s before the duplicate check is ever reached. That trap is
 * documented in duplicate-check.spec.ts:152-154 and is reproduced here for the same reason.
 */
export function validFormData(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    firstName: 'Session', lastName: 'Cee',
    pincode: '400001', state: 'Maharashtra', district: 'Mumbai',
    address: '1 Test Street, Mumbai', complainantCategory: 'individual',
    complaintCategory: 'GENERAL',
    complaintText: 'QA pass-4 session C automated field validation.',
    isCreditCardComplaint: 'no',
    entityState: 'Maharashtra', entityDistrict: 'Mumbai', entityBranch: 'Fort',
    hasAccountWithRE: 'no', isWalletComplaint: 'no', isBusinessCorrespondent: 'no',
    hasAuthRep: 'no',
    bankComplaintDate: '2026-01-15', bankComplaintRef: 'RE-QAC-REF-001',
    ...overrides,
  };
}

/**
 * Lands the browser on a given wizard step with a valid form.
 *
 * The FIRST `goto` is load-bearing and must not be optimised away: sessionStorage is
 * origin-scoped, so seeding the session before the app origin is loaded writes the session into
 * `about:blank` and the route guard then bounces you to login.
 * `publicAuthService` only checks that `cms_public_session` exists and is inside the inactivity
 * window (public-auth.service.ts:39-48) — the token signature is never validated, so a fake one
 * is sufficient for anything that does not hit an authenticated server endpoint.
 */
export async function openWizardAtStep(page: Page, mobile: string, seed: DraftSeed): Promise<void> {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, mobile, 'e2e-seeded-token.sig');

  const draft = {
    version: DRAFT_VERSION,
    phase: 'form',
    currentStep: seed.step,
    entityName: seed.entityName ?? 'Test Bank Ltd',
    declarationChecked: seed.declarationChecked ?? true,
    declaration2Checked: seed.declaration2Checked ?? true,
    eligibilityAnswers: seed.eligibilityAnswers ?? { filedWithRE: 'yes', receivedReply: 'yes' },
    formData: validFormData(seed.formData ?? {}),
    attachmentMeta: seed.attachmentMeta ?? [],
  };

  await page.evaluate(d => sessionStorage.setItem('cms_complaint_draft', JSON.stringify(d)), draft);
  await page.goto(`${APP_BASE}/public/file-complaint?resume=true`);
  await page.waitForLoadState('networkidle');
}

/** Step 3 — the Complaint Details screen, where blocks 33-45 live. */
export async function openComplaintDetails(
  page: Page, mobile: string, formData: Record<string, unknown> = {},
): Promise<void> {
  await openWizardAtStep(page, mobile, { step: 3, formData });
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// TEXT FIELD DRIVER — blocks 33, 34, 35, 37, 39, 40
// ═══════════════════════════════════════════════════════════════════════════════════════════════

export interface TextFieldSpec {
  /** The manual block number, for the describe title. */
  block: string;
  /** Human label as the manual names it. */
  label: string;
  /** `name`/ngModel key on the input, e.g. `savingsAccountNumber`. */
  field: string;
  /** Length the manual claims the field caps at (100 or 150). */
  claimedMaxLength: number;
  /**
   * Extra formData needed to make the field VISIBLE. All of these inputs are inside `@if` blocks
   * (account-type selection, or isWalletComplaint === 'yes'), so without this the locator never
   * resolves and the failure looks like a missing field rather than a missing precondition.
   */
  reveal: Record<string, unknown>;
  /** Account-type checkbox that must be ticked, where the field is gated on one. */
  accountType?: 'savings' | 'loan' | 'atm_debit' | 'credit_card';
  /** The mandatory-field error the component sets, or null where the manual says blank is allowed. */
  requiredError: string | null;
}

export function fieldInput(page: Page, field: string) {
  return page.locator(`input[name="${field}"]`);
}

/**
 * The error message belonging to ONE field.
 *
 * Must be anchored on the nearest `.form-group` ANCESTOR, not on a fixed number of hops. An
 * earlier version used `xpath=../..`, which for the account numbers climbs out of the field's own
 * `.form-group` and into the shared `.form-row three-col` that holds up to three fields — so
 * asserting "savings has no error" picked up the LOAN field's error and reported a product defect
 * that did not exist. Two fields in one row is the common case on this screen, not the exception.
 */
export function fieldError(page: Page, field: string) {
  return page.locator(`input[name="${field}"]`)
    .locator('xpath=ancestor::div[contains(@class,"form-group")][1]')
    .locator('.field-error');
}

/**
 * The account-type options, keyed by the component `value` the field gates on.
 *
 * ═══ WHY THE OPTIONS ARE ADDRESSED BY LABEL AND NOT BY name ═══
 * The template writes `[name]="'accountType_' + at.value"` (html:734), so `input[name=
 * "accountType_savings"]` looks like the obvious locator. It matches NOTHING. Angular does not
 * reflect that binding onto the element — the rendered checkbox carries only Angular's own classes
 * and no `name` attribute whatsoever (verified by dumping `.multiselect-panel` innerHTML). Nor is
 * there a data-testid. The visible label is the only stable handle.
 *
 * The labels come from master data (`/api/v1/masters/account-types`, which 404s on this backend,
 * falling back to `/assets/masters/account-types.json`), so they are asserted to be present rather
 * than assumed — if the master changes, the failure names the missing option.
 */
export const ACCOUNT_TYPE_LABELS: Record<string, string> = {
  savings: 'Savings Account',
  loan: 'Loan Account',
  atm_debit: 'ATM / Debit Card',
  credit_card: 'Credit Card',
};

/**
 * Reveals a conditional account-number field by driving the real controls.
 *
 * Account numbers are gated on a multiselect whose state lives in a component array
 * (`accountTypes[].checked`), NOT in `formData` — so seeding a draft cannot reveal them and the
 * dropdown has to be opened and the option clicked.
 *
 * ═══ THE TIMING TRAP ═══
 * `waitForLoadState('networkidle')` is NOT sufficient to find the options. The component asks the
 * backend for the account-type master, gets a 404, RETRIES TWICE, and only then falls back to the
 * local asset — so the panel renders empty for a moment and an immediate `.check()` times out
 * against a list that is about to exist. That produced 26 simultaneous failures whose screenshots
 * showed a perfectly healthy open dropdown. Waiting for the option itself is the fix; waiting
 * longer on the page is not.
 */
export async function revealAccountField(page: Page, accountType: string): Promise<void> {
  const label = ACCOUNT_TYPE_LABELS[accountType];
  if (!label) throw new Error(`no label mapped for account type "${accountType}"`);

  const trigger = page.locator('.multiselect-trigger');
  await trigger.click();

  // `.multiselect-item` whose span text is exactly this option. Exact matching matters:
  // "Credit Card" is a substring of nothing here, but "Savings Account" vs "Current Account" and
  // "ATM / Debit Card" vs "Credit Card" are close enough that a loose match is a latent trap.
  const option = page.locator('.multiselect-item', {
    has: page.locator('span', { hasText: new RegExp(`^\\s*${label.replace(/[/]/g, '\\/')}\\s*$`) }),
  });
  await option.locator('input[type="checkbox"]').check();

  await trigger.click(); // collapse, so the panel does not overlay the inputs beneath
}

/** The inverse, for proving that deselecting a type removes its number field and clears its value. */
export async function hideAccountField(page: Page, accountType: string): Promise<void> {
  const label = ACCOUNT_TYPE_LABELS[accountType];
  const trigger = page.locator('.multiselect-trigger');
  await trigger.click();
  const option = page.locator('.multiselect-item', {
    has: page.locator('span', { hasText: new RegExp(`^\\s*${label.replace(/[/]/g, '\\/')}\\s*$`) }),
  });
  await option.locator('input[type="checkbox"]').uncheck();
  await trigger.click();
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// AMOUNT FIELD DRIVER — blocks 42, 43, 44
// ═══════════════════════════════════════════════════════════════════════════════════════════════

export interface AmountFieldSpec {
  block: string;
  label: string;
  field: 'disputeAmount' | 'compensationSought' | 'reliefSought';
  /**
   * The cap the MANUAL claims, in rupees, or null where the manual states none.
   *
   * NOTE: this is recorded so the spec can state the manual's claim in its own findings, NOT so a
   * spec can assert it. Where the cap is real it is read from the code's observed behaviour and
   * flagged as hardcoded — `/api/v1/config/compensation-limits` is 404, so unlike upload limits
   * there is no server to ask. See findings-QA-C.md §1 rows 3-5.
   */
  claimedCapRupees: number | null;
  /** The error the component actually produces when the cap is breached. */
  capError: string | null;
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// UPLOAD LIMITS — read from the server, never pinned
// ═══════════════════════════════════════════════════════════════════════════════════════════════

export interface UploadLimits {
  maxFileSizeBytes: number;
  maxTotalSizeBytes: number;
  maxFileSizeMb: number;
  maxTotalSizeMb: number;
  maxFileCount: number;
}

/**
 * The authoritative limits. Standing ruling 1: configuration decides, so every boundary case is
 * COMPUTED from this rather than written as a literal. `ui-homogenisation/upload-limits.spec.ts`
 * fails any source file that hardcodes a byte figure, and a spec that pinned 2 MB here would be
 * asserting the stale side of the same rule the moment the figure is retuned.
 */
export async function fetchUploadLimits(request: APIRequestContext): Promise<UploadLimits> {
  const res = await request.get(`${API_BASE}/api/v1/config/upload-limits`);
  if (!res.ok()) throw new Error(`upload-limits returned ${res.status()}; cannot compute boundaries`);
  return (await res.json()) as UploadLimits;
}

/**
 * A file of an exact byte length, written to the per-session temp dir.
 *
 * Built programmatically from the configured limit for the same reason: an over-limit fixture
 * committed to the repo is both a large binary and a hardcoded limit in disguise — it would stop
 * being over-limit the day the configuration rises.
 */
export function fixturePath(name: string): string {
  const dir = process.env['PW_FIXTURE_DIR'] || '/c/tmp/pw_C/fixtures';
  return `${dir}/${name}`;
}
