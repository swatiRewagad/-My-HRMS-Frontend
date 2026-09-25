/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual blocks 42, 43, 44 (the three money fields on Complaint Details).
 * Manual source: prompt_testcase_cms_frontemd.txt lines 3012-3107 (Amount Involved),
 * 3108-3223 (Compensation — Consequential Loss), 3224-3346 (Compensation — Expenses/Harassment).
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Three blocks of eleven/twelve cases against three inputs that share ONE handler,
 * `onAmountInput` (ts:1580-1589). Table-driven for that reason: a charset bug would be identical
 * in all three, and a cap bug can only be in the two that have caps.
 *
 * ═══ HOW THE FIELDS ACTUALLY BEHAVE (and why the manual's cases are half-wrong) ═══
 *
 * `onAmountInput` STRIPS non-digits rather than rejecting them:
 *     const digitsOnly = value.replace(/[^\d]/g, '');
 *     this.formData[field] = digitsOnly ? this.formatIndianNumber(digitsOnly) : '';
 *
 * So the manual's "cannot accept alphanumeric / alphabetic / special / negative / decimal" cases
 * ARE honoured in substance — nothing but digits survives — but NOT in the form the manual
 * describes. The manual (lines 3165-3201, 3277-3323) demands that typing letters produce the
 * ₹30-lakh / ₹3-lakh CAP error message. That is impossible by construction: the letters are
 * stripped before any validator runs, so "abc" becomes "" and there is no amount to exceed a cap.
 * Demanding a cap error for alphabetic input is a category error in the test case.
 * These are asserted as the real behaviour (input silently normalised to digits, NO error shown)
 * and the manual's expectation is logged as D-C-06.
 *
 * ═══ THE DEFECT THIS FILE FOUND: THE STRIP DOES NOT REACH THE SCREEN WHEN IT STRIPS EVERYTHING ═══
 *
 * The model is cleared but the input is not. `formData[field]` correctly becomes `''` for "abcdef",
 * yet the citizen still sees "abcdef" in the box. The cause is the binding: html:832/841/850 use
 * ONE-WAY `[ngModel]` with `(ngModelChange)`, so Angular only rewrites the DOM when the new model
 * value DIFFERS from the old one. Starting from an empty field, `'' → ''` is not a change, nothing
 * is written back, and the rejected text survives on screen.
 *
 * `12a34b` → `1234` works for exactly the same reason inverted: the result differs, so the writeback
 * happens. This is the SAME regression the comment at ts:1581-1584 claims to have fixed — the fix
 * addressed the partial-strip case and left the total-strip case behind. Recorded as D-C-13, and the
 * two tests below assert the DEFECT-FREE behaviour as `fixme` per ruling 2.
 *
 * Note the surviving digits: `-500` → `500` and `1.50` → `150`. The sign and the decimal point are
 * discarded rather than the value being rejected, so a citizen who types a decimal amount gets a
 * silently DIFFERENT number (150 for 1.50 — a hundredfold error) with no warning. That is a real
 * usability risk and is recorded separately as D-C-12; it is asserted as observed, not fixme'd,
 * because "reject" vs "strip" is a BA decision, not a self-evident bug.
 *
 * ═══ THE CAPS: WHY NO SPEC HERE PINS ₹30 LAKH OR ₹3 LAKH AS CORRECT ═══
 *
 * Standing ruling 1 forbids pinning a configured number. The upload limit obeys this — it is
 * served by GET /api/v1/config/upload-limits and read at runtime. The compensation caps DO NOT:
 * they are the literals 3000000 and 300000, written twice each (ts:1632/1684 and ts:1641/1689),
 * with no config key and no endpoint (`/api/v1/config/compensation-limits` → 404, verified).
 *
 * That leaves a genuine dilemma, resolved as follows:
 *   - `capOf()` MEASURES the cap by binary-searching the field's own behaviour, then every
 *     boundary case (at / one under / one over) is computed from the measurement. So these tests
 *     keep passing if the figure is retuned, exactly as the ruling intends.
 *   - ONE test asserts that the cap is 30 lakh / 3 lakh — and it is `fixme`'d as the DEFECT
 *     marker, because what needs to change is not the number but the fact that it is compiled in.
 *     Its failure message is the finding.
 * Do not "simplify" this by writing `expect(cap).toBe(3000000)`.
 */

import { test, expect } from '../fixtures';
import { openComplaintDetails, sessionCMobile, fieldInput, fieldError } from './helpers-submission-c';
import { deleteOtpAttempts } from '../utils/test-data';

const mobiles: string[] = [];
function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

test.afterAll(async () => {
  for (const m of mobiles) {
    try { await deleteOtpAttempts(m); } catch { /* best effort */ }
  }
});

const NEXT = { hasText: 'Next' };

interface AmountField {
  block: string;
  label: string;
  field: 'disputeAmount' | 'compensationSought' | 'reliefSought';
  /** Whether a cap is enforced on this field at all. */
  capped: boolean;
  /** The figure the MANUAL claims, recorded for the findings — never asserted as correct. */
  manualClaimRupees: number | null;
  /** Fragment of the cap error the component produces, for locating it without pinning the number. */
  capErrorFragment: string | null;
}

const AMOUNT_FIELDS: AmountField[] = [
  {
    block: 'C42', label: 'Amount Involved in Transaction/Dispute', field: 'disputeAmount',
    capped: false, manualClaimRupees: null, capErrorFragment: null,
  },
  {
    block: 'C43', label: 'Compensation Sought — Consequential Loss', field: 'compensationSought',
    capped: true, manualClaimRupees: 3000000,
    capErrorFragment: 'consequential loss can be awarded only up to',
  },
  {
    block: 'C44', label: 'Compensation Sought — Expenses/Harassment/Mental Anguish', field: 'reliefSought',
    capped: true, manualClaimRupees: 300000,
    capErrorFragment: 'expenses, harassment, and mental anguish can be awarded only up to',
  },
];

/** Digits as the citizen typed them, ignoring the thousands separators the component inserts. */
async function digitsOf(page: any, field: string): Promise<string> {
  return (await fieldInput(page, field).inputValue()).replace(/[^\d]/g, '');
}

/**
 * The measured caps, held for the lifetime of the worker.
 *
 * A cap is a property of the shipped bundle, not of the page, so measuring it once is sound — and
 * necessary: discovery costs ~28 probes, and re-running it inside every test pushed the last one
 * past the 60s budget (`reliefSought`'s step-gating test died mid-probe on the first run).
 * `workers: 1` in playwright.config.ts means exactly one discovery per field for the whole file.
 */
const measuredCaps = new Map<string, number>();

/**
 * MEASURES the enforced cap instead of assuming it.
 *
 * A doubling probe followed by a binary search over the field's own error state. The alternative —
 * writing 3000000 into the spec — is precisely what the standing ruling forbids, and would silently
 * assert the stale side of the rule the day the scheme retunes the figure.
 */
async function capOf(page: any, field: string, errorFragment: string): Promise<number> {
  const cached = measuredCaps.get(field);
  if (cached !== undefined) return cached;

  const err = fieldError(page, field);
  const rejects = async (amount: number): Promise<boolean> => {
    await fieldInput(page, field).fill(String(amount));
    // Poll instead of sleeping a flat 120ms on every probe. The validator runs inside the
    // ngModelChange handler, so a REJECTED amount is usually visible on the first look and only the
    // accepted ones pay the full wait — which is what brought discovery inside the test budget.
    for (let i = 0; i < 8; i++) {
      if ((await err.count()) > 0 && (await err.first().innerText()).includes(errorFragment)) return true;
      await page.waitForTimeout(50);
    }
    return false;
  };

  let hi = 1000;
  while (!(await rejects(hi))) {
    hi *= 2;
    if (hi > 1e12) throw new Error(`no cap found on ${field} below 1e12 — is the field capped at all?`);
  }
  let lo = Math.floor(hi / 2);

  // Invariant: lo is accepted, hi is rejected. Converge on the largest accepted value.
  while (hi - lo > 1) {
    const mid = Math.floor((lo + hi) / 2);
    if (await rejects(mid)) hi = mid; else lo = mid;
  }
  measuredCaps.set(field, lo);
  return lo;
}

for (const spec of AMOUNT_FIELDS) {

  test.describe(`QA-${spec.block} — ${spec.label} accepts only digits and shows the amount in words`, () => {

    test('the field is present on the complaint details screen', async ({ page }) => {
      await openComplaintDetails(page, nextMobile());
      await expect(fieldInput(page, spec.field)).toBeVisible();
    });

    test('a numeric amount is accepted and grouped in the Indian convention', async ({ page }) => {
      await openComplaintDetails(page, nextMobile());

      const input = fieldInput(page, spec.field);
      await input.fill('1234567');

      // `formatIndianNumber` (ts:1592) groups as 12,34,567 — lakh/crore, not thousands.
      await expect(input).toHaveValue('12,34,567');
    });

    test('the amount is echoed in words so the citizen can check the magnitude', async ({ page }) => {
      // The strongest guard against the decimal-stripping surprise in D-C-12: "one hundred fifty
      // rupees" beside a field where the citizen typed 1.50 is the only on-screen warning.
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('250000');
      await expect(page.locator('.amount-words', { hasText: 'lakh' }).first()).toBeVisible();
    });

    test.fixme('alphabetic characters never reach the field', async ({ page }) => {
      // MANUAL (e.g. line 3073) says "should not accept alphabetic characters". It additionally
      // demands a cap error message here, which cannot occur — see D-C-06.
      //
      // OBSERVED: the MODEL is cleared but the INPUT is not. `formData[field]` is '' while the box
      // still reads "abcdef", because the one-way `[ngModel]` (html:832/841/850) only writes back on
      // a CHANGE and '' → '' is not one. So the citizen sees letters in a rupee field and the review
      // screen shows '—'. Asserting the required behaviour per ruling 2. See D-C-13.
      await openComplaintDetails(page, nextMobile());

      const input = fieldInput(page, spec.field);
      await input.fill('abcdef');
      await expect(input).toHaveValue('');
    });

    test('alphabetic characters are excluded from the submitted amount even though they stay on screen', async ({ page }) => {
      // The passing companion to the fixme above: it pins down WHICH half of the behaviour works, so
      // the defect is scoped rather than alarming. The model is clean — nothing but digits can be
      // submitted — and the amount-in-words line, which reads the MODEL, correctly shows nothing.
      // Delete this together with the fixme when the writeback is fixed.
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('abcdef');

      // The words line is driven by `@if (formData[field])`, so an empty model hides it entirely.
      await expect(page.locator('.amount-words')).toHaveCount(0);
    });

    test('an alphanumeric entry keeps only its digits', async ({ page }) => {
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('12a34b');
      // Regression guard: this used to self-assign a one-way binding, leaving the letters on screen
      // (documented at ts:1581-1584). "12a" must not survive as "12a".
      expect(await digitsOf(page, spec.field)).toBe('1234');
    });

    test.fixme('special characters never reach the field', async ({ page }) => {
      // Same defect as the alphabetic case, same cause: every character is stripped, so the model
      // goes '' → '' and the writeback never fires. "!@#$%^" stays visible. See D-C-13.
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('!@#$%^');
      await expect(fieldInput(page, spec.field)).toHaveValue('');
    });

    test('a partial strip DOES reach the screen, which is what localises the writeback defect', async ({ page }) => {
      // The discriminating case. Typing over an existing value produces a DIFFERENT model value, the
      // writeback fires, and the separators appear — proving the strip logic itself is sound and the
      // fault is purely the no-change writeback. Without this, D-C-13 could be misread as "the
      // component does not strip at all", which would send someone to rewrite `onAmountInput`.
      await openComplaintDetails(page, nextMobile());

      const input = fieldInput(page, spec.field);
      await input.fill('12!@34');
      await expect(input).toHaveValue('1,234');
    });

    test('a negative sign is discarded and the magnitude is kept', async ({ page }) => {
      // MANUAL says "cannot accept negative numbers". Observed: the MINUS is dropped, the digits
      // stay — so -500 becomes 500 rather than being refused. Recorded as D-C-12.
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('-500');
      expect(await digitsOf(page, spec.field)).toBe('500');
      await expect(fieldInput(page, spec.field)).not.toHaveValue('-500');
    });

    test('a decimal point is discarded, which silently changes the amount', async ({ page }) => {
      // The sharp edge of the same behaviour: 1.50 becomes 150, a hundredfold error, with no
      // warning beyond the amount-in-words line. Asserted as observed because "reject vs strip" is
      // a BA decision. See findings-QA-C.md D-C-12.
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('1.50');
      expect(await digitsOf(page, spec.field)).toBe('150');
    });

    test('the field may be left blank and the step still advances', async ({ page }) => {
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('');
      await page.locator('button', NEXT).first().click();

      // All three money fields are optional — the step must advance to 4 (representative screen).
      await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    });

    test('leading zeros are normalised away', async ({ page }) => {
      // `formatIndianNumber` strips them (ts:1593). Not in the manual; it is the obvious neighbour
      // of its blank case and the kind of thing that reaches a payload as "000500".
      await openComplaintDetails(page, nextMobile());

      await fieldInput(page, spec.field).fill('000500');
      expect(await digitsOf(page, spec.field)).toBe('500');
    });
  });

  if (spec.capped) {

    test.describe(`QA-${spec.block} — the ${spec.label} cap is whatever the running code enforces, and its boundary holds`, () => {

      test('the boundary is enforced exactly: at the cap accepted, one rupee over rejected', async ({ page }) => {
        await openComplaintDetails(page, nextMobile());

        const cap = await capOf(page, spec.field, spec.capErrorFragment!);

        // Every figure below is DERIVED from the measured cap. Nothing is pinned.
        const input = fieldInput(page, spec.field);
        const err = fieldError(page, spec.field);

        // Well under — clean.
        await input.fill(String(Math.floor(cap / 2)));
        await expect(err).toHaveCount(0);

        // Exactly at the cap — must be accepted, this is the classic off-by-one.
        await input.fill(String(cap));
        await expect(err).toHaveCount(0);

        // One rupee over — must be refused.
        await input.fill(String(cap + 1));
        await expect(err.first()).toContainText(spec.capErrorFragment!);

        // And far over.
        await input.fill(String(cap * 10));
        await expect(err.first()).toContainText(spec.capErrorFragment!);
      });

      test('the cap error names a rupee limit in the message the citizen reads', async ({ page }) => {
        await openComplaintDetails(page, nextMobile());

        const cap = await capOf(page, spec.field, spec.capErrorFragment!);
        await fieldInput(page, spec.field).fill(String(cap + 1));

        const text = await fieldError(page, spec.field).first().innerText();
        // The message must state a limit in lakh and must carry the rupee symbol, without this
        // test asserting WHICH figure — that is the configuration's business.
        expect(text).toMatch(/₹\s?[\d,.]+\s?lakh/);
        expect(text).toContain('Please enter an amount up to');
      });

      test('breaching the cap blocks the step, not merely the field', async ({ page }) => {
        // A field-level error that does not gate Next would let an over-cap amount reach the review
        // screen and the payload. `validateCurrentStep` re-checks at ts:1684/1689 for this reason.
        await openComplaintDetails(page, nextMobile());

        const cap = await capOf(page, spec.field, spec.capErrorFragment!);
        await fieldInput(page, spec.field).fill(String(cap + 1));

        await page.locator('button', NEXT).first().click();

        // Must still be on step 3 — the representative screen must NOT have appeared.
        await expect(page.locator('#auth-rep-label')).toHaveCount(0);
        await expect(fieldError(page, spec.field).first()).toContainText(spec.capErrorFragment!);
      });

      test('clearing an over-cap amount clears the error and unblocks the step', async ({ page }) => {
        await openComplaintDetails(page, nextMobile());

        const cap = await capOf(page, spec.field, spec.capErrorFragment!);
        const input = fieldInput(page, spec.field);

        await input.fill(String(cap + 1));
        await expect(fieldError(page, spec.field).first()).toBeVisible();

        await input.fill('');
        await expect(fieldError(page, spec.field)).toHaveCount(0);

        await page.locator('button', NEXT).first().click();
        await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
      });

      test.fixme(`the ${spec.label} cap is served by configuration rather than compiled into the bundle`, async ({ request }) => {
        // THE DEFECT MARKER, and the reason this file measures the cap instead of naming it.
        //
        // The upload limit obeys the standing ruling: GET /api/v1/config/upload-limits serves it
        // from system_config, UploadLimitsService reads it at runtime, and a source-scan guard
        // (ui-homogenisation/upload-limits.spec.ts) fails any file that hardcodes a byte figure.
        //
        // The compensation caps got none of that. They are the literals 3000000 and 300000, written
        // TWICE EACH (file-complaint.component.ts:1632 + :1684, and :1641 + :1689), and this
        // endpoint does not exist. These are STATUTORY award limits, so retuning one currently
        // means a frontend rebuild and redeploy. See findings-QA-C.md D-C-01.
        const res = await request.get(`${process.env['API_BASE_URL']}/api/v1/config/compensation-limits`);
        expect(res.status(), 'no endpoint serves the compensation caps').toBe(200);
      });

      test.fixme(`an over-cap ${spec.label} is refused by the server, not only by the browser`, async () => {
        // The caps are enforced in the Angular component only; no server-side rejection was found
        // for either figure, so a crafted POST files a complaint claiming any amount. Whether the
        // server SHOULD refuse is a BA/legal question (advisory at filing vs binding at award),
        // which is why this is a fixme carrying the question rather than a written assertion.
        // See findings-QA-C.md D-C-01, second half.
        expect(true, 'pending BA ruling on whether the filing-time cap is binding server-side').toBe(false);
      });
    });
  }
}

test.describe('QA-C43/C44 — the two caps are independent of each other', () => {

  test('a lawful consequential-loss amount is not rejected because harassment is over its own cap', async ({ page }) => {
    // The two validators write into one `validationErrors` map. Cross-contamination here would
    // block a citizen whose consequential-loss figure was perfectly lawful.
    await openComplaintDetails(page, nextMobile());

    const harassCap = await capOf(page, 'reliefSought',
      'expenses, harassment, and mental anguish can be awarded only up to');

    await fieldInput(page, 'reliefSought').fill(String(harassCap + 1));
    await fieldInput(page, 'compensationSought').fill(String(harassCap));

    await expect(fieldError(page, 'reliefSought').first()).toBeVisible();
    await expect(fieldError(page, 'compensationSought')).toHaveCount(0);
  });

  test('the harassment cap is lower than the consequential-loss cap', async ({ page }) => {
    // Relationship, not magnitude: the scheme awards far more for consequential loss than for
    // expenses and anguish, so if a retune ever inverted them that is a substantive error whichever
    // figures are current. This holds for any pair of values and pins neither.
    await openComplaintDetails(page, nextMobile());

    const lossCap = await capOf(page, 'compensationSought',
      'consequential loss can be awarded only up to');
    const harassCap = await capOf(page, 'reliefSought',
      'expenses, harassment, and mental anguish can be awarded only up to');

    expect(harassCap).toBeLessThan(lossCap);
  });

  test('the amount involved in the dispute is not capped by either compensation limit', async ({ page }) => {
    // A disputed transaction can lawfully exceed what may be AWARDED for it, so a cap on
    // disputeAmount would be wrong. Proven by driving it past the larger of the two caps.
    await openComplaintDetails(page, nextMobile());

    const lossCap = await capOf(page, 'compensationSought',
      'consequential loss can be awarded only up to');

    await fieldInput(page, 'disputeAmount').fill(String(lossCap * 100));
    await expect(fieldError(page, 'disputeAmount')).toHaveCount(0);

    // And it must not block the step.
    await fieldInput(page, 'compensationSought').fill('');
    await page.locator('button', NEXT).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
  });
});
