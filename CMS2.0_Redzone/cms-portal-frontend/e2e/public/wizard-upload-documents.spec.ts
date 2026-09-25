/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 * QA pass-4 Session C — manual block 45 ("Upload any additional documents", Complaint Details).
 * Manual source: prompt_testcase_cms_frontemd.txt lines 3347-3437.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *
 * ═══ NOT ONE BYTE FIGURE APPEARS IN THIS FILE, BY RULE ═══
 *
 * Standing ruling 1, and it is enforced mechanically here: `ui-homogenisation/upload-limits.spec.ts`
 * scans source for `\d+\s*\*\s*1024\s*\*\s*1024`, for the known byte literals, and for the label
 * pattern `\d+\s?MB` — and fails any file but `upload-limits.service.ts` that matches. So a spec
 * asserting "2 MB is refused" would (a) break that guard and (b) assert the stale side of the very
 * rule it breaks, the day the figure is retuned.
 *
 * Instead `fetchUploadLimits()` reads GET /api/v1/config/upload-limits and EVERY fixture size and
 * boundary is arithmetic on the answer:
 *     at limit        = maxFileSizeBytes
 *     just under      = maxFileSizeBytes - 1
 *     just over       = maxFileSizeBytes + 1
 *     the manual's "1.9 MB" and "2.1 MB" become 95% and 105% of the configured limit
 * The manual's 2 MB is therefore treated as a SNAPSHOT of configuration, not as a requirement.
 * Recorded in findings-QA-C.md §1 row 1.
 *
 * ═══ THE HIGHEST CITIZEN-IMPACT FINDING IN THIS SESSION: .docx IS REFUSED ═══
 *
 * The manual (line 3402-3411) names the supported formats as "JPG, PDF, DOCX" — three times, in
 * three cases. The product allows `['.pdf','.doc','.jpg','.jpeg','.png']` (file-validator.ts:36),
 * mirrored in the `accept` attribute (html:865) and the on-screen hint "Support formats: PDF, DOC,
 * JPG, PNG" (html:869).
 *
 * So `.docx` — the DEFAULT Word format since Office 2007 — is refused, while `.doc` and `.png`,
 * which the manual never mentions, are accepted. A citizen attaching a modern Word document is
 * turned away. This is a straight requirements-vs-code contradiction with real citizen cost, and it
 * is written as a `fixme` asserting the MANUAL's requirement per ruling 2, with a passing companion
 * that records today's refusal. See D-C-07.
 *
 * ═══ AND THE ERROR MESSAGES ARE NOT THE ONES SPECIFIED ═══
 *
 * Manual: "Unsupported file type" and "File size exceeds limit".
 * Product: `File type ".xyz" is not allowed. Allowed: PDF, DOC, JPG, PNG` and
 *          `File size (N.NMB) exceeds the {configured}MB limit.` (file-validator.ts:45,55).
 * The specs assert the REAL strings — by shape, never by figure — because the manual's are
 * paraphrases rather than contracts, and a spec pinning a paraphrase fails for the wrong reason.
 *
 * ═══ WHY MIME TYPE, NOT JUST THE EXTENSION, DECIDES ═══
 * `validateFile` checks the extension FIRST, then `file.type` against an allow-list
 * (file-validator.ts:48-51). Playwright derives `file.type` from the `mimeType` argument to
 * `setInputFiles`, NOT from the file's content. A fixture named `x.pdf` handed over with a wrong
 * mimeType is refused with the MIME message, not the extension one — which looks like a product bug
 * and is not. Every fixture below therefore carries its correct mimeType, and one test drives the
 * mismatch deliberately so the second gate is covered rather than tripped over.
 *
 * ═══ WHAT CANNOT BE PROVEN HERE ═══
 * That an accepted file is PERSISTED. The complaint form holds attachments in `component.attachments`
 * and uploads them on submit; block 45 covers only the field, and a full submit-and-verify lives in
 * the review/submission specs. This file therefore proves acceptance (the chip appears) and refusal
 * (the chip does not), which is exactly the scope of the manual block — and the repo has a documented
 * hazard of client-held uploads that never reach a server, so that gap is named, not assumed away.
 */

import { test, expect } from '../fixtures';
import { openComplaintDetails, sessionCMobile, fetchUploadLimits, type UploadLimits } from './helpers-submission-c';
import { deleteOtpAttempts } from '../utils/test-data';

const mobiles: string[] = [];
function nextMobile(): string {
  const m = sessionCMobile();
  mobiles.push(m);
  return m;
}

/**
 * Real container magic bytes.
 *
 * The client validator only reads the extension and `file.type`, so any bytes would pass it — but the
 * SERVER sniffs the container (the pattern is documented in withdrawal-attachments.spec.ts), and a
 * fixture that is a valid PDF by name and gibberish by content would pass here and fail the moment
 * anything downstream looks. Cheap to do correctly.
 */
const MAGIC: Record<string, Buffer> = {
  pdf: Buffer.from([0x25, 0x50, 0x44, 0x46, 0x2d, 0x31, 0x2e, 0x34, 0x0a]),
  doc: Buffer.from([0xd0, 0xcf, 0x11, 0xe0, 0xa1, 0xb1, 0x1a, 0xe1]),
  jpg: Buffer.from([0xff, 0xd8, 0xff, 0xe0]),
  png: Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  docx: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
  xlsx: Buffer.from([0x50, 0x4b, 0x03, 0x04]),
  exe: Buffer.from([0x4d, 0x5a]),
  txt: Buffer.from([0x68, 0x65, 0x6c, 0x6c, 0x6f]),
};

/** The MIME types the browser would really report, so `file.type` clears the validator's second gate. */
const MIME: Record<string, string> = {
  pdf: 'application/pdf',
  doc: 'application/msword',
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
  xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
  exe: 'application/x-msdownload',
  txt: 'text/plain',
};

/**
 * Bytes of an EXACT length, built at run time from the configured limit.
 *
 * Committing an over-limit binary fixture would be both a large file in git and a hardcoded limit in
 * disguise — it stops being over-limit the day the configuration rises. Generating means the boundary
 * is always the boundary actually in force. Files go through `setInputFiles` as buffers rather than
 * paths so nothing is left on disk between runs.
 */
function bufferOf(ext: string, totalBytes: number): Buffer {
  const magic = MAGIC[ext] ?? MAGIC['txt'];
  return Buffer.concat([magic, Buffer.alloc(Math.max(0, totalBytes - magic.length), 0x20)]);
}

/** The payload `setInputFiles` needs, with the mimeType the validator's second gate reads. */
function upload(name: string, ext: string, totalBytes: number) {
  return { name, mimeType: MIME[ext] ?? 'application/octet-stream', buffer: bufferOf(ext, totalBytes) };
}

let limits: UploadLimits;

test.beforeAll(async ({ request }) => {
  // If this throws, the run stops with a clear reason rather than every boundary test silently
  // failing against a guessed figure.
  limits = await fetchUploadLimits(request);
});

test.afterAll(async () => {
  for (const m of mobiles) {
    try { await deleteOtpAttempts(m); } catch { /* best effort */ }
  }
});

const FILE_INPUT = 'input#fileInput';
const CHIP = '.file-chip';
const UPLOAD_ERROR = '.error-msg';

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The field itself, and the two ways the manual says a citizen can use it
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C45 — the additional-documents field is present and accepts a valid document both ways', () => {

  test('the upload field is present on the complaint details screen', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    await expect(page.locator('.file-upload-area')).toBeVisible();
    // The input itself is deliberately hidden behind a styled label, so presence not visibility.
    await expect(page.locator(FILE_INPUT)).toHaveCount(1);
  });

  test('a valid document chosen through Browse is accepted and listed', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('evidence.pdf', 'pdf', 4096));

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator('.file-name')).toContainText('evidence.pdf');
    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
  });

  test('a valid document arriving by drag-and-drop is accepted and listed', async ({ page }) => {
    // `onFileDrop` (ts:2012-2036) is a SECOND, hand-duplicated copy of the same validation as
    // `onFilesSelected`. Two copies of a rule is exactly where the two drift apart, so the drop path
    // is driven for real rather than assumed equivalent to Browse.
    await openComplaintDetails(page, nextMobile());

    await dropFile(page, 'dropped-evidence.pdf', 'pdf', 4096);

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator('.file-name')).toContainText('dropped-evidence.pdf');
  });

  test('the field may be left blank and the step still advances', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    await expect(page.locator(CHIP)).toHaveCount(0);
    await page.locator('button', { hasText: 'Next' }).first().click();

    // Attachments are optional: step 4 (representative authorisation) must be reached.
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
  });

  test('an accepted document can be removed again', async ({ page }) => {
    // Not in the manual. `removeAttachment` (ts:2038-2047) splices two parallel arrays by index, so a
    // mismatch between them would leave a file in the payload that the citizen believes is gone.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles([
      upload('keep.pdf', 'pdf', 2048),
      upload('discard.jpg', 'jpg', 2048),
    ]);
    await expect(page.locator(CHIP)).toHaveCount(2);

    await page.locator(CHIP, { hasText: 'discard.jpg' }).locator('.remove-file').click();

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator('.file-name')).toContainText('keep.pdf');
  });
});

/**
 * Drives the real `(drop)` handler with a synthetic DataTransfer.
 *
 * Playwright has no drop primitive for files, so the DataTransfer is built inside the page and
 * dispatched at `.file-upload-area`. The bytes cross as an array of numbers because a Buffer does not
 * survive `evaluate` serialisation.
 */
async function dropFile(page: any, name: string, ext: string, totalBytes: number): Promise<void> {
  const bytes = Array.from(bufferOf(ext, totalBytes));
  await page.evaluate(
    ({ name, type, bytes }: { name: string; type: string; bytes: number[] }) => {
      const dt = new DataTransfer();
      dt.items.add(new File([new Uint8Array(bytes)], name, { type }));
      document.querySelector('.file-upload-area')!
        .dispatchEvent(new DragEvent('drop', { dataTransfer: dt, bubbles: true }));
    },
    { name, type: MIME[ext] ?? 'application/octet-stream', bytes },
  );
}

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Formats — where the manual and the product disagree outright
// ═══════════════════════════════════════════════════════════════════════════════════════════════

/** Extensions the PRODUCT allows (file-validator.ts:36). Table-driven: one bug would hit all of them. */
const ALLOWED = ['pdf', 'doc', 'jpg', 'jpeg', 'png'];

/** Extensions that must be refused. `.docx` is absent DELIBERATELY — it is the D-C-07 contradiction. */
const REFUSED = ['exe', 'xlsx', 'txt'];

test.describe('QA-C45 — the supported formats are the ones the running validator allows', () => {

  for (const ext of ALLOWED) {
    test(`a .${ext} document is accepted`, async ({ page }) => {
      await openComplaintDetails(page, nextMobile());

      await page.locator(FILE_INPUT).setInputFiles(upload(`evidence.${ext}`, ext, 4096));

      await expect(page.locator(CHIP), `.${ext} is in ALLOWED_EXTENSIONS and must be accepted`)
        .toHaveCount(1);
      await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
    });
  }

  for (const ext of REFUSED) {
    test(`a .${ext} file is refused and named in the error`, async ({ page }) => {
      await openComplaintDetails(page, nextMobile());

      await page.locator(FILE_INPUT).setInputFiles(upload(`malicious.${ext}`, ext, 2048));

      const err = page.locator(UPLOAD_ERROR);
      await expect(err).toBeVisible();
      // The real message names the offending extension and lists what IS allowed — materially more
      // useful than the manual's "Unsupported file type", and the reason the manual's string is not
      // asserted here. See findings-QA-C.md §1 row 14.
      await expect(err).toContainText(`File type ".${ext}" is not allowed`);
      await expect(err).toContainText('Allowed: PDF, DOC, JPG, PNG');

      await expect(page.locator(CHIP), 'a refused file must not be listed as attached')
        .toHaveCount(0);
    });
  }

  test('the on-screen hint names the formats the validator actually enforces', async ({ page }) => {
    // The hint and the allow-list are separate literals (html:869 vs file-validator.ts:36); if they
    // drift, the citizen is told one thing and refused for another.
    await openComplaintDetails(page, nextMobile());

    await expect(page.locator('.upload-hint')).toContainText('PDF, DOC, JPG, PNG');
  });

  test('the accept attribute matches the validator, so the file picker does not offer the refused', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    await expect(page.locator(FILE_INPUT)).toHaveAttribute('accept', '.pdf,.doc,.jpg,.jpeg,.png');
  });

  test.fixme('a .docx document is accepted, as the manual requires', async ({ page }) => {
    // MANUAL SAYS (lines 3402-3411, three separate cases): supported formats are JPG, PDF, DOCX.
    // OBSERVED: `.docx` is NOT in ALLOWED_EXTENSIONS (file-validator.ts:36) nor in the accept
    // attribute (html:865), and is refused with `File type ".docx" is not allowed`.
    //
    // This is the highest citizen-impact contradiction in this session: .docx has been Word's default
    // since Office 2007, so the typical citizen attaching a written complaint is turned away, while
    // .doc and .png — which the manual never mentions — are accepted.
    // Asserting the MANUAL's requirement per ruling 2. See findings-QA-C.md D-C-07.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('complaint-letter.docx', 'docx', 4096));

    await expect(page.locator(CHIP)).toHaveCount(1);
  });

  test('a .docx document is currently refused, which is the defect made concrete', async ({ page }) => {
    // The passing companion to the fixme above. Delete both together when .docx is allowed.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('complaint-letter.docx', 'docx', 4096));

    await expect(page.locator(UPLOAD_ERROR)).toContainText('File type ".docx" is not allowed');
    await expect(page.locator(CHIP)).toHaveCount(0);
  });

  test('a file whose MIME type contradicts its extension is refused on the MIME check', async ({ page }) => {
    // The validator's SECOND gate (file-validator.ts:48-51), which the manual never covers. It is the
    // one that stops an executable renamed to `.pdf`, so it matters — and it is also the gate that
    // makes a carelessly-built fixture fail for the wrong reason, which is worth pinning down once.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles({
      name: 'disguised.pdf', mimeType: 'application/x-msdownload', buffer: bufferOf('exe', 2048),
    });

    await expect(page.locator(UPLOAD_ERROR)).toContainText('is not permitted');
    await expect(page.locator(CHIP)).toHaveCount(0);
  });

  test('a zero-byte file with a supported extension is accepted, which the manual says it should not be', async ({ page }) => {
    // MANUAL SAYS (line 3416): "does not accept blank file".
    // OBSERVED: `validateFile` has no minimum-size check, so an empty .pdf passes every gate — the
    // extension is allowed, the MIME is allowed, and 0 bytes is under any limit.
    //
    // Asserted as OBSERVED rather than fixme'd, because "blank file" is ambiguous in the manual: a
    // 0-byte file, or a valid PDF with no content? The first is checkable and unchecked; the second
    // needs content inspection nobody has specified. Recorded as D-C-14 for the BA to disambiguate.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles({
      name: 'empty.pdf', mimeType: MIME['pdf'], buffer: Buffer.alloc(0),
    });

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Size — every figure computed from the served configuration
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C45 — the per-file size boundary is exactly the configured limit', () => {

  test('a file at exactly the configured per-file limit is accepted', async ({ page }) => {
    // The classic off-by-one: `sizeMB > limits.maxFileSizeMb` (file-validator.ts:54) is strictly
    // greater, so the limit itself must pass.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('at-limit.pdf', 'pdf', limits.maxFileSizeBytes));

    await expect(page.locator(CHIP), 'a file AT the limit must be accepted').toHaveCount(1);
    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
  });

  test('a file one byte under the configured limit is accepted', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('under.pdf', 'pdf', limits.maxFileSizeBytes - 1));

    await expect(page.locator(CHIP)).toHaveCount(1);
  });

  test('a file over the configured limit is refused, and the message states the limit in force', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    // Comfortably over, so the message's one-decimal rounding cannot read as equal to the limit.
    const over = Math.ceil(limits.maxFileSizeBytes * 1.5);
    await page.locator(FILE_INPUT).setInputFiles(upload('over.pdf', 'pdf', over));

    const err = page.locator(UPLOAD_ERROR);
    await expect(err).toBeVisible();
    // Shape, not figure: the message must state a size and a limit, both in MB. Which numbers appear
    // is configuration's business — asserting them is what ruling 1 forbids.
    await expect(err).toContainText(/File size \([\d.]+MB\) exceeds the [\d.]+MB limit\./);
    // And the limit it names must be the one the server served.
    await expect(err).toContainText(`the ${limits.maxFileSizeMb}MB limit`);

    await expect(page.locator(CHIP), 'an oversize file must not be listed').toHaveCount(0);
  });

  test('the manual\'s "just under" case passes, expressed as a fraction of the configured limit', async ({ page }) => {
    // MANUAL (line 3429): "accepts a file with 1.9 MB". Recast as 95% of whatever the limit is, so the
    // case keeps its meaning — "just inside the boundary" — after a retune.
    await openComplaintDetails(page, nextMobile());

    const justUnder = Math.floor(limits.maxFileSizeBytes * 0.95);
    await page.locator(FILE_INPUT).setInputFiles(upload('just-under.pdf', 'pdf', justUnder));

    await expect(page.locator(CHIP)).toHaveCount(1);
  });

  test('the manual\'s "just over" case is refused, expressed as a fraction of the configured limit', async ({ page }) => {
    // MANUAL (line 3433): "2.1 MB … error 'File size exceeds limit' should be displayed". The manual's
    // own expected-result text says the field "should accept" it and then demands the error — a
    // self-contradiction in the case. The error is plainly the intent. Logged as D-C-15.
    await openComplaintDetails(page, nextMobile());

    const justOver = Math.ceil(limits.maxFileSizeBytes * 1.05);
    await page.locator(FILE_INPUT).setInputFiles(upload('just-over.pdf', 'pdf', justOver));

    await expect(page.locator(UPLOAD_ERROR)).toBeVisible();
    await expect(page.locator(CHIP)).toHaveCount(0);
  });

  test('multiple valid documents are accepted together', async ({ page }) => {
    // MANUAL (line 3425): "accepts multiple files". Each is sized well inside the per-file limit so
    // this case tests multiplicity and not the total-size gate, which has its own test below.
    await openComplaintDetails(page, nextMobile());

    const each = Math.floor(limits.maxFileSizeBytes / 4);
    await page.locator(FILE_INPUT).setInputFiles([
      upload('doc-one.pdf', 'pdf', each),
      upload('doc-two.jpg', 'jpg', each),
      upload('doc-three.png', 'png', each),
    ]);

    await expect(page.locator(CHIP)).toHaveCount(3);
    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
  });

  test('one bad file in a batch discards the whole batch, including the valid documents', async ({ page }) => {
    // Not in the manual, and the most consequential thing this file found.
    //
    // `onFilesSelected` calls `validateFileSet` FIRST (ts:1990) and returns on failure. That function
    // loops the batch and `return`s on the first invalid member (file-validator.ts:76-77) — so ONE
    // oversize file discards every other file chosen with it. The per-file loop below it, which
    // `continue`s past a bad file and looks like it preserves the good ones (ts:1998-2008), is
    // unreachable for validity failures: it is dead code.
    //
    // A citizen who selects four documents and has one over the limit loses all four, and the single
    // error names only the size problem — nothing says the other three were dropped. Asserted as
    // OBSERVED because "reject the batch" vs "keep the valid ones" is a UX decision, not a
    // self-evident bug; but the dead `continue` shows the intent was to keep them. See D-C-16.
    await openComplaintDetails(page, nextMobile());

    const small = Math.floor(limits.maxFileSizeBytes / 4);
    await page.locator(FILE_INPUT).setInputFiles([
      upload('good-one.pdf', 'pdf', small),
      upload('too-big.pdf', 'pdf', Math.ceil(limits.maxFileSizeBytes * 1.5)),
      upload('good-two.jpg', 'jpg', small),
    ]);

    await expect(page.locator(UPLOAD_ERROR)).toBeVisible();
    await expect(page.locator(CHIP), 'the two valid documents are discarded along with the oversize one')
      .toHaveCount(0);
  });

  test('each valid file in a wholly-valid batch is kept, so the batch gate is the only thing dropping them', async ({ page }) => {
    // The control for the test above. Without it, "0 chips" could be read as the upload field being
    // broken for multi-select generally, which would send someone to the wrong place.
    await openComplaintDetails(page, nextMobile());

    const small = Math.floor(limits.maxFileSizeBytes / 4);
    await page.locator(FILE_INPUT).setInputFiles([
      upload('good-one.pdf', 'pdf', small),
      upload('good-two.jpg', 'jpg', small),
    ]);

    await expect(page.locator(CHIP)).toHaveCount(2);
  });

  test.fixme('the valid documents in a batch survive one bad file being rejected', async ({ page }) => {
    // THE DEFECT MARKER for D-C-16. The required behaviour, which the dead `continue` at ts:1998-2008
    // shows was intended: reject the offending file, name it, and keep the rest.
    // Fix is to move the per-file validity check ahead of the batch gate, or to have `validateFileSet`
    // report per-file rather than returning on the first failure. NOT applied — `src/**` is shared
    // with Sessions A and B tonight (ruling 6).
    await openComplaintDetails(page, nextMobile());

    const small = Math.floor(limits.maxFileSizeBytes / 4);
    await page.locator(FILE_INPUT).setInputFiles([
      upload('good-one.pdf', 'pdf', small),
      upload('too-big.pdf', 'pdf', Math.ceil(limits.maxFileSizeBytes * 1.5)),
      upload('good-two.jpg', 'jpg', small),
    ]);

    await expect(page.locator(CHIP)).toHaveCount(2);
    await expect(page.locator('.file-list')).not.toContainText('too-big.pdf');
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// The two limits the manual never mentions — and the one the citizen is never told
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C45 — the file-count and total-size limits are enforced but under-advertised', () => {

  test('more files than the configured count are refused, in one batch', async ({ page }) => {
    // `validateFileSet` (file-validator.ts:70-72) rejects the WHOLE batch on count, unlike the
    // per-file loop. Not in the manual at all.
    await openComplaintDetails(page, nextMobile());

    const tiny = 512;
    const tooMany = Array.from({ length: limits.maxFileCount + 1 },
      (_, i) => upload(`bulk-${i}.pdf`, 'pdf', tiny));
    await page.locator(FILE_INPUT).setInputFiles(tooMany);

    const err = page.locator(UPLOAD_ERROR);
    await expect(err).toBeVisible();
    await expect(err).toContainText(`Maximum ${limits.maxFileCount} files allowed`);
    await expect(page.locator(CHIP), 'the whole batch is refused on the count gate').toHaveCount(0);
  });

  test('exactly the configured number of files is accepted', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    const atCount = Array.from({ length: limits.maxFileCount },
      (_, i) => upload(`batch-${i}.pdf`, 'pdf', 512));
    await page.locator(FILE_INPUT).setInputFiles(atCount);

    await expect(page.locator(CHIP)).toHaveCount(limits.maxFileCount);
    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
  });

  test('the count limit counts files already attached, not just the new batch', async ({ page }) => {
    // `validateFileSet(newFiles, this.attachments.length, ...)` — the existing count is passed in, so
    // the limit cannot be evaded by uploading in several smaller batches.
    await openComplaintDetails(page, nextMobile());

    const first = Array.from({ length: limits.maxFileCount },
      (_, i) => upload(`first-${i}.pdf`, 'pdf', 512));
    await page.locator(FILE_INPUT).setInputFiles(first);
    await expect(page.locator(CHIP)).toHaveCount(limits.maxFileCount);

    // One more, in a fresh batch that is itself well within the limit.
    await page.locator(FILE_INPUT).setInputFiles(upload('one-too-many.pdf', 'pdf', 512));

    await expect(page.locator(UPLOAD_ERROR)).toContainText(`Maximum ${limits.maxFileCount} files allowed`);
    await expect(page.locator(CHIP)).toHaveCount(limits.maxFileCount);
  });

  test('the total-size limit is the one figure the screen actually tells the citizen', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    // Read from the server, so this asserts agreement between screen and configuration without this
    // spec naming a size itself.
    await expect(page.locator('.upload-size'))
      .toContainText(`Maximum Size of All Files: ${limits.maxTotalSizeMb}MB`);
  });

  test('the per-file limit is never shown to the citizen before a file is refused', async ({ page }) => {
    // D-C-02, asserted rather than merely reported. The screen advertises only the TOTAL, so a citizen
    // reasonably concludes one large file within that total is fine, chooses it, and is refused after
    // the fact. The file-count limit is likewise unannounced.
    await openComplaintDetails(page, nextMobile());

    const meta = page.locator('.file-upload-meta');
    await expect(meta).toContainText(`${limits.maxTotalSizeMb}MB`);
    // The per-file figure and the count are absent from the pre-upload guidance.
    await expect(meta).not.toContainText(`${limits.maxFileSizeMb}MB`);
    await expect(meta).not.toContainText(String(limits.maxFileCount));
  });

  test.fixme('the per-file size limit is stated on screen before the citizen chooses a file', async ({ page }) => {
    // THE DEFECT MARKER for D-C-02. The required behaviour: the guidance beside the upload control
    // names the per-file limit as well as the total, so a refusal is never a surprise.
    // Fix is one span in html:868-871 — not applied, `src/**` is shared tonight (ruling 6).
    await openComplaintDetails(page, nextMobile());

    await expect(page.locator('.file-upload-meta')).toContainText(`${limits.maxFileSizeMb}MB`);
  });

  test('the limits the browser enforces are the limits the server serves', async ({ request }) => {
    // The whole basis of ruling 1: `UploadLimitsService` fetches these at runtime, so a browser that
    // enforced a compiled-in figure would refuse attachments the API would accept. This asserts the
    // served payload is complete and self-consistent, naming no figure.
    const served = await fetchUploadLimits(request);

    expect(served.maxFileSizeBytes).toBeGreaterThan(0);
    expect(served.maxTotalSizeBytes).toBeGreaterThan(served.maxFileSizeBytes);
    expect(served.maxFileCount).toBeGreaterThan(1);
    // The MB figures are the byte figures, and the UI prints the MB ones.
    expect(served.maxFileSizeMb).toBe(Math.round(served.maxFileSizeBytes / (1024 * 1024)));
    expect(served.maxTotalSizeMb).toBe(Math.round(served.maxTotalSizeBytes / (1024 * 1024)));
  });
});

// ═══════════════════════════════════════════════════════════════════════════════════════════════
// Surviving the step, which is what makes an attachment worth anything
// ═══════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-C45 — an attached document survives navigation away from the step', () => {

  test('an attachment is still listed after leaving and returning to complaint details', async ({ page }) => {
    // Not in the manual. `attachmentPreviews` is component state, not formData, and the draft stores
    // only `attachmentMeta` — so a round trip is where a file quietly becomes "(re-upload needed)".
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('persistent.pdf', 'pdf', 4096));
    await expect(page.locator(CHIP)).toHaveCount(1);

    await page.locator('button', { hasText: 'Next' }).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });

    await page.locator('button.btn-go-back').first().click();

    await expect(page.locator(CHIP)).toHaveCount(1);
    await expect(page.locator('.file-name')).toContainText('persistent.pdf');
    // And it must be a real, previewable file rather than a placeholder needing re-upload.
    await expect(page.locator('.reupload-hint')).toHaveCount(0);
  });

  test('a rejected file leaves no trace that could reach the payload', async ({ page }) => {
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('rejected.exe', 'exe', 2048));
    await expect(page.locator(UPLOAD_ERROR)).toBeVisible();

    await page.locator('button', { hasText: 'Next' }).first().click();
    await expect(page.locator('#auth-rep-label')).toBeVisible({ timeout: 15000 });
    await page.locator('button.btn-go-back').first().click();

    await expect(page.locator(CHIP)).toHaveCount(0);
  });

  test('the error clears when a valid file follows a rejected one', async ({ page }) => {
    // `fileUploadError` is reset at the top of each handler (ts:1987, 2016). If it were sticky, a
    // citizen who corrected their mistake would still be looking at the old refusal.
    await openComplaintDetails(page, nextMobile());

    await page.locator(FILE_INPUT).setInputFiles(upload('bad.xlsx', 'xlsx', 2048));
    await expect(page.locator(UPLOAD_ERROR)).toBeVisible();

    await page.locator(FILE_INPUT).setInputFiles(upload('good.pdf', 'pdf', 4096));

    await expect(page.locator(UPLOAD_ERROR)).toHaveCount(0);
    await expect(page.locator(CHIP)).toHaveCount(1);
  });
});
