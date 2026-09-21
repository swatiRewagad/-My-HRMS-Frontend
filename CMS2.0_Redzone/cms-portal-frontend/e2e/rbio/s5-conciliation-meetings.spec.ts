import { test, expect } from './harness';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  rbioLadderAction,
  rbioEstablishLadderCustody,
} from '../utils/test-data';

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

/**
 * Conciliation meetings — S5 (UST496-503, 643-651).
 *
 * <p>These assert at the API level and READ THE PERSISTED ROW BACK rather than trusting a 200. That is
 * deliberate: the defect this batch fixes was a schedule that answered "scheduled successfully" while
 * persisting nothing at all, because the component sent `hearingDate` and the server read `meetingDate`.
 * A test that only checked the response status would have passed against that bug.
 */

/** Reads the meeting history through the API, as the screen does. */
async function meetingHistory(request: any, complaintNumber: string, role = 'RBIO_DEALING_OFFICIAL') {
  const res = await request.get(
    `${API_BASE}/api/v1/complaints/${complaintNumber}/meetings`,
    { headers: { 'X-User-Id': 'rbio_do_001', 'X-User-Name': 'rbio_do_001', 'X-User-Roles': role } }
  );
  expect(res.status(), 'the meetings endpoint must exist').toBe(200);
  const json = await res.json();
  return (json.data ?? []) as any[];
}

test.describe('S5 conciliation meetings', () => {

  test('a meeting cannot be scheduled without a date, a time or participants (UST496)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      // Each mandatory field is refused INDEPENDENTLY, so a single check passing cannot mask the others.
      const noFields = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL', {});
      expect(noFields.success, 'a meeting with no particulars must be refused').toBe(false);
      expect(noFields.message).toContain('rbio.meeting.error.date_required');

      const noTime = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10' });
      expect(noTime.success, 'a meeting with no time must be refused').toBe(false);
      expect(noTime.message).toContain('rbio.meeting.error.time_required');

      const noParticipants = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30' });
      expect(noParticipants.success, 'a meeting with no participants must be refused').toBe(false);
      expect(noParticipants.message).toContain('rbio.meeting.error.participants_required');

      // And nothing was written by any of the three refusals.
      expect(await meetingHistory(request, complaintNumber),
        'a refused meeting must persist no row').toHaveLength(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a scheduled meeting persists its date, time and participants (UST496)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      const res = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH',
          meetingVenue: 'RBI Mumbai Office' });
      expect(res.success, `SCHEDULE_MEETING failed: ${res.message}`).toBe(true);

      // READ IT BACK. This is the assertion the param-name bug would have failed.
      const history = await meetingHistory(request, complaintNumber);
      expect(history).toHaveLength(1);
      expect(history[0].eventType).toBe('SCHEDULED');
      expect(history[0].meetingDate, 'the date must persist, not default').toBe('2026-11-10');
      expect(history[0].meetingTime, 'the time must persist').toBe('11:30');
      expect(history[0].participants).toBe('BOTH');
      expect(history[0].operative).toBe(true);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a date-only value is accepted, not silently discarded', async ({ request }) => {
    // The old arm called LocalDateTime.parse on an <input type=date> value and swallowed the failure at
    // log.debug, so a correctly-named date STILL persisted nothing.
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      const res = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-12-01', meetingTime: '09:00', participants: 'ENTITY' });
      expect(res.success, res.message).toBe(true);

      const history = await meetingHistory(request, complaintNumber);
      expect(history[0].meetingDate).toBe('2026-12-01');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('an invalid time is refused rather than stored', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      const res = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '25:99', participants: 'BOTH' });
      expect(res.success, '25:99 is not a time anybody can attend').toBe(false);
      expect(res.message).toContain('rbio.meeting.error.time_invalid');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('rescheduling requires a reason and RETAINS the previous meeting (UST502)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      await rbioLadderAction(request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH' });

      // UST502: save is blocked until the Reason is completed.
      const noReason = await rbioLadderAction(
        request, complaintNumber, 'RESCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-20', meetingTime: '14:00', participants: 'ENTITY' });
      expect(noReason.success, 'a reschedule with no reason must be refused').toBe(false);
      expect(noReason.message).toContain('rbio.meeting.error.reason_required');

      const ok = await rbioLadderAction(
        request, complaintNumber, 'RESCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-20', meetingTime: '14:00', participants: 'ENTITY',
          rescheduleReason: 'Entity requested a later date due to auditor availability' });
      expect(ok.success, ok.message).toBe(true);

      const history = await meetingHistory(request, complaintNumber);
      expect(history, 'both the original and the reschedule must exist').toHaveLength(2);

      // The ORIGINAL row keeps its own particulars — this is the whole point of UST502.
      const original = history.find(m => m.eventType === 'SCHEDULED');
      expect(original.meetingDate, 'the original date must NOT be overwritten').toBe('2026-11-10');
      expect(original.meetingTime).toBe('11:30');
      expect(original.operative, 'the original is superseded, not deleted').toBe(false);
      expect(original.supersededAt).not.toBeNull();

      const rescheduled = history.find(m => m.eventType === 'RESCHEDULED');
      expect(rescheduled.meetingDate).toBe('2026-11-20');
      expect(rescheduled.operative).toBe(true);
      expect(rescheduled.rescheduleReason).toContain('auditor availability');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('rescheduling does NOT change the complaint status (UST503)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      await rbioLadderAction(request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH' });

      const before = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`);
      const statusBefore = (await before.json()).data?.status;

      const res = await rbioLadderAction(
        request, complaintNumber, 'RESCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-25', meetingTime: '15:00', participants: 'BOTH',
          rescheduleReason: 'Ombudsman unavailable' });
      expect(res.success, res.message).toBe(true);

      const after = await request.get(`${API_BASE}/api/v1/complaints/${complaintNumber}`);
      const statusAfter = (await after.json()).data?.status;

      // Asserted explicitly, as the story demands: a reschedule moves the meeting, not the case.
      expect(statusAfter, 'a reschedule must not move the workflow status').toBe(statusBefore);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('completing a meeting requires entity acceptance AND minutes (UST499)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      await rbioLadderAction(request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH' });

      const noAcceptance = await rbioLadderAction(
        request, complaintNumber, 'COMPLETE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { minutesOfMeeting: 'The parties discussed the disputed debit.' });
      expect(noAcceptance.success, 'completion without acceptance must be refused').toBe(false);
      expect(noAcceptance.message).toContain('rbio.meeting.error.acceptance_required');

      const noMinutes = await rbioLadderAction(
        request, complaintNumber, 'COMPLETE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { entityAccepted: 'Y' });
      expect(noMinutes.success, 'completion without minutes must be refused').toBe(false);
      expect(noMinutes.message).toContain('rbio.meeting.error.minutes_required');

      // Whitespace-only minutes are absent, not present.
      const blankMinutes = await rbioLadderAction(
        request, complaintNumber, 'COMPLETE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { entityAccepted: 'Y', minutesOfMeeting: '     ' });
      expect(blankMinutes.success, 'whitespace-only minutes must be refused').toBe(false);

      const ok = await rbioLadderAction(
        request, complaintNumber, 'COMPLETE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { entityAccepted: 'N', minutesOfMeeting: 'The entity declined the proposed settlement.' });
      expect(ok.success, ok.message).toBe(true);

      const history = await meetingHistory(request, complaintNumber);
      const completed = history.find(m => m.eventType === 'COMPLETED');
      expect(completed).toBeTruthy();
      // 'N' is preserved as a real refusal, not defaulted — "not answered" and "refused" must differ.
      expect(completed.entityAccepted).toBe('N');
      expect(completed.minutesOfMeeting).toContain('declined the proposed settlement');
      // The completed row carries the particulars of the meeting actually held.
      expect(completed.meetingDate).toBe('2026-11-10');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('all four roles can convene a meeting (UST643, 646, 649)', async ({ request }) => {
    // The screen admitted RBIO_CONCILIATOR alone and the server never granted the Reviewer at all, so
    // three of these four could not reach the milestone by any route.
    const roles: Array<'RBIO_DEALING_OFFICIAL' | 'RBIO_REVIEWER' | 'RBIO_DEPUTY_OMBUDSMAN' | 'RBIO_OMBUDSMAN'> =
      ['RBIO_DEALING_OFFICIAL', 'RBIO_REVIEWER', 'RBIO_DEPUTY_OMBUDSMAN', 'RBIO_OMBUDSMAN'];

    for (const role of roles) {
      const { complaintNumber } = await createRbioComplaint(request);
      try {
        await rbioEstablishLadderCustody(request, complaintNumber);
        const res = await rbioLadderAction(
          request, complaintNumber, 'SCHEDULE_MEETING', role,
          { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH' });
        expect(res.success, `${role} must be able to schedule a meeting: ${res.message}`).toBe(true);
      } finally {
        await cleanupRbioComplaint(request, complaintNumber);
      }
    }
  });

  test('a meeting is refused on an excluded status, server-side (UST497)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      // NO ladder custody here, and NO test.skip. WITHDRAWN is reachable directly from the filed state, and
      // establishing custody first moved the complaint somewhere WITHDRAWN was not valid from — which made
      // the precondition fail and the whole assertion vanish behind a skip. A skip is not a pass: the
      // precondition is established rather than hoped for, and if it cannot be, the test FAILS.
      const withdraw = await rbioLadderAction(
        request, complaintNumber, 'WITHDRAWN', 'RBIO_DEPUTY_OMBUDSMAN',
        { remarks: 'Complainant withdrew the complaint in writing.' });
      expect(withdraw.success,
        `could not reach the excluded status, so the exclusion is untested: ${withdraw.message}`).toBe(true);

      // A DIRECT POST, bypassing any browser control. This is exactly the case the old code accepted,
      // because VALID_FROM was advertisement-only and never consulted on execution.
      const res = await rbioLadderAction(
        request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH' });
      expect(res.success, 'a meeting on a withdrawn complaint must be refused by the SERVER').toBe(false);
      expect(res.message).toContain('rbio.meeting.error.status_excluded');

      // And nothing was written.
      expect(await meetingHistory(request, complaintNumber),
        'a refused meeting must persist no row').toHaveLength(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('additional entity participants are capped at six (UST498)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    const headers = {
      'Content-Type': 'application/json',
      'X-User-Id': 'rbio_do_001', 'X-User-Name': 'rbio_do_001',
      'X-User-Roles': 'RBIO_DEALING_OFFICIAL',
    };
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);

      // Six are accepted through the SHARED cap (RbioAdditionalEntityService), not a second counter.
      for (let i = 1; i <= 6; i++) {
        const res = await request.post(
          `${API_BASE}/api/v1/complaints/${complaintNumber}/meetings/participants`,
          { headers, data: { participantName: `Entity ${i}`, participantType: 'ENTITY' } });
        expect(res.status(), `participant ${i} should be accepted`).toBe(200);
      }

      // The seventh is refused with a 409 from the existing cap.
      const seventh = await request.post(
        `${API_BASE}/api/v1/complaints/${complaintNumber}/meetings/participants`,
        { headers, data: { participantName: 'Entity 7', participantType: 'ENTITY' } });
      expect(seventh.status(), 'the seventh entity participant must be refused').toBe(409);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('the MOM letter renders only after a meeting is completed (UST500)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    const headers = { 'X-User-Id': 'rbio_do_001', 'X-User-Name': 'rbio_do_001',
      'X-User-Roles': 'RBIO_DEALING_OFFICIAL' };
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      await rbioLadderAction(request, complaintNumber, 'SCHEDULE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { meetingDate: '2026-11-10', meetingTime: '11:30', participants: 'BOTH',
          meetingVenue: 'RBI Mumbai Office' });

      // Minutes for a meeting that has not happened would be a fabricated record.
      const tooEarly = await request.get(
        `${API_BASE}/api/v1/complaints/${complaintNumber}/meetings/mom-letter`, { headers });
      expect(tooEarly.status(),
        'a MOM letter must not render before the meeting is completed').toBe(409);

      await rbioLadderAction(request, complaintNumber, 'COMPLETE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { entityAccepted: 'Y', minutesOfMeeting: 'The entity agreed to reverse the disputed charge.' });

      const letter = await request.get(
        `${API_BASE}/api/v1/complaints/${complaintNumber}/meetings/mom-letter`, { headers });
      expect(letter.status()).toBe(200);
      expect(letter.headers()['content-type']).toContain('text/html');

      const html = await letter.text();
      expect(html).toContain('MINUTES OF MEETING');
      expect(html).toContain(complaintNumber);
      expect(html, 'the persisted minutes must appear in the letter')
        .toContain('reverse the disputed charge');

      // No unsubstituted placeholder: the legacy templates use single braces and never substitute.
      expect(html, 'the template must be fully rendered').not.toContain('{{');

      // The Scheme name must be correct and must NOT assert a non-existent amendment.
      expect(html).toContain('Reserve Bank - Integrated Ombudsman Scheme, 2021');
      expect(html, 'no fabricated 2026 amendment').not.toContain('as amended 2026');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a meeting cannot be completed when none was scheduled', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioEstablishLadderCustody(request, complaintNumber);
      const res = await rbioLadderAction(
        request, complaintNumber, 'COMPLETE_MEETING', 'RBIO_DEALING_OFFICIAL',
        { entityAccepted: 'Y', minutesOfMeeting: 'Minutes for a meeting that never happened.' });
      expect(res.success, 'minutes without a meeting must be refused').toBe(false);
      expect(res.message).toContain('rbio.meeting.error.nothing_to_complete');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('an unauthorised role cannot convene a meeting', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const res = await request.post(
        `${API_BASE}/api/v1/complaints/${complaintNumber}/meetings/participants`,
        {
          headers: {
            'Content-Type': 'application/json',
            'X-User-Id': 're_nodal_001', 'X-User-Name': 're_nodal_001',
            'X-User-Roles': 'RE_NODAL_OFFICER',
          },
          data: { participantName: 'Unauthorised', participantType: 'ENTITY' },
        });
      expect(res.status(), 'a regulated entity officer must not add meeting participants').toBe(403);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
