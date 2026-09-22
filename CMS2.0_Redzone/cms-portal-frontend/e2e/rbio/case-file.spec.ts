import { test, expect } from './harness';
import {
  createRbioComplaint,
  cleanupRbioComplaint,
  rbioCaseFileGet,
  rbioCaseFilePost,
} from '../utils/test-data';

/**
 * S3 — additional entities, field-level override history, and legal case.
 *
 * ── What was broken ─────────────────────────────────────────────────────────────────────────────
 * All three of these endpoints were PHANTOMS. A grep of cms-backend for `additional-entities`,
 * `action-override` and `legal-case` returned ZERO matches, while the frontend had been calling them
 * since it was written. Each GET's `catchError(() => of(default))` turned the 404 into an empty list or
 * null, so:
 *   - the add-entity form accepted six entities and stored none,
 *   - the History tab rendered "no overrides" for every complaint, always,
 *   - the legal-case form reopened blank after every save.
 * Nothing on screen looked broken, which is why this went unnoticed.
 *
 * ── The six-cap is the important one ────────────────────────────────────────────────────────────
 * rbio-add-entity.component.ts:20 has always had MAX_ADDITIONAL_ENTITIES = 6 and disabled its own
 * control at six. That is an affordance, not a control: it is enforced in the browser, so any direct
 * POST ignores it. The cap test below deliberately bypasses the UI entirely, because that is the only
 * way to prove the RULE exists rather than the button being greyed out.
 */

const ENTITY_FIELDS = {
  entityBranch: 'Test Branch',
  entityType: 'BANK',
  entityCategory: 'SCHEDULED_COMMERCIAL',
};

test.describe('RBIO additional entities — the six cap (S3)', () => {
  test('accepts six entities and refuses the seventh with 409, server-side', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      for (let i = 1; i <= 6; i++) {
        const added = await rbioCaseFilePost(request, complaintNumber, 'additional-entities', {
          entityName: `Additional Entity ${i}`,
          ...ENTITY_FIELDS,
        });
        expect(added.status, `entity ${i} should be created: ${added.message}`).toBe(201);
      }

      const seventh = await rbioCaseFilePost(request, complaintNumber, 'additional-entities', {
        entityName: 'Additional Entity 7',
        ...ENTITY_FIELDS,
      });

      // 409 rather than 400: the request is well-formed, it conflicts with the current state.
      expect(seventh.status).toBe(409);
      expect(seventh.message).toContain('6');

      // The refusal must also not have stored anything — a cap that rejects the response but keeps the
      // row would be worse than no cap at all.
      const after = await rbioCaseFileGet(request, complaintNumber, 'additional-entities');
      expect(after.data.length).toBe(6);
      expect(after.meta.used).toBe(6);
      expect(after.meta.canAddMore).toBe(false);
      expect(after.meta.remaining).toBe(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('reports remaining capacity so the UI can disable its control honestly', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const empty = await rbioCaseFileGet(request, complaintNumber, 'additional-entities');
      expect(empty.meta.used).toBe(0);
      expect(empty.meta.maximum).toBe(6);
      expect(empty.meta.remaining).toBe(6);
      expect(empty.meta.canAddMore).toBe(true);

      await rbioCaseFilePost(request, complaintNumber, 'additional-entities', {
        entityName: 'Solo Entity',
        ...ENTITY_FIELDS,
      });

      const one = await rbioCaseFileGet(request, complaintNumber, 'additional-entities');
      expect(one.meta.used).toBe(1);
      expect(one.meta.remaining).toBe(5);
      expect(one.meta.canAddMore).toBe(true);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('mandatory fields are enforced on the SERVER, not just marked red (UST478)', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      // Each of the four fields mirrors the primary entity's mandatory set (UST487).
      for (const missing of ['entityName', 'entityBranch', 'entityType', 'entityCategory']) {
        const body: Record<string, unknown> = { entityName: 'Partial Entity', ...ENTITY_FIELDS };
        delete body[missing];

        const refused = await rbioCaseFilePost(request, complaintNumber, 'additional-entities', body);
        expect(refused.status, `omitting ${missing} must be refused`).toBe(400);
        expect(refused.message).toContain(missing);
      }

      const still = await rbioCaseFileGet(request, complaintNumber, 'additional-entities');
      expect(still.data.length, 'no partial entity may have been stored').toBe(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('the same entity cannot be recorded twice on one complaint', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const first = await rbioCaseFilePost(request, complaintNumber, 'additional-entities', {
        entityName: 'Duplicate Bank Ltd',
        ...ENTITY_FIELDS,
      });
      expect(first.status).toBe(201);

      // Case-insensitively: the same entity typed with different capitalisation is the same entity, and
      // letting it through would also let a complaint exceed six DISTINCT entities.
      const dupe = await rbioCaseFilePost(request, complaintNumber, 'additional-entities', {
        entityName: 'duplicate bank ltd',
        ...ENTITY_FIELDS,
      });
      expect(dupe.status).toBe(409);

      const after = await rbioCaseFileGet(request, complaintNumber, 'additional-entities');
      expect(after.data.length).toBe(1);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a role outside DO/Reviewer/Deputy cannot add an entity', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      // A conciliator holds a stage, not a rank on the assessing ladder, so UST487-495 does not grant it.
      const refused = await rbioCaseFilePost(
        request, complaintNumber, 'additional-entities',
        { entityName: 'Unauthorised Entity', ...ENTITY_FIELDS },
        'RBIO_ADJUDICATOR'
      );
      expect(refused.status).toBe(403);

      const after = await rbioCaseFileGet(request, complaintNumber, 'additional-entities');
      expect(after.data.length).toBe(0);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});

test.describe('RBIO field-level override history (S3)', () => {
  test('records an override and returns it in the shape the History tab reads', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const empty = await rbioCaseFileGet(request, complaintNumber, 'action-override');
      expect(empty.status).toBe(200);
      expect(empty.data).toEqual([]);

      const recorded = await rbioCaseFilePost(request, complaintNumber, 'action-override', {
        fieldName: 'Proposed Action',
        oldValue: 'Advisory',
        newValue: 'Conciliation',
      });
      expect(recorded.status).toBe(201);

      const history = await rbioCaseFileGet(request, complaintNumber, 'action-override');
      expect(history.data.length).toBe(1);

      const row = history.data[0];
      // Field names are asserted individually because the frontend's ActionOverride interface reads
      // exactly these. `timestamp` in particular is NOT `overriddenAt` — renaming it would leave the
      // History tab showing blank dates while every other assertion still passed.
      expect(row.fieldName).toBe('Proposed Action');
      expect(row.oldValue).toBe('Advisory');
      expect(row.newValue).toBe('Conciliation');
      expect(row.timestamp).toBeTruthy();
      // Authorship comes from the resolved identity, not from whatever the client claimed.
      expect(row.overriddenBy).toBe('rbio_do_001');
      expect(row.overriddenByRole).toBe('RBIO_DEALING_OFFICIAL');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('preserves the original when a later role overrides the same field again', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      await rbioCaseFilePost(request, complaintNumber, 'action-override', {
        fieldName: 'Proposed Clause',
        oldValue: '',
        newValue: '15(1)(a)',
      }, 'RBIO_DEALING_OFFICIAL');

      await rbioCaseFilePost(request, complaintNumber, 'action-override', {
        fieldName: 'Proposed Clause',
        oldValue: '15(1)(a)',
        newValue: '15(1)(b)',
      }, 'RBIO_DEPUTY_OMBUDSMAN');

      const history = await rbioCaseFileGet(request, complaintNumber, 'action-override');
      expect(history.data.length).toBe(2);

      // Newest first, and the EARLIER decision must still be readable — that is the entire point of the
      // record. An in-place update would leave only the Deputy's value and erase the fact that they
      // departed from the dealing official's recommendation.
      expect(history.data[0].newValue).toBe('15(1)(b)');
      expect(history.data[0].overriddenByRole).toBe('RBIO_DEPUTY_OMBUDSMAN');
      expect(history.data[1].newValue).toBe('15(1)(a)');
      expect(history.data[1].overriddenByRole).toBe('RBIO_DEALING_OFFICIAL');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('a no-change edit is accepted but not stored', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      // The client fires on every field blur, so equal values must not be refused (that would surface an
      // error for something the user did not do) nor stored (that would bury real overrides under
      // "X → X" rows).
      const noop = await rbioCaseFilePost(request, complaintNumber, 'action-override', {
        fieldName: 'Proposed Action',
        oldValue: 'Advisory',
        newValue: 'Advisory',
      });
      expect(noop.status).toBe(200);

      const history = await rbioCaseFileGet(request, complaintNumber, 'action-override');
      expect(history.data).toEqual([]);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});

test.describe('RBIO legal case (S3, UST553)', () => {
  test('saves and reloads a legal case — the form no longer reopens blank', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const before = await rbioCaseFileGet(request, complaintNumber, 'legal-case');
      expect(before.status).toBe(200);
      expect(before.data).toBeNull();

      const saved = await rbioCaseFilePost(request, complaintNumber, 'legal-case', {
        caseNumber: 'WP/1234/2026',
        courtName: 'Bombay High Court',
        caseStatus: 'PENDING',
        filingDate: '2026-05-01',
        nextHearingDate: '2026-10-15',
        remarks: 'Writ petition filed by the complainant',
      });
      expect(saved.status).toBe(200);

      const after = await rbioCaseFileGet(request, complaintNumber, 'legal-case');
      expect(after.data).not.toBeNull();
      expect(after.data.caseNumber).toBe('WP/1234/2026');
      expect(after.data.courtName).toBe('Bombay High Court');
      expect(after.data.filingDate).toBe('2026-05-01');
      expect(after.data.nextHearingDate).toBe('2026-10-15');
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('refuses a hearing date that precedes the filing date', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const refused = await rbioCaseFilePost(request, complaintNumber, 'legal-case', {
        caseNumber: 'WP/9999/2026',
        courtName: 'Delhi High Court',
        filingDate: '2026-06-01',
        nextHearingDate: '2026-05-01',
      });
      expect(refused.status).toBe(400);

      const after = await rbioCaseFileGet(request, complaintNumber, 'legal-case');
      expect(after.data, 'an impossible legal-case record must not be stored').toBeNull();
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });

  test('requires the case number and court name', async ({ request }) => {
    const { complaintNumber } = await createRbioComplaint(request);
    try {
      const noCase = await rbioCaseFilePost(request, complaintNumber, 'legal-case', {
        courtName: 'Madras High Court',
      });
      expect(noCase.status).toBe(400);

      const noCourt = await rbioCaseFilePost(request, complaintNumber, 'legal-case', {
        caseNumber: 'WP/1/2026',
      });
      expect(noCourt.status).toBe(400);
    } finally {
      await cleanupRbioComplaint(request, complaintNumber);
    }
  });
});
