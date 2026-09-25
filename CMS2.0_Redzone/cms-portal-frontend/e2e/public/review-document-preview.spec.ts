/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual blocks 52 AND 57, which are one feature: previewing the documents a
 * citizen has attached to a complaint.
 * Manual source: prompt_testcase_cms_frontemd.txt lines 3801-3861 (block 52) and 4068-4108 (block 57).
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * ═══ WHY TWO BLOCKS SHARE ONE SPEC ═══
 * Block 57 is block 52 re-typed. Seven of its twelve cases are verbatim: the login preamble, "on the
 * review screen the user has the option to preview", "preview option is present", "the user is able to
 * preview", "the supported file types: PDF, JPG, PNG, DOCX", "not able to preview unsupported types",
 * and "preview opens in an inline viewer". The only differences are that block 52 adds a specific error
 * string and the close/remove/re-upload group, while block 57 adds the download group, the per-document
 * label, and opening several documents in turn. They are covered once here, per the brief's instruction
 * to collapse known duplication, and the coverage table attributes the tests to both.
 *
 * ═══ THE HEADLINE: THE PREVIEW IS NOT WHERE THE MANUAL SAYS, AND IS NOT WHAT THE MANUAL DESCRIBES ═══
 *
 * Both blocks open with "on the complaint details REVIEW screen, the user has the option to preview the
 * uploaded documents". Two things are wrong with that.
 *
 * (1) WHERE. The preview control lives on step 3, the upload field itself (`button.preview-file`,
 *     html:894). The review screen — step 6 — lists attachments as NAMES ONLY (html:1202-1207): a
 *     `.rs-row-full` per file carrying `f.name` and a file icon, with no preview button, no download
 *     button and no remove button. A citizen doing the one thing a review screen exists for — checking
 *     what they are about to submit — cannot open the documents from it. That is D-C-32.
 *
 * (2) WHAT. `previewAttachment` (ts:2061-2063) is one line: `window.open(url, '_blank')` on an
 *     `URL.createObjectURL` blob. There is no viewer. It is a new browser tab, so:
 *       - "preview opens in an inline or modal popup viewer" is false on both counts;
 *       - "the close option is present on the preview screen" — there is no preview screen to put a
 *         close control on; the citizen closes a browser tab;
 *       - "zoom options" are whatever the browser provides, not the product;
 *       - "download option is present for each uploaded document" — none exists anywhere in the product;
 *         the browser's own PDF toolbar is what block 57 is describing.
 *     This is D-C-33. It is written as a passing test recording that a tab opens, plus `fixme`s carrying
 *     the manual's viewer/close/download requirements per ruling 2.
 *
 * ═══ THE UNSUPPORTED-TYPE CASE IS UNREACHABLE, NOT UNIMPLEMENTED ═══
 * Both blocks require that an unsupported file cannot be previewed, block 52 naming the message
 * "Preview not available for this file type." That string does not exist in the portal — the only
 * "Preview not available" in the repository is `crpc/draft-assessment.component.html:231`, an officer
 * screen. But the requirement cannot be satisfied even in principle: an unsupported file is refused by
 * the UPLOAD validator (see D-C-07), so it never becomes an attachment and never acquires a preview
 * button to press. The precondition the case needs cannot be established. Proven here rather than
 * asserted away — a `.xlsx` is offered, refused, and no chip appears — and recorded as D-C-34, because
 * "unreachable" and "broken" are different findings and the BA should be told which this is.
 *
 * ═══ DOCX AGAIN ═══
 * "The user is able to preview the supported file types: PDF, JPG, PNG, DOCX" — `.docx` cannot be
 * attached at all (`file-validator.ts:36` allows `.pdf .doc .jpg .jpeg .png`), so a third of the named
 * set is unpreviewable because it is unuploadable. Already D-C-07 from block 45; not re-raised, but the
 * preview test covers the three types that CAN be attached and a `fixme` carries the fourth, so the
 * defect surfaces in this block too rather than being silently narrowed to what works.
 *
 * ═══ THE FINDING NEITHER BLOCK ASKS FOR: A RESUMED DRAFT CANNOT PREVIEW ANYTHING ═══
 * A saved draft persists `attachmentMeta` — name, type, size — and no bytes (ts:1879). On resume the
 * previews are rebuilt with `url: ''` (ts:1965). The template gates the preview button on `f.url`
 * (html:890-895), so a resumed draft shows the file name, a "(re-upload needed)" hint, and NO preview
 * control. Correct given that the bytes are gone, and worth pinning because it is the state a citizen
 * who saved and came back is actually in — the manual tests only the happy path within one session.
 *
 * ═══ TRAPS PAID FOR ═══
 *  - `window.open` produces a Playwright POPUP, not a navigation. It must be caught with
 *    `page.waitForEvent('popup')` STARTED BEFORE the click, or the event is missed and the test hangs
 *    to timeout. A popup on a `blob:` URL also never fires `load` reliably, so nothing here waits on it.
 *  - **A blob PDF popup reports `url() === ''` in HEADLESS chromium, and the real blob URL when HEADED.**
 *    Diagnosed, not guessed: the same fixture gives `blob:http://localhost:4202/<uuid>` with `--headed`
 *    and `''` without, while a PNG and a JPG give the blob URL in both. Headless chromium ships no PDF
 *    plugin, so the tab has nothing to commit a navigation to. It is a HARNESS artefact — the product
 *    opened the tab correctly in every case — so `openPreview` asserts only that a tab opened, and the
 *    tests that need a blob URL to compare use IMAGE fixtures. Do not "fix" this by asserting
 *    `/^blob:/` on a PDF; it will pass headed and fail in CI.
 *  - The popup's URL is `blob:<origin>/<uuid>`, which carries no file name. So a test cannot prove
 *    WHICH document opened from the URL; identity is proven from the button's `aria-label` instead.
 *  - Preview and Remove are both icon-only buttons inside the same `.file-chip`. Addressing them by
 *    class (`.preview-file` / `.remove-file`) is unambiguous; addressing by `button` ordinal is not.
 *  - Fixtures carry correct `mimeType` values because `validateFile` checks `file.type` as a second
 *    gate (file-validator.ts:48-51) — a wrong MIME is refused for the MIME reason and looks like a
 *    preview fault. Same trap documented at wizard-upload-documents.spec.ts:45-51.
 *  - Accessibility of the preview ("screen reader compatible, zoom options", lines 3825-3827) is in the
 *    deferred bucket: it needs an assistive-technology audit, and with the preview being a browser tab
 *    there is no product surface to audit.
 */

import { test, expect } from '../fixtures';
import { openWizardAtStep, openComplaintDetails, sessionCMobile } from './helpers-submission-c';
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

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Fixtures. Real container magic bytes and real MIME types, because the validator reads `file.type`
// and anything downstream that sniffs content would reject a fixture that lies about itself.
// ═══════════════════════════════════════════════════════════════════════════════════════════════

const MAGIC: Record<string, Buffer> = {
  pdf: Buffer.from([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a]),
  jpg: Buffer.from([0xff, 0xd8, 0xff, 0xe0]),
  png: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  doc: Buffer.from([0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1]),
  docx: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
  xlsx: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
};

const MIME: Record<string, string> = {
  pdf: 'application/pdf',
  jpg: 'image/jpeg',
  png: 'image/png',
  doc: 'application/msword',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
};

/** A small, valid-enough file of the given extension. Sizes stay far below any configured limit. */
function attachment(name: string, ext: string, bytes = 2048) {
  const magic = MAGIC[ext] ?? Buffer.from([0x68, 0x69]);
  return {
    name,
    mimeType: MIME[ext] ?? 'application/octet-stream',
    buffer: Buffer.concat([magic, Buffer.alloc(Math.max(0, bytes - magic.length), 0x20)]),
  };
}

const FILE_INPUT = 'input#fileInput';
const CHIP = '.file-chip';
const PREVIEW = '.preview-file';
const REMOVE = '.remove-file';
const FILE_NAME = '.file-name';
const UPLOAD_ERROR = '.error-msg';
const NEXT = 'button.btn-next';
const BACK = 'button.btn-go-back';

/** Attaches files on step 3 and waits for the chips, so every test starts from a known list. */
async function attachOnStep3(page: any, mobile: string, files: ReturnType<typeof attachment>[]) {
  await openComplaintDetails(page, mobile);
  await page.locator(FILE_INPUT).setInputFiles(files);
  await expect(page.locator(CHIP)).toHaveCount(files.length);
}

/**
 * Clicks a preview button and returns the tab it opened.
 *
 * The listener is registered BEFORE the click on purpose — `window.open` is synchronous and the popup
 * event fires immediately, so a listener attached afterwards misses it and the test times out rather
 * than failing with a useful message.
 *
 * Returns the popup without asserting anything about its URL: for a PDF in headless chromium there is
 * no URL to assert (see the header). The popup EXISTING is the product behaviour under test.
 */
async function openPreview(page: any, chip: any): Promise<any> {
  const popupPromise = page.waitForEvent('popup', { timeout: 15000 });
  await chip.locator(PREVIEW).click();
  return popupPromise;
}

/**
 * The blob URL a preview opened, for the tests that must compare one preview with another.
 *
 * Only meaningful for image fixtures — hence the guard, which turns the silent headless-PDF trap into a
 * loud failure if someone later points one of these tests at a `.pdf`.
 */
async function blobUrlOf(popup: any, expectedName: string): Promise<string> {
  const url = popup.url();
  expect(url, `${expectedName} must open a blob preview (use an image fixture, not a PDF — see header)`)
    .toMatch(/^blob:/);
  return url;
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Where the preview control actually is — and where both blocks say it is
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C52/57 — the preview control sits on the upload step, not on the review screen', () => {

  test('a preview control is offered for each attached document', async ({ page }) => {
    // MANUAL (both blocks): "Preview option should be present for uploaded documents". It is — on the
    // upload step. Two files so the per-document claim is tested rather than assumed from one.
    await attachOnStep3(page, nextMobile(), [
      attachment('statement.pdf', 'pdf'),
      attachment('receipt.jpg', 'jpg'),
    ]);

    await expect(page.locator(PREVIEW)).toHaveCount(2);
    await expect(page.locator(CHIP, { hasText: 'statement.pdf' }).locator(PREVIEW)).toBeVisible();
    await expect(page.locator(CHIP, { hasText: 'receipt.jpg' }).locator(PREVIEW)).toBeVisible();
  });

  test('the review screen lists the attached documents by name', async ({ page }) => {
    // The review screen does show WHAT is attached, which is worth pinning separately from the fact
    // that it cannot open any of it — the two are different requirements and only one is unmet.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    await page.locator(NEXT).first().click();                 // 3 → 4
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();                 // 4 → 5
    await expect(page.locator('input[name="declaration"]')).toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();                 // 5 → 6
    await expect(page.locator('.rs-section').first()).toBeVisible({ timeout: 15000 });

    await expect(page.locator('.rs-label', { hasText: 'Uploaded Documents' })).toBeVisible();
    await expect(page.locator('.rs-label', { hasText: 'statement.pdf' })).toBeVisible();
  });

  test('the review screen offers no way to open, download or remove an attachment', async ({ page }) => {
    // THE MEASUREMENT for D-C-32, asserted rather than described. Both blocks place the preview on this
    // screen; the screen has names and nothing else. Scoped to `.rs-section` so the assertion cannot be
    // satisfied by controls belonging to step 3, which is no longer rendered anyway.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    await page.locator(NEXT).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();
    await expect(page.locator('input[name="declaration"]')).toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();
    await expect(page.locator('.rs-section').first()).toBeVisible({ timeout: 15000 });

    const review = page.locator('.review-summary, .rs-section').first();
    await expect(review.locator(PREVIEW)).toHaveCount(0);
    await expect(review.locator(REMOVE)).toHaveCount(0);
    // The only download control on step 6 is the acknowledgement PDF, which is not per-document.
    await expect(page.locator('.rs-section .btn-download-pdf')).toHaveCount(0);
  });

  test.fixme('the uploaded documents can be previewed from the review screen', async ({ page }) => {
    // THE DEFECT MARKER for D-C-32. Required by the opening case of BOTH blocks 52 and 57.
    // The review screen is the last chance to check what is about to be submitted; a citizen who
    // attached the wrong bank statement cannot find out here.
    // Fix is a preview button on the `.rs-row-full` at html:1205, reusing `previewAttachment(i)`.
    // NOT applied — `src/**` is shared with Sessions A and B (ruling 6).
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    await page.locator(NEXT).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();
    await expect(page.locator('input[name="declaration"]')).toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();
    await expect(page.locator('.rs-section').first()).toBeVisible({ timeout: 15000 });

    await expect(page.locator('.rs-section').locator(PREVIEW).first()).toBeVisible();
  });

  test('no preview control exists when nothing has been attached', async ({ page }) => {
    // MANUAL (block 52): "If there are no documents uploaded then preview option should not be
    // present". `@if (attachmentPreviews.length > 0)` (html:884) satisfies this.
    await openComplaintDetails(page, nextMobile());

    await expect(page.locator(CHIP)).toHaveCount(0);
    await expect(page.locator(PREVIEW)).toHaveCount(0);
    await expect(page.locator('.file-list')).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// What pressing Preview actually does
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C52/57 — pressing preview opens the document in a new browser tab', () => {

  test('a preview opens the document in a new tab', async ({ page }) => {
    // MANUAL (both blocks): "The user should be able to preview the uploaded documents". They can; the
    // mechanism is `window.open` on an object URL (ts:2061-2063), which is why the observable is a
    // popup rather than any viewer element. A PDF is used because it is the commonest evidence format,
    // and the assertion is on the tab's existence — headless chromium cannot report a blob PDF's URL.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    const before = page.context().pages().length;
    const popup = await openPreview(page, page.locator(CHIP).first());

    expect(page.context().pages().length, 'a new tab must have been opened').toBe(before + 1);
    expect(await popup.opener(), 'the preview tab is opened by the complaint form').toBeTruthy();
    await popup.close();
  });

  test('the preview is a blob held by the browser, not a document fetched from a server', async ({ page }) => {
    // The mechanism assertion, on an IMAGE so the URL is readable in headless. A `blob:` scheme proves
    // the bytes never left the browser, which is the constraint behind every other finding here.
    await attachOnStep3(page, nextMobile(), [attachment('receipt.png', 'png')]);

    const popup = await openPreview(page, page.locator(CHIP).first());

    await blobUrlOf(popup, 'receipt.png');
    await popup.close();
  });

  test('previewing does not upload the document anywhere', async ({ page }) => {
    // Worth proving because it constrains every other finding in this block: the preview is entirely
    // client-side, so nothing about it can be fixed server-side, and no preview is possible at all for
    // a file whose bytes the browser no longer holds (see the resumed-draft tests below).
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    const calls: string[] = [];
    page.on('request', r => {
      if (r.method() !== 'GET' && /attach|upload|document|file/i.test(r.url())) calls.push(r.url());
    });

    const popup = await openPreview(page, page.locator(CHIP).first());
    await popup.close();

    expect(calls, 'preview must not, and does not, transmit the document').toEqual([]);
  });

  test('each of the three attachable types can be previewed', async ({ page }) => {
    // MANUAL names "PDF, JPG, PNG, DOCX". Three of the four can be attached, so three are driven here
    // and the fourth is the `fixme` below — narrowing the case to what works would hide D-C-07.
    await attachOnStep3(page, nextMobile(), [
      attachment('a.pdf', 'pdf'),
      attachment('b.jpg', 'jpg'),
      attachment('c.png', 'png'),
    ]);

    for (const name of ['a.pdf', 'b.jpg', 'c.png']) {
      const before = page.context().pages().length;
      const popup = await openPreview(page, page.locator(CHIP, { hasText: name }));
      expect(page.context().pages().length, `${name} must open a preview tab`).toBe(before + 1);
      await popup.close();
    }
  });

  test.fixme('a DOCX document can be attached and previewed, as both blocks require', async ({ page }) => {
    // THE DEFECT MARKER, sharing D-C-07 with block 45 rather than raising a new one. `.docx` is not in
    // the allow-list (`file-validator.ts:36`), so the manual's fourth supported type cannot be
    // attached and therefore cannot be previewed. Recorded here too because a reader of block 52 or 57
    // would otherwise see the preview tests pass and conclude the requirement is met.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(attachment('agreement.docx', 'docx'));

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator(CHIP).locator(PREVIEW)).toBeVisible();
  });

  test('the citizen can open several documents one after another', async ({ page }) => {
    // MANUAL (block 57): "The user should be able to open multiple documents one by one during
    // preview". Each chip owns its own button and its own blob, so the interesting failure would be one
    // URL being reused for every file — asserted by comparing the two blob URLs. Both fixtures are
    // images so both URLs are readable in headless.
    await attachOnStep3(page, nextMobile(), [
      attachment('first.jpg', 'jpg'),
      attachment('second.png', 'png'),
    ]);

    const p1 = await openPreview(page, page.locator(CHIP, { hasText: 'first.jpg' }));
    const url1 = await blobUrlOf(p1, 'first.jpg');
    await p1.close();

    const p2 = await openPreview(page, page.locator(CHIP, { hasText: 'second.png' }));
    const url2 = await blobUrlOf(p2, 'second.png');
    await p2.close();

    expect(url1, 'each document must have its own blob, or every preview shows the same file')
      .not.toBe(url2);
  });

  test('the preview is neither inline nor a modal, which is what both blocks describe', async ({ page }) => {
    // THE MEASUREMENT for D-C-33. Block 52 says "inline or modal pop up viewer", block 57 says "inline
    // viewer". It is a browser tab: no element is added to the page and no dialog appears. Asserted on
    // the page AFTER the popup opens, so a viewer that rendered late would still be caught.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    const popup = await openPreview(page, page.locator(CHIP).first());

    await expect(page.locator('[role="dialog"]')).toHaveCount(0);
    await expect(page.locator('.preview-overlay, .preview-modal, .document-viewer, iframe, embed, object'))
      .toHaveCount(0);
    // And the form itself is untouched — the citizen's page did not change at all.
    await expect(page.locator(FILE_INPUT)).toHaveCount(1);

    await popup.close();
  });

  test.fixme('the preview opens in an in-page viewer the product controls', async ({ page }) => {
    // THE DEFECT MARKER for D-C-33, carrying the manual's requirement per ruling 2.
    //
    // Not pedantry. An in-page viewer is the precondition for four other cases both blocks ask for and
    // the product cannot answer: a close control, zoom, a per-document download button, and screen
    // reader support for the preview. With `window.open` all four belong to the browser, and on a
    // popup-blocking browser the citizen gets no preview and no error either.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    await page.locator(CHIP).first().locator(PREVIEW).click();

    await expect(page.locator('.document-viewer, .preview-modal')).toBeVisible();
  });

  test.fixme('the preview offers a close control, and closing it returns the citizen to the form', async ({ page }) => {
    // THE DEFECT MARKER for the block-52 close group — three cases: the close option is present, the
    // user can close the preview, and after closing the form can still be edited.
    // Unreachable today: there is no preview screen, so there is nothing to close. Note that the THIRD
    // case is separately proven below (editing still works), so only the close control is missing.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    await page.locator(CHIP).first().locator(PREVIEW).click();

    const viewer = page.locator('.document-viewer, .preview-modal');
    await expect(viewer.locator('button.btn-close, .preview-close')).toBeVisible();
  });

  test('after previewing, the complaint form is still editable', async ({ page }) => {
    // MANUAL (block 52): "Post closing the preview screen, the user should be able to edit the
    // complaint form". The outcome the citizen needs holds — because the form was never covered in the
    // first place — so it passes on its own merits while the close control remains the `fixme` above.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    const popup = await openPreview(page, page.locator(CHIP).first());
    await popup.close();

    const facts = page.locator('textarea[name="complaintText"]');
    await facts.fill('Edited after previewing the attachment.');
    await expect(facts).toHaveValue('Edited after previewing the attachment.');

    await page.locator(NEXT).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
  });

  test.fixme('each uploaded document has its own download control', async ({ page }) => {
    // THE DEFECT MARKER for the block-57 download group — three cases: a download option per document,
    // the user can click it, and the downloaded file is available to preview.
    // OBSERVED: no download control exists for an attachment anywhere in the portal. The only
    // `btn-download-pdf` on this component is the acknowledgement (html:1277) and `nm.download` is the
    // closure letter (html:1317-1318) — neither is a citizen's own attachment. What block 57 describes
    // is the browser's PDF toolbar inside the tab `window.open` produced.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf')]);

    await expect(page.locator(CHIP).first().locator('.download-file')).toBeVisible();
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The unsupported-file case, and why it cannot be run as written
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C52/57 — an unpreviewable document never becomes an attachment in the first place', () => {

  test('an unsupported file is refused at upload, so it never gains a preview control', async ({ page }) => {
    // MANUAL (both blocks): "The user should not be able to preview the unsupported file types".
    // The requirement holds, but not for the reason the case gives: there is no preview-time type check
    // at all — `previewAttachment` opens whatever blob it is handed. The protection is one gate
    // earlier. Proven by establishing the case's own precondition and finding it cannot be established.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(attachment('ledger.xlsx', 'xlsx'));

    await expect(page.locator(CHIP)).toHaveCount(0);
    await expect(page.locator(PREVIEW)).toHaveCount(0);
    await expect(page.locator(UPLOAD_ERROR)).toBeVisible();
  });

  test('the message the citizen gets is the upload refusal, not the preview message block 52 specifies', async ({ page }) => {
    // THE MEASUREMENT for D-C-34. Manual: "Preview not available for this file type." Product: the
    // file-type refusal from the upload validator. The manual's string exists nowhere in the portal —
    // the sole "Preview not available" in the repository is on an officer screen
    // (`crpc/draft-assessment.component.html:231`), which is a different component entirely.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(attachment('ledger.xlsx', 'xlsx'));

    const error = page.locator(UPLOAD_ERROR);
    await expect(error).toBeVisible();
    await expect(error).toContainText(/not allowed/i);
    await expect(error, 'the manual\'s preview message does not exist in this product')
      .not.toContainText(/Preview not available/i);
  });

  test('every attachment that exists can be previewed, because preview applies no type check', async ({ page }) => {
    // The corollary, and the reason D-C-34 is "unreachable" rather than "broken": there is no state in
    // which a chip exists and its preview fails. Asserted across all three attachable types so a future
    // type added to the upload allow-list without a matching preview path would show up here.
    await attachOnStep3(page, nextMobile(), [
      attachment('x.pdf', 'pdf'),
      attachment('y.jpg', 'jpg'),
      attachment('z.png', 'png'),
    ]);

    await expect(page.locator(CHIP)).toHaveCount(3);
    await expect(page.locator(PREVIEW)).toHaveCount(3);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Labels, removal, and re-upload
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C52/57 — each attachment is labelled, removable and replaceable', () => {

  test('each document is labelled with its own file name', async ({ page }) => {
    // MANUAL (block 57): "Clear label should be present for each uploaded document". The label is the
    // file name the citizen chose, which is the only label that means anything to them.
    await attachOnStep3(page, nextMobile(), [
      attachment('bank-statement-march.pdf', 'pdf'),
      attachment('atm-receipt.jpg', 'jpg'),
    ]);

    const names = await page.locator(FILE_NAME).allTextContents();
    expect(names.map(n => n.trim())).toEqual(['bank-statement-march.pdf', 'atm-receipt.jpg']);
  });

  test('the preview and remove controls name the document they act on', async ({ page }) => {
    // Not in the manual, and the only way a screen-reader user can tell two icon-only buttons apart.
    // Also the only place the product states WHICH file a preview button belongs to — the blob URL it
    // opens carries no name, so this is the identity assertion the popup tests cannot make.
    await attachOnStep3(page, nextMobile(), [attachment('bank-statement-march.pdf', 'pdf')]);

    const chip = page.locator(CHIP).first();
    await expect(chip.locator(PREVIEW)).toHaveAttribute('aria-label', 'Preview bank-statement-march.pdf');
    await expect(chip.locator(REMOVE)).toHaveAttribute('aria-label', 'Remove bank-statement-march.pdf');
  });

  test('a remove control is offered for each attachment', async ({ page }) => {
    // MANUAL (block 52): "Remove option should be present for uploaded documents".
    await attachOnStep3(page, nextMobile(), [
      attachment('one.pdf', 'pdf'),
      attachment('two.pdf', 'pdf'),
    ]);

    await expect(page.locator(REMOVE)).toHaveCount(2);
  });

  test('removing a document removes that document and leaves the others', async ({ page }) => {
    // MANUAL (block 52) asks three times over: the option can be clicked, and the document is removed.
    // Three files, middle one removed, because `removeAttachment` (ts:2051-2059) splices two parallel
    // arrays by index — an off-by-one would drop the wrong file, which removing the last one hides.
    await attachOnStep3(page, nextMobile(), [
      attachment('keep-a.pdf', 'pdf'),
      attachment('drop-me.jpg', 'jpg'),
      attachment('keep-b.png', 'png'),
    ]);

    await page.locator(CHIP, { hasText: 'drop-me.jpg' }).locator(REMOVE).click();

    await expect(page.locator(CHIP)).toHaveCount(2);
    const names = (await page.locator(FILE_NAME).allTextContents()).map(n => n.trim());
    expect(names).toEqual(['keep-a.pdf', 'keep-b.png']);
  });

  test('the surviving documents still preview correctly after one is removed', async ({ page }) => {
    // The consequence of that parallel-array splice that a name check cannot catch: if `attachments`
    // and `attachmentPreviews` fell out of step, the chip would be labelled with one file and open the
    // bytes of another. Distinct blob URLs after a removal is the observable form of that. All three
    // fixtures are images so the surviving two have readable URLs in headless.
    await attachOnStep3(page, nextMobile(), [
      attachment('keep-a.jpg', 'jpg'),
      attachment('drop-me.jpg', 'jpg'),
      attachment('keep-b.png', 'png'),
    ]);
    await page.locator(CHIP, { hasText: 'drop-me.jpg' }).locator(REMOVE).click();
    await expect(page.locator(CHIP)).toHaveCount(2);

    const pA = await openPreview(page, page.locator(CHIP, { hasText: 'keep-a.jpg' }));
    const urlA = await blobUrlOf(pA, 'keep-a.jpg');
    await pA.close();
    const pB = await openPreview(page, page.locator(CHIP, { hasText: 'keep-b.png' }));
    const urlB = await blobUrlOf(pB, 'keep-b.png');
    await pB.close();

    expect(urlA, 'the two surviving chips must not share a blob').not.toBe(urlB);
  });

  test('a removed document can be replaced with the correct one', async ({ page }) => {
    // MANUAL (block 52): "The user should be able to re upload the documents if it is incorrect" — the
    // whole point of the remove control. The replacement must be previewable too, or the citizen has
    // swapped a wrong document for an unverifiable one.
    await attachOnStep3(page, nextMobile(), [attachment('wrong-statement.pdf', 'pdf')]);

    await page.locator(CHIP).first().locator(REMOVE).click();
    await expect(page.locator(CHIP)).toHaveCount(0);

    await page.locator(FILE_INPUT).setInputFiles(attachment('right-statement.pdf', 'pdf'));

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator(FILE_NAME)).toContainText('right-statement.pdf');
    await expect(page.locator(CHIP).locator(PREVIEW)).toBeVisible();
  });

  test('a document with the same name can be attached again after removal', async ({ page }) => {
    // Not in the manual. The realistic re-upload: the citizen fixes the FILE, not its name. If removal
    // left anything keyed by name behind, this is where it would show.
    await attachOnStep3(page, nextMobile(), [attachment('statement.pdf', 'pdf', 2048)]);

    await page.locator(CHIP).first().locator(REMOVE).click();
    await expect(page.locator(CHIP)).toHaveCount(0);
    await page.locator(FILE_INPUT).setInputFiles(attachment('statement.pdf', 'pdf', 4096));

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator(FILE_NAME)).toContainText('statement.pdf');
    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The state neither block tests: a citizen who saved a draft and came back
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C52/57 — a resumed draft names its attachments but cannot preview them', () => {

  /** A resumed draft, seeded the way the component itself rebuilds one: metadata with no bytes. */
  async function openResumedWithAttachments(page: any): Promise<void> {
    await openWizardAtStep(page, nextMobile(), {
      step: 3,
      attachmentMeta: [
        { name: 'saved-statement.pdf', type: 'application/pdf', size: 20480 },
        { name: 'saved-receipt.jpg', type: 'image/jpeg', size: 10240 },
      ],
    });
    await expect(page.locator(CHIP)).toHaveCount(2, { timeout: 15000 });
  }

  test('the attachments saved in a draft are listed again on resume', async ({ page }) => {
    await openResumedWithAttachments(page);

    const names = (await page.locator(FILE_NAME).allTextContents()).map(n => n.trim());
    expect(names).toEqual(['saved-statement.pdf', 'saved-receipt.jpg']);
  });

  test('no preview is offered for a resumed attachment, because its bytes are gone', async ({ page }) => {
    // Correct behaviour, pinned because it is the state a returning citizen is actually in and neither
    // block tests it. A draft persists `attachmentMeta` only (ts:1879) and resume rebuilds the previews
    // with `url: ''` (ts:1965); the template gates the preview button on `f.url` (html:890-895). A
    // preview button here would open `blob:` on an empty string.
    await openResumedWithAttachments(page);

    await expect(page.locator(PREVIEW)).toHaveCount(0);
  });

  test('a resumed attachment is marked as needing re-upload rather than looking intact', async ({ page }) => {
    // The saving grace of the above: the citizen is TOLD, rather than being left to discover at submit
    // that the file they can see by name was never going to be sent.
    await openResumedWithAttachments(page);

    await expect(page.locator('.file-chip.needs-reupload')).toHaveCount(2);
    await expect(page.locator('.reupload-hint').first()).toContainText('re-upload needed');
  });

  test('re-attaching a file after resume restores its preview', async ({ page }) => {
    // The recovery path. The re-uploaded file gains a blob and therefore a preview; the still-missing
    // one keeps its warning. Proves the two states coexist per-file rather than per-screen.
    await openResumedWithAttachments(page);

    await page.locator(FILE_INPUT).setInputFiles(attachment('saved-receipt.jpg', 'jpg'));

    await expect(page.locator(CHIP)).toHaveCount(3);
    await expect(page.locator(PREVIEW)).toHaveCount(1);
    await expect(page.locator('.file-chip.needs-reupload')).toHaveCount(2);

    const popup = await openPreview(page, page.locator(CHIP).last());
    await blobUrlOf(popup, 'saved-receipt.jpg');
    await popup.close();
  });

  test('a resumed attachment can still be removed', async ({ page }) => {
    // `removeAttachment` splices `attachments` only `if (this.attachments[index])` (ts:2055-2057) — on
    // a resumed draft that array is EMPTY while `attachmentPreviews` has two entries, so the guard is
    // load-bearing. Without it the chip would remain, or the wrong live file would be dropped.
    await openResumedWithAttachments(page);

    await page.locator(CHIP, { hasText: 'saved-receipt.jpg' }).locator(REMOVE).click();

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator(FILE_NAME)).toContainText('saved-statement.pdf');
  });

  test('going back and forward does not resurrect the bytes of a resumed attachment', async ({ page }) => {
    // A preview button appearing after navigation would mean a blob was invented for a file the browser
    // does not have, which would download as an empty document and look like corruption.
    await openResumedWithAttachments(page);

    await page.locator(BACK).first().click();
    await expect(page.locator('#complaintCategory, select[name="complaintCategory"]').first())
      .toBeVisible({ timeout: 15000 });
    await page.locator(NEXT).first().click();

    await expect(page.locator(CHIP)).toHaveCount(2, { timeout: 15000 });
    await expect(page.locator(PREVIEW)).toHaveCount(0);
  });
});
