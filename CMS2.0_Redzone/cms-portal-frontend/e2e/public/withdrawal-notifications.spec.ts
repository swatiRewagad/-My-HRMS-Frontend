import { test, expect } from '../fixtures';
import { createTestComplaint, loginCitizen, identityHeadersFor } from '../utils/test-data';
import { sql } from '../aa/aa-shared-fixtures';

/**
 * Withdrawal NOTIFICATIONS to the officers who hold the case. Manual QA cases 15-22.
 *
 * ── GROUND TRUTH established before these were written ──────────────────────────────────────────
 *
 * The withdrawal handler is ComplaintApiV1Controller.withdrawComplaint (java:554-651). It writes the
 * three withdrawal_* columns, saves, and adds ONE timeline row. Lines 632-635 are an explicit comment
 * saying no event is published:
 *
 *     // Publish Kafka event (if publisher is available)
 *     // Since there's no dedicated withdrawn publisher, we skip Kafka here —
 *     // the outbox/event infrastructure can be added in a future phase.
 *
 * The class does not inject NotificationService at all (its dependency list is java:45-57), and
 * `grep -i withdraw` finds nothing in NotificationService, ReNotificationService,
 * NotificationConfigService or NotificationScheduledTasks. A live database confirms the consequence:
 * across 35 complaints already in status `withdrawn`, IN_APP_NOTIFICATIONS, COMMUNICATION_OUTBOX and
 * OUTBOX_EVENT hold ZERO withdrawal rows.
 *
 * These tests therefore assert the INTENDED behaviour the manual cases describe. They are expected to
 * fail, as one gap with one cause: withdrawal emits no notification of any kind. They are written as
 * assertions rather than skips so the gap stays visible in every run until it is built.
 *
 * ── HOW A NOTIFICATION WOULD BE OBSERVED ────────────────────────────────────────────────────────
 *
 * Two independent views, both checked:
 *  - IN_APP_NOTIFICATIONS (the officer's bell), read through the officer's OWN
 *    GET /api/v1/notifications/unread so the test proves the notification is VISIBLE TO THEM and not
 *    merely present in a table addressed to an undeliverable role string — a real defect class in
 *    this repo (see NotificationRecipientResolver's class comment).
 *  - COMMUNICATION_OUTBOX, the transactional outbox for email/SMS.
 *
 * ── RECIPIENT MODEL ─────────────────────────────────────────────────────────────────────────────
 *
 * DO  = COMPLAINTS.assigned_officer (NotificationConfigService.RECIPIENT_COMPLAINT_OWNER).
 * NO / PNO = ENTITY_USERS rows for the complaint's entity_code with re_role NODAL_OFFICER / PNO
 *            (NotificationRecipientResolver.entityRoleMembers, java:126-141). ENTITY_USERS is EMPTY in
 *            cms_db, so these tests seed their own officers for entity A and entity B and remove them
 *            afterwards. Without that seed a "no notification" assertion would pass vacuously because
 *            no NO exists at all.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

const CITIZEN_PHONE = '9876543210';
const WITHDRAWAL_REASON = 'Issue resolved by the Regulated Entity';

/** Every seeded row carries this prefix so cleanup can never touch another session's fixtures. */
const PREFIX = 'WDN';

/** entity_code holds the NORMALISED ENTITY NAME in this schema, not a code. See aa-shared-fixtures. */
const ENTITY_A = 'HDFC Bank';
const ENTITY_B = 'State Bank of India';

const DO_ASSIGNED = `${PREFIX}_do_assigned`;
const DO_UNRELATED = `${PREFIX}_do_unrelated`;
const NO_A = `${PREFIX}_no_a`;
const PNO_A = `${PREFIX}_pno_a`;
const NO_B = `${PREFIX}_no_b`;
const PNO_B = `${PREFIX}_pno_b`;

interface Notification {
  id: number;
  type: string;
  title: string;
  message: string;
  relatedEntityId: string;
  actionUrl: string;
}

/** What the officer's own bell shows them. The authorisation path, not a raw table read. */
async function bellFor(
  request: import('@playwright/test').APIRequestContext,
  userId: string,
  scope: 'CEPC' | 'RE' = 'CEPC'
): Promise<Notification[]> {
  const res = await request.get(`${API_BASE}/api/v1/notifications/unread`, {
    headers: identityHeadersFor(userId, scope),
  });
  expect(res.status(), `${userId} must be able to read their own notifications`).toBe(200);
  return (await res.json()) as Notification[];
}

/** Rows in IN_APP_NOTIFICATIONS for one complaint, whoever they are addressed to. */
function notificationRows(complaintNumber: string): Array<{ target: string; type: string; title: string; message: string; url: string }> {
  const out = sql(
    `SELECT target_user_id, type, title, COALESCE(message,''), COALESCE(action_url,'') ` +
    `FROM in_app_notifications WHERE related_entity_id = '${complaintNumber}' ORDER BY id ASC`
  );
  if (!out) return [];
  return out.split('\n').filter(l => l.trim()).map(line => {
    const [target, type, title, message, url] = line.split('\t');
    return { target, type, title, message, url };
  });
}

/** Rows queued for email/SMS dispatch for one complaint. */
function outboxRows(complaintNumber: string): Array<{ type: string; recipient: string }> {
  const out = sql(
    `SELECT communication_type, COALESCE(recipient,'') FROM communication_outbox ` +
    `WHERE related_reference = '${complaintNumber}' ORDER BY id ASC`
  );
  if (!out) return [];
  return out.split('\n').filter(l => l.trim()).map(line => {
    const [type, recipient] = line.split('\t');
    return { type, recipient };
  });
}

function seedEntityUser(userId: string, entityCode: string, role: string, email: string): void {
  sql(
    `INSERT INTO ENTITY_USERS ` +
    `(user_id, entity_code, display_name, email, re_role, active, created_at) ` +
    `VALUES ('${userId}', '${entityCode}', '${userId} display', '${email}', '${role}', 1, NOW())`
  );
}

function purge(): void {
  sql(`DELETE FROM ENTITY_USERS WHERE user_id LIKE '${PREFIX}\\_%'`);
  sql(`DELETE FROM in_app_notifications WHERE target_user_id LIKE '${PREFIX}\\_%'`);
}

/** Creates a complaint owned by CITIZEN_PHONE, scoped to one entity, held by one DO. */
async function seedComplaintFor(
  request: import('@playwright/test').APIRequestContext,
  entityCode: string,
  owner: string,
  subject: string
): Promise<{ complaintNumber: string; complaintId: number }> {
  const created = await createTestComplaint(request, {
    subject,
    complainantName: 'Withdrawal Notification Citizen',
    complainantPhone: CITIZEN_PHONE,
    entityName: entityCode,
  });

  // entity_code and assigned_officer are the two keys every recipient rule reads, and neither is
  // settable through the create endpoint's payload for a named entity/officer. Set directly so the
  // routing under test has real inputs rather than whatever the round-robin happened to pick.
  sql(
    `UPDATE COMPLAINTS SET entity_code = '${entityCode}', assigned_officer = '${owner}' ` +
    `WHERE complaint_number = '${created.complaintNumber}'`
  );

  return { complaintNumber: created.complaintNumber, complaintId: Number(created.complaintId) };
}

async function withdraw(
  request: import('@playwright/test').APIRequestContext,
  complaintNumber: string,
  token: string,
  reason = WITHDRAWAL_REASON
): Promise<void> {
  const res = await request.post(`${API_BASE}/api/v1/complaints/${complaintNumber}/withdraw`, {
    data: { reason, remarks: '' },
    headers: { 'Content-Type': 'application/json', 'X-Citizen-Token': token },
  });
  expect(res.status(), `the withdrawal itself must succeed. Body: ${await res.text()}`).toBe(200);
  expect(
    sql(`SELECT status FROM COMPLAINTS WHERE complaint_number = '${complaintNumber}'`),
    'the withdrawal must be applied before notifications are asserted'
  ).toBe('withdrawn');
}

/**
 * Waits for a notification addressed to `userId` about `complaintNumber`, or gives up.
 *
 * Polls because notification writes in this codebase are frequently @Async — asserting once
 * immediately after the call would report a gap that is really a race.
 */
async function waitForNotification(
  request: import('@playwright/test').APIRequestContext,
  userId: string,
  complaintNumber: string,
  scope: 'CEPC' | 'RE' = 'CEPC'
): Promise<Notification | undefined> {
  const deadline = Date.now() + 15_000;
  while (Date.now() < deadline) {
    const found = (await bellFor(request, userId, scope))
      .find(n => (n.relatedEntityId || '') === complaintNumber
        || (n.message || '').includes(complaintNumber));
    if (found) return found;
    await new Promise(r => setTimeout(r, 1_000));
  }
  return undefined;
}

/** Every mandatory field the manual cases require the notification to carry. */
function assertMandatoryFields(
  notification: Notification | undefined,
  role: string,
  complaintNumber: string,
  complainantName: string,
  reason: string,
  expectDocuments: boolean
): void {
  expect(
    notification,
    `${role} must receive a withdrawal notification for ${complaintNumber}. None was found in ` +
    'their own notification feed.'
  ).toBeTruthy();

  const body = `${notification!.title || ''} ${notification!.message || ''}`;
  expect(body, `the ${role} notification must carry the Complaint Number`).toContain(complaintNumber);
  expect(body, `the ${role} notification must carry the Complainant Name`).toContain(complainantName);
  expect(body, `the ${role} notification must carry the Reason for Withdrawal`).toContain(reason);

  // Date of Withdrawal — an ISO date, or the words. Either renders; neither being present does not.
  expect(
    /\d{4}-\d{2}-\d{2}|\d{2}[/-]\d{2}[/-]\d{4}/.test(body),
    `the ${role} notification must carry the Date of Withdrawal. Body was: ${body}`
  ).toBe(true);

  if (expectDocuments) {
    expect(
      `${body} ${notification!.actionUrl || ''}`,
      `the ${role} notification must link to the documents the citizen uploaded`
    ).toMatch(/\/api\/files\/|\/download\/|attachment/i);
  }
}

test.describe('Withdrawal notifications — DO / NO / PNO', () => {

  test.beforeAll(() => {
    purge();
    seedEntityUser(NO_A, ENTITY_A, 'NODAL_OFFICER', `${NO_A}@example.com`);
    seedEntityUser(PNO_A, ENTITY_A, 'PNO', `${PNO_A}@example.com`);
    seedEntityUser(NO_B, ENTITY_B, 'NODAL_OFFICER', `${NO_B}@example.com`);
    seedEntityUser(PNO_B, ENTITY_B, 'PNO', `${PNO_B}@example.com`);
  });

  test.afterAll(() => purge());

  // ══ Case 15 ═════════════════════════════════════════════════════════════════════════════════
  test('case 15: the assigned Processing Officer is notified, with the complaint and the reason',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — DO');
      await withdraw(request, complaintNumber, token!);

      const notification = await waitForNotification(request, DO_ASSIGNED, complaintNumber);
      expect(
        notification,
        'the assigned Processing Officer must be told their case was withdrawn — they are otherwise ' +
        'working a complaint the complainant has abandoned. Rows written for this complaint: ' +
        JSON.stringify(notificationRows(complaintNumber))
      ).toBeTruthy();

      const body = `${notification!.title || ''} ${notification!.message || ''}`;
      expect(body, 'the DO notification must identify the complaint').toContain(complaintNumber);
      expect(body, 'the DO notification must carry the withdrawal reason').toContain(WITHDRAWAL_REASON);
    });

  // ══ Case 16 ═════════════════════════════════════════════════════════════════════════════════
  test('case 16: the Nodal Officer of the concerned Regulated Entity is notified',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — NO');
      await withdraw(request, complaintNumber, token!);

      const notification = await waitForNotification(request, NO_A, complaintNumber, 'RE');
      expect(
        notification,
        `the Nodal Officer of ${ENTITY_A} must be told the complaint was withdrawn so the entity ` +
        'stops working it. Rows written: ' + JSON.stringify(notificationRows(complaintNumber))
      ).toBeTruthy();
      expect(`${notification!.title} ${notification!.message}`).toContain(complaintNumber);
    });

  // ══ Case 17 ═════════════════════════════════════════════════════════════════════════════════
  test('case 17: the Principal Nodal Officer of the concerned Regulated Entity is notified',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — PNO');
      await withdraw(request, complaintNumber, token!);

      const notification = await waitForNotification(request, PNO_A, complaintNumber, 'RE');
      expect(
        notification,
        `the Principal Nodal Officer of ${ENTITY_A} must be notified of the withdrawal. Rows ` +
        'written: ' + JSON.stringify(notificationRows(complaintNumber))
      ).toBeTruthy();
      expect(`${notification!.title} ${notification!.message}`).toContain(complaintNumber);
    });

  // ══ Case 18 ═════════════════════════════════════════════════════════════════════════════════
  test('case 18: the DO notification carries every mandatory field and the document links',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber, complaintId } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — DO mandatory fields');

      // A document really attached to the complaint, so "the notification must link to it" is a
      // statement about the notification and not about a missing upload.
      const upload = await request.post(
        `${API_BASE}/api/files/upload?complaintNumber=${complaintNumber}` +
        `&complaintId=${complaintId}&documentType=WITHDRAWAL_SUPPORT`,
        {
          multipart: {
            file: {
              name: 'withdrawal-support.pdf',
              mimeType: 'application/pdf',
              buffer: Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(512, 0x20)]),
            },
          },
          headers: identityHeadersFor('cepc_do1'),
        }
      );
      expect(upload.status(), 'the supporting document must be attached before withdrawal').toBe(200);

      await withdraw(request, complaintNumber, token!);

      const notification = await waitForNotification(request, DO_ASSIGNED, complaintNumber);
      assertMandatoryFields(
        notification, 'the Processing Officer', complaintNumber,
        'Withdrawal Notification Citizen', WITHDRAWAL_REASON, true);
    });

  // ══ Case 19 ═════════════════════════════════════════════════════════════════════════════════
  test('case 19: the NO and PNO notifications carry every mandatory field and the document links',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber, complaintId } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — RE mandatory fields');

      const upload = await request.post(
        `${API_BASE}/api/files/upload?complaintNumber=${complaintNumber}` +
        `&complaintId=${complaintId}&documentType=WITHDRAWAL_SUPPORT`,
        {
          multipart: {
            file: {
              name: 'withdrawal-support-re.pdf',
              mimeType: 'application/pdf',
              buffer: Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(512, 0x20)]),
            },
          },
          headers: identityHeadersFor('cepc_do1'),
        }
      );
      expect(upload.status()).toBe(200);

      await withdraw(request, complaintNumber, token!);

      for (const [role, userId] of [['Nodal Officer', NO_A], ['Principal Nodal Officer', PNO_A]] as const) {
        const notification = await waitForNotification(request, userId, complaintNumber, 'RE');
        assertMandatoryFields(
          notification, `the ${role}`, complaintNumber,
          'Withdrawal Notification Citizen', WITHDRAWAL_REASON, true);
      }
    });

  // ══ Case 20 ═════════════════════════════════════════════════════════════════════════════════
  test('case 20: with no supporting documents the notification still carries every other field, ' +
    'and the document field is safely empty', async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber, complaintId } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — no documents');
      await withdraw(request, complaintNumber, token!);

      expect(
        sql(`SELECT COUNT(*) FROM complaint_attachments WHERE complaint_id = ${complaintId}`),
        'this case requires a complaint with no attachments'
      ).toBe('0');

      const notification = await waitForNotification(request, DO_ASSIGNED, complaintNumber);
      assertMandatoryFields(
        notification, 'the Processing Officer', complaintNumber,
        'Withdrawal Notification Citizen', WITHDRAWAL_REASON, false);

      // Safely empty: no null rendering, and no link that goes nowhere.
      const body = `${notification!.title || ''} ${notification!.message || ''} ${notification!.actionUrl || ''}`;
      expect(
        body,
        'a withdrawal with no documents must not render "null" or "undefined" to the officer'
      ).not.toMatch(/\bnull\b|\bundefined\b/i);

      const links = body.match(/\/api\/files\/download\/\d+/g) || [];
      for (const link of links) {
        const res = await request.get(`${API_BASE}${link}`, { headers: identityHeadersFor('cepc_do1') });
        expect(
          res.status(),
          `the notification must not contain a broken document link (${link})`
        ).toBeLessThan(400);
      }
    });

  // ══ Case 21 ═════════════════════════════════════════════════════════════════════════════════
  test('case 21: an unassigned Processing Officer does NOT receive the withdrawal alert',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — DO isolation');
      await withdraw(request, complaintNumber, token!);

      // The POSITIVE half must hold first, or "officer Y got nothing" is satisfied by a system that
      // notifies nobody at all — which would make this an assertion that proves nothing.
      expect(
        await waitForNotification(request, DO_ASSIGNED, complaintNumber),
        'the assigned officer must be notified, otherwise this isolation check is vacuous'
      ).toBeTruthy();

      const unrelated = (await bellFor(request, DO_UNRELATED))
        .filter(n => (n.relatedEntityId || '') === complaintNumber);
      expect(
        unrelated,
        `${DO_UNRELATED} holds no part of this case and must not receive its withdrawal alert`
      ).toHaveLength(0);

      expect(
        notificationRows(complaintNumber).map(r => r.target),
        'no notification for this complaint may be addressed to an unrelated officer'
      ).not.toContain(DO_UNRELATED);
    });

  // ══ Case 22 ═════════════════════════════════════════════════════════════════════════════════
  test('case 22: the NO/PNO of an unrelated Regulated Entity do NOT receive the notification',
    async ({ request }) => {
      const token = await loginCitizen(request, CITIZEN_PHONE);
      test.skip(!token, 'Requires cms.auth.otp.dev-auto-populate=true (dev-local profile)');

      const { complaintNumber } = await seedComplaintFor(
        request, ENTITY_A, DO_ASSIGNED, 'E2E Withdrawal notification — RE isolation');
      await withdraw(request, complaintNumber, token!);

      // Positive half first, for the same reason as case 21.
      expect(
        await waitForNotification(request, NO_A, complaintNumber, 'RE'),
        `the Nodal Officer of ${ENTITY_A} must be notified, otherwise this isolation check is vacuous`
      ).toBeTruthy();

      for (const [role, userId] of [['Nodal Officer', NO_B], ['Principal Nodal Officer', PNO_B]] as const) {
        const leaked = (await bellFor(request, userId, 'RE'))
          .filter(n => (n.relatedEntityId || '') === complaintNumber);
        expect(
          leaked,
          `the ${role} of ${ENTITY_B} must not see a withdrawal for a complaint against ${ENTITY_A} — ` +
          'that is a cross-entity data leak'
        ).toHaveLength(0);
      }

      const targets = notificationRows(complaintNumber).map(r => r.target);
      expect(targets, 'entity B officers must not be addressed at all').not.toContain(NO_B);
      expect(targets, 'entity B officers must not be addressed at all').not.toContain(PNO_B);

      // Email/SMS is the other delivery path and leaks the same way, so it is checked too.
      const recipients = outboxRows(complaintNumber).map(r => r.recipient);
      expect(recipients.join(' '), `${ENTITY_B}'s officers must not be emailed either`)
        .not.toContain(NO_B);
    });
});
