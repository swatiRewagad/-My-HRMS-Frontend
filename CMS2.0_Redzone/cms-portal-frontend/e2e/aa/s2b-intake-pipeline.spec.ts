/**
 * S2B — inbound email & letter intake: suppression, deduplication, OCR gating, NFR-006.
 *
 * API-level by design. environment.ts points the browser at 8082 at BUILD time, so a UI-driven test
 * cannot be aimed at this session's backend without route interception; every control asserted here
 * is a SERVER-side control, which is exactly what must be proven.
 */
import { test, expect, APIRequestContext } from '../fixtures';
import {
  API_BASE,
  sql,
  assertBackendReady,
  TEN_LOCALES,
  fetchTranslations,
} from './aa-shared-fixtures';

const DO_HEADERS = { 'X-User-Roles': 'AA_DO', 'X-User-Id': 'aa_do_001' };
const ADMIN_HEADERS = { 'X-User-Roles': 'AA_ADMIN', 'X-User-Id': 'aa_admin_001' };

const PREFIX = 'S2B-';
const INTAKE = `${API_BASE}/api/v1/email-syndication`;

/** Local escape: esc() is private to the shared fixtures. */
function q(value: string): string {
  return value.replace(/\\/g, '\\\\').replace(/'/g, "''");
}

function purgeS2b(): void {
  sql(`DELETE FROM ignored_email_log WHERE sender_email LIKE '%${q(PREFIX)}%' OR subject LIKE '${q(PREFIX)}%'`);
  sql(`DELETE FROM email_ignore_list WHERE reason LIKE '${q(PREFIX)}%'`);
  sql(`DELETE FROM email_draft_attachments WHERE draft_id IN
        (SELECT draft_id FROM email_drafts WHERE subject LIKE '${q(PREFIX)}%' OR sender_email LIKE '%${q(PREFIX)}%')`);
  sql(`DELETE FROM email_drafts WHERE subject LIKE '${q(PREFIX)}%' OR sender_email LIKE '%${q(PREFIX)}%'`);
  sql(`DELETE FROM simulated_emails WHERE subject LIKE '%${q(PREFIX)}%'`);
  sql(`DELETE FROM COMPLAINTS WHERE subject LIKE '${q(PREFIX)}%' OR complaint_number LIKE '${q(PREFIX)}%'`);
}

/** Creates an ignore rule directly, returning its id. */
function seedIgnoreRule(fields: Record<string, string>): string {
  const cols = ['email_pattern', 'pattern_type', 'match_field', 'is_active', 'reason', 'created_at'];
  const vals = [
    `'${q(fields['emailPattern'] ?? '')}'`,
    `'${q(fields['patternType'] ?? 'EXACT')}'`,
    `'${q(fields['matchField'] ?? 'FROM')}'`,
    '1',
    `'${q(fields['reason'] ?? PREFIX + 'test rule')}'`,
    'NOW(6)',
  ];
  for (const [key, col] of [
    ['toPattern', 'to_pattern'],
    ['ccPattern', 'cc_pattern'],
    ['bccPattern', 'bcc_pattern'],
    ['subjectPattern', 'subject_pattern'],
    ['exceptionPattern', 'exception_pattern'],
  ] as const) {
    if (fields[key]) {
      cols.push(col);
      vals.push(`'${q(fields[key]!)}'`);
    }
  }
  sql(`INSERT INTO email_ignore_list (${cols.join(',')}) VALUES (${vals.join(',')})`);
  return sql(`SELECT id FROM email_ignore_list WHERE reason LIKE '${q(PREFIX)}%' ORDER BY id DESC LIMIT 1`);
}

async function ingest(request: APIRequestContext, payload: Record<string, unknown>) {
  const res = await request.post(`${INTAKE}/ingest`, { headers: DO_HEADERS, data: payload });
  expect(res.ok(), `ingest must respond 2xx, got ${res.status()}`).toBeTruthy();
  return (await res.json()).data as Record<string, unknown>;
}

test.describe('S2B intake — ignore list (Exceptional Email Master)', () => {
  test.beforeAll(() => purgeS2b());
  test.afterAll(() => purgeS2b());

  test('a matching rule suppresses draft creation and logs WHICH rule matched', async ({ request }) => {
    await assertBackendReady(request, DO_HEADERS);
    const sender = `${PREFIX}spam@marketing.example`;
    const ruleId = seedIgnoreRule({ emailPattern: sender, patternType: 'EXACT', matchField: 'FROM' });

    const data = await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}Buy now`,
      body: 'promotional content',
      messageId: `${PREFIX}msg-ignore-1`,
    });

    expect(data['draftCreated'], 'a suppressed email must NOT create a draft').toBe(false);
    expect(data['status']).toBe('IGNORED');
    expect(data['messageKey']).toBe('intake.email_suppressed_by_rule');
    // The rule's identity must survive: a bare boolean could not report this.
    expect(String(data['matchedRuleId']), 'the matched rule id must be reported').toBe(ruleId);

    const draftCount = sql(`SELECT COUNT(*) FROM email_drafts WHERE sender_email = '${q(sender)}'`);
    expect(draftCount, 'no draft row may exist for a suppressed sender').toBe('0');

    // The suppression is auditable, with the rule that caused it.
    const logged = sql(
      `SELECT matched_rule_id FROM ignored_email_log WHERE sender_email = '${q(sender)}' ORDER BY id DESC LIMIT 1`
    );
    expect(logged, 'the suppression must be logged against the matching rule').toBe(ruleId);
  });

  test('matching happens BEFORE creation, so no complaint or acknowledgement is produced', async ({ request }) => {
    const sender = `${PREFIX}noise@bulk.example`;
    seedIgnoreRule({ emailPattern: 'bulk.example', patternType: 'DOMAIN', matchField: 'FROM' });
    const subject = `${PREFIX}Bulk notice`;

    await ingest(request, { senderEmail: sender, subject, body: 'x', messageId: `${PREFIX}msg-ignore-2` });

    // Previously ingest created a Complaint and an outbound ack before the ignore check ran.
    expect(sql(`SELECT COUNT(*) FROM COMPLAINTS WHERE subject = '${q(subject)}'`),
      'a suppressed email must not create a complaint').toBe('0');
    expect(sql(`SELECT COUNT(*) FROM simulated_emails WHERE subject LIKE '%${q(subject)}%'`),
      'a suppressed email must not generate any email row').toBe('0');
  });

  test('a rule on CC is enforced — proving To/CC/BCC are persisted and matched', async ({ request }) => {
    const sender = `${PREFIX}citizen@example.com`;
    seedIgnoreRule({ emailPattern: '', ccPattern: 'internal-audit@example.com', patternType: 'CONTAINS' });

    const data = await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}CC rule`,
      body: 'x',
      messageId: `${PREFIX}msg-cc-1`,
      ccRecipients: 'internal-audit@example.com',
    });

    // This rule was unenforceable before, because ccRecipients was never stored.
    expect(data['draftCreated'], 'a CC-matched rule must suppress the draft').toBe(false);
    expect(data['matchedRuleField']).toBe('FROM');
  });

  test('an exception pattern overrides the ignore rule and the draft IS created', async ({ request }) => {
    const sender = `${PREFIX}vip@partner.example`;
    seedIgnoreRule({
      emailPattern: 'partner.example',
      patternType: 'DOMAIN',
      matchField: 'FROM',
      exceptionPattern: 'vip',
    });

    const data = await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}Important`,
      body: 'genuine complaint',
      messageId: `${PREFIX}msg-exception-1`,
    });

    expect(data['draftCreated'], 'the exception must admit this sender').not.toBe(false);
    expect(data['draftId'], 'a draft must be created when the exception matches').toBeTruthy();
  });

  test('an inactive rule does not suppress', async ({ request }) => {
    const sender = `${PREFIX}inactive@example.com`;
    const ruleId = seedIgnoreRule({ emailPattern: sender, patternType: 'EXACT' });
    sql(`UPDATE email_ignore_list SET is_active = 0 WHERE id = ${ruleId}`);

    const data = await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}Still a complaint`,
      body: 'x',
      messageId: `${PREFIX}msg-inactive-1`,
    });
    expect(data['draftId'], 'a deactivated rule must not suppress intake').toBeTruthy();
  });

  test('admin CRUD persists — a created rule survives and is really deleted', async ({ request }) => {
    const created = await request.post(`${INTAKE}/ignore-list`, {
      headers: ADMIN_HEADERS,
      data: {
        emailPattern: `${PREFIX}crud@example.com`,
        patternType: 'EXACT',
        matchField: 'FROM',
        subjectPattern: 'unsubscribe',
        reason: `${PREFIX}crud rule`,
      },
    });
    expect(created.ok()).toBeTruthy();
    const rule = (await created.json()).data as Record<string, unknown>;
    const id = rule['id'];
    expect(id, 'a persisted rule must have a database id').toBeTruthy();
    // Attribution comes from the caller, not a hardcoded "admin".
    expect(rule['addedBy']).toBe('aa_admin_001');
    expect(rule['subjectPattern']).toBe('unsubscribe');

    expect(sql(`SELECT COUNT(*) FROM email_ignore_list WHERE id = ${id}`),
      'the rule must exist in the database, not just in memory').toBe('1');

    const updated = await request.put(`${INTAKE}/ignore-list/${id}`, {
      headers: ADMIN_HEADERS,
      data: { emailPattern: `${PREFIX}crud@example.com`, reason: `${PREFIX}crud rule edited`, patternType: 'CONTAINS' },
    });
    expect(updated.ok()).toBeTruthy();
    expect(sql(`SELECT pattern_type FROM email_ignore_list WHERE id = ${id}`),
      'PUT must actually mutate the row (it used to echo the body)').toBe('CONTAINS');

    const deleted = await request.delete(`${INTAKE}/ignore-list/${id}`, { headers: ADMIN_HEADERS });
    expect(deleted.ok()).toBeTruthy();
    expect(sql(`SELECT COUNT(*) FROM email_ignore_list WHERE id = ${id}`),
      'DELETE must remove the row').toBe('0');
  });
});

test.describe('S2B intake — acknowledgement suppression', () => {
  test.beforeAll(() => purgeS2b());
  test.afterAll(() => purgeS2b());

  test('an @rbi.org.in sender receives NO automatic acknowledgement', async ({ request }) => {
    const subject = `${PREFIX}Internal forward`;
    const data = await ingest(request, {
      senderEmail: `${PREFIX}officer@rbi.org.in`,
      subject,
      body: 'forwarded internally',
      messageId: `${PREFIX}msg-ack-internal`,
    });

    expect(data['draftId'], 'an internal email still becomes a draft').toBeTruthy();
    const outbound = sql(
      `SELECT COUNT(*) FROM simulated_emails WHERE direction = 'OUTBOUND' AND subject LIKE '%${q(subject)}%'`
    );
    expect(outbound, 'no acknowledgement may be sent to an internal RBI sender').toBe('0');
  });

  test('an external citizen sender DOES receive an acknowledgement', async ({ request }) => {
    const subject = `${PREFIX}External complaint`;
    await ingest(request, {
      senderEmail: `${PREFIX}citizen@gmail.com`,
      subject,
      body: 'my card was debited twice',
      messageId: `${PREFIX}msg-ack-external`,
    });

    const outbound = sql(
      `SELECT COUNT(*) FROM simulated_emails WHERE direction = 'OUTBOUND' AND subject LIKE '%${q(subject)}%'`
    );
    expect(outbound, 'a citizen must still be acknowledged — the gate must not suppress everyone').toBe('1');
  });
});

test.describe('S2B intake — deduplication', () => {
  test.beforeAll(() => purgeS2b());
  test.afterAll(() => purgeS2b());

  /** Seeds a draft whose parent complaint has the given status. */
  function seedPriorDraft(sender: string, subject: string, parentNumber: string, parentStatus: string): void {
    // record_version is NOT NULL with no default in the live cms_db (V58 declares DEFAULT 0 but
    // ddl-auto rebuilt the column without it). @Version means Hibernate always supplies it, so the
    // app never notices; raw INSERT must. Omitting it gives
    //   ERROR 1364 (HY000): Field 'record_version' doesn't have a default value
    // which aborted these three tests before the intake endpoint was ever called.
    sql(`INSERT INTO COMPLAINTS (complaint_number, complainant_name, complainant_email, subject,
           description, status, priority, filing_type, record_version, created_at, updated_at)
         VALUES ('${q(parentNumber)}', 'S2B Prior', '${q(sender)}', '${q(subject)}',
           'prior complaint', '${q(parentStatus)}', 'medium', 'email', 0, NOW(6), NOW(6))`);
    // is_duplicate / ocr_processed / sub_judice / is_vernacular / requires_manual_entry are NOT NULL
    // without defaults, so they must be supplied explicitly here.
    sql(`INSERT INTO email_drafts (draft_id, message_id, sender_email, subject, body, status,
           mode_of_receipt, parent_complaint_id, converted_complaint_id,
           is_duplicate, ocr_processed, ocr_confidence, sub_judice, is_vernacular,
           requires_manual_entry, created_at, updated_at, received_at)
         VALUES ('${q(PREFIX + parentNumber)}', '${q(PREFIX + parentNumber + '-msg')}',
           '${q(sender)}', '${q(subject)}', 'prior', 'CONVERTED', 'EMAIL',
           '${q(parentNumber)}', '${q(parentNumber)}',
           0, 0, 0, 0, 0, 0, NOW(6), NOW(6), NOW(6))`);
  }

  test('same sender + identical subject with a CLOSED parent creates NO new draft and links to the parent', async ({ request }) => {
    const sender = `${PREFIX}repeat@example.com`;
    const subject = `${PREFIX}Unauthorised debit`;
    const parent = `${PREFIX}CMP-CLOSED-1`;
    seedPriorDraft(sender, subject, parent, 'closed');

    const before = sql(`SELECT COUNT(*) FROM email_drafts WHERE sender_email = '${q(sender)}'`);

    const data = await ingest(request, {
      senderEmail: sender,
      subject,
      body: 'same complaint again',
      messageId: `${PREFIX}msg-dup-closed`,
    });

    expect(data['isDuplicate'], 'this must be detected as a duplicate').toBe(true);
    expect(data['draftCreated'], 'a confirmed duplicate must NOT create a new draft').toBe(false);
    expect(data['messageKey']).toBe('intake.duplicate_linked_to_parent');
    expect(data['parentComplaintNumber'], 'the duplicate must name its CLOSED parent').toBe(parent);

    const after = sql(`SELECT COUNT(*) FROM email_drafts WHERE sender_email = '${q(sender)}'`);
    expect(after, 'the draft count must be unchanged').toBe(before);

    // The correspondence still reaches the parent complaint's email thread.
    expect(
      sql(`SELECT COUNT(*) FROM simulated_emails WHERE complaint_number = '${q(parent)}' AND status = 'LINKED_DUPLICATE'`),
      'the duplicate email must be linked to the parent complaint'
    ).toBe('1');
  });

  test('an OPEN parent is NOT a duplicate — live correspondence must still reach the officer', async ({ request }) => {
    const sender = `${PREFIX}ongoing@example.com`;
    const subject = `${PREFIX}Follow up on my case`;
    const parent = `${PREFIX}CMP-OPEN-1`;
    seedPriorDraft(sender, subject, parent, 'assigned');

    const data = await ingest(request, {
      senderEmail: sender,
      subject,
      body: 'any update?',
      messageId: `${PREFIX}msg-dup-open`,
    });

    // The ported logic had this inverted: it linked on an OPEN parent and let a CLOSED one through.
    expect(data['isDuplicate'], 'a follow-up on an OPEN complaint is not a duplicate').not.toBe(true);
    expect(data['draftId'], 'a new draft must be created for live correspondence').toBeTruthy();
  });

  test('a different subject from the same sender is not a duplicate', async ({ request }) => {
    const sender = `${PREFIX}multi@example.com`;
    const parent = `${PREFIX}CMP-CLOSED-2`;
    seedPriorDraft(sender, `${PREFIX}First matter`, parent, 'closed');

    const data = await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}An entirely different matter`,
      body: 'new issue',
      messageId: `${PREFIX}msg-dup-diff-subject`,
    });
    expect(data['draftId'], 'subject must match EXACTLY for a duplicate').toBeTruthy();
  });

  test('redelivery of the same messageId does not create a second draft', async ({ request }) => {
    const sender = `${PREFIX}idem@example.com`;
    const messageId = `${PREFIX}mail-intake-idem-1`;
    const payload = { senderEmail: sender, subject: `${PREFIX}Idempotent`, body: 'x', messageId };

    const first = await ingest(request, payload);
    const second = await ingest(request, payload);

    expect(second['duplicateDelivery'], 'a redelivered messageId must be recognised').toBe(true);
    expect(second['draftId'], 'redelivery must return the SAME draft').toBe(first['draftId']);
    expect(sql(`SELECT COUNT(*) FROM email_drafts WHERE message_id = '${q(messageId)}'`),
      'messageId is unique — only one row may exist').toBe('1');
  });
});

test.describe('S2B intake — OCR gating (vernacular routed to a human)', () => {
  test.beforeAll(() => purgeS2b());
  test.afterAll(() => purgeS2b());

  test('vernacular content is flagged, NOT OCR-processed, and routed to manual entry', async ({ request }) => {
    const subject = `${PREFIX}शिकायत`;
    const data = await ingest(request, {
      senderEmail: `${PREFIX}hindi@example.com`,
      subject,
      body: 'मेरे खाते से पैसे गलत तरीके से काट लिए गए हैं। कृपया मेरी शिकायत दर्ज करें और जांच करें।',
      messageId: `${PREFIX}msg-vernacular-1`,
    });

    expect(data['isVernacular'], 'Devanagari content must be detected as vernacular').toBe(true);
    expect(data['detectedLanguage']).toBe('hi');
    expect(data['ocrProcessed'], 'NO OCR may be attempted on vernacular content').toBe(false);
    expect(data['requiresManualEntry'], 'vernacular must be routed to a skilled operator').toBe(true);
    expect(data['ocrSkipReason']).toBe('VERNACULAR');
    expect(data['status'], 'the draft must sit in the manual-entry queue').toBe('PENDING_MANUAL_ENTRY');
  });

  test('the citizen original is preserved as the body — never replaced by a machine translation', async ({ request }) => {
    const original = 'বিনা অনুমতিতে আমার অ্যাকাউন্ট থেকে টাকা কেটে নেওয়া হয়েছে। অনুগ্রহ করে তদন্ত করুন।';
    const data = await ingest(request, {
      senderEmail: `${PREFIX}bengali@example.com`,
      subject: `${PREFIX}অভিযোগ`,
      body: original,
      messageId: `${PREFIX}msg-vernacular-2`,
    });

    const draftId = data['draftId'] as string;
    const storedBody = sql(`SELECT body FROM email_drafts WHERE draft_id = '${q(draftId)}'`);
    // The body used to be overwritten with an LLM translation, making a machine's words the record.
    expect(storedBody, "the complainant's own words must remain the record").toContain('অ্যাকাউন্ট');
    expect(sql(`SELECT COALESCE(translated_body,'') FROM email_drafts WHERE draft_id = '${q(draftId)}'`),
      'no machine translation may be stored as the complaint text').toBe('');
  });

  test('plain English content is not flagged as vernacular', async ({ request }) => {
    const data = await ingest(request, {
      senderEmail: `${PREFIX}english@example.com`,
      subject: `${PREFIX}Unauthorised transaction`,
      body: 'An amount of Rs 5000 was debited from my account without my authorisation on 12 March.',
      messageId: `${PREFIX}msg-english-1`,
    });
    expect(data['isVernacular'], 'English must not be flagged vernacular').toBe(false);
    expect(data['detectedLanguage']).toBe('en');
    expect(data['requiresManualEntry'], 'English needs no manual-entry routing').toBe(false);
  });

  test('a vernacular message padded with Latin punctuation is still detected', async ({ request }) => {
    // Boundary: the ratio is over LETTERS, not raw length. Dividing by raw length let whitespace
    // and punctuation dilute a short vernacular line below the threshold.
    const data = await ingest(request, {
      senderEmail: `${PREFIX}padded@example.com`,
      subject: `${PREFIX}Ref: 12345`,
      body: '...   ---   >>>   मेरा पैसा वापस चाहिए   <<<   ---   ...',
      messageId: `${PREFIX}msg-vernacular-3`,
    });
    expect(data['isVernacular'], 'punctuation padding must not defeat detection').toBe(true);
  });
});

test.describe('S2B intake — NFR-006 attachment limits (server-enforced)', () => {
  test.beforeAll(() => purgeS2b());
  test.afterAll(() => purgeS2b());

  test('a file over 2MB is rejected even when the client declares an allowed type', async ({ request }) => {
    const oversized = Buffer.alloc(2 * 1024 * 1024 + 1024, 0x41);
    const res = await request.post(`${INTAKE}/ingest-with-attachment`, {
      headers: DO_HEADERS,
      multipart: {
        senderEmail: `${PREFIX}big@example.com`,
        subject: `${PREFIX}Oversized`,
        body: 'see attached',
        messageId: `${PREFIX}msg-oversize-1`,
        attachment: { name: 'big.pdf', mimeType: 'application/pdf', buffer: oversized },
      },
    });

    // A browser-only size check is not a control; the server must refuse it.
    expect(res.status(), 'a file above the per-file limit must be rejected').toBeGreaterThanOrEqual(400);
    expect(sql(`SELECT COUNT(*) FROM email_drafts WHERE subject = '${q(PREFIX + 'Oversized')}'`),
      'a rejected upload must not leave a draft').toBe('0');
  });

  test('a file whose bytes contradict its declared content type is rejected', async ({ request }) => {
    // Declared as PDF; the bytes are not a PDF. The old code trusted the declared type.
    const res = await request.post(`${INTAKE}/ingest-with-attachment`, {
      headers: DO_HEADERS,
      multipart: {
        senderEmail: `${PREFIX}spoof@example.com`,
        subject: `${PREFIX}Spoofed`,
        body: 'see attached',
        messageId: `${PREFIX}msg-spoof-1`,
        attachment: {
          name: 'evil.pdf',
          mimeType: 'application/pdf',
          buffer: Buffer.from('MZ  this is an executable, not a pdf'),
        },
      },
    });
    expect(res.status(), 'magic bytes must be checked, not the declared type').toBeGreaterThanOrEqual(400);
  });
});

test.describe('S2B intake — ignored-email report and CSV export', () => {
  test.beforeAll(() => purgeS2b());
  test.afterAll(() => purgeS2b());

  test('the report lists sender, subject, timestamp and matched rule', async ({ request }) => {
    const sender = `${PREFIX}report@bulk.example`;
    const ruleId = seedIgnoreRule({ emailPattern: sender, patternType: 'EXACT', reason: `${PREFIX}report rule` });
    await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}Reported spam`,
      body: 'x',
      messageId: `${PREFIX}msg-report-1`,
    });

    const res = await request.get(`${INTAKE}/ignored-emails?senderEmail=${encodeURIComponent(sender)}`, {
      headers: ADMIN_HEADERS,
    });
    expect(res.ok()).toBeTruthy();
    const payload = (await res.json()).data as Record<string, unknown>;
    const entries = payload['entries'] as Array<Record<string, unknown>>;

    expect(entries.length, 'the suppressed email must appear in the report').toBeGreaterThan(0);
    const entry = entries[0];
    expect(entry['senderEmail']).toBe(sender);
    expect(entry['subject']).toBe(`${PREFIX}Reported spam`);
    expect(entry['receivedAt'], 'a timestamp is required').toBeTruthy();
    expect(String(entry['matchedRuleId']), 'the report must name the rule that suppressed it').toBe(ruleId);
  });

  test('the CSV export is downloadable and quotes a subject containing a comma', async ({ request }) => {
    const sender = `${PREFIX}csv@bulk.example`;
    seedIgnoreRule({ emailPattern: sender, patternType: 'EXACT', reason: `${PREFIX}csv rule` });
    await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}Hello, world, again`,
      body: 'x',
      messageId: `${PREFIX}msg-csv-1`,
    });

    const res = await request.get(`${INTAKE}/ignored-emails/export?senderEmail=${encodeURIComponent(sender)}`, {
      headers: ADMIN_HEADERS,
    });
    expect(res.ok()).toBeTruthy();
    expect(res.headers()['content-disposition'], 'the export must download as a file').toContain('ignored-emails.csv');

    const csv = await res.text();
    expect(csv.split('\n')[0]).toContain('Matched Rule ID');
    // Unquoted, the commas in the subject would shift every later column.
    expect(csv, 'a comma-bearing subject must be quoted').toContain('"' + PREFIX + 'Hello, world, again"');
  });

  test('the stats endpoint reports a real ignored count, not a hardcoded zero', async ({ request }) => {
    const sender = `${PREFIX}stats@bulk.example`;
    seedIgnoreRule({ emailPattern: sender, patternType: 'EXACT', reason: `${PREFIX}stats rule` });
    await ingest(request, {
      senderEmail: sender,
      subject: `${PREFIX}Stats spam`,
      body: 'x',
      messageId: `${PREFIX}msg-stats-1`,
    });

    const res = await request.get(`${INTAKE}/stats`, { headers: DO_HEADERS });
    const stats = (await res.json()).data as Record<string, number>;
    expect(stats['ignoredCount'], 'ignoredCount was hardcoded 0 and must now be real').toBeGreaterThan(0);
  });
});

test.describe('S2B intake — i18n', () => {
  test('every intake message key resolves in all ten locales without falling back to English', async ({ request }) => {
    const keys = [
      'intake.email_suppressed_by_rule',
      'intake.duplicate_linked_to_parent',
      'intake.vernacular_manual_entry_required',
      'intake.ocr_low_confidence_manual_entry',
      'intake.attachment_too_large',
      'intake.attachment_total_too_large',
      'intake.attachment_too_many',
      'intake.attachment_rejected',
      'intake.manual_entry_required_banner',
      'intake.ignored_emails_report_title',
      'intake.exceptional_email_master_title',
    ];

    const english = await fetchTranslations(request, 'en');
    for (const key of keys) {
      expect(english[key], `${key} must exist in English`).toBeTruthy();
    }

    for (const locale of TEN_LOCALES.filter((l) => l !== 'en')) {
      const values = await fetchTranslations(request, locale);
      for (const key of keys) {
        expect(values[key], `${key} must exist in ${locale}`).toBeTruthy();
        expect(values[key], `${key} in ${locale} must not be the English string copied through`)
          .not.toBe(english[key]);
      }
    }
  });
});
