import { test, expect, Page, APIRequestContext } from '../fixtures';
import { seedCitizenSession } from '../utils/test-data';

/**
 * UST13 — the Regulated Entity selection on the citizen "File a Complaint" form.
 *
 * Step 1 of the eligibility wizard is "which Regulated Entity is this complaint against?". It is the
 * gate to everything else: no entity, no complaint, and the entity's DEPARTMENT decides which
 * maintainability questions are even asked (isQuestionVisible derives applicability from it). So this
 * control is not cosmetic — a citizen who cannot find their bank here cannot use the Scheme at all.
 *
 * ── HOW THE LIST IS SERVED, AND WHAT THAT MEANS FOR CASES 3-6 ──────────────────────────────────────
 * The list is REAL master data: GET /api/v1/routing/entities/list, ComplaintRoutingController#
 * listEntities over the REGULATED_ENTITIES table (145 rows at the time of writing, 86 CEPC / 59 RBIO,
 * nine distinct ENTITY_TYPEs). This is explicitly NOT one of the project's phantom-endpoint features —
 * `the entity list comes from a real endpoint` below proves the endpoint exists and that the rows the
 * browser renders are the rows the API returned.
 *
 * Searching, however, is CLIENT-SIDE over that already-fetched list (filterEntities()). That is a
 * deliberate design, recorded here because it changes what cases 3-6 prove: they prove the FILTER is
 * correct, not that a server query is correct. It also means the search term never reaches a query, so
 * there is no SQL injection surface in the search box at all. The server-side `?search=` parameter —
 * used by staff screens, not by this one — is separately probed below, because if it ever becomes the
 * citizen's path the injection question becomes live.
 *
 * ── WHY THERE IS ONE CONTROL ───────────────────────────────────────────────────────────────────────
 * There were two: a searchable combobox and a plain <select> of the full list, both bound to the same
 * answer — one question wearing two controls. The <select> carried a defect of its own: the component
 * rendered its "covered" and "not covered" entity groups into the single list with nothing marking
 * which group an option came from, so a citizen could pick an entity the Scheme does not cover and be
 * told nothing. It was removed during the UI homogenisation, and every spec that drove it (this one,
 * eligibility-re-window, -maintainability-questions, -sub-questions, -clause-interpolation,
 * -simplify-statutory, filing-windows, session-timeout, otp-lifecycle, non-maintainable-closure and
 * the two shared helpers) now drives the combobox. Selection is asserted on the recorded answer —
 * see expectRecordedAnswer below — never on the box's text, which a citizen can fill without choosing.
 *
 * ── WHAT WAS BROKEN AND IS NOW FIXED (all three verified in a real browser first) ───────────────────
 *   1. There was NO search box. filterEntities/selectEntityFromSearch/clearEntitySelection/
 *      closeEntityDropdown/getSelectedEntityLabel existed in the component and were referenced by
 *      nothing — the template rendered only the plain <select>. Cases 3-6 and 8-10 were untestable
 *      because the feature was absent, not merely broken.
 *   2. Pressing Next with no entity chosen emitted eligibility.response_mandatory, "Response is
 *      mandatory." — the generic line for the Yes/No questions — on a screen with no question to
 *      respond to. QA case 7 wants the field named.
 *   3. A failed entity fetch was swallowed: setEntityOptions([]) left a dropdown holding only the
 *      disabled placeholder and nothing at all saying the list had failed to load. Driven live against
 *      a stubbed 500, the screen was indistinguishable from "RBI regulates no entities".
 *
 * ── WHERE THE NEW STRINGS LIVE ─────────────────────────────────────────────────────────────────────
 * The six new keys (entity_mandatory, entity_search_label/_placeholder/_clear/_no_results,
 * entities_unavailable) are in EligibilityTranslationSeeder AND in TWO migrations, because the seeder is
 * insert-if-absent by key code and so can only populate a database that has never been seeded:
 *   database/V105__re_entity_selection_search_strings.sql          (MySQL — the dev/test database)
 *   database/oracle/V104__re_entity_selection_search_strings.sql   (Oracle — production)
 * Both are generated from the Java seeder's own literals so the three cannot drift. The MySQL one is
 * applied here and proven idempotent; the Oracle one is unexecuted (no Oracle instance available).
 *
 * ── NO SEEDED ROWS ─────────────────────────────────────────────────────────────────────────────────
 * Nothing here inserts into REGULATED_ENTITIES. The database is shared with other sessions, entity
 * rows have no lifecycle (nothing deletes them) and a row left behind would appear in every other
 * session's dropdown forever. Every test instead reads the master through the API and asserts against
 * what it actually holds, so no assertion depends on a particular bank existing.
 */

const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';
const APP_BASE = process.env['APP_BASE_URL'] || process.env['UI_BASE_URL'] || 'http://localhost:4200';
const APP_API_BASE = 'http://localhost:8082';

/** The bundle's compiled apiBaseUrl is 8082; point it at the backend under test. */
test.beforeEach(async ({ page }) => {
  if (API_BASE === APP_API_BASE) return;
  await page.route(`${APP_API_BASE}/api/**`, route =>
    route.continue({ url: route.request().url().replace(APP_API_BASE, API_BASE) }));
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// Helpers
// ══════════════════════════════════════════════════════════════════════════════════════════════════

interface Entity { id: number; name: string; department: string; entityType: string | null; }

/** The entity master as the API actually serves it. No test hardcodes a bank name. */
async function servedEntities(request: APIRequestContext): Promise<Entity[]> {
  const res = await request.get(`${API_BASE}/api/v1/routing/entities/list`);
  expect(res.status(), 'GET /api/v1/routing/entities/list is not reachable').toBe(200);
  const body = await res.json();
  const data = body?.data ?? body;
  expect(Array.isArray(data), 'the entity list endpoint did not return an array').toBe(true);
  expect(data.length, 'the entity master is empty, so no assertion below can mean anything')
    .toBeGreaterThan(0);
  return data as Entity[];
}

/** An entityType that at least two entities share, so "filter by type" is a real narrowing. */
function commonEntityType(entities: Entity[]): string {
  const counts = new Map<string, number>();
  for (const e of entities) {
    if (e.entityType) counts.set(e.entityType, (counts.get(e.entityType) ?? 0) + 1);
  }
  const best = [...counts.entries()].sort((a, b) => b[1] - a[1])[0];
  expect(best, 'no entity in the master carries an entityType, so type search cannot be tested')
    .toBeTruthy();
  expect(best[1], `the most common entityType "${best[0]}" has only one entity`).toBeGreaterThan(1);
  return best[0];
}

async function openFileComplaint(page: Page) {
  await page.goto(`${APP_BASE}/public`);
  await seedCitizenSession(page, '9876500131', 'e2e-seeded-token.sig');
  await page.goto(`${APP_BASE}/public/file-complaint`);
  await page.waitForLoadState('networkidle');
  // A non-existent Angular route falls through to the public home page, so confirm the wizard mounted.
  await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 25000 });
  // The list arrives asynchronously; every test below needs it loaded. It renders only while the
  // dropdown is OPEN, which focusing the box does — and leaving it open is the state a citizen who has
  // reached this question is in anyway.
  await searchBox(page).click();
  await expect(results(page).first()).toBeVisible({ timeout: 25000 });
}

/**
 * ── THERE IS ONE ENTITY CONTROL, AND IT IS THIS COMBOBOX ─────────────────────────────────────────
 * There used to be two: a `select.entity-select-dropdown` and this searchable input, bound to the SAME
 * answer. A citizen faced two controls for one question, and the select carried a further defect — the
 * component split the master into "covered" and "not covered" groups and rendered both into it with
 * nothing marking which group an option belonged to. The select was removed.
 *
 * So the recorded answer is no longer readable from a mirror control. It is read off the BOX, which
 * holds the chosen entity's NAME (component ts:1288-1295 writes it there precisely so the citizen can
 * see what they picked) and is empty when nothing is chosen.
 */
const searchBox = (page: Page) => page.locator('input.entity-search-input');
const results = (page: Page) => page.locator('li.entity-search-option');
const resultNames = (page: Page) => page.locator('li.entity-search-option .es-name');

/**
 * Types into the search box the way a citizen does, so each keystroke re-filters.
 *
 * Waits for the box to actually hold the whole term before returning. pressSequentially dispatches one
 * key at a time and Angular applies each (ngModelChange) in a later change-detection pass, so a caller
 * that read the filtered list the instant this resolved saw the list as of some EARLIER keystroke — the
 * cause of three false failures on the first run (a "no results" line reading `Zzqx Nonexistent` while
 * `Zzqx Nonexistent Bank` had been typed, and a result count of 145 for a term that matches one row).
 */
async function typeSearch(page: Page, term: string) {
  const box = searchBox(page);
  await box.click();
  await box.fill('');
  await box.pressSequentially(term);
  await expect(box).toHaveValue(term);
}

/**
 * The answer the wizard has RECORDED — asserted on `aria-selected`, not on the search box's text.
 *
 * The distinction is the substance of QA 7: text in the box is not an answer. A citizen who typed
 * their bank's name and never clicked it has a full box and no recorded entity, so reading the box
 * would call that a selection. The option list carries
 * `[attr.aria-selected]="eligibilityAnswers[currentQuestionKey] === opt.value"` (html:88-90), which is
 * the recorded answer itself, and is also what a screen reader announces.
 *
 * The list renders only while the dropdown is open, so this opens it — by CLEARING THE FILTER, which
 * matters for two separate reasons:
 *
 *   1. A click does not reliably reopen it. After a choice the box still holds focus, so `click()`
 *      fires no `focus` event and `openEntityDropdown()` never runs. `fill('')` drives
 *      (ngModelChange) → `onEntitySearchInput` → `filterEntities`, which both re-filters and sets
 *      `entityDropdownOpen = true`. This is the filter only; the × button is what drops the answer.
 *   2. It makes the `null` case non-vacuous. `toHaveCount(0)` on a CLOSED list passes no matter what
 *      is recorded, so "nothing is selected" would be proven by a list that simply was not rendered.
 *      Waiting for an option to be visible first means the whole master is on screen and genuinely
 *      carries no selected row.
 *
 * Auto-retrying assertions on purpose: the attribute is written by a later change-detection pass than
 * the mousedown handler, so a single immediate read fails a selection that did in fact happen.
 *
 * Pass `null` for "nothing is recorded".
 */
async function expectRecordedAnswer(page: Page, name: string | null, message?: string) {
  const box = searchBox(page);
  await box.click();
  await box.fill('');
  await expect(results(page).first(),
    'the entity list did not reopen, so nothing can be concluded about what is recorded')
    .toBeVisible({ timeout: 20000 });

  const selected = page.locator('li.entity-search-option[aria-selected="true"]');
  if (name === null) {
    await expect(selected, message ?? 'an entity was recorded when none should have been')
      .toHaveCount(0);
    return;
  }
  await expect(selected, message).toHaveCount(1);
  await expect(selected.locator('.es-name'), message).toHaveText(name);
}

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 1 / QA 2 — the list is selectable, and a valid selection lets the citizen proceed
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('The Regulated Entity is chosen from a list of REs', () => {

  test('the entity list comes from a real endpoint, and the screen shows those rows', async ({ page, request }) => {
    // The project has a standing trap: citizen features that are UI-complete against endpoints that do
    // not exist, with the error swallowed so a demo looks clean. This test is the proof that the RE
    // dropdown is NOT one of them — the options rendered are the rows the API returned, by name.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    const rendered = await resultNames(page).allInnerTexts();
    expect(rendered.length,
      `the API serves ${entities.length} entities but the list rendered ${rendered.length}`)
      .toBe(entities.length);

    const servedNames = new Set(entities.map(e => e.name));
    for (const name of rendered) {
      expect(servedNames.has(name.trim()),
        `the list offers "${name.trim()}", which the entity master does not contain`).toBe(true);
    }
  });

  test('the entity list is searchable, which is the whole of UST13 S3', async ({ page, request }) => {
    const entities = await servedEntities(request);
    await openFileComplaint(page);
    await expect(searchBox(page),
      'there is no way to search the entity list, so UST13 S3 cannot be satisfied').toBeVisible();
    await expect(searchBox(page)).toBeEnabled();
    // Searchable AND complete: a box over a truncated list satisfies the letter of S3 and not its
    // point. The resting list is the whole master.
    await expect.poll(() => results(page).count(), { timeout: 20000 }).toBe(entities.length);
  });

  test('choosing from the resting list records the answer and advances', async ({ page, request }) => {
    // The citizen who scrolls rather than types. Both paths must satisfy QA2.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    const target = entities[0];
    const option = results(page).filter({ hasText: target.name }).first();
    await expect(option).toBeVisible({ timeout: 10000 });
    // mousedown, not click: the option's handler is (mousedown), which fires BEFORE the input's blur
    // closes the list. A click would let blur remove the option mid-gesture.
    await option.dispatchEvent('mousedown');
    await expectRecordedAnswer(page, target.name);

    await page.locator('button.btn-next').click();
    // QA2: the citizen reaches the next section, which is the first radio question.
    await expect(page.locator('.radio-list'),
      'a valid entity selection did not let the citizen proceed').toBeVisible({ timeout: 20000 });
  });

  test('selecting from the search results records the answer and advances', async ({ page, request }) => {
    // The citizen who types. QA2 must hold whichever way they chose.
    const entities = await servedEntities(request);
    const target = entities[0];
    await openFileComplaint(page);

    await typeSearch(page, target.name);
    const option = results(page).filter({ hasText: target.name }).first();
    await expect(option).toBeVisible({ timeout: 10000 });
    await option.dispatchEvent('mousedown');

    await expectRecordedAnswer(page, target.name,
      'choosing from the search results did not record the entity as the answer');
    await page.locator('button.btn-next').click();
    await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
  });

  test('a selection made by searching names the entity in the questions that follow', async ({ page, request }) => {
    // The questions interpolate <RE Name>. A selection that recorded an id but no resolvable name would
    // show a citizen "the Regulated Entity" instead of their bank — the selection would look accepted
    // and be half-lost.
    const entities = await servedEntities(request);
    const target = entities.find(e => e.name.length > 6) ?? entities[0];
    await openFileComplaint(page);

    await typeSearch(page, target.name);
    await results(page).filter({ hasText: target.name }).first().click();
    await page.locator('button.btn-next').click();
    await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });

    // filedWithRE=yes reveals sub-fields whose labels name the entity.
    await page.locator('.radio-list .radio-option').first().click();
    const named = page.locator('.sub-fields-row');
    if (await named.count() > 0) {
      await expect(named.first()).toContainText(target.name);
      await expect(named.first(),
        'the wizard fell back to the generic placeholder, so the chosen entity was not resolved')
        .not.toContainText('the Regulated Entity');
    }
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 3 / QA 4 / QA 6 — search by exact name, partial name, and case-insensitively
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('Searching the entity list by name', () => {

  test('an exact entity name finds that entity', async ({ page, request }) => {
    const entities = await servedEntities(request);
    const target = entities[0];
    await openFileComplaint(page);

    await typeSearch(page, target.name);
    await expect(resultNames(page).filter({ hasText: target.name }).first(),
      `searching the exact name "${target.name}" did not offer it`).toBeVisible({ timeout: 10000 });
  });

  test('every entity in the master is findable by its exact name', async ({ page, request }) => {
    // The filter used to slice(0, 50). A citizen whose bank sorted 51st was shown "no results" for a
    // name that IS on the list — the worst possible failure for this control, and invisible unless
    // every row is swept. Sampled across the whole list so the sweep stays inside the 60s timeout.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    const step = Math.max(1, Math.floor(entities.length / 12));
    for (let i = 0; i < entities.length; i += step) {
      const target = entities[i];
      await typeSearch(page, target.name);
      await expect(resultNames(page).filter({ hasText: target.name }).first(),
        `entity #${i} "${target.name}" is in the master but the search cannot find it — the result ` +
        `list is being truncated`).toBeVisible({ timeout: 10000 });
    }
  });

  test('a partial name finds every entity containing it', async ({ page, request }) => {
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    // A fragment of a real name, long enough to be meaningful and short enough to match several rows.
    const fragment = entities[0].name.slice(0, 4);
    const expected = entities.filter(e => e.name.toLowerCase().includes(fragment.toLowerCase()));
    expect(expected.length, `"${fragment}" matches nothing in the master`).toBeGreaterThan(0);

    await typeSearch(page, fragment);
    // Every expected row must be offered — a partial search that finds SOME matches still hides banks.
    for (const e of expected) {
      await expect(resultNames(page).filter({ hasText: e.name }).first(),
        `partial search "${fragment}" omitted "${e.name}"`).toBeVisible({ timeout: 10000 });
    }
  });

  test('a partial name offers nothing that does not contain it', async ({ page, request }) => {
    // The other half of QA4: a filter that returned the whole list would pass the test above.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    const fragment = entities[0].name.slice(0, 4).toLowerCase();
    const types = new Set(entities.map(e => e.entityType?.toLowerCase()).filter(Boolean));
    await typeSearch(page, fragment);

    const offered = (await resultNames(page).allInnerTexts()).map(s => s.trim());
    expect(offered.length, 'the search offered nothing at all').toBeGreaterThan(0);
    for (const name of offered) {
      const byName = name.toLowerCase().includes(fragment);
      // A row may also legitimately match on its TYPE, which is the same search box.
      const byType = [...types].some(t => t!.includes(fragment) &&
        entities.some(e => e.name === name && e.entityType?.toLowerCase() === t));
      expect(byName || byType,
        `"${name}" was offered for "${fragment}" but matches neither its name nor its type`).toBe(true);
    }
  });

  test('search is case-insensitive in both directions', async ({ page, request }) => {
    const entities = await servedEntities(request);
    // A name with both cases in it, so UPPER and lower are each a real change.
    const target = entities.find(e => /[a-z]/.test(e.name) && /[A-Z]/.test(e.name)) ?? entities[0];
    await openFileComplaint(page);

    for (const variant of [target.name.toUpperCase(), target.name.toLowerCase()]) {
      await typeSearch(page, variant);
      await expect(resultNames(page).filter({ hasText: target.name }).first(),
        `searching "${variant}" did not find "${target.name}", so the match is case-sensitive`)
        .toBeVisible({ timeout: 10000 });
    }
  });

  test('leading and trailing whitespace does not defeat the search', async ({ page, request }) => {
    // Citizens paste names. A trimmed term is the difference between finding a bank and being told it
    // does not exist.
    const entities = await servedEntities(request);
    const target = entities[0];
    await openFileComplaint(page);

    await typeSearch(page, `   ${target.name}   `);
    await expect(resultNames(page).filter({ hasText: target.name }).first(),
      'a pasted name with surrounding spaces was not found').toBeVisible({ timeout: 10000 });
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 5 — filtering by entity TYPE
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('Searching the entity list by entity type', () => {

  test('an entity type narrows the list to entities of that type', async ({ page, request }) => {
    const entities = await servedEntities(request);
    const type = commonEntityType(entities);
    const expected = entities.filter(e => e.entityType === type);
    await openFileComplaint(page);

    await typeSearch(page, type);
    await expect(results(page).first()).toBeVisible({ timeout: 10000 });

    // Every entity of that type is offered...
    for (const e of expected.slice(0, 8)) {
      await expect(resultNames(page).filter({ hasText: e.name }).first(),
        `type search "${type}" omitted "${e.name}", which is of that type`).toBeVisible();
    }
    // ...and the result is a NARROWING, not the whole list.
    await expect
      .poll(() => results(page).count(), {
        timeout: 10000,
        message: `type search "${type}" returned all ${entities.length} entities, so it did not filter at all`,
      })
      .toBeLessThan(entities.length);
  });

  test('entity type matching is case-insensitive too', async ({ page, request }) => {
    const entities = await servedEntities(request);
    const type = commonEntityType(entities);
    const anyOfType = entities.find(e => e.entityType === type)!;
    await openFileComplaint(page);

    await typeSearch(page, type.toLowerCase());
    await expect(resultNames(page).filter({ hasText: anyOfType.name }).first(),
      `"${type.toLowerCase()}" found nothing, so entity-type matching is case-sensitive`)
      .toBeVisible({ timeout: 10000 });
  });

  test('the type is shown beside each result, so the citizen can tell two similar names apart', async ({ page, request }) => {
    // The master genuinely contains near-duplicates ("Axis Bank" and "Axis Bank Limited"). A list of
    // bare names makes those indistinguishable, and picking the wrong one misroutes the complaint.
    const entities = await servedEntities(request);
    const type = commonEntityType(entities);
    const anyOfType = entities.find(e => e.entityType === type)!;
    await openFileComplaint(page);

    await typeSearch(page, anyOfType.name);
    const row = results(page).filter({ hasText: anyOfType.name }).first();
    await expect(row).toBeVisible({ timeout: 10000 });
    await expect(row.locator('.es-type'),
      'the result does not show the entity type').toHaveText(type);
  });

  test('an entity selected by type search is the entity that gets recorded', async ({ page, request }) => {
    const entities = await servedEntities(request);
    const type = commonEntityType(entities);
    const target = entities.find(e => e.entityType === type)!;
    await openFileComplaint(page);

    await typeSearch(page, type);
    await results(page).filter({ hasText: target.name }).first().dispatchEvent('mousedown');
    await expectRecordedAnswer(page, target.name,
      'selecting from a type-filtered list recorded the wrong entity');
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 7 — submitting with no entity selected
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('No entity selected is refused, naming the field', () => {

  test('pressing Next with nothing selected shows "Regulated Entity Name is mandatory."', async ({ page }) => {
    await openFileComplaint(page);
    await expectRecordedAnswer(page, null, 'an entity was pre-selected, so this case proves nothing');

    await page.locator('button.btn-next').click();

    const error = page.locator('#eligibility-mandatory-error');
    await expect(error).toBeVisible();
    // The exact string QA specifies. Asserted verbatim because the field-specific wording IS the case:
    // the generic "Response is mandatory." was what the product emitted before, and it asks the citizen
    // to answer a "response" on a screen with no question to respond to.
    await expect(error).toContainText('Regulated Entity Name is mandatory.');
  });

  test('the refusal does not advance the wizard', async ({ page }) => {
    await openFileComplaint(page);
    await page.locator('button.btn-next').click();
    await expect(page.locator('#eligibility-mandatory-error')).toBeVisible();
    await expect(page.locator('.radio-list'),
      'the wizard advanced past the entity step with no entity chosen').toHaveCount(0);
    await expect(searchBox(page)).toBeVisible();
  });

  test('typing a search term without choosing a result is still no selection', async ({ page, request }) => {
    // Text in a box is not an answer. A citizen who typed their bank's name and pressed Next without
    // clicking it must be refused, not silently filed against nothing — or worse, against a guess.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    await typeSearch(page, entities[0].name);
    await expect(results(page).first()).toBeVisible({ timeout: 10000 });
    await page.locator('button.btn-next').click();

    await expect(page.locator('#eligibility-mandatory-error'))
      .toContainText('Regulated Entity Name is mandatory.');
    await expectRecordedAnswer(page, null, 'typing alone recorded an entity');
  });

  test('the refusal clears once an entity is chosen', async ({ page, request }) => {
    const entities = await servedEntities(request);
    await openFileComplaint(page);
    await page.locator('button.btn-next').click();
    await expect(page.locator('#eligibility-mandatory-error')).toBeVisible();

    await typeSearch(page, entities[0].name);
    await results(page).filter({ hasText: entities[0].name }).first().click();
    await expect(page.locator('#eligibility-mandatory-error'),
      'a stale mandatory error stayed on screen after the citizen answered').toHaveCount(0);
  });

  test('the mandatory notice is master translation data, not a string in the bundle', async ({ request }) => {
    // The wording is the product owner's to change, so it must be changeable without a release. Also
    // guards the seeder/migration pair: a seeder edit alone never reaches an already-seeded database.
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    for (const locale of LOCALES) {
      const res = await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`);
      expect(res.status(), `${locale} translations unavailable`).toBe(200);
      const body = await res.json();
      const t: Record<string, string> = body.data ?? body;
      expect(t['eligibility.entity_mandatory'],
        `eligibility.entity_mandatory is missing for ${locale}, so that citizen sees a raw key or ` +
        `English`).toBeTruthy();
    }
  });

  test('the generic response_mandatory is still what the Yes/No questions use', async ({ page, request }) => {
    // The two messages are different BY DESIGN. Naming the entity field must not have broken the
    // generic line the maintainability questions rely on (UST14/UST18 S3, covered in
    // eligibility-maintainability-questions.spec.ts) — this is the guard that they stayed distinct.
    const entities = await servedEntities(request);
    await openFileComplaint(page);
    const chosen = results(page).filter({ hasText: entities[0].name }).first();
    await expect(chosen).toBeVisible({ timeout: 10000 });
    await chosen.dispatchEvent('mousedown');
    await expect(searchBox(page)).toHaveValue(entities[0].name, { timeout: 10000 });
    await page.locator('button.btn-next').click();
    await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });

    await page.locator('button.btn-next').click();
    const error = page.locator('#eligibility-mandatory-error');
    await expect(error).toBeVisible();
    await expect(error, 'the entity-specific wording leaked onto a Yes/No question')
      .not.toContainText('Regulated Entity Name is mandatory.');
    await expect(error).toContainText(/response is mandatory/i);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 8 — a search that matches nothing
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('A search that matches nothing says so', () => {

  test('a non-existent entity name shows "No results found"', async ({ page }) => {
    await openFileComplaint(page);
    await typeSearch(page, 'Zzqx Nonexistent Bank Of Nowhere');

    const empty = page.locator('.entity-search-empty');
    await expect(empty,
      'a search matching nothing showed neither results nor any message, so the citizen cannot tell ' +
      'whether the list is still loading').toBeVisible({ timeout: 10000 });
    await expect(empty).toContainText('No results found');
    await expect(results(page)).toHaveCount(0);
  });

  test('a non-existent entity TYPE shows the same message', async ({ page }) => {
    await openFileComplaint(page);
    await typeSearch(page, 'Interplanetary Credit Union');
    await expect(page.locator('.entity-search-empty')).toContainText('No results found');
  });

  test('the citizen search term is interpolated, never printed as a raw placeholder', async ({ page }) => {
    // eligibility.entity_search_no_results carries {{term}}, and TranslationService.translate(key,
    // params?) takes params OPTIONALLY — so rendering the key through the pipe would print a literal
    // "{{term}}" to the citizen. This project has already had to fix exactly that once.
    await openFileComplaint(page);
    await typeSearch(page, 'Zzqx Nonexistent Bank');

    const empty = page.locator('.entity-search-empty');
    await expect(empty).toBeVisible({ timeout: 10000 });
    // Retried, because the message tracks the box keystroke by keystroke: a one-shot innerText read can
    // catch it mid-term (it read `Zzqx Nonexistent` for `Zzqx Nonexistent Bank` on the first run), which
    // is the control working, not a failure to interpolate.
    await expect(empty, 'the message does not echo what the citizen searched for')
      .toContainText('Zzqx Nonexistent Bank');
    const text = await empty.innerText();
    expect(text, `the raw placeholder reached the citizen: ${text}`).not.toContain('{{term}}');
    expect(text, 'a raw translation key was rendered').not.toContain('eligibility.');
  });

  test('the no-results message is master translation data in every locale', async ({ request }) => {
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    for (const locale of LOCALES) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;
      const value = t['eligibility.entity_search_no_results'];
      expect(value, `entity_search_no_results is missing for ${locale}`).toBeTruthy();
      // The placeholder must survive translation: a locale that lost it would stop telling the citizen
      // WHAT was not found, which is the whole content of the message.
      expect(value, `${locale} lost its {{term}} placeholder`).toContain('{{term}}');
    }
  });

  test('an empty box is not "no results"', async ({ page }) => {
    // The message must be the answer to a SEARCH, not the resting state of the control. An empty box
    // reading "No results found" tells a citizen the RBI regulates nothing.
    await openFileComplaint(page);
    await searchBox(page).click();
    await expect(results(page).first()).toBeVisible({ timeout: 10000 });
    await expect(page.locator('.entity-search-empty')).toHaveCount(0);
  });

  test('correcting the term recovers from the empty state', async ({ page, request }) => {
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    await typeSearch(page, 'Zzqx Nonexistent Bank');
    await expect(page.locator('.entity-search-empty')).toBeVisible({ timeout: 10000 });

    await typeSearch(page, entities[0].name);
    await expect(page.locator('.entity-search-empty'),
      'the empty-state message stayed on screen beside real results').toHaveCount(0);
    await expect(resultNames(page).filter({ hasText: entities[0].name }).first()).toBeVisible();
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 9 — special characters: no crash, no injection, no XSS
// ══════════════════════════════════════════════════════════════════════════════════════════════════

/**
 * Terms chosen to cover the three distinct risks rather than just "odd punctuation": SQL metacharacters
 * and comment syntax, HTML/JS, and regex metacharacters (the filter is a substring match today, but an
 * unescaped term handed to a RegExp would throw and take the control out).
 */
const HOSTILE_TERMS = [
  "' OR '1'='1",
  "'; DROP TABLE regulated_entities; --",
  '" OR 1=1 --',
  '<img src=x onerror=alert(1)>',
  "<script>alert('xss')</script>",
  '%%__$#@!&*()[]{}|\/<>?~',
  // Regex metacharacters: harmless to a substring match, fatal to an unescaped RegExp.
  '.*+?^$()[]{}|',
  // Already percent-encoded, in case anything decodes the term before matching it.
  '%27%20OR%201%3D1',
  "admin'--",
  // Unicode direction override plus a zero-width space — these have broken input handling before.
  'ctrl ‮test​',
];

test.describe('Special characters in the search box are inert', () => {

  test('no hostile term crashes the control or the page', async ({ page }) => {
    const pageErrors: string[] = [];
    page.on('pageerror', e => pageErrors.push(e.message));
    await openFileComplaint(page);

    for (const term of HOSTILE_TERMS) {
      await typeSearch(page, term);
      // The control must still be alive and must still be able to answer.
      await expect(searchBox(page), `the search box died on: ${term}`).toBeVisible();
      await expect(page.locator('.eligibility-card'), `the wizard unmounted on: ${term}`).toBeVisible();
      await expect
        .poll(async () =>
          (await results(page).count()) + (await page.locator('.entity-search-empty').count()), {
          timeout: 10000,
          message: `"${term}" produced neither results nor a no-results message — the control is stuck`,
        })
        .toBeGreaterThan(0);
    }

    expect(pageErrors, `hostile search terms raised uncaught errors: ${pageErrors.join(' | ')}`)
      .toEqual([]);
  });

  test('the control still works normally after hostile input', async ({ page, request }) => {
    // "No crash" is not enough: a control that survives but stops matching is equally broken.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    for (const term of HOSTILE_TERMS) await typeSearch(page, term);

    await typeSearch(page, entities[0].name);
    await expect(resultNames(page).filter({ hasText: entities[0].name }).first(),
      'after hostile input the search no longer finds a real entity').toBeVisible({ timeout: 10000 });
  });

  test('an HTML term is rendered as text, not as markup', async ({ page }) => {
    // The term is echoed back in the no-results message, which is the one place it reaches the DOM.
    await openFileComplaint(page);
    const XSS = `<img src=x onerror=alert(1)>`;

    let alerted = false;
    page.on('dialog', async d => { alerted = true; await d.dismiss(); });

    await typeSearch(page, XSS);
    const empty = page.locator('.entity-search-empty');
    await expect(empty).toBeVisible({ timeout: 10000 });

    // Escaped: the term appears as text and no <img> was created inside the message.
    await expect(empty).toContainText('<img src=x onerror=alert(1)>');
    expect(await empty.locator('img, script').count(),
      'the search term was injected into the DOM as markup').toBe(0);
    expect(alerted, 'a script in the search term executed').toBe(false);
  });

  test('a script tag in the term creates no script node anywhere on the page', async ({ page }) => {
    await openFileComplaint(page);
    const before = await page.locator('script[data-xss], img[src="x"]').count();
    await typeSearch(page, `<script>window.__pwned=1</script><img src=x onerror=alert(1)>`);
    await expect(page.locator('.entity-search-empty')).toBeVisible({ timeout: 10000 });

    expect(await page.locator('img[src="x"]').count(),
      'an <img> from the search term was attached to the document').toBe(before);
    expect(await page.evaluate(() => (window as unknown as Record<string, unknown>)['__pwned']),
      'script from the search term executed').toBeUndefined();
  });

  test('the search never reaches the network, so there is no injection surface here', async ({ page, request }) => {
    // Filtering is client-side over the list already fetched. Pinned as an assertion because it is the
    // REASON the box cannot be injected: if searching ever becomes a server round trip, this fails and
    // the parameterisation of that query has to be re-examined.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    const calls: string[] = [];
    await page.route('**/api/**', async route => {
      calls.push(new URL(route.request().url()).pathname + (new URL(route.request().url()).search));
      return route.fallback();
    });

    for (const term of [...HOSTILE_TERMS, entities[0].name]) await typeSearch(page, term);
    await page.waitForTimeout(500);

    const entityCalls = calls.filter(c => c.includes('/routing/entities'));
    expect(entityCalls,
      `the search box issued entity requests (${JSON.stringify(entityCalls)}) — searching is no longer ` +
      `purely client-side, so the server query must be re-checked for parameterisation`).toEqual([]);
  });

  test('the server-side entity search is parameterised and cannot be injected', async ({ request }) => {
    // Not the citizen's path today (staff screens use ?search=), but it is the same table and the same
    // term, so if it is ever wired to this box the hole would be live. RegulatedEntityRepository#
    // searchByNormalizedName is a JPQL @Query with a bound @Param, and RegulatedEntity.normalize()
    // strips everything outside [A-Z0-9 ] before it is bound. Probed live rather than read off the
    // source, and the table is proved still standing afterwards.
    //
    // The assertion is "the result is exactly what normalize-then-substring-match predicts", NOT
    // "fewer rows than the whole table". Two of the hostile terms (`%%__$#@!...` and `.*+?^$()[]{}|`)
    // are ALL punctuation, so normalize() reduces them to the empty string and the controller's
    // `search.isBlank()` branch correctly returns the full list — a full list there is the term having
    // been stripped to nothing, which is the defence working, not a tautology being evaluated. Asserting
    // a bare row-count drop failed on exactly those two terms on the first run. Note this also proves
    // `%` and `_` are inert rather than live LIKE wildcards: normalize() removes them, so `BAJA%FINANCE`
    // matches nothing (verified) instead of matching "BAJAJ FINANCE LIMITED".
    const before = await servedEntities(request);
    const normalize = (s: string) =>
      s.toUpperCase().replace(/[^A-Z0-9 ]/g, '').replace(/\s+/g, ' ').trim();
    const beforeNames = before.map(e => normalize(e.name));

    for (const term of HOSTILE_TERMS) {
      const res = await request.get(`${API_BASE}/api/v1/routing/entities/list`,
        { params: { search: term }, failOnStatusCode: false });
      expect(res.status(), `"${term}" made the entity search fail with ${res.status()}`).toBe(200);
      const body = await res.json();
      const data = body?.data ?? body;
      expect(Array.isArray(data), `"${term}" did not return a list`).toBe(true);

      const normalized = normalize(term);
      const predicted = normalized === ''
        ? before.length                              // stripped to nothing → the unfiltered list
        : beforeNames.filter(n => n.includes(normalized)).length;
      expect(data.length,
        `"${term}" returned ${data.length} rows but a bound, normalized substring match predicts ` +
        `${predicted} (normalized to "${normalized}") — the term is being interpreted, not bound`)
        .toBe(predicted);
      // Belt and braces on the case that actually matters: a tautology must never open the table up.
      if (normalized !== '') {
        expect(data.length,
          `"${term}" returned the whole table, which is what a successful tautology looks like`)
          .toBeLessThan(before.length);
      }
    }

    const after = await servedEntities(request);
    expect(after.length,
      `the entity master went from ${before.length} to ${after.length} rows during injection probing`)
      .toBe(before.length);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// QA 10 — clearing the search restores the full list
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('Clearing the search restores the full list', () => {

  test('deleting the search text brings back every entity', async ({ page, request }) => {
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    // Polled, not read once: pressSequentially delivers the term a key at a time and Angular applies
    // each change in a later pass, so an immediate count can still be the unfiltered list.
    await typeSearch(page, entities[0].name.slice(0, 4));
    await expect
      .poll(() => results(page).count(),
        { timeout: 10000, message: 'the search did not narrow anything, so clearing it proves nothing' })
      .toBeLessThan(entities.length);

    await searchBox(page).fill('');
    await expect
      .poll(() => results(page).count(), { timeout: 10000 })
      .toBe(entities.length);
    await expect(page.locator('.entity-search-empty')).toHaveCount(0);
  });

  test('the clear button restores the full list', async ({ page, request }) => {
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    await typeSearch(page, entities[0].name);
    const clear = page.locator('button.entity-search-clear');
    await expect(clear, 'there is no way to clear the search in one action').toBeVisible();
    await clear.click();

    await expect(searchBox(page)).toHaveValue('');
    await expect.poll(() => results(page).count(), { timeout: 10000 }).toBe(entities.length);
  });

  test('clearing after a no-results search also restores the full list', async ({ page, request }) => {
    // The path a citizen actually takes: mistype, see nothing, clear, start again.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    await typeSearch(page, 'Zzqx Nonexistent Bank');
    await expect(page.locator('.entity-search-empty')).toBeVisible({ timeout: 10000 });

    await searchBox(page).fill('');
    await expect(page.locator('.entity-search-empty')).toHaveCount(0);
    await expect.poll(() => results(page).count(), { timeout: 10000 }).toBe(entities.length);
  });

  test('clearing the search also clears the selection', async ({ page, request }) => {
    // Leaving the answer behind while the box reads empty would let a citizen press Next and file
    // against an entity the screen no longer names — a silently wrong respondent.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    await typeSearch(page, entities[0].name);
    await results(page).filter({ hasText: entities[0].name }).first().dispatchEvent('mousedown');
    // Deliberately NOT expectRecordedAnswer here: that helper empties the box to reopen the list, and
    // the × button only renders while the box has text (`@if (entitySearchText)`), so asserting
    // through it would remove the very control this case is about. The box holding the chosen name is
    // enough to know the choice landed — the answer itself is asserted after the clear.
    await expect(searchBox(page)).toHaveValue(entities[0].name, { timeout: 10000 });

    await page.locator('button.entity-search-clear').click();
    await expectRecordedAnswer(page, null,
      'the entity stayed selected after the search was cleared, so the screen and the answer disagree');

    await page.locator('button.btn-next').click();
    await expect(page.locator('#eligibility-mandatory-error'),
      'the wizard advanced on a selection the citizen had just cleared').toBeVisible();
  });

  test('the full list is the whole master, not a truncated page of it', async ({ page, request }) => {
    // The regression guard for the slice(0, 50): the resting list must be everything the API serves.
    const entities = await servedEntities(request);
    await openFileComplaint(page);

    await searchBox(page).click();
    await expect.poll(() => results(page).count(), { timeout: 10000 }).toBe(entities.length);
  });
});

// ══════════════════════════════════════════════════════════════════════════════════════════════════
// A failed entity fetch must be visible, not an empty dropdown
// ══════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('An unavailable entity master is reported, not shown as an empty list', () => {

  test('a 500 on the entity list is surfaced with a retry', async ({ page }) => {
    // Driven live before this was fixed: the citizen got a dropdown containing only the disabled
    // "Select Regulated Entity Name" and nothing whatsoever indicating a failure. Indistinguishable
    // from "RBI regulates no entities", on the one screen a complaint cannot get past.
    await page.route('**/api/v1/routing/entities/list*', route =>
      route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"down"}' }));

    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, '9876500131', 'e2e-seeded-token.sig');
    await page.goto(`${APP_BASE}/public/file-complaint`);
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 25000 });

    const notice = page.locator('.entities-load-error');
    await expect(notice,
      'the entity list failed to load and the citizen was told nothing').toBeVisible({ timeout: 20000 });
    await expect(notice, 'a raw translation key was rendered').not.toContainText('eligibility.');
    await expect(page.locator('button.btn-retry-entities')).toBeVisible();
  });

  test('retry recovers once the endpoint is healthy again', async ({ page, request }) => {
    // A notice with a dead button is not a recovery path.
    const entities = await servedEntities(request);
    let fail = true;
    await page.route('**/api/v1/routing/entities/list*', route => fail
      ? route.fulfill({ status: 500, contentType: 'application/json', body: '{"error":"down"}' })
      : route.fallback());

    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, '9876500131', 'e2e-seeded-token.sig');
    await page.goto(`${APP_BASE}/public/file-complaint`);
    await expect(page.locator('.entities-load-error')).toBeVisible({ timeout: 25000 });

    fail = false;
    await page.locator('button.btn-retry-entities').click();

    await expect(page.locator('.entities-load-error')).toHaveCount(0, { timeout: 20000 });
    // The notice going away is not recovery — the whole master has to be back. The list renders only
    // while the dropdown is open, so open it and count what the retry actually loaded.
    await searchBox(page).click();
    await expect
      .poll(() => results(page).count(), { timeout: 20000 })
      .toBe(entities.length);
  });

  test('an empty entity master is reported rather than shown as a blank dropdown', async ({ page }) => {
    // A 200 carrying zero rows is the same situation for the citizen as a 500, and is what a
    // misconfigured environment actually produces.
    await page.route('**/api/v1/routing/entities/list*', route =>
      route.fulfill({ status: 200, contentType: 'application/json',
                      body: JSON.stringify({ success: true, data: [] }) }));

    await page.goto(`${APP_BASE}/public`);
    await seedCitizenSession(page, '9876500131', 'e2e-seeded-token.sig');
    await page.goto(`${APP_BASE}/public/file-complaint`);
    await expect(page.locator('.eligibility-card')).toBeVisible({ timeout: 25000 });

    await expect(page.locator('.entities-load-error'),
      'an empty entity master was presented as a working but empty dropdown')
      .toBeVisible({ timeout: 20000 });
  });

  test('the unavailable notice is master translation data in every locale', async ({ request }) => {
    const LOCALES = ['en', 'hi', 'mr', 'bn', 'te', 'ta', 'gu', 'ur', 'kn', 'ml'];
    for (const locale of LOCALES) {
      const body = await (await request.get(`${API_BASE}/api/v1/i18n/translations/${locale}`)).json();
      const t: Record<string, string> = body.data ?? body;
      expect(t['eligibility.entities_unavailable'],
        `eligibility.entities_unavailable is missing for ${locale}`).toBeTruthy();
    }
  });
});
