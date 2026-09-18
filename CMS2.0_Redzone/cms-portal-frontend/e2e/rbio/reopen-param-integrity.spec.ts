import { test, expect } from './harness';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  rbioLadderAction,
  rbioCaseFileGet,
  rbioReadComplaintColumns,
  rbioEstablishLadderCustody,
  DEFAULT_CLOSURE_CLAUSE,
  RBIO_LADDER_ACTORS,
} from '../utils/test-data';

/**
 * S3 — reopen restrictions (UST550-554) and the param-name integrity class.
 *
 * ── The param-name class ────────────────────────────────────────────────────────────────────────
 * This is the defect family that answers `200 OK` while storing the wrong data, so it cannot be caught
 * by checking status codes. The known instance: rbio-adjudication.component.ts sent
 * `compensationAmount` while the server read `awardAmount`, and a citizen's statutory compensation
 * persisted as 0.00 through a 30-lakh cap check, reporting success. Two more of the same shape existed
 * in this area and are covered below:
 *
 *   - IMPLEAD_PARTY: the component sends `impleadPartyName`, the server read `partyName`.
 *   - REASSIGN:      the component sends `targetUserId`,     the server read `targetUser`.
 *
 * REASSIGN was the dangerous one, because it declared no required params: the mismatch produced a
 * cheerful 200 and left the complaint with its ORIGINAL officer. Each test below asserts the value
 * actually LANDED, not merely that the call succeeded.
 */
test.describe('RBIO reopen restrictions (S3, UST550-554)', () => {
  /** Closes a complaint through the product path, with a clause so it is a lawful closure. */
  async function closeComplaint(request: any, complaintNumber: string): Promise<void> {
    const closed = await rbioLadderAction(
      request, complaintNumber, 'CLOSE_COMPLAINT', 'RBIO_ADMIN',
      { remarks: 'E2E: closing to test reopen', closureClause: DEFAULT_CLOSURE_CLAUSE }
    );
    if (!closed.success) {
      throw new Error(`Could not close ${complaintNumber}: ${closed.status} ${closed.message}`);
    }
  }

  test('only the Ombudsman may reopen — a Reviewer is refused server-side', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await closeComplaint(request, complaintNumber);

      const refused = await rbioLadderAction(
        request, complaintNumber, 'REOPEN', 'RBIO_REVIEWER',
        { remarks: 'Trying to reopen', reopenReason: 'COURT_ORDER', reopenJustification: 'Order dated 2026-09-01' }
      );

      // Hiding the button is not the control: this is a direct API call by an authenticated Reviewer.
      expect(refused.success).toBe(false);

      const columns = rbioReadComplaintColumns(complaintNumber, ['status', 'reopen_reason']);
      expect(columns['status'], 'a refused reopen must leave the complaint closed').toBe('closed');
      expect(columns['reopen_reason'], 'no reopen reason may have been recorded').toBe('');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a reopen without a justification is refused even for the Ombudsman', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await closeComplaint(request, complaintNumber);

      const refused = await rbioLadderAction(
        request, complaintNumber, 'REOPEN', 'RBIO_OMBUDSMAN',
        { remarks: 'Reopening', reopenReason: 'COURT_ORDER' }
      );

      expect(refused.status).toBe(400);
      expect(refused.message.toLowerCase()).toContain('justification');

      expect(rbioReadComplaintColumns(complaintNumber, ['status'])['status']).toBe('closed');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a reason outside the configured vocabulary is refused', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await closeComplaint(request, complaintNumber);

      const refused = await rbioLadderAction(
        request, complaintNumber, 'REOPEN', 'RBIO_OMBUDSMAN',
        { remarks: 'Reopening', reopenReason: 'I_CHANGED_MY_MIND', reopenJustification: 'Because' }
      );

      expect(refused.status).toBe(400);
      // The permitted set comes from SYSTEM_CONFIG cms.rbio.reopen.reasons, so the three UST551 values
      // are configuration rather than an enum. The message lists them, which is what lets an operator
      // see what a newly added reason should look like.
      expect(refused.message).toContain('APPELLATE_AUTHORITY');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('the Ombudsman reopens with a valid reason, and the file returns to the ORIGINAL dealing official',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        // Establish that rbio_do_001 originally processed it, then move it up and away before closing, so
        // "the original officer" and "the last officer" are genuinely different people. Without that, a
        // test could pass by returning the file to whoever touched it last.
        await rbioEstablishLadderCustody(request, complaintNumber);
        await closeComplaint(request, complaintNumber);

        const reopened = await rbioLadderAction(
          request, complaintNumber, 'REOPEN', 'RBIO_OMBUDSMAN',
          {
            remarks: 'Reopening on the appellate authority direction',
            reopenReason: 'APPELLATE_AUTHORITY',
            reopenJustification: 'AA order dated 2026-09-10 directs reconsideration',
          }
        );

        expect(reopened.success, `REOPEN failed: ${reopened.message}`).toBe(true);
        expect(reopened.data.newStatus).toBe('in_progress');
        // UST552: the ORIGINALLY processing dealing official, not the last holder.
        expect(reopened.data.assignedOfficer).toBe(RBIO_LADDER_ACTORS.RBIO_DEALING_OFFICIAL);
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('a reopen preserves the prior history rather than replacing it (UST552)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      const before = await rbioCaseFileGet(request, complaintNumber, 'assignment-history', 'RBIO_ADMIN');
      const rowsBefore = before.data.length;

      await closeComplaint(request, complaintNumber);
      await rbioLadderAction(
        request, complaintNumber, 'REOPEN', 'RBIO_OMBUDSMAN',
        { remarks: 'Reopening', reopenReason: 'CORRECTION_REQUIRED', reopenJustification: 'Award miscalculated' }
      );

      const after = await rbioCaseFileGet(request, complaintNumber, 'assignment-history', 'RBIO_ADMIN');
      // Strictly MORE rows: the reopen appends, and every earlier custody row survives. A reopen that
      // reset the trail would destroy the evidence of how the complaint was originally handled.
      expect(after.data.length).toBeGreaterThan(rowsBefore);
      expect(after.data.filter((row: any) => row.current).length).toBe(1);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});

test.describe('RBIO param-name integrity (S3)', () => {
  test('IMPLEAD_PARTY accepts the name the frontend actually sends, and persists it', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');
      await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_ADJUDICATION', 'RBIO_OMBUDSMAN',
        { remarks: 'To adjudication' }
      );

      // ONLY impleadPartyName — exactly what rbio-adjudication.component.ts:153-159 sends. The server
      // used to read only `partyName`, so this was refused and the impleading screen could never work.
      const impleaded = await rbioLadderAction(
        request, complaintNumber, 'IMPLEAD_PARTY', 'RBIO_OMBUDSMAN',
        { remarks: 'Impleading the acquirer', impleadPartyName: 'Acme Payments Ltd', impleadPartyType: 'PSO' }
      );

      expect(impleaded.success, `IMPLEAD_PARTY failed: ${impleaded.message}`).toBe(true);

      // The party must be READABLE afterwards. A 200 alone would not distinguish "stored" from
      // "silently discarded", which is the whole point of this defect class.
      const columns = rbioReadComplaintColumns(complaintNumber, ['impleaded_parties']);
      expect(columns['impleaded_parties']).toContain('Acme Payments Ltd');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('IMPLEAD_PARTY with NEITHER name is refused rather than storing a blank party',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');
        await rbioLadderAction(
          request, complaintNumber, 'FORWARD_TO_ADJUDICATION', 'RBIO_OMBUDSMAN', { remarks: 'To adjudication' }
        );

        const refused = await rbioLadderAction(
          request, complaintNumber, 'IMPLEAD_PARTY', 'RBIO_OMBUDSMAN', { remarks: 'No party named' }
        );

        expect(refused.success).toBe(false);

        // Not an empty string and not a stray comma: an identity field must never default silently.
        const columns = rbioReadComplaintColumns(complaintNumber, ['impleaded_parties']);
        expect(columns['impleaded_parties']).toBe('');
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('REASSIGN with only targetUserId actually moves the file (was a silent no-op)',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');

        const ownerBefore = rbioReadComplaintColumns(complaintNumber, ['assigned_officer'])['assigned_officer'];

        // ONLY targetUserId — what rbio-workflow.service.ts:93 sends. The server read `targetUser`, so
        // this answered 200 while leaving the complaint with its original officer. REASSIGN declares no
        // required params, so nothing caught it.
        const reassigned = await rbioLadderAction(
          request, complaintNumber, 'REASSIGN', 'RBIO_ADMIN',
          { remarks: 'Reassigning', targetUserId: 'rbio_officer_002', targetRole: 'RBIO_DEALING_OFFICIAL' }
        );

        expect(reassigned.success, `REASSIGN failed: ${reassigned.message}`).toBe(true);
        expect(reassigned.data.assignedOfficer).toBe('rbio_officer_002');
        expect(reassigned.data.assignedOfficer).not.toBe(ownerBefore);

        const after = rbioReadComplaintColumns(complaintNumber, ['assigned_officer']);
        expect(after['assigned_officer']).toBe('rbio_officer_002');
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('the transposed 13(1) notice code the frontend sends is accepted', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');
      await rbioLadderAction(
        request, complaintNumber, 'FORWARD_TO_ADJUDICATION', 'RBIO_OMBUDSMAN', { remarks: 'To adjudication' }
      );

      // rbio-adjudication.component.ts:121 sends ISSUE_13_1_NOTICE; the server's own code is
      // ISSUE_NOTICE_13_1. A statutory notice is not worth risking on a component rename, so the server
      // accepts both spellings for one effect.
      const issued = await rbioLadderAction(
        request, complaintNumber, 'ISSUE_13_1_NOTICE', 'RBIO_OMBUDSMAN',
        { remarks: 'Issuing notice under clause 13(1)' }
      );

      expect(issued.success, `ISSUE_13_1_NOTICE failed: ${issued.message}`).toBe(true);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a maintainability decision records the finding taken from the ACTION, not a contradicting param',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');

        // The param deliberately contradicts the action. The action code IS the decision, so the stored
        // finding must follow the action — otherwise a client could record the opposite of what it did.
        const decided = await rbioLadderAction(
          request, complaintNumber, 'DECIDE_MAINTAINABLE', 'RBIO_DEALING_OFFICIAL',
          { remarks: 'Complaint is maintainable', maintainability: 'NON_MAINTAINABLE' }
        );

        expect(decided.success, decided.message).toBe(true);

        const columns = rbioReadComplaintColumns(complaintNumber, ['maintainability_determination']);
        expect(columns['maintainability_determination']).toBe('MAINTAINABLE');
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('a non-maintainable closure requires the clause it is made under', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');

      // A non-maintainable closure denies a citizen statutory recourse, so it may not be recorded
      // without the clause. The clause is supplied and validated against the master table — never
      // defaulted, and never compiled into the transition declarations.
      const refused = await rbioLadderAction(
        request, complaintNumber, 'DECIDE_NON_MAINTAINABLE', 'RBIO_OMBUDSMAN',
        { remarks: 'Not maintainable' }
      );

      expect(refused.success).toBe(false);
      expect(refused.message.toLowerCase()).toContain('closureclause');

      expect(rbioReadComplaintColumns(complaintNumber, ['status'])['status']).not.toBe('closed');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
