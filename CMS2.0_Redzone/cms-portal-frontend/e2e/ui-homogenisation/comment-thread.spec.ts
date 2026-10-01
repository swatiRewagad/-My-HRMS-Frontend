import { test, expect } from '@playwright/test';
import { createTestComplaint, identityHeadersFor } from '../utils/test-data';

/**
 * The three-tier staff comment thread.
 *
 * WHAT THIS SPEC IS ACTUALLY PROTECTING: before this feature, `rbio-complaint-detail` rendered a
 * comment thread that was two hardcoded strings and a textarea bound to a local signal. It looked
 * like it worked. An officer could type, see their comment appear, and lose it on navigation. So the
 * assertions below are deliberately about PERSISTENCE and READERSHIP, not about markup — a spec that
 * only checked "a comment appears after posting" would have passed against the mock.
 *
 * The negative cases matter more than the positive ones. A visibility control that fails open is worse
 * than no visibility control, because officers will write candidly into a box they believe is private.
 */

const API = process.env.API_BASE_URL || 'http://localhost:8082';

function commentsUrl(complaintNumber: string): string {
  return `${API}/api/v1/complaint-comments/complaint/${complaintNumber}`;
}

test.describe('comment thread — readership', () => {

  test('a PRIVATE comment is invisible to another officer, including a supervisor', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    const posted = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Private working note — my own reasoning, not for the file.', visibility: 'PRIVATE' },
    });
    expect(posted.status()).toBe(201);

    const authorView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO'),
    });
    expect(authorView.ok()).toBeTruthy();
    const authorBodies = (await authorView.json()).comments.map((c: any) => c.body);
    expect(authorBodies).toContain('Private working note — my own reasoning, not for the file.');

    // A supervisor outranks the author and still must not see it. PRIVATE is not "private unless
    // someone senior asks"; if it were, the tier would be worthless as a thinking space.
    const supervisorView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_supervisor_001', 'RBIO'),
    });
    expect(supervisorView.ok()).toBeTruthy();
    const supervisorBodies = (await supervisorView.json()).comments.map((c: any) => c.body);
    expect(supervisorBodies).not.toContain('Private working note — my own reasoning, not for the file.');
  });

  test('a RESTRICTED comment reaches the named role and nobody else', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    const posted = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: {
        body: 'For the supervisor only: the entity response contradicts their own statement.',
        visibility: 'RESTRICTED',
        restrictedToRoles: ['RBIO_SUPERVISOR'],
      },
    });
    expect(posted.status()).toBe(201);

    const supervisorView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_supervisor_001', 'RBIO'),
    });
    const supervisorBodies = (await supervisorView.json()).comments.map((c: any) => c.body);
    expect(supervisorBodies).toContain(
      'For the supervisor only: the entity response contradicts their own statement.');

    // A CEPC officer is staff, so they pass the staff gate — and must still be excluded, because the
    // restriction is the control, not the staff gate. This is the case a denylist would have missed.
    const cepcView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('cepc_do_001', 'CEPC'),
    });
    expect(cepcView.ok()).toBeTruthy();
    const cepcBodies = (await cepcView.json()).comments.map((c: any) => c.body);
    expect(cepcBodies).not.toContain(
      'For the supervisor only: the entity response contradicts their own statement.');
  });

  test('a PUBLIC comment reaches staff in a different office', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Entity has confirmed the refund was reversed on 12 Sep.', visibility: 'PUBLIC' },
    });

    const cepcView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('cepc_do_001', 'CEPC'),
    });
    const bodies = (await cepcView.json()).comments.map((c: any) => c.body);
    expect(bodies).toContain('Entity has confirmed the refund was reversed on 12 Sep.');
  });

  test('a RESTRICTED comment naming nobody stays with its author', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    // The composer refuses this, so it can only arrive from a direct API call. It is asserted anyway:
    // the UI guard is a convenience and the server is the control. Note this INVERTS the
    // ClosureClauseMaster precedent where a blank restriction list means unrestricted — failing open
    // there publishes a clause, failing open here publishes an officer's candid assessment.
    await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Restricted to nobody in particular.', visibility: 'RESTRICTED', restrictedToRoles: [] },
    });

    const otherView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_supervisor_001', 'RBIO'),
    });
    const bodies = (await otherView.json()).comments.map((c: any) => c.body);
    expect(bodies).not.toContain('Restricted to nobody in particular.');
  });

  test('an RE identity is refused the thread outright, at every tier', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Internal assessment of the entity conduct.', visibility: 'PUBLIC' },
    });

    // The regulated entity is the SUBJECT of these comments. PUBLIC means public-to-RBI-staff, and an
    // RE nodal officer is not RBI staff however authenticated they are.
    const reView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('re_nodal_001', 'RE'),
    });
    expect(reView.status()).toBe(403);
  });
});

test.describe('comment thread — threading and editing', () => {

  test('a reply inherits its parent tier rather than taking the one it was sent with', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    const parent = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: {
        body: 'Restricted parent.',
        visibility: 'RESTRICTED',
        restrictedToRoles: ['RBIO_SUPERVISOR'],
      },
    });
    const parentId = (await parent.json()).commentId;

    // A PUBLIC reply under a RESTRICTED parent would quote the restricted substance into the open
    // thread, so the server ignores the tier the client sent and inherits instead.
    const reply = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_supervisor_001', 'RBIO') },
      data: { body: 'Agreed, pursue it.', visibility: 'PUBLIC', parentId },
    });
    expect(reply.status()).toBe(201);
    expect((await reply.json()).visibility).toBe('RESTRICTED');

    const cepcView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('cepc_do_001', 'CEPC'),
    });
    const flattened = JSON.stringify((await cepcView.json()).comments);
    expect(flattened).not.toContain('Agreed, pursue it.');
  });

  test('replies arrive nested under their parent, not as top-level rows', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    const parent = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Top level.', visibility: 'PUBLIC' },
    });
    const parentId = (await parent.json()).commentId;

    await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_supervisor_001', 'RBIO') },
      data: { body: 'Nested reply.', visibility: 'PUBLIC', parentId },
    });

    const view = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO'),
    });
    const comments = (await view.json()).comments;
    // The client does no parent/child stitching, so a flat list would render the reply as a sibling.
    expect(comments).toHaveLength(1);
    expect(comments[0].body).toBe('Top level.');
    expect(comments[0].replies.map((r: any) => r.body)).toEqual(['Nested reply.']);
  });

  test('only the author may edit, and editable says so before they try', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    const posted = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Original wording.', visibility: 'PUBLIC' },
    });
    const commentId = (await posted.json()).commentId;

    // `editable` is server-computed so the pencil is not offered on a comment the save would refuse.
    const otherView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_supervisor_001', 'RBIO'),
    });
    const seenByOther = (await otherView.json()).comments.find((c: any) => c.id === commentId);
    expect(seenByOther.editable).toBe(false);

    const refused = await request.put(`${API}/api/v1/complaint-comments/${commentId}`, {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_supervisor_001', 'RBIO') },
      data: { body: 'Rewritten by someone else.' },
    });
    expect(refused.status()).toBe(403);

    const accepted = await request.put(`${API}/api/v1/complaint-comments/${commentId}`, {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'Corrected wording.' },
    });
    expect(accepted.ok()).toBeTruthy();

    const authorView = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO'),
    });
    const edited = (await authorView.json()).comments.find((c: any) => c.id === commentId);
    expect(edited.body).toBe('Corrected wording.');
    // The edited marker in the UI is driven by updatedAt, so it has to be populated.
    expect(edited.updatedAt).not.toBeNull();
  });

  test('a comment persists across a fresh request, which the mock thread never did', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: 'This must survive a reload.', visibility: 'PUBLIC' },
    });

    const reread = await request.get(commentsUrl(complaint.complaintNumber), {
      headers: identityHeadersFor('rbio_officer_001', 'RBIO'),
    });
    const bodies = (await reread.json()).comments.map((c: any) => c.body);
    expect(bodies).toContain('This must survive a reload.');
  });

  test('an empty body is refused rather than stored blank', async ({ request }) => {
    const complaint = await createTestComplaint(request);

    const refused = await request.post(commentsUrl(complaint.complaintNumber), {
      headers: { 'Content-Type': 'application/json', ...identityHeadersFor('rbio_officer_001', 'RBIO') },
      data: { body: '   ', visibility: 'PUBLIC' },
    });
    expect(refused.status()).toBe(400);
  });
});
