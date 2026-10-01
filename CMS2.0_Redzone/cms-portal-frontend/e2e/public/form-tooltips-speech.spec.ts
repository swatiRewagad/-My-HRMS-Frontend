/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B48 / QA-B53 — FR-G-009 field tooltips, and the speech-to-text control on Facts of the Complaint
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack block 48 (lines 3578-3613, 5 cases after its 2-row preamble) and the automatable head of
 * block 53 (3853-3865, 2 cases after its 1-row preamble).
 *
 * ── FR-G-009 IS UNIMPLEMENTED: A FIFTEEN-ENTRY TOOLTIP MAP THAT NOTHING RENDERS ──────────────────
 * The component carries `tooltips: Record<string, string>` with fifteen entries, commented
 * `// FR-G-009: Tooltips` (ts:1713-1729). It is referenced **zero times** in the template and **zero
 * times anywhere else in src/**. There is no `pTooltip`, no `matTooltip`, no `[title]` binding and no
 * `.tooltip` element in ANY public component. The only three static `title=` attributes in the whole
 * public area are on unrelated buttons (delete-draft, refresh-CAPTCHA, play-CAPTCHA).
 *
 * So all 5 of block 48's cases describe a feature that does not exist. They are asserted as ABSENCE
 * rather than skipped, because "FR-G-009 is not built" is the finding and a skip is not a pass
 * (ruling 4). Logged as defect D-B9.
 *
 * One entry in that dead map is `disputeDate` — a tooltip written for a field that is itself never
 * rendered (D-B6). The map documents intent that was never wired up on either side.
 *
 * ── CASE 48.6 IS A PORTAL-WIDE SURVEY, NOT A TEST ────────────────────────────────────────────────
 * "Verify that the consistency of the tooltip is maintained across the portal" has no pass criterion and
 * no enumerated scope — it cannot be automated as written even if tooltips existed. Recorded as
 * needs-scoping in findings-QA-B.md §4 rather than guessed at. Same for case 48.5's "all forms defined in
 * FR-G-009", which the pack never lists; the test below covers every field on every step of the citizen
 * wizard, which is the widest defensible reading.
 *
 * ── BLOCK 53: ONLY FIELD AND CONTROL PRESENCE IS AUTOMATED ───────────────────────────────────────
 * Lines 3866-3910 (mic capture, real dictation, English-only dictation, live start/pause/stop) need the
 * Web Speech API driven with fake audio, which Playwright cannot do headlessly. Deferred per the brief.
 * What IS automated: the Facts field is present and accepts input (53.2/53.3, which overlap block 29 and
 * so are asserted only once here, minimally), and the mic control's presence, labelling and toggle
 * wiring — none of which needs audio.
 *
 * ── THE MIC BUTTON IS SHOWN EVEN WHERE SPEECH IS UNSUPPORTED ─────────────────────────────────────
 * `speechSupported` is computed at init (ts:801) and then **used nowhere** — the button is rendered
 * unconditionally (html:696). `startRecording` returns early with no SpeechRecognition constructor
 * (ts:2062-2063), so on a browser without the API the citizen gets a button that silently does nothing:
 * no message, no disabled state, no fallback. Defect D-B10, proven by removing the constructor.
 *
 * ── NO SEEDED ROWS, NO CLEANUP ──────────────────────────────────────────────────────────────────
 * Nothing is submitted; no OTP is sent; SYSTEM_CONFIG is untouched. Mobiles come from Session B's
 * reserved 98765_2____ range via the helper.
 */

import { test, expect, Page } from '../fixtures';
import {
  gotoComplainantDetails, gotoReDetails, gotoComplaintDetails, fillComplainantMinimalAndAdvance,
  redirectAppApi,
} from './helpers-forms-b';

/**
 * Every mechanism by which a tooltip could reach the citizen. Checked as a set, because asserting only
 * one of them would leave "we used a different library" as an untested escape.
 */
const TOOLTIP_MECHANISMS = [
  '[title]:not([title=""])',
  '[data-pc-name="tooltip"]',
  '.p-tooltip',
  '.tooltip',
  '[role="tooltip"]',
  '[aria-describedby]',
  '.pi-info-circle',
  '.info-icon',
];

/** Asserts that no tooltip of any kind exists among the fields of the current step. */
async function assertNoTooltipsOnStep(page: Page, stepName: string) {
  const step = page.locator('.step-content').first();
  await expect(step, `${stepName} must be rendered before its tooltips can be judged absent`)
    .toBeVisible();

  // The step genuinely has labelled fields, so this is absence of tooltips rather than absence of a step.
  expect(await step.locator('label').count(), `${stepName} must carry labelled fields`)
    .toBeGreaterThan(0);

  for (const mechanism of TOOLTIP_MECHANISMS) {
    await expect(step.locator(mechanism),
      `${stepName}: no tooltip via "${mechanism}"`).toHaveCount(0);
  }
}

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 48 — FR-G-009 tooltips
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B48 — FR-G-009 field tooltips are not implemented anywhere in the citizen wizard', () => {

  test('DEFECT D-B9: no field on Complainant Details carries a tooltip by any mechanism', async ({ page }) => {
    await gotoComplainantDetails(page);
    // Case 48.5 for step 1. Six of the fifteen dead tooltip entries target fields on this step
    // (name, email, complainantCategory, state, pincode, bankComplaintRef).
    await assertNoTooltipsOnStep(page, 'Complainant Details');
  });

  test('DEFECT D-B9: no field on Regulated Entity Details carries a tooltip by any mechanism', async ({ page }) => {
    await gotoReDetails(page);
    await assertNoTooltipsOnStep(page, 'Regulated Entity Details');
  });

  test('DEFECT D-B9: no field on Complaint Details carries a tooltip, though six tooltips are written for it', async ({ page }) => {
    await gotoComplaintDetails(page);

    // This is the step the map invests most in: complaintCategory, subCategory1, complaintText,
    // disputeAmount, compensationSought, reliefSought — plus disputeDate, for a field that does not
    // even exist (D-B6). None of the seven reaches the screen.
    await assertNoTooltipsOnStep(page, 'Complaint Details');

    // Named explicitly, because these are the fields a BA will check first.
    for (const name of ['complaintCategory', 'complaintText', 'disputeAmount']) {
      const field = page.locator(`[name="${name}"]`);
      await expect(field, `${name} must be on the step`).toHaveCount(1);
      expect(await field.getAttribute('title'), `${name} has no title tooltip`).toBeNull();
      expect(await field.getAttribute('aria-describedby'),
        `${name} points at no description`).toBeNull();
    }
  });

  test('DEFECT D-B9: there is no hover, click or info-icon affordance to reveal a tooltip (cases 48.7, 48.9)', async ({ page }) => {
    await gotoComplaintDetails(page);
    const facts = page.locator('textarea[name="complaintText"]');

    // Case 48.7 wants the tooltip reachable on hover, click or an info icon. Case 48.9 wants it to
    // disappear on mouse-out. Neither can be exercised: there is nothing to reveal and nothing to hide.
    await facts.hover();
    await page.waitForTimeout(1200); // longer than any tooltip show-delay would be
    for (const mechanism of TOOLTIP_MECHANISMS) {
      await expect(page.locator(mechanism),
        `hovering the Facts field revealed nothing via "${mechanism}"`).toHaveCount(0);
    }

    await facts.click();
    await page.waitForTimeout(600);
    await expect(page.locator('[role="tooltip"], .p-tooltip, .tooltip'),
      'clicking the field revealed no tooltip either').toHaveCount(0);

    // Focus is the third affordance an accessible implementation would use.
    await facts.focus();
    await page.waitForTimeout(600);
    expect(await facts.getAttribute('aria-describedby'),
      'focusing the field points at no description').toBeNull();
  });

  test('DEFECT D-B9: case 48.6 cannot be satisfied — no tooltip states purpose, format or mandatory-ness', async ({ page }) => {
    await gotoComplaintDetails(page);

    // Case 48.6 requires each tooltip to state the field's purpose, its expected input format
    // (including character limits) and whether it is mandatory. The Facts field has all three facts
    // available — it is mandatory and capped at 5000 — and communicates none of them.
    const facts = page.locator('textarea[name="complaintText"]');
    const cap = await facts.getAttribute('maxlength');
    expect(cap, 'the cap exists in the markup, so it COULD be surfaced').toBe('5000');

    const group = page.locator('.form-group', { has: facts }).first();
    const groupText = ((await group.textContent()) || '');
    expect(groupText, 'the character limit is never told to the citizen').not.toContain('5000');
    expect(groupText.toLowerCase(), 'no format guidance is given').not.toMatch(/alphanumeric|character limit/);

    // Mandatory-ness IS conveyed — but by a red asterisk, not by a tooltip.
    await expect(group.locator('.required'), 'mandatory-ness is shown as an asterisk only').toHaveCount(1);
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 53 — Facts of the Complaint and its speech-to-text control
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B53 — the Facts field carries a speech-to-text control whose presence is not gated on support', () => {

  test('the Facts of the Complaint field is present and accepts input', async ({ page }) => {
    await gotoComplaintDetails(page);

    // Cases 53.2 and 53.3. Block 29 owns this field's validation in full; asserted here only to the
    // depth block 53 asks for, so the two specs do not duplicate each other.
    const facts = page.locator('textarea[name="complaintText"]');
    await expect(facts).toBeVisible();
    await facts.fill('Dictated narrative typed by hand.');
    expect(await facts.inputValue()).toBe('Dictated narrative typed by hand.');
  });

  test('the mic control sits with the Facts field and is labelled for screen readers', async ({ page }) => {
    await gotoComplaintDetails(page);

    const mic = page.locator('.mic-pill-btn');
    await expect(mic).toBeVisible();
    expect(await mic.getAttribute('type'),
      'the mic must not submit the form when clicked').toBe('button');
    expect(await mic.getAttribute('aria-label'),
      'the control is announced to assistive technology').toBe('Start voice recording');

    // It belongs to the Facts field, not to some other control that happens to be nearby.
    const wrapper = page.locator('.textarea-wrapper', {
      has: page.locator('textarea[name="complaintText"]'),
    });
    await expect(wrapper.locator('.mic-pill-btn')).toHaveCount(1);
  });

  test('DEFECT D-B10: the mic button is rendered even when the browser has no Speech API, and does nothing silently', async ({ page }) => {
    // `speechSupported` is computed at init (ts:801) and then used NOWHERE; the button is rendered
    // unconditionally (html:696). Removing the constructor before the app boots reproduces any browser
    // without the API — Firefox, or a hardened corporate build.
    await page.addInitScript(() => {
      delete (window as any).SpeechRecognition;
      delete (window as any).webkitSpeechRecognition;
    });
    await gotoComplaintDetails(page);

    const mic = page.locator('.mic-pill-btn');
    await expect(mic, 'the control is offered regardless of support').toBeVisible();
    await expect(mic, 'and it is not disabled').toBeEnabled();

    await mic.click();
    await page.waitForTimeout(800);

    // startRecording returns early, so nothing happens at all: no recording state, no message, no
    // fallback instruction. The citizen is left tapping a dead button.
    await expect(mic, 'no recording state is entered').not.toHaveClass(/recording/);
    expect(await mic.getAttribute('aria-label'),
      'the label still invites the citizen to start').toBe('Start voice recording');
    await expect(page.locator('.field-error, .hint', { hasText: /speech|voice|microphone|support/i }),
      'nothing tells the citizen speech input is unavailable here').toHaveCount(0);
  });

  test('the mic toggle flips the control into and out of its recording state', async ({ page }) => {
    // Chromium exposes webkitSpeechRecognition, but a real recognition session needs microphone audio
    // this harness cannot supply. The constructor is stubbed so the TOGGLE WIRING can be proven without
    // asserting anything about dictation — that half stays deferred (lines 3866-3910).
    await page.addInitScript(() => {
      class StubRecognition {
        lang = ''; continuous = false; interimResults = false;
        onresult: ((e: any) => void) | null = null;
        onerror: (() => void) | null = null;
        onend: (() => void) | null = null;
        start() { /* a real session would begin capturing here */ }
        stop() { this.onend?.(); }
      }
      (window as any).SpeechRecognition = StubRecognition;
      (window as any).webkitSpeechRecognition = StubRecognition;
    });
    await gotoComplaintDetails(page);

    const mic = page.locator('.mic-pill-btn');
    await mic.click();
    await expect(mic, 'starting enters the recording state').toHaveClass(/recording/);
    expect(await mic.getAttribute('aria-label')).toBe('Stop voice recording');

    await mic.click();
    await expect(mic, 'stopping leaves it').not.toHaveClass(/recording/);
    expect(await mic.getAttribute('aria-label')).toBe('Start voice recording');
  });

  test('a dictated transcript is appended to the narrative rather than replacing it', async ({ page }) => {
    // The one dictation behaviour that can be proven without audio: onresult appends to whatever the
    // citizen has already typed (ts:2075). Worth asserting because replacing would silently destroy a
    // narrative the citizen had typed by hand.
    await page.addInitScript(() => {
      class StubRecognition {
        lang = ''; continuous = false; interimResults = false;
        onresult: ((e: any) => void) | null = null;
        onerror: (() => void) | null = null;
        onend: (() => void) | null = null;
        start() {
          setTimeout(() => this.onresult?.({
            resultIndex: 0,
            results: [[{ transcript: 'spoken words' }]],
          }), 50);
        }
        stop() { this.onend?.(); }
      }
      (window as any).SpeechRecognition = StubRecognition;
      (window as any).webkitSpeechRecognition = StubRecognition;
    });
    await gotoComplaintDetails(page);

    const facts = page.locator('textarea[name="complaintText"]');
    await facts.fill('Typed by hand.');
    await page.locator('.mic-pill-btn').click();

    await expect(facts, 'the typed narrative survives and the transcript is appended')
      .toHaveValue('Typed by hand. spoken words');
  });
});
