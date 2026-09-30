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
 * ── BLOCK 1'S ARROW-KEY CASE FAILS, AND IT NOW BLOCKS FILING ──────────────────────────────────────
 * The search results list is `<ul role="listbox">` of `<li role="option">` (html:86-95). Each option
 * binds `(mousedown)="selectEntityFromSearch(opt)"` and NOTHING ELSE. There is no `(keydown)`, no
 * ArrowDown/ArrowUp handling, no active-descendant tracking and no `tabindex` on the options —
 * confirmed by grepping the whole component for keyboard handlers: the only two are `onStepKeydown`
 * on steps 1 and 3, unrelated to this control. A keyboard-only citizen cannot use the search results
 * at all. Recorded as defect D-B4.
 *
 * When this was first written a native `<select>` stood beside the combobox and WAS keyboard-operable
 * (browsers give that for free), so the citizen was not locked out of filing. That select has since
 * been removed in the UI homogenisation — one question must have one control — which makes the
 * mouse-only combobox the only way to answer the question that gates the entire Scheme. D-B4 is
 * therefore re-raised as blocking: a keyboard-only citizen cannot file a complaint.
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

import { test, expect } from '../fixtures';
import {
  openWizard, gotoComplainantDetails, gotoComplainantDetailsWithEntity, gotoReDetails, gotoReCascade,
  fillComplainantMinimalAndAdvance, fieldError, clickNextAndCollectErrors, entityByDepartment,
  redirectAppApi, API_BASE, waitForEntityPicker, entitySearchOptions,
} from './helpers-forms-b';

test.beforeEach(async ({ page }) => {
  await redirectAppApi(page);
});

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
   * DEFECT D-B4, NOW BLOCKING. Case 1.3 asks for arrow-key + Enter selection from the searchable list.
   * The options bind only (mousedown), so a keyboard-only citizen cannot reach or activate them.
   *
   * This was a degraded-experience defect while the native <select> stood beside the combobox, because
   * browsers make a select keyboard-operable for free. The select has since been removed (see the
   * combobox note in helpers-forms-b.ts), so the mouse-only control is now the ONLY way to answer the
   * one question that gates the whole Scheme: a keyboard-only citizen cannot file a complaint at all.
   * Re-raised at that severity.
   *
   * Written as a failing-behaviour proof rather than test.fixme: the test PASSES by demonstrating the
   * keyboard does nothing, which is what the BA needs evidence of. The single-select case above
   * already covers the mouse path, so this documents the gap without pretending it is satisfied.
   */
  test('DEFECT: the searchable entity list cannot be driven by keyboard — arrow keys and Enter select nothing', async ({ page }) => {
    await openWizard(page);
    const box = await waitForEntityPicker(page);

    await box.click();
    await box.fill('');
    await box.pressSequentially('Bank');
    await expect(page.locator('li.entity-search-option').first()).toBeVisible({ timeout: 20000 });

    // No option is marked active, and none is focusable — the two things a keyboard listbox needs.
    const firstOption = page.locator('li.entity-search-option').first();
    expect(await firstOption.getAttribute('tabindex'), 'options are not focusable').toBeNull();
    expect(await box.getAttribute('aria-activedescendant'),
      'no active-descendant is tracked, so arrow keys have nothing to move').toBeNull();

    await box.press('ArrowDown');
    await box.press('ArrowDown');
    expect(await box.getAttribute('aria-activedescendant'),
      'ArrowDown does not highlight an option').toBeNull();
    await expect(page.locator('li.entity-search-option[aria-selected="true"]'),
      'ArrowDown selects nothing').toHaveCount(0);

    await box.press('Enter');
    // The recorded answer is what matters, and it is readable off aria-selected — not off the box,
    // which holds the citizen's typed 'Bank' and would look like a selection to a naive read.
    await expect(page.locator('li.entity-search-option[aria-selected="true"]'),
      'Enter records no entity: the keyboard path selects nothing at all').toHaveCount(0);

    // And the consequence, which is the severity: the citizen who got this far by keyboard is stopped.
    await page.locator('button.btn-next').click();
    await expect(page.locator('#eligibility-mandatory-error'),
      'a keyboard-only citizen cannot get past the entity question at all').toBeVisible();
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
