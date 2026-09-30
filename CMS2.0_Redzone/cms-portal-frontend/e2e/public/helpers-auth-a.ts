import { type Page, type APIRequestContext } from '@playwright/test';
// `test` comes from the local fixtures, not from @playwright/test: the fixture rewrites the bundle's
// hardcoded localhost:8082 browser calls to API_BASE_URL, so a driver that declared tests against the
// bare base `test` would run them against the wrong backend.
import { test, expect } from '../fixtures';
import { execFileSync } from 'node:child_process';

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * SESSION-A HELPERS — citizen login screen and Complainant Details field validation
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * This file exists because `e2e/utils/test-data.ts` is a chokepoint every concurrent session imports,
 * so nothing here may be added to it. Everything below is used ONLY by the `*-a` specs written in this
 * session.
 *
 * WHY A PARAMETRISED FIELD DRIVER RATHER THAN HAND-WRITTEN TESTS
 * Manual blocks 9, 10, 11 and 15 (First Name, Middle Name, Surname, Organization Name) are the SAME
 * thirteen assertions applied to four inputs, and blocks 4 and 7 repeat most of them again on the mobile
 * and OTP fields. Hand-writing them produces ~60 near-identical tests in which a copy-paste slip is
 * invisible. `describeTextFieldValidation` takes the field's contract as data and derives the cases, so
 * a rule stated once is applied identically everywhere and the boundary is computed from the field's own
 * declared maxlength rather than repeated as a literal.
 *
 * NO LENGTH LIMIT IS WRITTEN DOWN HERE. Per the standing ruling, the 150-character cap is read from the
 * rendered control's `maxlength` attribute and the boundaries are computed from THAT, so these tests
 * follow the product when a cap is retuned instead of pinning the number QA wrote down.
 */

const MYSQL = process.env['MYSQL_CLI'] || 'C:\\Program Files\\MySQL\\MySQL Server 8.4\\bin\\mysql.exe';

function runSql(sql: string): string {
  return execFileSync(
    MYSQL,
    ['--default-character-set=utf8mb4', '-u', 'cms_user', '-pcms_pass', 'cms_db', '-N', '-B', '-e', sql],
    { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }
  );
}

/**
 * Session A's mobile numbers. The range 987651xxxx is allocated to this session alone — OTP cooldown,
 * the per-mobile hourly limit and invalidateActiveOtps all key on the number in a database shared with
 * two other concurrent sessions, so a literal borrowed from another spec makes both suites' results
 * depend on which ran first.
 */
export function sessionAMobile(suffix: number): string {
  const s = String(suffix).padStart(4, '0');
  if (s.length !== 4) throw new Error(`Session A mobile suffix out of range: ${suffix}`);
  return `987651${s}`;
}

/**
 * Ages the CAPTCHA challenge behind `token` past its own expiry.
 *
 * ── Why SQL, and why this is not a `waitForTimeout` ─────────────────────────────────────────────
 * Manual cases 800-802, 806-807 and 2589-2593 turn on a CAPTCHA that has expired by wall clock. The
 * configured window is cms.auth.captcha.expiry-minutes — 10 minutes under dev-local — and the suite's
 * per-test timeout is 60 seconds, so waiting it out is not merely slow, it is impossible. It would also
 * prove nothing: a test that slept 10 minutes and saw a refusal cannot distinguish "expired" from "the
 * token was consumed".
 *
 * CaptchaService.verifyCaptcha selects on `used = false AND expires_at > now()`, so moving BOTH
 * timestamps back by the same interval makes the row exactly that old as far as every line of shipping
 * code is concerned, preserving a state the product could genuinely have reached. Nothing is stubbed:
 * the same query, the same predicate, the same INVALID_CAPTCHA branch.
 *
 * This is the same technique `backdateOtpAttempt` uses for the OTP clock; there is no equivalent helper
 * for CAPTCHA in test-data.ts and that file may not be edited from this session.
 */
export function expireCaptcha(token: string, seconds = 3600): void {
  if (!/^[0-9a-fA-F-]{36}$/.test(token)) {
    throw new Error(`Refusing to use an unexpected CAPTCHA token in SQL: ${token}`);
  }
  if (!Number.isInteger(seconds) || seconds < 1 || seconds > 86_400) {
    throw new Error(`Refusing an implausible CAPTCHA backdate: ${seconds}`);
  }
  runSql(
    `UPDATE captcha_sessions SET created_at = DATE_SUB(created_at, INTERVAL ${seconds} SECOND), ` +
    `expires_at = DATE_SUB(expires_at, INTERVAL ${seconds} SECOND) WHERE captcha_token = '${token}';`
  );
}

/**
 * The stored state of one CAPTCHA challenge, for proving single-use and expiry in the row itself.
 *
 * ── `windowSeconds` is the created_at → expires_at SPAN, and that choice is load-bearing ────────
 * The obvious way to ask "how long is this challenge valid for" — compare `expires_at` against the
 * current time — is wrong here in two different ways, and both produced a failure that looked exactly
 * like a product defect ("the challenge is already expired on arrival", -19200 seconds):
 *
 *   1. Parsing the timestamp in Node fails because `datetime(6)` is a naive local value with no offset,
 *      and a 6-digit fractional part is not a form V8 treats as local — so it is read as UTC.
 *   2. Comparing it against MySQL's own `NOW()` fails too, because THE JVM AND MYSQL DISAGREE ABOUT THE
 *      TIMEZONE. Measured on this machine: MySQL `NOW()` = 17:53 IST while the rows the backend has just
 *      written carry 12:23 — the backend's `LocalDateTime.now()` resolves to UTC. Every timestamp in
 *      `captcha_sessions` and `otp_attempts` is therefore UTC, while `NOW()` is IST, a 5h30m skew.
 *
 * The span between two columns written by the SAME clock in the SAME statement is immune to all of this:
 * it is exactly `cms.auth.captcha.expiry-minutes` in seconds, whatever timezone either side thinks it is
 * in. And it is the only figure the boundary math needs, because `expireCaptcha` shifts both columns by
 * the same delta — so ageing a row by `windowSeconds - 1` leaves it one second short of expiry as judged
 * by the clock that wrote it, which is the clock `verifyCaptcha` compares against.
 *
 * This is the same reason `backdateOtpAttempt` moves both OTP columns together rather than setting an
 * absolute time.
 */
export function captchaSessionRow(
  token: string
): { used: string; expiresAt: string; type: string; windowSeconds: number } | null {
  if (!/^[0-9a-fA-F-]{36}$/.test(token)) {
    throw new Error(`Refusing to use an unexpected CAPTCHA token in SQL: ${token}`);
  }
  const out = runSql(
    `SELECT used+0, expires_at, captcha_type, TIMESTAMPDIFF(SECOND, created_at, expires_at) ` +
    `FROM captcha_sessions WHERE captcha_token = '${token}';`
  ).trim();
  if (!out) return null;
  const [used, expiresAt, type, windowSeconds] = out.split('\t');
  return { used, expiresAt, type, windowSeconds: Number(windowSeconds) };
}

/**
 * Seeds a TEXT CAPTCHA challenge with a known answer, exactly as CaptchaService.saveCaptchaSession does.
 *
 * ── Why this is the only way to test case-sensitivity ───────────────────────────────────────────
 * Manual case 800 asks whether the CAPTCHA is case-sensitive. That question cannot be asked of the MATH
 * variant — its answer is a number, which has no case — and it cannot be asked of the VISUAL variant
 * either, because `generateVisualCaptcha` deliberately never sends the answer to the client (it is only
 * legible from the rendered PNG). So there is no API path by which a test can know a text answer and
 * then submit it in a different case.
 *
 * The row is therefore inserted directly, with the answer hashed the way the product hashes it:
 * `sha256(answer.toLowerCase())`, computed by MySQL's own SHA2 so this helper never reimplements the
 * digest. Everything downstream is untouched shipping code — the same
 * findByCaptchaTokenAndUsedFalseAndExpiresAtAfter query, the same constant-time comparison, the same
 * INVALID_CAPTCHA branch. What is seeded is a row the product itself produces; only the answer is known.
 */
export function seedTextCaptcha(answer: string, token: string, expiryMinutes = 10): void {
  if (!/^[0-9a-fA-F-]{36}$/.test(token)) {
    throw new Error(`Refusing to use an unexpected CAPTCHA token in SQL: ${token}`);
  }
  if (!/^[A-Za-z0-9]{1,32}$/.test(answer)) {
    throw new Error(`Refusing an unexpected CAPTCHA answer in SQL: ${answer}`);
  }
  if (!Number.isInteger(expiryMinutes) || expiryMinutes < 1 || expiryMinutes > 1440) {
    throw new Error(`Refusing an implausible CAPTCHA expiry: ${expiryMinutes}`);
  }
  runSql(
    `DELETE FROM captcha_sessions WHERE captcha_token = '${token}';` +
    `INSERT INTO captcha_sessions (captcha_token, answer_hash, captcha_type, used, created_at, expires_at) ` +
    `VALUES ('${token}', SHA2('${answer.toLowerCase()}', 256), 'VISUAL', 0, NOW(), ` +
    `DATE_ADD(NOW(), INTERVAL ${expiryMinutes} MINUTE));`
  );
}

/** Removes a seeded challenge. */
export function deleteCaptchaSession(token: string): void {
  if (!/^[0-9a-fA-F-]{36}$/.test(token)) {
    throw new Error(`Refusing to use an unexpected CAPTCHA token in SQL: ${token}`);
  }
  runSql(`DELETE FROM captcha_sessions WHERE captcha_token = '${token}';`);
}

/**
 * Clears the lockout rows THIS SESSION's own failures created.
 *
 * ── Why every negative test in this session needs this ──────────────────────────────────────────
 * CooloffService.recordFailedAttempt fires on EVERY rejected CAPTCHA and every wrong OTP, and
 * checkCooloff then refuses the NEXT request with COOLOFF_ACTIVE (429) instead of the refusal the test is
 * asserting — so without this, negative tests poison each other and report product defects that are not
 * there. failed_attempts drives an escalating penalty (dev-local 5s → 10s → 20s) that only resets after
 * cms.auth.cooloff.reset-after-minutes, so the row is deleted rather than merely expired.
 *
 * ── Why it is scoped to the 987651 range and NOT to the loopback IP ─────────────────────────────
 * `login_cooloffs` is shared with two other concurrent sessions, which also run from loopback — so
 * `DELETE ... WHERE client_ip = '127.0.0.1'` would silently destroy THEIR lockout state mid-test. (It is
 * also refused outright by the unattended-run guard, correctly.)
 *
 * Scoping by mobile is sufficient *and* exact, because of how recordFailedAttempt maintains the row: it
 * looks the row up by fingerprint+IP and then OVERWRITES mobile_number with the number just attempted.
 * So the row that will block this session is, by construction, carrying one of this session's own
 * numbers. Deleting the 987651 range removes precisely the debris of my own failures and nothing else.
 */
export function clearCooloff(mobile?: string): void {
  if (mobile !== undefined && !/^\d{10}$/.test(mobile)) {
    throw new Error(`Refusing to use an unexpected mobile number in SQL: ${mobile}`);
  }
  // Session A's allocated range only — see the note above on why this is both safe and sufficient.
  runSql(`DELETE FROM login_cooloffs WHERE mobile_number LIKE '987651%';`);
  if (mobile) {
    runSql(`DELETE FROM login_cooloffs WHERE mobile_number = '${mobile}';`);
  }
}

/** Every CAPTCHA the page has been served, oldest first. See otp-lifecycle.spec.ts for the full rationale. */
export function trackCaptchas(page: Page): Array<{ question: string; token: string }> {
  const seen: Array<{ question: string; token: string }> = [];
  page.on('response', async (res) => {
    if (!res.url().includes('/citizen/auth/captcha')) return;
    try {
      const body = await res.json();
      if (body?.audioQuestion) seen.push({ question: body.audioQuestion, token: body.token });
    } catch {
      // Not a JSON challenge; the caller's wait will time out and say so rather than guessing here.
    }
  });
  return seen;
}

/**
 * Solves the MATH CAPTCHA currently bound to the component's token and types the answer.
 *
 * Condensed from otp-lifecycle.spec.ts, whose reasoning applies unchanged: the screen opens on the VISUAL
 * variant whose answer never reaches the client, so the audio control is clicked to swap to MATH (the only
 * variant a test can solve, and a real accessible-citizen path rather than a weakening of the control);
 * and `loadCaptcha()` BLANKS captchaInput when its response lands, so an answer typed while a refresh is
 * in flight is silently wiped and Send OTP then stays disabled on `!captchaInput` with nothing on screen
 * to explain it. The fill is therefore re-confirmed and re-solved if it did not survive.
 *
 * `seen` is how many challenges had arrived BEFORE the action that triggered this refresh.
 */
export async function solveAndFillCaptcha(
  page: Page,
  tracked: Array<{ question: string; token: string }>,
  seen = 0
): Promise<number> {
  let answer = 0;
  await expect(async () => {
    const question = page.locator('.captcha-math');
    if (!(await question.isVisible().catch(() => false))) {
      await page.locator('.icon-btn[aria-label="Listen to CAPTCHA audio"]').click();
    }
    await expect(question).toBeVisible({ timeout: 3000 });

    expect(tracked.length, 'no CAPTCHA challenge arrived after the action that should have refreshed it')
      .toBeGreaterThan(seen);

    const shown = ((await question.textContent()) || '').trim();
    expect(shown, 'the rendered CAPTCHA is still the previous challenge')
      .toBe((tracked[tracked.length - 1]?.question || '').trim());

    await expect(page.locator('.captcha-input')).toBeEnabled({ timeout: 3000 });

    const match = /(\d+)\s*(plus|minus)\s*(\d+)/i.exec(shown);
    expect(match, `captcha question was not solvable: ${shown}`).toBeTruthy();
    const [, a, op, b] = match!;
    answer = op.toLowerCase() === 'plus' ? Number(a) + Number(b) : Number(a) - Number(b);

    await page.locator('.captcha-input').fill(String(answer));
    // A wipe means a refresh landed after the solve: the answer belongs to a discarded challenge.
    await expect(page.locator('.captcha-input')).toHaveValue(String(answer), { timeout: 2000 });
  }).toPass({ timeout: 25000 });
  return answer;
}

/** Opens the login screen and waits for it to be interactive. */
export async function openLogin(page: Page): Promise<void> {
  await page.goto('/public/login', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
  await expect(page.locator('#mobile-input')).toBeVisible({ timeout: 15000 });
}

/** Fills the whole mobile step and clicks Send OTP, leaving the page on the OTP step. */
export async function reachOtpStep(page: Page, mobile: string): Promise<void> {
  const tracked = trackCaptchas(page);
  await openLogin(page);
  await page.locator('#mobile-input').fill(mobile);
  await solveAndFillCaptcha(page, tracked);
  await page.locator('.consent-section input[type="checkbox"]').check();
  const send = page.locator('.send-otp-btn');
  await expect(send).toBeEnabled({ timeout: 10000 });
  await send.click();
  await expect(page.locator('.otp-inputs')).toBeVisible({ timeout: 15000 });
}

/** A fresh MATH challenge fetched straight from the API, with its token, for API-level CAPTCHA cases. */
export async function freshMathCaptcha(
  request: APIRequestContext,
  apiBase: string
): Promise<{ token: string; answer: number }> {
  const res = await request.get(`${apiBase}/api/v1/citizen/auth/captcha?type=MATH`);
  expect(res.ok(), 'the CAPTCHA endpoint did not serve a challenge').toBeTruthy();
  const body = await res.json();
  const match = /(\d+)\s*(plus|minus)\s*(\d+)/i.exec(body.audioQuestion || '');
  expect(match, `unsolvable MATH challenge: ${body.audioQuestion}`).toBeTruthy();
  const [, a, op, b] = match!;
  return {
    token: body.token,
    answer: op.toLowerCase() === 'plus' ? Number(a) + Number(b) : Number(a) - Number(b),
  };
}

/**
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 * GETTING TO STEP 1 — COMPLAINANT DETAILS
 * ════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Blocks 8-16 each open with the same seven-step preamble ("login, pick an entity, answer the
 * eligibility questions"), which is not what those cases test — the login half is asserted exhaustively
 * in blocks 3-7 above, and the gate itself belongs to the eligibility-* specs.
 *
 * Session B keeps its own copy of this walk in helpers-forms-b.ts. It is duplicated rather than
 * imported deliberately: a helper edited by two concurrent sessions is exactly the chokepoint this file
 * exists to avoid, and sharing it would couple two suites that must be able to fail independently.
 *
 * The session is SEEDED rather than driven through OTP because `publicAuthService` stores it in
 * sessionStorage and never validates the token's signature — the established pattern in this repository
 * (seedCitizenSession) for reaching guarded public routes.
 */

/** ISO date `days` ago, for the "when did you complain to the RE" question. */
function isoDaysAgo(days: number): string {
  const d = new Date();
  d.setDate(d.getDate() - days);
  return d.toISOString().slice(0, 10);
}

/**
 * Walks the eligibility gate with the non-blocking answer to every question, landing on step 1.
 *
 * A BOUNDED LOOP over whatever questions the product serves, not a fixed sequence: the questions are
 * master data in ELIGIBILITY_QUESTION_MASTER, so their number and order change without a frontend
 * release and a hardcoded walk would break silently on a new row. 'no' is non-blocking for every
 * blocking question EXCEPT "have you complained to the RE", where 'no' blocks and 'yes' demands a date
 * and a copy of the complaint — that one is recognised by its text and answered 'yes'.
 *
 * A `.block-note` means the gate refused us, which would leave every downstream field assertion testing
 * the wrong screen; it throws with the offending question rather than failing obscurely later.
 */
export async function reachComplainantDetails(page: Page, mobile: string): Promise<void> {
  await page.goto('/public', { waitUntil: 'domcontentloaded' });
  await page.evaluate((m) => {
    sessionStorage.setItem('cms_public_session', JSON.stringify({
      identifier: m, token: 'e2e-session-a.sig', startedAt: Date.now(),
    }));
    sessionStorage.setItem('cms_public_last_activity', String(Date.now()));
  }, mobile);

  await page.goto('/public/file-complaint', { waitUntil: 'domcontentloaded' });
  await page.waitForLoadState('networkidle');
  // An unmatched Angular route falls through to the home page, so prove the wizard actually mounted
  // rather than asserting against a page that merely looks blank.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 20000 });

  // The entity question is a SEARCHABLE COMBOBOX, not a native <select>. It used to be both controls
  // bound to the same answer; the select was removed, so `input.entity-search-input` +
  // `li.entity-search-option` is the only path. Kept inline rather than imported from
  // helpers-forms-b.ts for the reason given in this file's header.
  const entityBox = page.locator('input.entity-search-input');
  await expect(entityBox).toBeVisible({ timeout: 20000 });
  // The list renders only while the dropdown is open, which focus does.
  await entityBox.click();
  const firstEntity = page.locator('li.entity-search-option').first();
  await expect(firstEntity, 'the entity master offered nothing to select')
    .toBeVisible({ timeout: 20000 });
  const entityName = ((await firstEntity.locator('.es-name').textContent()) || '').trim();
  // mousedown, not click: the option's handler is (mousedown), which fires BEFORE the input's blur
  // closes the list. A click would let blur remove the option mid-gesture.
  await firstEntity.dispatchEvent('mousedown');
  await expect(entityBox, 'the chosen entity was not recorded')
    .toHaveValue(entityName, { timeout: 10000 });
  await page.locator('button.btn-next').click();
  await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });

  for (let guard = 0; guard < 25; guard++) {
    if (await page.locator('.step-content').first().isVisible().catch(() => false)) break;

    const question = (await page.locator('.question-text').first().textContent().catch(() => '')) || '';
    const filedWithRe = /complaint.*(with|to).*(bank|entity)|filed a written|electronic complaint/i
      .test(question);

    // YES_NO_OPTIONS is [yes, no] and the template binds [value], so options are picked by order.
    await page.locator('.radio-list .radio-option').nth(filedWithRe ? 0 : 1).click();

    if (filedWithRe) {
      const date = page.locator('input[name="bankComplaintDate"]');
      if (await date.isVisible().catch(() => false)) {
        await date.fill('');
        const iso = isoDaysAgo(40);
        await date.pressSequentially(`${iso.slice(8, 10)}${iso.slice(5, 7)}${iso.slice(0, 4)}`);
      }
      const file = page.locator('input#complaintFileWithRE');
      if (await file.count()) {
        await file.setInputFiles({
          name: 're-complaint.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4 e2e'),
        });
      }
    }

    if (await page.locator('.block-note').isVisible().catch(() => false)) {
      throw new Error(`Eligibility blocked while walking the gate, on: ${question.trim()}`);
    }

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(250);
  }

  await expect(page.locator('select[name="complainantCategory"]'),
    'the eligibility gate did not hand over to the Complainant Details step')
    .toBeVisible({ timeout: 20000 });
}

/**
 * Fills the pincode and waits for the state/district derivation to settle.
 *
 * State and district are NOT independently selectable — both are derived by `onPincodeInput`, which
 * calls GET /api/v1/location/pincode/{code} and falls back to a compiled-in table. The step-1 validator
 * requires a state, and ONLY this lookup can supply one, so any test that needs to press Next has to go
 * through here and wait for the options to arrive.
 */
export async function fillPincode(page: Page, pincode: string): Promise<void> {
  const input = page.locator('input[name="pincode"]');
  await input.fill('');
  await input.fill(pincode);
  if (/^\d{6}$/.test(pincode)) {
    await expect(page.locator('select[name="state"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });
  }
  await expect(page.locator('.hint', { hasText: 'Loading' })).toHaveCount(0, { timeout: 20000 });
}

/**
 * The character classes every free-text field in this scope is required to reject or accept.
 *
 * Kept as data so blocks 4, 7, 9, 10, 11 and 15 assert the same vocabulary. QA words these as "does not
 * accept alphanumeric / alphabetic / special characters / only spaces", which is one rule per class.
 */
export const INPUT_CLASSES = {
  alphabetic: 'Ramesh',
  alphanumeric: 'Ram3sh9',
  special: '@#$%^&*!',
  spacesOnly: '    ',
  digits: '9876543210',
  emoji: 'Ram\u{1F600}esh',
} as const;

/** The contract of one free-text name field, as read off the rendered control rather than assumed. */
export interface TextFieldSpec {
  /** Manual block number, for the findings trail. */
  block: number;
  /** Manual line range, likewise. */
  lines: string;
  /** Label as the manual script words it — used in test titles so a reviewer can match them up. */
  label: string;
  /** `name` attribute of the input. */
  name: string;
  /** Which category renders this field: individual-only, or organisation-only. */
  shownFor: 'individual' | 'organisation';
  /**
   * Whether step 1 actually refuses to advance when this field is blank — MEASURED, not assumed.
   * Only firstName is in `validateCurrentStep()`; organizationName shares its error key but is never
   * checked, and middleName/lastName are not checked at all.
   */
  blocksNextWhenBlank: boolean;
  /**
   * Whether the MANUAL SCRIPT calls this field mandatory. Kept separate from `blocksNextWhenBlank` so the
   * two can disagree — where they do, that disagreement is the finding.
   */
  qaClaimsMandatory: boolean;
  /** Base mobile suffix; the driver allocates one number per generated test from here upward. */
  mobileFrom: number;
}

/**
 * Derives the field-validation cases of manual blocks 9, 10, 11 and 15 from a field's contract.
 *
 * Every assertion is of OBSERVED behaviour (standing ruling 2). The measured facts these tests encode,
 * established by reading the markup before any of them was written:
 *
 *   - None of the four inputs carries a `maxlength` or a `pattern`. There is NO client-side length or
 *     character validation on any name field.
 *   - The only length cap in the entire stack is `varchar(200)` on `COMPLAINT.complainant_name`
 *     (`Complaint.java:34-35`), and it applies to the CONCATENATION
 *     `[firstName, middleName, lastName].filter(Boolean).join(' ')` (component.ts:2161) — not to any one
 *     field. QA's "max 150 characters per field" has no counterpart anywhere in the product.
 *   - The string "Only letters are allowed." does not exist in the codebase.
 *
 * So the cases QA words as "should not accept X, with message Y" are asserted as *accepted, silently* —
 * each paired with a `test.fixme` carrying QA's expectation, so the day validation is added the fixme
 * starts passing and points back at the findings entry.
 */
export function describeTextFieldValidation(spec: TextFieldSpec): void {
  const { block, lines, label, name, shownFor, blocksNextWhenBlank, qaClaimsMandatory, mobileFrom } = spec;
  const sel = `input[name="${name}"]`;
  const individual = shownFor === 'individual';
  // A category that renders this field, and one that does not.
  const showing = individual ? 'individual' : 'trust';
  const hiding = individual ? 'trust' : 'individual';
  let cursor = mobileFrom;
  const mobile = () => sessionAMobile(cursor++);

  const open = async (page: Page): Promise<void> => {
    await reachComplainantDetails(page, mobile());
    await page.locator('select[name="complainantCategory"]').selectOption(showing);
    await expect(page.locator(sel), `${label} is not rendered for category "${showing}"`).toBeVisible();
  };

  test.describe(`QA-A${block} — the ${label} field (manual ${lines})`, () => {
    test(`${label} is displayed for the categories that render it, and hidden for the others`,
      async ({ page }) => {
        await open(page);
        await page.locator('select[name="complainantCategory"]').selectOption(hiding);
        await expect(page.locator(sel), `${label} is still shown for category "${hiding}"`).toHaveCount(0);
      });

    test(`${label} carries no maxlength and no pattern — there is no client-side validation at all`,
      async ({ page }) => {
        await open(page);
        const input = page.locator(sel);
        await expect(input, `${label} now has a maxlength — the 150-cap contradiction may be resolved`)
          .not.toHaveAttribute('maxlength', /.*/);
        await expect(input, `${label} now has a pattern — character validation may have been added`)
          .not.toHaveAttribute('pattern', /.*/);
      });

    // ── Character classes. Every one is accepted verbatim with no message; QA expects five refused.
    const classes: Array<{ what: string; typed: string; qaExpectsRefused: boolean }> = [
      { what: 'alphabets', typed: INPUT_CLASSES.alphabetic, qaExpectsRefused: false },
      { what: 'spaces in between values', typed: 'Ram Kumar', qaExpectsRefused: false },
      { what: 'alphanumeric data', typed: INPUT_CLASSES.alphanumeric, qaExpectsRefused: true },
      { what: 'numeric data', typed: INPUT_CLASSES.digits, qaExpectsRefused: true },
      { what: 'special characters', typed: INPUT_CLASSES.special, qaExpectsRefused: true },
      { what: 'emojis', typed: INPUT_CLASSES.emoji, qaExpectsRefused: true },
      { what: 'leading and trailing spaces', typed: '  Ramesh  ', qaExpectsRefused: true },
    ];

    for (const { what, typed, qaExpectsRefused } of classes) {
      test(`${label} accepts ${what}, keeping every character and raising no message`,
        async ({ page }) => {
          await open(page);
          const input = page.locator(sel);
          await input.fill(typed);
          // The control keeps the value verbatim — no filtering, no trimming, no truncation.
          await expect(input, `${label} altered the typed value`).toHaveValue(typed);
          await expect(page.locator('.field-error'),
            `${label} raised a message for ${what} — validation may have been added`).toHaveCount(0);
        });

      if (qaExpectsRefused) {
        test.fixme(`${label} rejects ${what} with "Only letters are allowed."`, async ({ page }) => {
          await open(page);
          await page.locator(sel).fill(typed);
          await expect(page.locator('.field-error').filter({ hasText: /only letters are allowed/i }))
            .toBeVisible({ timeout: 10000 });
        });
      }
    }

    // ── Spaces only. Kept separate from the classes above because `validateCurrentStep` uses `.trim()`,
    //    so for a field that IS validated whitespace counts as blank and behaves like the blank case.
    test(`${label} accepts a value of only spaces into the control`, async ({ page }) => {
      await open(page);
      const input = page.locator(sel);
      await input.fill(INPUT_CLASSES.spacesOnly);
      await expect(input, `${label} stripped a whitespace-only value`)
        .toHaveValue(INPUT_CLASSES.spacesOnly);
      await expect(page.locator('.field-error'),
        `${label} raised a message for whitespace before Next was pressed`).toHaveCount(0);
    });

    // ── The 150-character claim. 150 goes in, and so do 151 and 400, because nothing client-side stops it.
    test(`${label} accepts 150 characters, and is not capped there or anywhere else`,
      async ({ page }) => {
        await open(page);
        const input = page.locator(sel);
        for (const length of [150, 151, 400]) {
          const value = 'A'.repeat(length);
          await input.fill(value);
          await expect(input, `${label} truncated at ${length} characters — a cap may have been added`)
            .toHaveValue(value);
        }
        await expect(page.locator('.field-error'),
          `${label} raised a length message — the 150-cap may now be enforced`).toHaveCount(0);
      });

    test.fixme(`${label} refuses more than 150 characters`, async ({ page }) => {
      await open(page);
      const input = page.locator(sel);
      // `fill` obeys maxlength, so a real cap has to be probed by typing.
      await input.pressSequentially('A'.repeat(151), { delay: 0 });
      await expect(input).toHaveValue('A'.repeat(150));
    });

    // ── Blank. The one case that genuinely differs between the four fields, so it is driven from the
    //    measured `blocksNextWhenBlank` rather than from QA's claim (which says "mandatory" for three).
    test(`a blank ${label} ${blocksNextWhenBlank ? 'refuses' : 'permits'} Next`, async ({ page }) => {
      await open(page);

      // Satisfy everything step 1 needs EXCEPT the field under test.
      if (individual && name !== 'firstName') {
        await page.locator('input[name="firstName"]').fill('Ramesh');
      }
      await fillPincode(page, '411001');
      await page.locator('textarea[name="address"]').fill('12 Test Street, Pune');
      await page.locator(sel).fill('');

      await page.locator('button.btn-next').click();

      if (blocksNextWhenBlank) {
        await expect(page.locator('select[name="complainantCategory"]'),
          `a blank ${label} advanced the wizard`).toBeVisible();
        // And the refusal is VISIBLE. Both blocking fields share `validationErrors['name']`, whose two
        // renderers sit inside the category-conditional blocks — so this only holds because the rendered
        // field is the one validated (A-D5/A-C11b). A silent refusal here is the dead end returning.
        await expect(page.locator('.field-error').filter({ hasText: /name/i }),
          `a blank ${label} refused Next without saying so`).not.toHaveCount(0);
      } else {
        await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
          `a blank ${label} refused to advance, though it is not validated`)
          .toBeVisible({ timeout: 20000 });
      }
    });

    // QA's literal "Response is mandatory." is DECLINED where the field IS validated: the product names
    // the field it is refusing, which is more useful than a generic sentence, and the assertion above
    // already proves the citizen is told. A fixme is kept only where QA calls a field mandatory and the
    // product does not validate it — there the open question is not the wording but whether the field is
    // mandatory at all, which is still an unanswered ruling (A-C15).
    if (qaClaimsMandatory && !blocksNextWhenBlank) {
      test.fixme(`a blank ${label} is refused as mandatory`, async ({ page }) => {
        await open(page);
        await page.locator(sel).fill('');
        await page.locator('button.btn-next').click();
        await expect(page.locator('.field-error').filter({ hasText: /mandatory|required/i }))
          .toBeVisible({ timeout: 10000 });
      });
    }
  });
}
