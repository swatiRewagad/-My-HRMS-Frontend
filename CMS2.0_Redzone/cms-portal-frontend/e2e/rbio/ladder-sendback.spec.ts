import { test, expect } from './harness';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  rbioLadderAction,
  rbioCaseFileGet,
  rbioEstablishLadderCustody,
  RBIO_LADDER_ACTORS,
} from '../utils/test-data';

/**
 * S3 — the RBIO ladder and its three role-specific send-backs.
 *
 * ── What was broken ─────────────────────────────────────────────────────────────────────────────
 * Fifteen action codes the frontend can POST had no server counterpart and hit "Unknown RBIO action".
 * Among them all three send-backs (SEND_BACK_DO / _REVIEWER / _INCHARGE), dispatched from
 * task-action.component.ts on the RBIO route, where only a single generic RETURN_TO_OFFICER existed
 * with no notion of WHICH role to return to. Every failure path is `catchError(() => of(default))`, so
 * the screens reported success and nothing happened.
 *
 * ── Why these are API tests ─────────────────────────────────────────────────────────────────────
 * Every assertion here is about SERVER enforcement: who may act, what is refused, and what is
 * persisted. A browser test cannot prove a control holds, because the control has to hold for a
 * caller who never loaded the page. The UI affordances are covered separately.
 *
 * ── The trap these tests are written to avoid ───────────────────────────────────────────────────
 * A send-back on a complaint nobody has handed over is LEGITIMATELY refused (422, no previous holder).
 * Asserting that and calling it a pass would prove nothing about the happy path, so
 * rbioEstablishLadderCustody establishes real custody first.
 */
test.describe('RBIO ladder and send-backs (S3)', () => {
  test('DO submits for review, and the file moves up the ladder to a Reviewer', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const accepted = await rbioLadderAction(request, complaintNumber, 'ACCEPT', 'RBIO_DEALING_OFFICIAL');
      expect(accepted.success, `ACCEPT failed: ${accepted.message}`).toBe(true);

      const submitted = await rbioLadderAction(
        request, complaintNumber, 'SUBMIT_FOR_REVIEW', 'RBIO_DEALING_OFFICIAL',
        { remarks: 'Assessment complete' }
      );

      expect(submitted.success, `SUBMIT_FOR_REVIEW failed: ${submitted.message}`).toBe(true);
      expect(submitted.data.newStatus).toBe('reviewer_review');
      expect(submitted.data.assignedRole).toBe('RBIO_REVIEWER');
      // Round robin is the default, so SOMEBODY holding the reviewer role must own it now. Asserting a
      // specific username would pin the pointer's position rather than the handover.
      expect(submitted.data.assignedOfficer, 'the file must have an owner after a handover').toBeTruthy();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a send-back returns the file to the officer who previously held that role, not to the sender',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        await rbioEstablishLadderCustody(request, complaintNumber);

        // The resolved target is published for S5's UST759 — assert the contract, not just the effect.
        const target = await rbioCaseFileGet(
          request, complaintNumber, 'last-active-officer', 'RBIO_REVIEWER',
          '?role=RBIO_DEALING_OFFICIAL'
        );
        expect(target.status).toBe(200);
        expect(target.data.availability).toBe('AVAILABLE');
        expect(target.data.userId).toBe(RBIO_LADDER_ACTORS.RBIO_DEALING_OFFICIAL);
        expect(target.data.requiresManualSelection).toBe(false);

        // No targetUser is supplied: the server must derive the previous holder itself.
        const sentBack = await rbioLadderAction(
          request, complaintNumber, 'SEND_BACK_DO', 'RBIO_REVIEWER',
          { remarks: 'Attach the transaction log before resubmitting' }
        );

        expect(sentBack.success, `SEND_BACK_DO failed: ${sentBack.message}`).toBe(true);
        expect(sentBack.data.assignedRole).toBe('RBIO_DEALING_OFFICIAL');
        // THE point of the whole mechanism. CEPC's equivalent leaves assignedOfficer untouched when no
        // targetUser is sent, so the role drops to the dealing official while the REVIEWER WHO SENT IT
        // stays its owner. This assertion is what makes that regression impossible here.
        expect(sentBack.data.assignedOfficer).toBe(RBIO_LADDER_ACTORS.RBIO_DEALING_OFFICIAL);
        expect(sentBack.data.assignedOfficer).not.toBe(RBIO_LADDER_ACTORS.RBIO_REVIEWER);
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('a send-back without a comment is refused, and nothing moves', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      const before = await rbioCaseFileGet(request, complaintNumber, 'assignment-history', 'RBIO_REVIEWER');
      const ownerBefore = before.data.find((row: any) => row.current)?.officerId;

      const refused = await rbioLadderAction(
        request, complaintNumber, 'SEND_BACK_DO', 'RBIO_REVIEWER', {}
      );

      // 400, not a 200-with-success:false. The Angular clients' error branch never fires on a 200, so
      // a refusal delivered as 200 renders as a successful send-back.
      expect(refused.status).toBe(400);
      expect(refused.success).toBe(false);
      expect(refused.message.toLowerCase()).toContain('comment');

      // A refused action must leave the complaint untouched — the mandatory-comment check runs before
      // any mutation precisely so this holds.
      const after = await rbioCaseFileGet(request, complaintNumber, 'assignment-history', 'RBIO_REVIEWER');
      expect(after.data.find((row: any) => row.current)?.officerId).toBe(ownerBefore);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a send-back with no previous holder is refused with 422 and demands manual selection',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        // Deliberately NO custody established: nobody has ever held the reviewer rank on this file.
        const refused = await rbioLadderAction(
          request, complaintNumber, 'SEND_BACK_REVIEWER', 'RBIO_OMBUDSMAN',
          { remarks: 'Back to the reviewer please' }
        );

        // 422 rather than 400: the request is well-formed, the STATE cannot satisfy it, and the client
        // must respond by prompting for an officer. Degrading to round-robin here would hand a live
        // case file to an officer the process does not name.
        expect(refused.status).toBe(422);
        expect(refused.success).toBe(false);
        expect(refused.message.toLowerCase()).toMatch(/no previous|select an officer/);

        const target = await rbioCaseFileGet(
          request, complaintNumber, 'last-active-officer', 'RBIO_OMBUDSMAN', '?role=RBIO_REVIEWER'
        );
        expect(target.data.availability).toBe('NO_HISTORY');
        expect(target.data.requiresManualSelection).toBe(true);
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('an explicitly named officer overrides the derived previous holder', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      // This is how the forced-manual-selection pop-up completes: the client retries the same action
      // naming the officer the user picked.
      const sentBack = await rbioLadderAction(
        request, complaintNumber, 'SEND_BACK_DO', 'RBIO_REVIEWER',
        { remarks: 'Reassigning to a different dealing official', targetUser: 'rbio_officer_002' }
      );

      expect(sentBack.success, sentBack.message).toBe(true);
      expect(sentBack.data.assignedOfficer).toBe('rbio_officer_002');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('the custody trail records every holder and leaves exactly one current holder',
    async ({ request }) => {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        await rbioEstablishLadderCustody(request, complaintNumber);
        await rbioLadderAction(
          request, complaintNumber, 'SEND_BACK_DO', 'RBIO_REVIEWER', { remarks: 'Needs more detail' }
        );

        const trail = await rbioCaseFileGet(request, complaintNumber, 'assignment-history', 'RBIO_REVIEWER');
        expect(trail.status).toBe(200);
        expect(Array.isArray(trail.data)).toBe(true);
        expect(trail.data.length).toBeGreaterThanOrEqual(3);

        // The invariant the send-back lookup depends on. Two open rows would make "who held this
        // before" permanently ambiguous; zero would mean the file is owned by nobody.
        expect(trail.data.filter((row: any) => row.current).length).toBe(1);

        const actions = trail.data.map((row: any) => row.assignedByAction);
        expect(actions).toContain('SUBMIT_FOR_REVIEW');
        expect(actions).toContain('SEND_BACK_DO');
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    });

  test('a role that the table does not grant the action cannot perform it', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      // SEND_BACK_REVIEWER is granted to the Deputy and the Ombudsman only — a dealing official
      // cannot send a file back to the rank ABOVE them.
      const refused = await rbioLadderAction(
        request, complaintNumber, 'SEND_BACK_REVIEWER', 'RBIO_DEALING_OFFICIAL',
        { remarks: 'Trying to send upward' }
      );

      expect(refused.success).toBe(false);
      expect(refused.message.toLowerCase()).toContain('not authorized');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
