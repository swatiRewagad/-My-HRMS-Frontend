/**
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 * QA-B1 / QA-B23 / QA-B24 / QA-B26 / QA-B27 — Regulated Entity selection and the RE Details cascade
 * ═══════════════════════════════════════════════════════════════════════════════════════════════════
 *
 * Manual pack block 1 (lines 1-33, 4 cases) and blocks 23/24/26/27 (lines 1476-1891, 32 rows of which
 * 11 are an identical login/navigation preamble replayed in each block).
 *
 * ── DEDUPLICATION, AS THE BRIEF DIRECTS ──────────────────────────────────────────────────────────
 * Block 23 (credit-card radio) is contained entirely within blocks 24/26/27 — each of those replays
 * the same three radio rows before getting to its own field. The radio is therefore tested ONCE here,
 * and the cascade blocks assert only what is theirs. The 11 preamble rows ("user is able to login",
 * "post successful login the user is navigated to the Select Regulated Entity Name screen") are
 * covered by Session A's login specs and by `openWizard`'s own assertions; they are not re-automated.
 *
 * ── WHAT IS ALREADY COVERED ELSEWHERE, AND SO IS NOT REPEATED HERE ───────────────────────────────
 * `re-entity-search.spec.ts` already carries 42 tests over the entity list: search by name and by
 * type, case-insensitivity, whitespace tolerance, no-results messaging, hostile-input inertness,
 * clearing the search, and the unavailable-master path. Block 1's net-new surface against that file is
 * only four things — changing an existing selection, scrollbar navigation over a large list, arrow-key
 * + Enter selection, and the absence of multi-select — so only those are written below.
 *
 * ── BLOCK 1'S ARROW-KEY CASE: D-B4, RAISED TO BLOCKING, NOW FIXED ─────────────────────────────────
 * The search results list is `<ul role="listbox">` of `<li role="option">`. Each option used to bind
 * `(mousedown)="selectEntityFromSearch(opt)"` and NOTHING ELSE — no `(keydown)`, no ArrowDown/ArrowUp
 * handling, no active-descendant tracking, no `tabindex`. A keyboard-only citizen could tab into the
 * box and type, but could neither reach nor activate a result. Recorded as defect D-B4.
 *
 * WHY IT BECAME BLOCKING, which is the non-obvious part and the reason it is kept on record: when this
 * was first written a native `<select>` of the same list stood beside the combobox and WAS
 * keyboard-operable (browsers give that for free), so the citizen was merely inconvenienced. That
 * select was removed in the UI homogenisation — one question must have one control — which left the
 * mouse-only combobox as the ONLY way to answer the question that gates the entire Scheme, i.e. a
 * keyboard-only citizen could not file a complaint at all.
 *
 * FIXED. The input now carries `role="combobox"`, `aria-autocomplete="list"`, `aria-expanded`,
 * `aria-controls` and `aria-activedescendant`; each option carries a stable id and an `.active`
 * highlight class. The input's `(keydown)` handler (`onEntitySearchKeydown`) drives ArrowDown/ArrowUp
 * (wrapping), Home/End, Enter (through the SAME `selectEntityFromSearch` the mouse uses) and Escape
 * (closes the list, leaves the recorded answer alone). DOM focus never leaves the input, per the ARIA
 * pattern, so typing to narrow the list keeps working. The mouse path is untouched: `(mousedown)`
 * remains, because it must beat the input's blur. Asserted below.
 *
 * ── THE PACK'S RBIO ROUTING RULE DOES NOT EXIST ──────────────────────────────────────────────────
 * Blocks 24/26/27 each assert: "if the Regulated entity selected is other than RBIO then the user is
 * NOT navigated to the Regulated entity details screen". There is no such branch. `nextStep()`
 * (component.ts:1838-1847) increments unconditionally once `validateCurrentStep()` passes; there is no
 * department test anywhere in the navigation path. `selectedEntityType` (component.ts:2223-2227) and
 * `isCEPCEntity` exist and DO steer eligibility questions, but they do not steer step routing.
 *
 * This is not hypothetical: the live master holds 145 entities, 86 of them CEPC and 59 RBIO, so most
 * entities a citizen picks are "other than RBIO". Proven below by driving a CEPC entity all the way to
 * step 2 — which the pack says must not happen. Recorded as contradiction 24-C1.
 *
 * ── ENTITY BRANCH IS A TEXT INPUT, AND ITS ENDPOINT 404s ─────────────────────────────────────────
 * Block 27 spends 10 cases on an "Entity Branch dropdown" that opens, closes, offers single-select,
 * refuses multi-select, and is populated from the chosen state and district. The product renders:
 *
 *     <input type="text" name="entityBranch" placeholder="Enter Branch Name">   (html:652)
 *
 * The citizen TYPES the branch name. There is no dropdown, and no list is ever fetched for it:
 * `onEntityDistrictChange()` does call GET /api/v1/location/branches (component.ts:487), but
 *   (a) `LocationController` has no `/branches` mapping — the call 404s, verified live on a fresh JVM;
 *   (b) the error callback is `error: () => {}`, so the failure is swallowed silently; and
 *   (c) `entityBranches` is never referenced in the template (0 occurrences), so even a successful
 *       response would be discarded.
 * A dead request feeding a dead getter bound to nothing. Recorded as defect D-B5.
 *
 * ── THE CASCADE THAT DOES EXIST, AND DOES WORK ───────────────────────────────────────────────────
 * entityState → entityDistrict is a genuine server-backed cascade, and the one the complainant screen
 * lacked (see complainant-address-state-district.spec.ts). `onEntityStateChange()` clears district AND
 * branch then fetches /api/v1/location/districts?state=X; `onEntityDistrictChange()` clears branch.
 * The stale-child defect does not occur. Asserted against the endpoint's own response, never against a
 * hardcoded district list (ruling 1).
 *
 * ── NO SEEDED ROWS, NO CLEANUP ──────────────────────────────────────────────────────────────────
 * Nothing is submitted; no OTP is sent; SYSTEM_CONFIG is untouched. All lookups are read-only GETs.
 * Mobiles come from Session B's reserved 98765_2____ range via the helper.
 */

import { test, expect, Page } from '../fixtures';
import {
  openWizard, gotoComplainantDetails, gotoComplainantDetailsWithEntity, gotoReDetails, gotoReCascade,
  fillComplainantMinimalAndAdvance, fieldError, clickNextAndCollectErrors, entityByDepartment,
  redirectAppApi, API_BASE, waitForEntityPicker, entitySearchOptions,
} from './helpers-forms-b';

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

/**
 * Types `term` and waits for the rendered list to be the list for THAT term, not for some earlier
 * keystroke.
 *
 * `toHaveValue(term)` is not sufficient on its own. pressSequentially delivers one key at a time and
 * Angular applies each (ngModelChange) in a later change-detection pass, so the box can already read
 * 'Bank' while the popup is still showing the rows matching 'Ban'. A row COUNT read at that instant is
 * too large, and every aria-activedescendant index derived from it is then wrong — which is exactly
 * how the ArrowUp case failed intermittently in a full run and passed in isolation.
 *
 * The expected count comes from the master through the API (ruling 1: never a hardcoded number), and
 * reproduces filterEntities' rule — case-insensitive substring over NAME or ENTITY TYPE.
 */
async function typeAndSettle(page: Page, box: ReturnType<Page['locator']>, term: string): Promise<number> {
  const master = await (await page.request.get(`${API_BASE}/api/v1/routing/entities/list`)).json();
  const rows = (master?.data ?? master ?? []) as any[];
  const needle = term.toLowerCase().trim();
  const expected = rows.filter(r =>
    String(r.name ?? '').toLowerCase().includes(needle) ||
    String(r.entityType ?? '').toLowerCase().includes(needle)).length;
  expect(expected, `"${term}" matches nothing in the master, so no index assertion below can hold`)
    .toBeGreaterThan(2);

  await box.focus();
  await box.fill('');
  await box.pressSequentially(term);
  await expect(box).toHaveValue(term);
  await expect
    .poll(() => entitySearchOptions(page).count(),
      { timeout: 15000, message: `the popup never settled on the ${expected} entities matching "${term}"` })
    .toBe(expected);
  return expected;
}

/**
 * Reopens the entity list on the FULL master, by keyboard only, after a choice has been made.
 *
 * Needed because `selectEntityFromSearch` writes the chosen entity's name into the box and the box's
 * text is the filter term — so simply reopening shows a one-row list holding only what was already
 * chosen, and `#entity-search-option-1` would not exist. Selecting-all and deleting is how a citizen
 * with no pointing device clears it; `fill('')` would do it too but is not a keystroke.
 */
async function clearFilterByKeyboard(page: Page, box: ReturnType<Page['locator']>, chosenLabel: string) {
  // Waits for the chosen label to LAND first, and for that exact label rather than merely for a
  // non-empty box. The component writes it in a change-detection pass later than the keydown that
  // made the choice, so a clear issued immediately empties the box and then Angular fills the name
  // straight back in — leaving the list filtered to the one row that was already chosen.
  await expect(box, 'the chosen entity never reached the box, so there is no filter to clear')
    .toHaveValue(chosenLabel, { timeout: 10000 });
  await box.press('ControlOrMeta+a');
  await box.press('Delete');
  await expect(box).toHaveValue('');

  // And waits for the POPUP to catch up with the empty box, for the same reason typeAndSettle exists:
  // an empty box whose list is still the single chosen row would make the assertions that follow read
  // a one-row view instead of the whole master.
  const master = await (await page.request.get(`${API_BASE}/api/v1/routing/entities/list`)).json();
  const total = ((master?.data ?? master ?? []) as any[]).length;
  await expect
    .poll(() => entitySearchOptions(page).count(),
      { timeout: 15000, message: 'clearing the filter did not restore the full entity master' })
    .toBe(total);
}

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 1 — Regulated Entity selection: only what re-entity-search.spec.ts does not already cover
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B1 — changing, scrolling and keyboard-driving the Regulated Entity list', () => {

  test('the citizen can change an entity selection to a different entity before proceeding', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);
    const options = entitySearchOptions(page);
    expect(await options.count(), 'the master must offer at least two entities to change between')
      .toBeGreaterThan(1);

    // Read the names while the list is still on screen: advancing past the entity question removes it
    // from the DOM, so a post-advance read finds nothing.
    const firstName = ((await options.nth(0).locator('.es-name').textContent()) || '').trim();
    const changedName = ((await options.nth(1).locator('.es-name').textContent()) || '').trim();
    expect(changedName, 'the two entities must be distinguishable by name').not.toBe(firstName);

    // Case 1.1. Choose one, then choose a different row, then confirm the SECOND is what is held.
    // mousedown, not click: the option's handler is (mousedown), which fires BEFORE the input's blur
    // closes the list, so a click lets blur remove the option mid-gesture.
    await options.nth(0).dispatchEvent('mousedown');
    await expect(box).toHaveValue(firstName, { timeout: 10000 });

    // Changing the answer means RETYPING, and that is the product's actual behaviour rather than a
    // harness detail: selectEntityFromSearch writes the chosen name into the box (component.ts:1292),
    // and the box's text is the filter term (filterEntities, ts:1244). So merely reopening the list
    // shows the one row already chosen — the citizen who wants a different bank has to type its name,
    // or clear the box first. Driven the way a citizen does it.
    await box.click();
    await box.fill('');
    await box.pressSequentially(changedName.slice(0, 20));
    const changedRow = options.filter({ hasText: changedName }).first();
    await expect(changedRow, 'the entity being changed to was not offered').toBeVisible({ timeout: 15000 });
    await changedRow.dispatchEvent('mousedown');
    await expect(box, 'the changed selection must replace the first, not be ignored')
      .toHaveValue(changedName, { timeout: 10000 });

    // And the changed selection is the one carried forward: the questionnaire interpolates the RE name
    // into its questions, so it names the entity last chosen, not the one first clicked.
    await page.locator('button.btn-next').click();
    await expect(page.locator('.radio-list')).toBeVisible({ timeout: 20000 });
    const questionnaire = page.locator('.eligibility-card');
    await expect(questionnaire, 'the questions must name the entity last chosen')
      .toContainText(changedName, { timeout: 20000 });
    await expect(questionnaire, 'the abandoned first selection must not be named')
      .not.toContainText(firstName);
  });

  test('a large entity list is scrollable rather than truncated', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);
    const options = entitySearchOptions(page);

    // Case 1.2. Two things make up this case, and the combobox lets both be checked where a native
    // <select> allowed only the second (its popup is painted by the OS, outside the DOM).
    const master = await (await page.request.get(`${API_BASE}/api/v1/routing/entities/list`)).json();
    const rows = (master?.data ?? master ?? []) as any[];
    expect(rows.length, 'this assertion is only meaningful on a large master').toBeGreaterThan(50);

    // (a) every entity the master returns is offered, rather than the list being capped or paged; and
    expect(await options.count(), 'every entity in the master must be reachable, none paged away')
      .toBe(rows.length);

    // (b) the list really does scroll — the popup overflows its own box rather than growing without
    // bound or clipping rows away. This is the scrollbar the case asks for, now that it is a real
    // element: scrollHeight exceeding clientHeight IS a scrollable region.
    const list = page.locator('ul.entity-search-results');
    const metrics = await list.evaluate((el: HTMLElement) => ({
      scrollHeight: el.scrollHeight, clientHeight: el.clientHeight,
      overflowY: getComputedStyle(el).overflowY,
    }));
    expect(metrics.scrollHeight,
      'the list is not scrollable: it renders at full height, so a 145-row master runs off screen')
      .toBeGreaterThan(metrics.clientHeight);
    expect(['auto', 'scroll'], 'the overflow is not scrollable').toContain(metrics.overflowY);

    // And the last entity is reachable by scrolling to it, not merely present in the DOM.
    await options.last().scrollIntoViewIfNeeded();
    await expect(options.last(), 'the last entity cannot be scrolled to').toBeVisible();
    await expect(box).toHaveValue('');
  });

  test('the entity list is single-select, so multiple entities cannot be chosen', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);
    const options = entitySearchOptions(page);

    // Case 1.4. The control is a `ul role="listbox"` of `li role="option"`, so there is no `multiple`
    // attribute to read as there was on the select. What replaces it is stronger: the answer is a
    // SINGLE value (`eligibilityAnswers[currentQuestionKey]`), and `aria-selected` is bound to an
    // equality test against it, so at most one option can ever be marked selected. Asserted on the
    // behaviour — choosing a second entity replaces the first rather than adding to it.
    await expect(options.first()).toBeVisible({ timeout: 20000 });
    const firstName = ((await options.nth(0).locator('.es-name').textContent()) || '').trim();
    const secondName = ((await options.nth(1).locator('.es-name').textContent()) || '').trim();

    await options.nth(0).dispatchEvent('mousedown');
    await expect(box).toHaveValue(firstName, { timeout: 10000 });
    // Retyping, because the chosen name is also the filter term — see the change-selection case above.
    await box.click();
    await box.fill('');
    await box.pressSequentially(secondName.slice(0, 20));
    await options.filter({ hasText: secondName }).first().dispatchEvent('mousedown');
    await expect(box).toHaveValue(secondName, { timeout: 10000 });

    // Clearing the FILTER (not the selection) brings the whole master back with the chosen row still
    // marked, so the single-selection guarantee is not an artifact of a one-row filtered view.
    //
    // `fill('')` and not `click()`: the box still holds focus after the choice, so a click fires no
    // focus event and `openEntityDropdown` never runs — the list would stay closed and every
    // aria-selected assertion would vacuously find zero. Typing drives (ngModelChange), which
    // re-filters AND reopens. Note this is the filter only: clearEntitySelection (the × button) is
    // what drops the answer.
    await box.fill('');
    await expect(options.first()).toBeVisible({ timeout: 15000 });
    const selected = page.locator('li.entity-search-option[aria-selected="true"]');
    await expect(selected, 'exactly one entity is ever selected').toHaveCount(1);
    await expect(selected.locator('.es-name'), 'the second choice must have replaced the first')
      .toHaveText(secondName);
    // Also on the aria contract itself: a multi-selectable listbox announces aria-multiselectable.
    expect(await page.locator('ul.entity-search-results').getAttribute('aria-multiselectable'),
      'the listbox must not advertise multi-select').toBeNull();
  });

  /**
   * D-B4 (was blocking, now FIXED). Case 1.3: arrow-key + Enter selection from the searchable list.
   *
   * The three tests below are the acceptance evidence for the fix. They are written keyboard-only on
   * purpose — `focus()` and `press()`, never `click()` or `dispatchEvent('mousedown')` — so none of
   * them can pass on the strength of the pointer path that the single-select case above already covers.
   *
   * Selection is asserted on `aria-selected`, never on the box's text: the component writes the chosen
   * name into the box, but a citizen can type that same text without ever choosing, so the box is not
   * evidence of a recorded answer. And because the list renders only while open, every "nothing is
   * selected" assertion first proves an option is VISIBLE — `toHaveCount(0)` against a closed list is
   * vacuous and would pass no matter what was recorded.
   */
  test('the searchable entity list is keyboard-operable: ArrowDown highlights, Enter records the answer, and filing proceeds', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);
    const options = entitySearchOptions(page);

    // focus(), not click(): this whole test must hold for a citizen with no pointing device.
    await typeAndSettle(page, box, 'Bank');
    await expect(options.first()).toBeVisible({ timeout: 20000 });

    // The ARIA contract the pattern requires, without which a screen reader cannot follow the highlight.
    await expect(box).toHaveAttribute('role', 'combobox');
    await expect(box).toHaveAttribute('aria-autocomplete', 'list');
    await expect(box).toHaveAttribute('aria-expanded', 'true');
    await expect(box).toHaveAttribute('aria-controls', 'entity-search-results');

    // Nothing is highlighted on arrival, so an Enter pressed out of habit cannot pick a bank at random.
    expect(await box.getAttribute('aria-activedescendant'),
      'a row is highlighted before the citizen has arrowed to it').toBeNull();

    await box.press('ArrowDown');
    await expect(box, 'ArrowDown did not highlight the first result')
      .toHaveAttribute('aria-activedescendant', 'entity-search-option-0');
    await box.press('ArrowDown');
    await expect(box, 'a second ArrowDown did not advance the highlight')
      .toHaveAttribute('aria-activedescendant', 'entity-search-option-1');

    // The highlight is VISIBLE, and is exactly one row — a sighted keyboard user has to be able to see
    // where they are, and must not see two candidates at once.
    await expect(page.locator('li.entity-search-option.active'),
      'the keyboard highlight is not rendered, so a sighted keyboard user cannot see where they are')
      .toHaveCount(1);
    const highlighted = page.locator('#entity-search-option-1');
    const expectedName = ((await highlighted.locator('.es-name').textContent()) || '').trim();
    expect(expectedName, 'the highlighted row must name an entity').not.toBe('');

    await box.press('Enter');
    // Reopened on the FULL master rather than on the 'Bank' filter, so "exactly one row is selected"
    // is a claim about every entity and not about the handful matching a term. The list has to be on
    // screen before any aria-selected count means anything — a count against a closed list is vacuous.
    await clearFilterByKeyboard(page, box, expectedName);
    await expect(options.first()).toBeVisible({ timeout: 15000 });
    const selected = page.locator('li.entity-search-option[aria-selected="true"]');
    await expect(selected, 'Enter recorded no entity').toHaveCount(1);
    await expect(selected.locator('.es-name'), 'Enter recorded a different entity than the highlighted one')
      .toHaveText(expectedName);

    // And the severity is discharged: the keyboard-only citizen gets past the question that gates the
    // whole Scheme, instead of being held on it by the mandatory-field error.
    await box.press('Escape');
    await page.locator('button.btn-next').click();
    await expect(page.locator('.radio-list'),
      'a keyboard-only citizen still cannot get past the entity question').toBeVisible({ timeout: 20000 });
    await expect(page.locator('#eligibility-mandatory-error')).toHaveCount(0);
    await expect(page.locator('.eligibility-card'), 'the keyboard-chosen entity is not what was carried forward')
      .toContainText(expectedName, { timeout: 20000 });
  });

  test('ArrowUp walks the list backwards from the end, and the highlight wraps at both ends', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);
    const options = entitySearchOptions(page);

    const count = await typeAndSettle(page, box, 'Bank');
    await expect(options.first()).toBeVisible({ timeout: 20000 });

    // With nothing highlighted, ArrowUp enters the list at the LAST row — the citizen reaching for the
    // bottom of a 145-row popup should not have to arrow down through all of it.
    await box.press('ArrowUp');
    await expect(box, 'ArrowUp did not enter the list at its last row')
      .toHaveAttribute('aria-activedescendant', `entity-search-option-${count - 1}`);
    await box.press('ArrowUp');
    await expect(box, 'ArrowUp did not move backwards')
      .toHaveAttribute('aria-activedescendant', `entity-search-option-${count - 2}`);

    // The contract is WRAPPING, not clamping, at both ends.
    await box.press('ArrowDown');
    await expect(box).toHaveAttribute('aria-activedescendant', `entity-search-option-${count - 1}`);
    await box.press('ArrowDown');
    await expect(box, 'ArrowDown past the last row must wrap to the first, not stick')
      .toHaveAttribute('aria-activedescendant', 'entity-search-option-0');
    await box.press('ArrowUp');
    await expect(box, 'ArrowUp before the first row must wrap to the last, not stick')
      .toHaveAttribute('aria-activedescendant', `entity-search-option-${count - 1}`);

    // And ArrowUp + Enter records the row it landed on, not merely the row ArrowDown would have found.
    const lastName = ((await page.locator(`#entity-search-option-${count - 1} .es-name`).textContent()) || '').trim();
    await box.press('Enter');
    await clearFilterByKeyboard(page, box, lastName);
    await expect(options.first()).toBeVisible({ timeout: 15000 });
    const selected = page.locator('li.entity-search-option[aria-selected="true"]');
    await expect(selected).toHaveCount(1);
    await expect(selected.locator('.es-name'), 'the entity reached by ArrowUp was not the one recorded')
      .toHaveText(lastName);
  });

  test('Escape closes the list and drops the highlight without changing the recorded answer', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);
    const options = entitySearchOptions(page);

    // Record an answer first, so "unchanged" is a real claim rather than "nothing was chosen anyway".
    await box.focus();
    await box.fill('');
    await box.press('ArrowDown');
    await expect(options.first()).toBeVisible({ timeout: 20000 });
    const chosen = ((await page.locator('#entity-search-option-0 .es-name').textContent()) || '').trim();
    expect(chosen, 'the row about to be chosen must name an entity').not.toBe('');
    await box.press('Enter');
    await clearFilterByKeyboard(page, box, chosen);
    await expect(options.first()).toBeVisible({ timeout: 15000 });
    await expect(page.locator('li.entity-search-option[aria-selected="true"] .es-name'),
      'no entity was recorded, so Escape has nothing to leave alone').toHaveText(chosen);
    await expect
      .poll(() => options.count(), { timeout: 10000, message: 'a second row is needed to arrow onto' })
      .toBeGreaterThan(1);

    // Arrow onto a DIFFERENT row, then dismiss. Dismissing a popup is not un-answering a question, and
    // it is not answering it either: the highlight must be discarded, not committed. Row 1, because
    // the answer just recorded is row 0 of the unfiltered master (ArrowDown entered it at the top).
    await box.press('ArrowDown');
    await box.press('ArrowDown');
    await expect(box).toHaveAttribute('aria-activedescendant', 'entity-search-option-1');
    const notChosen = ((await page.locator('#entity-search-option-1 .es-name').textContent()) || '').trim();
    expect(notChosen, 'the row arrowed onto is the one already chosen, so nothing would change anyway')
      .not.toBe(chosen);

    await box.press('Escape');
    await expect(options.first(), 'Escape did not close the list').toBeHidden({ timeout: 10000 });
    await expect(box).toHaveAttribute('aria-expanded', 'false');
    expect(await box.getAttribute('aria-activedescendant'),
      'Escape left a stale highlight behind, so a later Enter would fire at it').toBeNull();

    // Reopened before reading, because a count against a closed list proves nothing. The box is still
    // empty here — Escape does not restore the chosen label — so this is the full master again.
    await box.press('ArrowDown');
    await expect(options.first()).toBeVisible({ timeout: 15000 });
    const selected = page.locator('li.entity-search-option[aria-selected="true"]');
    await expect(selected, 'Escape changed how many entities are recorded').toHaveCount(1);
    await expect(selected.locator('.es-name'), 'Escape committed the highlighted row instead of discarding it')
      .toHaveText(chosen);
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 23 — the credit-card radio (tested once; blocks 24/26/27 replay these rows verbatim)
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B23 — "Is Complaint related to credit card?" is mandatory and drives the RE cascade', () => {

  test('the credit-card radio is displayed on the Regulated Entity Details step', async ({ page }) => {
    await gotoReDetails(page);

    await expect(page.locator('input[name="isCreditCardComplaint"][value="yes"]')).toBeAttached();
    await expect(page.locator('input[name="isCreditCardComplaint"][value="no"]')).toBeAttached();
    await expect(page.locator('#credit-card-label')).toBeVisible();

    // Neither option is pre-chosen: the citizen must answer.
    await expect(page.locator('input[name="isCreditCardComplaint"]:checked')).toHaveCount(0);
  });

  test('either Yes or No can be selected, and the choice replaces the other', async ({ page }) => {
    await gotoReDetails(page);
    const yes = page.locator('input[name="isCreditCardComplaint"][value="yes"]');
    const no = page.locator('input[name="isCreditCardComplaint"][value="no"]');

    await yes.check();
    await expect(yes).toBeChecked();
    await expect(no).not.toBeChecked();

    await no.check();
    await expect(no).toBeChecked();
    await expect(yes, 'radios are mutually exclusive').not.toBeChecked();
  });

  test('CONTRADICTION: a blank credit-card radio blocks the step, but the message is "Please select Yes or No", not "Response is mandatory."', async ({ page }) => {
    await gotoReDetails(page);
    await expect(page.locator('input[name="isCreditCardComplaint"]:checked')).toHaveCount(0);

    await page.locator('button.btn-next').click();
    await page.waitForTimeout(300);

    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      'the step must be held').toBeVisible();
    expect(await fieldError(page, 'isCreditCardComplaint')).toBe('Please select Yes or No');
  });

  test('answering Yes hides the state/district/branch group entirely', async ({ page }) => {
    await gotoReDetails(page);
    await page.locator('input[name="isCreditCardComplaint"][value="yes"]').check();

    // The whole @if block is removed, so none of the three fields exists in the DOM.
    await expect(page.locator('select[name="entityState"]')).toHaveCount(0);
    await expect(page.locator('select[name="entityDistrict"]')).toHaveCount(0);
    await expect(page.locator('input[name="entityBranch"]')).toHaveCount(0);
  });

  test('answering Yes advances to Complaint Details without asking for any entity location', async ({ page }) => {
    await gotoReDetails(page);
    await page.locator('input[name="isCreditCardComplaint"][value="yes"]').check();
    await page.locator('button.btn-next').click();

    await expect(page.locator('textarea[name="complaintText"]')).toBeVisible({ timeout: 20000 });
  });

  test('answering No reveals state, district and branch', async ({ page }) => {
    await gotoReDetails(page);
    await page.locator('input[name="isCreditCardComplaint"][value="no"]').check();

    await expect(page.locator('select[name="entityState"]')).toBeVisible({ timeout: 20000 });
    await expect(page.locator('select[name="entityDistrict"]')).toBeVisible();
    await expect(page.locator('input[name="entityBranch"]')).toBeVisible();
  });

  test('switching from No back to Yes removes the group again', async ({ page }) => {
    await gotoReCascade(page);
    await page.locator('input[name="isCreditCardComplaint"][value="yes"]').check();
    await expect(page.locator('select[name="entityState"]')).toHaveCount(0);
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// The RBIO routing rule the pack asserts three times over, which does not exist
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B24/26/27 — the documented RBIO-only routing rule', () => {

  test('CONTRADICTION: a non-RBIO (CEPC) entity reaches the Regulated Entity Details step, which the pack says must not happen', async ({ page }) => {
    // Read a real CEPC entity from the master rather than pinning an id (ruling 1).
    const byDept = await entityByDepartment(page);
    expect(byDept['CEPC'], 'this assertion needs a CEPC entity in the master').toBeTruthy();

    await gotoComplainantDetailsWithEntity(page, byDept['CEPC'].name);
    await fillComplainantMinimalAndAdvance(page);

    // Blocks 24, 26 and 27 each state the user "should not be navigated to the Regulated entity
    // details screen" for a non-RBIO entity. They are.
    await expect(page.locator('input[name="isCreditCardComplaint"]').first(),
      `CEPC entity "${byDept['CEPC'].name}" reached step 2; nextStep() has no department branch`)
      .toBeVisible({ timeout: 20000 });
  });

  test('an RBIO entity reaches the Regulated Entity Details step, as documented', async ({ page }) => {
    const byDept = await entityByDepartment(page);
    expect(byDept['RBIO'], 'this assertion needs an RBIO entity in the master').toBeTruthy();

    await gotoComplainantDetailsWithEntity(page, byDept['RBIO'].name);
    await fillComplainantMinimalAndAdvance(page);

    await expect(page.locator('input[name="isCreditCardComplaint"]').first())
      .toBeVisible({ timeout: 20000 });
  });

  test('the step-2 screen names the entity the citizen actually chose', async ({ page }) => {
    const byDept = await entityByDepartment(page);
    const entity = byDept['RBIO'] || byDept['CEPC'];

    await gotoComplainantDetailsWithEntity(page, entity.name);
    await fillComplainantMinimalAndAdvance(page);

    await expect(page.locator('.re-value'), 'the chosen entity must be echoed back, not a placeholder')
      .toHaveText(entity.name, { timeout: 20000 });
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 24 — Entity State
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B24 — Entity State is a server-backed single-select, shown only when the complaint is not about a credit card', () => {

  test('the entity state options are exactly what /location/states returns', async ({ page }) => {
    await gotoReCascade(page);

    const res = await page.request.get(`${API_BASE}/api/v1/location/states`);
    const body = await res.json();
    const expected = (body?.data ?? body ?? []) as string[];
    expect(expected.length, 'the states endpoint must return rows').toBeGreaterThan(0);

    const rendered = await page.locator('select[name="entityState"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(rendered.sort(), 'no hardcoded state list: the options are the endpoint\'s response')
      .toEqual([...expected].sort());
  });

  test('a single entity state can be selected and is retained', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');

    const values = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(values[0]);
    await expect(state).toHaveValue(values[0]);

    // Changing it replaces rather than accumulates.
    await state.selectOption(values[1]);
    await expect(state).toHaveValue(values[1]);
    expect(await state.evaluate((el: HTMLSelectElement) => el.selectedOptions.length)).toBe(1);
  });

  test('the entity state control is a single-select, so multiple states cannot be chosen', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');

    expect(await state.getAttribute('multiple')).toBeNull();
    expect(await state.evaluate((el: HTMLSelectElement) => el.multiple)).toBe(false);
    expect(await state.evaluate((el: HTMLSelectElement) => el.size)).toBeLessThanOrEqual(1);
  });

  test('CONTRADICTION: a blank entity state blocks the step, but the message is "Entity state is required", not "Response is mandatory."', async ({ page }) => {
    await gotoReCascade(page);
    await expect(page.locator('select[name="entityState"]')).toHaveValue('');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Entity state is required');
    expect(await fieldError(page, 'entityState')).toBe('Entity state is required');
    await expect(page.locator('select[name="entityState"]'), 'the step is held').toBeVisible();
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 26 — Entity District: the cascade the complainant screen does not have
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B26 — Entity District genuinely cascades from Entity State', () => {

  test('the district list is empty until a state is chosen', async ({ page }) => {
    await gotoReCascade(page);

    await expect(page.locator('select[name="entityDistrict"] option:not([value=""])')).toHaveCount(0);
    await expect(page.locator('select[name="entityDistrict"]')).toHaveValue('');
  });

  test('choosing a state populates the districts with exactly that state\'s districts', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const values = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    // Case 26.3: "values in the Entity District dropdown will be present based on the Entity state
    // value selected". This is the one cascade in the wizard that works as documented.
    const chosen = values[0];
    await state.selectOption(chosen);
    await expect(page.locator('select[name="entityDistrict"] option:not([value=""])').first())
      .toBeAttached({ timeout: 20000 });

    const res = await page.request.get(`${API_BASE}/api/v1/location/districts?state=${encodeURIComponent(chosen)}`);
    const body = await res.json();
    const expected = (body?.data ?? body ?? []) as string[];

    const rendered = await page.locator('select[name="entityDistrict"] option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    expect(rendered.sort(), `districts for ${chosen} must be the endpoint's response, not a fixed list`)
      .toEqual([...expected].sort());
  });

  test('the cascade is state-specific: two different states yield different district sets', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');
    const values = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    const sets: string[][] = [];
    for (const value of [values[0], values[1]]) {
      await state.selectOption(value);
      // The handler clears the list synchronously, so wait for the NEW fetch to land rather than
      // reading whatever the previous state left behind.
      await expect(district.locator('option:not([value=""])').first())
        .toBeAttached({ timeout: 20000 });
      sets.push(await district.locator('option:not([value=""])')
        .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value)));
    }

    expect(sets[0].sort().join('|'), 'two states must not offer identical districts')
      .not.toBe(sets[1].sort().join('|'));
  });

  test('changing the state clears a district already chosen, leaving no stale child value', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');
    const values = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    await state.selectOption(values[0]);
    await expect(district.locator('option:not([value=""])').first()).toBeAttached({ timeout: 20000 });
    const districts = await district.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await district.selectOption(districts[0]);
    await expect(district).toHaveValue(districts[0]);

    // The classic stale-child defect. onEntityStateChange clears district AND branch first, so it
    // does not occur.
    await state.selectOption(values[1]);
    await expect(district, 'the district from the previous state must not survive').toHaveValue('');
  });

  test('the entity district control is a single-select', async ({ page }) => {
    await gotoReCascade(page);
    const district = page.locator('select[name="entityDistrict"]');

    expect(await district.getAttribute('multiple')).toBeNull();
    expect(await district.evaluate((el: HTMLSelectElement) => el.multiple)).toBe(false);
  });

  test('CONTRADICTION: a blank entity district blocks the step, but the message is "Entity district is required", not "Response is mandatory."', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const values = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(values[0]);
    await expect(page.locator('select[name="entityDistrict"]')).toHaveValue('');

    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Entity district is required');
    expect(await fieldError(page, 'entityDistrict')).toBe('Entity district is required');
  });
});

// ═════════════════════════════════════════════════════════════════════════════════════════════════
// Block 27 — Entity Branch: a text input, not a dropdown, behind a dead endpoint
// ═════════════════════════════════════════════════════════════════════════════════════════════════

test.describe('QA-B27 — Entity Branch is a free-text field, not a cascading dropdown', () => {

  test('CONTRADICTION: the entity branch control is a text input — there is no dropdown to open, close or select from', async ({ page }) => {
    await gotoReCascade(page);
    const branch = page.locator('input[name="entityBranch"]');

    // Answers cases 27.6-27.10 (single-select / no multi-select / opens / closes) in one fact.
    await expect(branch).toBeVisible();
    expect(await branch.evaluate(el => el.tagName), 'the pack calls this a dropdown').toBe('INPUT');
    expect(await branch.getAttribute('type')).toBe('text');
    expect(await branch.getAttribute('multiple')).toBeNull();
    expect(await branch.getAttribute('list'), 'no <datalist> is wired to it').toBeNull();

    const branchGroup = page.locator('.form-group', { has: branch }).first();
    await expect(branchGroup.locator('select')).toHaveCount(0);
    await expect(branchGroup.locator('datalist')).toHaveCount(0);
  });

  test('the citizen types the branch name, and it is retained verbatim', async ({ page }) => {
    await gotoReCascade(page);
    const branch = page.locator('input[name="entityBranch"]');

    await branch.fill('Shivajinagar Main Branch');
    expect(await branch.inputValue()).toBe('Shivajinagar Main Branch');

    // No maxlength is declared, so nothing is truncated.
    expect(await branch.getAttribute('maxlength'), 'the branch field declares no length cap').toBeNull();
  });

  /**
   * DEFECT D-B5. Case 27.5: "values in Entity branch dropdown field will be displayed based on the
   * Entity state and Entity district selected". No values are ever displayed, for three independent
   * reasons, all of which this test proves at once.
   */
  test('DEFECT: no branch list is or can be populated — the /location/branches endpoint 404s, the error is swallowed, and the result is bound to nothing', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');

    // (a) The endpoint the component calls does not exist. LocationController maps only
    //     /pincode/{pincode}, /districts and /states.
    const values = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(values[0]);
    await expect(district.locator('option:not([value=""])').first()).toBeAttached({ timeout: 20000 });
    const districts = await district.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    const probe = await page.request.get(
      `${API_BASE}/api/v1/location/branches?district=${encodeURIComponent(districts[0])}`);
    expect(probe.status(), 'the branches endpoint has no controller mapping').toBe(404);

    // (b) Selecting a district fires that request. The citizen sees no error of any kind, because
    //     onEntityDistrictChange's handler is `error: () => {}`.
    const failed: number[] = [];
    page.on('response', res => {
      if (res.url().includes('/api/v1/location/branches')) failed.push(res.status());
    });
    await district.selectOption(districts[0]);
    await page.waitForTimeout(1500);
    expect(failed, 'the dead request really is issued on district change').toContain(404);
    await expect(page.locator('.field-error', { hasText: /branch/i }),
      'the 404 is swallowed silently — the citizen is told nothing').toHaveCount(0);

    // (c) And the field remains a text input regardless: even a 200 response would be discarded,
    //     because `entityBranches` is never referenced in the template.
    await expect(page.locator('input[name="entityBranch"]')).toBeVisible();
    await expect(page.locator('select[name="entityBranch"]')).toHaveCount(0);
  });

  test('changing the district clears a branch already typed', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');
    const branch = page.locator('input[name="entityBranch"]');

    const states = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(states[0]);
    await expect(district.locator('option:not([value=""])').first()).toBeAttached({ timeout: 20000 });
    const districts = await district.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));

    await district.selectOption(districts[0]);
    await branch.fill('Camp Branch');
    await expect(branch).toHaveValue('Camp Branch');

    // onEntityDistrictChange clears entityBranch, so the typed name cannot outlive its district.
    await district.selectOption(districts[1]);
    await expect(branch, 'a branch typed under the previous district must not survive').toHaveValue('');
  });

  test('CONTRADICTION: a blank entity branch blocks the step, but the message is "Entity branch is required", not "Response is mandatory."', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');

    const states = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(states[0]);
    await expect(district.locator('option:not([value=""])').first()).toBeAttached({ timeout: 20000 });
    const districts = await district.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await district.selectOption(districts[0]);

    await expect(page.locator('input[name="entityBranch"]')).toHaveValue('');
    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Entity branch is required');
    expect(await fieldError(page, 'entityBranch')).toBe('Entity branch is required');
  });

  test('a whitespace-only branch is rejected — it is trimmed before the mandatory check', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');

    const states = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(states[0]);
    await expect(district.locator('option:not([value=""])').first()).toBeAttached({ timeout: 20000 });
    const districts = await district.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await district.selectOption(districts[0]);

    await page.locator('input[name="entityBranch"]').fill('     ');
    const errors = await clickNextAndCollectErrors(page);
    expect(errors.join(' | ')).toContain('Entity branch is required');
  });

  test('state, district and a typed branch together advance to Complaint Details', async ({ page }) => {
    await gotoReCascade(page);
    const state = page.locator('select[name="entityState"]');
    const district = page.locator('select[name="entityDistrict"]');

    const states = await state.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await state.selectOption(states[0]);
    await expect(district.locator('option:not([value=""])').first()).toBeAttached({ timeout: 20000 });
    const districts = await district.locator('option:not([value=""])')
      .evaluateAll(opts => opts.map(o => (o as HTMLOptionElement).value));
    await district.selectOption(districts[0]);
    await page.locator('input[name="entityBranch"]').fill('Shivajinagar Main Branch');

    await page.locator('button.btn-next').click();
    await expect(page.locator('textarea[name="complaintText"]')).toBeVisible({ timeout: 20000 });
  });
});
