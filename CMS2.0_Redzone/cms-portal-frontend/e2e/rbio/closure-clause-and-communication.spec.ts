import { test, expect, type APIRequestContext } from '@playwright/test';
import {
  createTestComplaint,
  readCommunicationOutbox,
  readImpleadedParties,
  rbioReadComplaintColumns,
  setSystemConfig,
} from '../utils/test-data';

/**
 * Closure clause tiering, the closure communication gate, impleading and the award date —
 * UST504-509, 520, 541-546, 548, 576, 581-584, 762-764, 769.
 *
 * ── What this proves, and why it needed proving ───────────────────────────────────────────────────
 *
 * 1. CLAUSE TIERING WAS NOT IMPLEMENTED AND UST584 WAS VIOLATED. The endpoint RBIO called built a
 *    hardcoded List<Map> and never read CLOSURE_CLAUSE_MASTER, even though that table already models
 *    restricted_to_roles and appealability. The Deputy branch was an empty block and the Reviewer branch
 *    two comment lines, so every role received the Ombudsman's full list including the appealable award
 *    clauses. The role also arrived as an untrusted query parameter on an endpoint with no guard.
 *
 * 2. THE RBIO CLOSURE NEVER PERSISTED ITS CLAUSE. `grep setClosureClause` found four call sites and none
 *    was RBIO — the clause reached only a timeline row, so COMPLAINTS.closure_clause stayed NULL after
 *    every RBIO closure and an appeal against it then failed closed with 503. The appeal assertion below
 *    is the one that matters: it is the citizen-visible consequence.
 *
 * 3. THE COMMUNICATION GATE WAS BROWSER-ONLY. dateOfSending was POSTed and appeared NOWHERE in
 *    cms-backend; the signed letter lived in an Angular signal<File> that was never uploaded. A direct
 *    API call therefore closed a complaint with no letter, no date and no email to the complainant.
 *
 * 4. IMPLEAD VALIDATION REPORTED SUCCESS FROM A MISSING ENDPOINT. The client called
 *    /implead-validation, which existed in no module, and swallowed the 404 as {valid:true} — so closure
 *    could finalise over parties the Ombudsman had never cited a clause against.
 *
 * ── Why these are API-level tests ─────────────────────────────────────────────────────────────────
 * Every assertion here is a server-side legal determination: which clause a role may cite, whether a
 * citizen was told their case closed, whether a closure may finalise. A UI test would pass whether or not
 * the server enforced anything — that is precisely how these defects survived to a final QA gate. Several
 * assertions read the DATABASE rather than the action's own response, because the defect class is an
 * action answering success while persisting nothing.
 *
 * Run with: API_BASE_URL=http://localhost:8095 npx playwright test
 *   e2e/rbio/closure-clause-and-communication.spec.ts --project=chromium --reporter=list
 */

const OMBUDSMAN = { actor: 'omb_e2e_001', role: 'RBIO_OMBUDSMAN' };
const DEPUTY = { actor: 'dep_e2e_001', role: 'RBIO_DEPUTY_OMBUDSMAN' };

/** Seeded OMBUDSMAN_ONLY in CLOSURE_CLAUSE_MASTER, and appealable. */
const OMBUDSMAN_ONLY_CLAUSE = '16(2)(c)';
/** Unrestricted in the master table. */
const OPEN_CLAUSE = '16(2)(a)';

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';


/**
 * Posts an RBIO action as the Ombudsman.
 *
 * <p>Written here rather than using {@code performRbioAction} because that helper derives its identity
 * headers from the actor name through {@code identityHeadersFor}, which cannot produce RBIO_OMBUDSMAN or
 * RBIO_DEPUTY_OMBUDSMAN — and these tests are specifically about what a named role may and may not do.
 */
async function closeAsOmbudsman(
  request: APIRequestContext,
  complaintNumber: string,
  extras: Record<string, unknown>
) {
  const response = await request.post(
    `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
    {
      headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
      data: {
        action: 'DECIDE_NON_MAINTAINABLE',
        actor: OMBUDSMAN.actor,
        userRole: OMBUDSMAN.role,
        ...extras,
      },
    }
  );
  if (!response.ok()) {
    throw new Error(`Closure failed: ${response.status()} - ${await response.text()}`);
  }
  return response;
}

async function impleadAsOmbudsman(
  request: APIRequestContext,
  complaintNumber: string,
  partyName: string,
  reason: string
) {
  const response = await request.post(
    `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
    {
      headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
      data: {
        action: 'IMPLEAD_PARTY',
        actor: OMBUDSMAN.actor,
        userRole: OMBUDSMAN.role,
        remarks: reason,
        partyName,
        partyType: 'CO_RESPONDENT',
      },
    }
  );
  if (!response.ok()) {
    throw new Error(`Implead failed: ${response.status()} - ${await response.text()}`);
  }
  return response;
}

test.describe('Closure clause tiering (UST581-584)', () => {
  test('the Ombudsman receives the appealable clauses and the Deputy does not', async ({ request }) => {
    const ombudsman = await request.get(
      `${API_BASE}/api/v1/workflow/closure-clauses?role=${OMBUDSMAN.role}`,
      { headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor } }
    );
    expect(ombudsman.ok()).toBeTruthy();
    const ombudsmanCodes: string[] = (await ombudsman.json()).data.map((c: any) => c.code);

    const deputy = await request.get(
      `${API_BASE}/api/v1/workflow/closure-clauses?role=${DEPUTY.role}`,
      { headers: { 'X-User-Roles': DEPUTY.role, 'X-User-Id': DEPUTY.actor } }
    );
    const deputyCodes: string[] = (await deputy.json()).data.map((c: any) => c.code);

    // The award clauses are the ones UST584 withholds.
    expect(ombudsmanCodes).toContain('15(1)(a)');
    expect(ombudsmanCodes).toContain('15(1)(b)');
    expect(ombudsmanCodes).toContain(OMBUDSMAN_ONLY_CLAUSE);

    expect(deputyCodes).not.toContain('15(1)(a)');
    expect(deputyCodes).not.toContain('15(1)(b)');
    expect(deputyCodes).not.toContain(OMBUDSMAN_ONLY_CLAUSE);

    // Before this batch these two sets were IDENTICAL. That equality is the regression to guard.
    expect(deputyCodes.length).toBeLessThan(ombudsmanCodes.length);
  });

  test('the invented 2026 clauses 16(5) and 16(6) are no longer served to anybody', async ({ request }) => {
    // They existed in no migration and no seeder, were flagged newIn2026, and were added unconditionally
    // outside every role branch with no date gate. A complaint closed under an unconfigured clause can
    // never be appealed, because AppealClassificationService correctly fails closed.
    for (const role of [OMBUDSMAN.role, DEPUTY.role, 'RBIO_REVIEWER', 'RBIO_DEALING_OFFICIAL']) {
      const response = await request.get(
        `${API_BASE}/api/v1/workflow/closure-clauses?role=${role}`,
        { headers: { 'X-User-Roles': role, 'X-User-Id': 'probe_001' } }
      );
      const codes: string[] = (await response.json()).data.map((c: any) => c.code);
      expect(codes, `role ${role} must not be offered invented clauses`).not.toContain('16(5)');
      expect(codes, `role ${role} must not be offered invented clauses`).not.toContain('16(6)');
    }
  });

  test('every clause served carries the scheme version it belongs to', async ({ request }) => {
    // UST769/774 need the set to be scheme-scoped rather than a compiled-in list, so that a reprocessed
    // complaint can be offered the clauses it was originally closed under.
    const response = await request.get(
      `${API_BASE}/api/v1/workflow/closure-clauses?role=${OMBUDSMAN.role}`,
      { headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor } }
    );
    const clauses = (await response.json()).data;
    expect(clauses.length).toBeGreaterThan(0);
    for (const clause of clauses) {
      expect(clause.schemeVersion).toBe('RBIOS_2021');
    }
  });

  test('a Deputy citing an Ombudsman-only clause is REFUSED by the server', async ({ request }) => {
    // The control, as distinct from the filtered picker. A filtered list is a courtesy; this is the rule.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Tier Refusal',
      subject: 'Deputy cites an Ombudsman-only clause',
    });

    const response = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': DEPUTY.role, 'X-User-Id': DEPUTY.actor },
        data: {
          action: 'DECIDE_NON_MAINTAINABLE',
          actor: DEPUTY.actor,
          userRole: DEPUTY.role,
          remarks: 'Attempting a clause reserved to the Ombudsman',
          closureClause: OMBUDSMAN_ONLY_CLAUSE,
          maintainability: 'NON_MAINTAINABLE',
        },
      }
    );

    // 409, not 200-with-success:false: a statutory refusal must not be indistinguishable from a
    // successful call, and the message key must survive for translation.
    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body.success).toBe(false);
    expect(body.messageKey).toBe('rbio.closure.error_clause_not_permitted_for_role');

    // The refusal must leave the complaint untouched.
    const row = rbioReadComplaintColumns(complaintNumber, ['status', 'closure_clause']);
    expect(row['status']).not.toBe('closed');
    expect(row['closure_clause']).toBe('');
  });

  test('an unconfigured clause FAILS CLOSED rather than being accepted', async ({ request }) => {
    // Accepting it would persist a clause AppealClassificationService cannot classify, silently denying
    // the citizen an appeal. Refusing the closure is strictly the safer failure.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Unknown Clause',
      subject: 'Unconfigured clause must fail closed',
    });

    const response = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
        data: {
          action: 'DECIDE_NON_MAINTAINABLE',
          actor: OMBUDSMAN.actor,
          userRole: OMBUDSMAN.role,
          remarks: 'Citing a clause that is not in the master table',
          closureClause: '99(9)(z)',
          maintainability: 'NON_MAINTAINABLE',
        },
      }
    );

    expect(response.status()).toBe(409);
    expect((await response.json()).messageKey).toBe('rbio.closure.error_clause_unknown');
  });
});

test.describe('The RBIO closure persists its clause (UST548, 762)', () => {
  test('a closed RBIO complaint records its clause and is therefore appealable', async ({ request }) => {
    // THE HEADLINE ASSERTION. COMPLAINTS.closure_clause was NULL after every RBIO closure, so the appeal
    // eligibility check failed closed with 503 appeal.error_clause_not_configured — no RBIO complaint
    // closed through the product could be appealed at all.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Clause Persistence',
      subject: 'Closure clause must persist and permit an appeal',
    });

    const closure = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
        data: {
          action: 'DECIDE_NON_MAINTAINABLE',
          actor: OMBUDSMAN.actor,
          userRole: OMBUDSMAN.role,
          remarks: 'Closing with a configured, permitted clause',
          closureClause: OPEN_CLAUSE,
          maintainability: 'NON_MAINTAINABLE',
        },
      }
    );
    expect(closure.ok()).toBeTruthy();

    // Read the column, not the response: the response said success before this fix too.
    const row = rbioReadComplaintColumns(complaintNumber, ['status', 'closure_clause']);
    expect(row['status']).toBe('closed');
    expect(row['closure_clause']).toBe(OPEN_CLAUSE);

    // And the citizen-visible consequence.
    const eligibility = await request.get(
      `${API_BASE}/api/v1/appeals/eligibility/${complaintNumber}`,
      { headers: { 'X-User-Roles': 'AA_DO', 'X-User-Id': 'aa_do_001' } }
    );
    expect(eligibility.status(), 'a clause-bearing closure must not 503').toBe(200);
    expect((await eligibility.json()).data.eligible).toBe(true);
  });

  test('the custom closure text limit is enforced by the SERVER, at the exact boundary', async ({ request }) => {
    // UST576 was a maxlength attribute in two templates and nothing else, so an API caller could exceed
    // it and the column's own length surfaced the overflow as a database error, not a validation message.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Text Limit',
      subject: 'Custom closure text boundary',
    });

    const tooLong = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
        data: {
          action: 'DECIDE_NON_MAINTAINABLE',
          actor: OMBUDSMAN.actor,
          userRole: OMBUDSMAN.role,
          remarks: 'Overlong custom text',
          closureClause: OPEN_CLAUSE,
          maintainability: 'NON_MAINTAINABLE',
          customClosureText: 'x'.repeat(2001),
        },
      }
    );
    expect((await tooLong.json()).success).toBe(false);
    expect(rbioReadComplaintColumns(complaintNumber, ['status'])['status']).not.toBe('closed');

    // Exactly at the limit must be accepted — an off-by-one here would block a legitimate closure.
    const atLimit = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
        data: {
          action: 'DECIDE_NON_MAINTAINABLE',
          actor: OMBUDSMAN.actor,
          userRole: OMBUDSMAN.role,
          remarks: 'Custom text exactly at the limit',
          closureClause: OPEN_CLAUSE,
          maintainability: 'NON_MAINTAINABLE',
          customClosureText: 'y'.repeat(2000),
        },
      }
    );
    expect((await atLimit.json()).success).toBe(true);
  });
});

test.describe('Closure communications are queued durably (UST504-506, 757, 763)', () => {
  test('closing queues both an email and an SMS against the complaint', async ({ request }) => {
    // Before this, the CEPC path generated the letter bytes, DISCARDED the return value, stamped
    // closureLetterSentAt and logged; RBIO did nothing at all. The database recorded "sent" for a message
    // never handed to any transport, so a complainant asking "why was I never told" could not be answered.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Outbox',
      complainantEmail: 's4outbox@example.com',
      complainantPhone: '9876512345',
      subject: 'Closure must queue communications',
    });

    await closeAsOmbudsman(request, complaintNumber, {
      remarks: 'Closing to trigger the communication queue',
      closureClause: OPEN_CLAUSE,
      maintainability: 'NON_MAINTAINABLE',
      dateOfSending: '2026-09-19',
    });

    const queued = readCommunicationOutbox(complaintNumber);
    const channels = queued.map((row) => row.channel).sort();
    expect(channels).toEqual(['EMAIL', 'SMS']);
    expect(queued.find((r) => r.channel === 'EMAIL')?.recipient).toBe('s4outbox@example.com');
    expect(queued.find((r) => r.channel === 'SMS')?.recipient).toBe('9876512345');
  });

  test('the Date of Sending is persisted and cannot be revised afterwards', async ({ request }) => {
    // dateOfSending was POSTed on every closure and dropped — it appeared nowhere in cms-backend. UST757/763
    // additionally require it to be non-editable once recorded, which was previously "satisfied" only by the
    // accident that nothing read it at all.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Send Date',
      subject: 'Send date must persist and be write-once',
    });

    await closeAsOmbudsman(request, complaintNumber, {
      remarks: 'Recording the send date',
      closureClause: OPEN_CLAUSE,
      maintainability: 'NON_MAINTAINABLE',
      dateOfSending: '2026-09-19',
    });

    expect(rbioReadComplaintColumns(complaintNumber, ['date_of_sending'])['date_of_sending'])
      .toBe('2026-09-19');

    // A later action supplying a different date must not overwrite it.
    await request.post(`${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`, {
      headers: { 'X-User-Roles': 'RBIO_ADMIN', 'X-User-Id': 'admin_001' },
      data: {
        action: 'REOPEN',
        actor: 'admin_001',
        userRole: 'RBIO_ADMIN',
        remarks: 'Reopening and attempting to revise the send date',
        dateOfSending: '2026-01-01',
      },
    });

    expect(
      rbioReadComplaintColumns(complaintNumber, ['date_of_sending'])['date_of_sending'],
      'the send date evidences when the citizen was told and must not be revisable'
    ).toBe('2026-09-19');
  });
});

test.describe('The armed closure gate refuses an incomplete closure (UST507-509, 520)', () => {
  // The guard is seeded OFF, because arming it changes citizen-facing legal behaviour on a database where
  // 1646 of 1647 closed complaints predate the requirement. These tests arm it, prove it, and disarm it.
  test.afterEach(async () => {
    await setSystemConfig('cms.rbio.closure.require_send_date', 'false');
  });

  test('with the guard armed, a closure with no Date of Sending is refused and nothing is mutated', async ({
    request,
  }) => {
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Armed Gate',
      subject: 'Armed gate must refuse a closure with no send date',
    });

    await setSystemConfig('cms.rbio.closure.require_send_date', 'true');

    const response = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
        data: {
          action: 'DECIDE_NON_MAINTAINABLE',
          actor: OMBUDSMAN.actor,
          userRole: OMBUDSMAN.role,
          remarks: 'Closing without a send date',
          closureClause: OPEN_CLAUSE,
          maintainability: 'NON_MAINTAINABLE',
        },
      }
    );

    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body.messageKey).toBe('rbio.closure.error_send_date_required');
    expect(body.missingRequirement).toBe('SEND_DATE');

    // This is the assertion that proves it is a real control: the complaint is untouched, not
    // half-closed. A guard that refuses after mutating is worse than no guard.
    const row = rbioReadComplaintColumns(complaintNumber, ['status', 'closure_clause', 'date_of_sending']);
    expect(row['status']).not.toBe('closed');
    expect(row['closure_clause']).toBe('');
    expect(row['date_of_sending']).toBe('');
  });
});

test.describe('Impleading records real parties and gates closure (UST544-546, 767)', () => {
  test('an impleaded party gets its own row, keeping the party type the UI collected', async ({ request }) => {
    // Impleading appended a name to a CSV column; partyType was collected by the screen and persisted by
    // nothing. A CSV substring cannot carry a per-party clause, compensation or completeness state.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Implead Row',
      subject: 'Impleading must create a per-party record',
    });

    await impleadAsOmbudsman(request, complaintNumber, 'HDFC Bank Ltd',
      'Party bears joint liability for the disputed transaction');

    const parties = readImpleadedParties(complaintNumber);
    expect(parties).toHaveLength(1);
    expect(parties[0].partyName).toBe('HDFC Bank Ltd');
    expect(parties[0].partyType, 'partyType was previously discarded').toBe('CO_RESPONDENT');
    // A new party starts out owing information; defaulting to COMPLETE would let a closure finalise over
    // a party nobody had served.
    expect(parties[0].dataStatus).toBe('INFORMATION_REQUIRED');

    // The legacy CSV column stays maintained, because other sessions and tests read it.
    expect(rbioReadComplaintColumns(complaintNumber, ['impleaded_parties'])['impleaded_parties'])
      .toContain('HDFC Bank Ltd');
  });

  test('closure is REFUSED while an impleaded party has no clause, and names the party', async ({ request }) => {
    // The client called /implead-validation — an endpoint that existed in no module — and swallowed the
    // 404 as {valid:true}, so this case previously passed while checking nothing.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Implead Gate',
      subject: 'Closure must not finalise over an incomplete impleaded party',
    });

    await impleadAsOmbudsman(request, complaintNumber, 'ICICI Bank Ltd',
      'Impleaded pending its own clause');

    const response = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': OMBUDSMAN.role, 'X-User-Id': OMBUDSMAN.actor },
        data: {
          action: 'CLOSE_COMPLAINT',
          actor: OMBUDSMAN.actor,
          userRole: OMBUDSMAN.role,
          remarks: 'Attempting to close over an incomplete party',
          closureClause: OPEN_CLAUSE,
        },
      }
    );

    expect(response.status()).toBe(409);
    const body = await response.json();
    expect(body.messageKey).toBe('rbio.implead.error_incomplete_parties');
    // Naming the parties is the difference between an actionable refusal and a dead end.
    expect(body.incompleteParties).toContain('ICICI Bank Ltd');

    expect(rbioReadComplaintColumns(complaintNumber, ['status'])['status']).not.toBe('closed');
  });
});

test.describe('The adjudication award (UST539, 543)', () => {
  test('an award sent under the COMPONENT\'s field name persists its real value', async ({ request }) => {
    // The most severe defect found in the whole QA gate: rbio-adjudication.component.ts sent
    // `compensationAmount` while the service read `awardAmount`, so the figure never bound — it defaulted
    // to 0, passed the cap check however large the operator typed, and persisted as 0.00 while the API
    // answered success:true. A citizen's statutory compensation recorded as nothing, on an irreversible
    // act. Every pre-existing test supplied `awardAmount`, which is exactly why it survived.
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Award Binding',
      subject: 'Award must bind under the component field name',
    });

    const response = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': 'RBIO_ADJUDICATOR', 'X-User-Id': 'adj_e2e_001' },
        data: {
          action: 'ADJUDICATION_AWARD',
          actor: 'adj_e2e_001',
          userRole: 'RBIO_ADJUDICATOR',
          remarks: 'Award issued',
          compensationAmount: '750000',
          compensationType: 'COMBINED',
        },
      }
    );
    expect(response.ok()).toBeTruthy();

    const row = rbioReadComplaintColumns(complaintNumber, ['award_amount', 'award_passed_date']);
    expect(Number(row['award_amount'])).toBe(750000);
    // UST543: distinct from adjudication_date, which is shared with award REJECTION.
    expect(row['award_passed_date']).not.toBe('');
  });

  test('an award with no amount is REFUSED rather than defaulted to zero', async ({ request }) => {
    const { complaintNumber } = await createTestComplaint(request, {
      complainantName: 'S4 Award Refusal',
      subject: 'A missing award amount must be refused',
    });

    const response = await request.post(
      `${API_BASE}/api/v1/workflow/rbio/action/${complaintNumber}`,
      {
        headers: { 'X-User-Roles': 'RBIO_ADJUDICATOR', 'X-User-Id': 'adj_e2e_001' },
        data: {
          action: 'ADJUDICATION_AWARD',
          actor: 'adj_e2e_001',
          userRole: 'RBIO_ADJUDICATOR',
          remarks: 'No amount supplied',
        },
      }
    );

    expect((await response.json()).success).toBe(false);
    expect(rbioReadComplaintColumns(complaintNumber, ['award_amount'])['award_amount']).toBe('');
  });
});
