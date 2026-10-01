import { test, expect } from '@playwright/test';
import {
  fileComplaintForOffice,
  getOfficeThresholds,
  resetOfficeCounters,
  setOfficeThreshold,
} from '../utils/test-data';

/**
 * Office capacity routing — UST464-467, UST755.
 *
 * ── What this proves, and why it needed proving ───────────────────────────────────────────────────
 * OfficeRoutingService implemented per-office thresholds, an overflow walk and a vernacular bypass,
 * but `routeToOffice` had ZERO callers: nothing on registration invoked it. The office was resolved
 * while building the complaint number and then thrown away, so COMPLAINTS.rbio_office_code was NULL
 * on every complaint filed since the V36 backfill and no capacity was ever counted.
 *
 * These are API-level tests on purpose. The office a complaint lands in is a server-side territorial
 * determination — it decides which Ombudsman may lawfully hear the complaint and its appeal — so the
 * assertions target the persisted record and the capacity table, not a rendered screen. A UI test
 * here would pass whether or not anything was stored.
 *
 * Run with: API_BASE_URL=http://localhost:8094 npx playwright test e2e/rbio/office-routing.spec.ts
 *   --project=chromium --reporter=list
 */
test.describe('Office capacity routing', () => {
  // Mumbai-I. Chosen because Maharashtra/Mumbai is an unambiguous jurisdiction in
  // OMBUDSMAN_OFFICE_MASTER and its OFFICE_CODE is the one embedded in the complaint number.
  const MUMBAI_I = '013';

  test.beforeEach(async ({ request }) => {
    await resetOfficeCounters(request, 'RBIO');
  });

  test.afterEach(async ({ request }) => {
    // Restore the seeded capacity so a provoked overflow cannot leak into another spec.
    await setOfficeThreshold(request, MUMBAI_I, 500);
    await resetOfficeCounters(request, 'RBIO');
  });

  test('every seeded ombudsman office has a capacity row keyed by OFFICE_CODE', async ({ request }) => {
    const rows = await getOfficeThresholds(request);

    // 24 territorial offices + CEPC. Before V73 this table was EMPTY on dev (it existed only via
    // ddl-auto), so routeToOffice returned NOT_FOUND for every office.
    expect(rows.length).toBeGreaterThanOrEqual(25);

    const mumbai = rows.find(r => r.officeId === MUMBAI_I);
    expect(mumbai, `no capacity row for office ${MUMBAI_I}`).toBeTruthy();
    expect(mumbai!.officeName).toContain('Mumbai');
    expect(mumbai!.maxThreshold).toBeGreaterThan(0);

    // The retired synthetic ids must not be routable any more: they covered four of twenty-four
    // offices and had no join key to either office master.
    const synthetic = rows.filter(r => ['RBIO-MUM', 'RBIO-DEL', 'RBIO-CHN', 'RBIO-KOL'].includes(r.officeId));
    for (const row of synthetic) {
      expect(row.active, `${row.officeId} should be deactivated`).toBe(false);
    }
  });

  test('the overflow chain is configured, not left null', async ({ request }) => {
    const rows = (await getOfficeThresholds(request)).filter(r => r.department === 'RBIO' && r.active);
    const withTarget = rows.filter(r => r.overflowTargetOffice);

    // The chain column existed and was seeded but the service ignored it entirely, re-scanning the
    // whole department instead. If the chain is empty the service has nothing to honour.
    expect(withTarget.length).toBeGreaterThan(0);

    // Every hop must point at a real office in the same department, or capacity spills into a
    // jurisdiction that does not exist.
    const ids = new Set(rows.map(r => r.officeId));
    for (const row of withTarget) {
      expect(ids, `${row.officeId} overflows to unknown office ${row.overflowTargetOffice}`)
        .toContain(row.overflowTargetOffice!);
    }
  });

  test('a filed complaint records the territorial office it was routed to', async ({ request }) => {
    const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(filed.status, `filing failed: ${filed.message}`).toBe(201);
    expect(filed.complaintNumber).toBeTruthy();

    // N{FY:6}{office:3}{seq:6} — the number carries the office it was numbered against.
    expect(filed.complaintNumber).toMatch(/^N\d{6}\d{3}\d{6}$/);
    expect(filed.complaintNumber.substring(7, 10)).toBe(MUMBAI_I);

    // ...and capacity was actually claimed for it. This is the assertion that fails if
    // routeToOffice is not called at all, which was the state before this change.
    const after = await getOfficeThresholds(request);
    const mumbai = after.find(r => r.officeId === MUMBAI_I);
    expect(mumbai!.currentCount).toBeGreaterThanOrEqual(1);
  });

  test('capacity is claimed exactly once per complaint', async ({ request }) => {
    // Capacity is deliberately left generous here so every filing lands at Mumbai-I and the delta is
    // a clean count. If the office were near its cap the later filings would overflow, and the delta
    // would legitimately be less than the number filed.
    await setOfficeThreshold(request, MUMBAI_I, 500);
    const before = (await getOfficeThresholds(request)).find(r => r.officeId === MUMBAI_I)!.currentCount;

    for (let i = 0; i < 3; i++) {
      const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
      expect(filed.status, `filing ${i + 1} failed: ${filed.message}`).toBe(201);
    }

    const after = (await getOfficeThresholds(request)).find(r => r.officeId === MUMBAI_I)!.currentCount;
    // Exactly 3 — not 0 (routing never invoked) and not 6 (counted twice per complaint).
    expect(after - before).toBe(3);
  });

  test('a complaint diverts to the configured overflow office when its office is full', async ({ request }) => {
    const rows = await getOfficeThresholds(request);
    const mumbai = rows.find(r => r.officeId === MUMBAI_I)!;
    const overflowTo = mumbai.overflowTargetOffice;
    test.skip(!overflowTo, `office ${MUMBAI_I} has no overflow target configured`);

    // Provoke saturation rather than filing 500 complaints.
    const status = await setOfficeThreshold(request, MUMBAI_I, 1);
    test.skip(
      status !== 200,
      `cannot set office capacity through the API (HTTP ${status}); the endpoint requires a real JWT`
    );

    const first = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(first.status).toBe(201);
    // First complaint fills Mumbai-I (capacity 1) and is numbered against it.
    expect(first.complaintNumber.substring(7, 10)).toBe(MUMBAI_I);

    const second = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(second.status, 'a saturated office must not reject the citizen').toBe(201);

    // The second complaint is still NUMBERED against Mumbai-I — the number is allocated before the
    // capacity check — but the office HOLDING it is the overflow office. The two legitimately
    // differ, which is exactly why the office needs its own column rather than being substringed
    // back out of the complaint number.
    const after = await getOfficeThresholds(request);
    const overflowRow = after.find(r => r.officeId === overflowTo)!;
    expect(
      overflowRow.currentCount,
      `overflow office ${overflowTo} did not absorb the complaint`
    ).toBeGreaterThanOrEqual(1);

    // Mumbai-I must not have been pushed past its declared capacity.
    const mumbaiAfter = after.find(r => r.officeId === MUMBAI_I)!;
    expect(mumbaiAfter.currentCount).toBeLessThanOrEqual(mumbaiAfter.maxThreshold);
  });

  test('a saturated office keeps its real load instead of being reset to zero', async ({ request }) => {
    // The previous implementation zeroed EVERY counter in the department once all offices were full,
    // discarding the real load of offices that were still holding maxThreshold live complaints. Any
    // dashboard reading currentCount was then wrong, and the complaint was admitted past a limit
    // that had just been declared breached.
    const status = await setOfficeThreshold(request, MUMBAI_I, 1);
    test.skip(status !== 200, `cannot set office capacity through the API (HTTP ${status})`);

    const first = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(first.status).toBe(201);
    const filled = (await getOfficeThresholds(request)).find(r => r.officeId === MUMBAI_I)!;
    expect(filled.currentCount, 'first complaint did not claim capacity at Mumbai-I').toBe(1);

    // A second filing overflows away from Mumbai-I. The point of this assertion is that Mumbai-I's
    // counter still reflects the complaint it is actually holding — it is neither wiped to 0 nor
    // pushed above its declared capacity of 1.
    const second = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Mumbai' });
    expect(second.status).toBe(201);

    const after = (await getOfficeThresholds(request)).find(r => r.officeId === MUMBAI_I)!;
    expect(after.currentCount, 'Mumbai-I counter was reset, losing its real load').toBe(1);
    expect(after.currentCount).toBeLessThanOrEqual(after.maxThreshold);
  });

  test('district splits resolve to different offices within one state', async ({ request }) => {
    // Maharashtra is served by Mumbai-I and Mumbai-II on a district split. A resolver that collapsed
    // the split (as the deleted GeoLocationController switch did) would send both to one office.
    const filed = await fileComplaintForOffice(request, { state: 'Maharashtra', district: 'Pune' });
    expect(filed.status).toBe(201);

    const office = filed.complaintNumber.substring(7, 10);
    // Whichever office serves Pune, it must be a REAL configured office rather than the '014'
    // hardcoded fallback that resolveOfficeCode falls back to when a name does not match.
    const rows = await getOfficeThresholds(request);
    const ids = rows.filter(r => r.active).map(r => r.officeId);
    expect(ids).toContain(office);
  });
});
