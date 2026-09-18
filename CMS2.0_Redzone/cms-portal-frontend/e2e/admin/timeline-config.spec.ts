import { test, expect, APIRequestContext } from '@playwright/test';

// Honours API_BASE_URL like every other spec. Hardcoding 8082 meant this spec always hit whichever
// backend happened to own that port, and failed with ECONNREFUSED when it was down — regardless of
// the instance actually under test.
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

const TIMELINES = `${API_BASE}/api/v1/admin/config/timelines`;

/**
 * Loads the timeline configs and ASSERTS the table is populated.
 *
 * Every test in this file used to open with `if (configs.length === 0) { test.skip(); return; }` — a
 * bare skip with no reason. An empty config table is not a reason to stop testing: it is the most
 * likely failure of the very endpoint under test (a missing seed migration, a wrong key prefix, a
 * filter that matches nothing), and six of the seven tests would have vanished silently while the run
 * reported green. The precondition is therefore asserted, so an empty table fails loudly and names
 * itself.
 */
async function loadConfigs(request: APIRequestContext): Promise<Array<Record<string, string>>> {
  const res = await request.get(TIMELINES);
  expect(res.status(), `GET ${TIMELINES} — is the backend at ${API_BASE} up?`).toBe(200);

  const configs = (await res.json()) as Array<Record<string, string>>;
  expect(Array.isArray(configs)).toBeTruthy();
  expect(
    configs.length,
    'SYSTEM_CONFIG holds no timeline.* rows. Either the seed data is missing or ' +
      'TimelineConfigService.getTimelineConfigs no longer matches the key prefix — both are ' +
      'defects in the feature under test, not reasons to skip.'
  ).toBeGreaterThan(0);
  return configs;
}

/** The key every write test operates on, resolved from live data rather than hardcoded. */
async function firstConfigKey(request: APIRequestContext): Promise<string> {
  const configs = await loadConfigs(request);
  return configs[0]['configKey'];
}

/**
 * Asserts a rejected write: 400 AND an explanatory body AND the stored value untouched.
 *
 * A status-only check is not enough here. This API returns 200 with `{"success": false}` on some
 * refusal paths, and even where it does return 400 a body-less refusal is unusable by the UI — the
 * admin screen has nothing to render, so the operator sees a silent no-op. The read-back is the part
 * that proves the rejection actually prevented the write rather than merely reporting one.
 */
async function expectRejected(
  request: APIRequestContext,
  configKey: string,
  value: string,
  expectedMessage: RegExp
): Promise<void> {
  const before = await currentValue(request, configKey);

  const res = await request.put(`${TIMELINES}/${configKey}`, {
    data: { value },
    headers: { 'X-User-Name': 'test-admin' },
  });
  expect(res.status(), `PUT value=${value} must be refused`).toBe(400);

  const body = await res.json();
  expect(body.error, `refusing value=${value} must explain why`).toBeTruthy();
  expect(String(body.error)).toMatch(expectedMessage);

  expect(
    await currentValue(request, configKey),
    `value=${value} was refused but the stored value changed anyway`
  ).toBe(before);
}

async function currentValue(request: APIRequestContext, configKey: string): Promise<string> {
  const configs = await loadConfigs(request);
  const row = configs.find((c) => c['configKey'] === configKey);
  expect(row, `config ${configKey} disappeared from the listing`).toBeTruthy();
  return row!['configValue'];
}

test.describe('Timeline Config — Admin API (UST115)', () => {

  test('admin sees current timeline values', async ({ request }) => {
    const configs = await loadConfigs(request);

    // Should contain at least one timeline config
    const timelineKeys = configs.map((c) => c['configKey']);
    const hasTimelineKey = timelineKeys.some((key) => key.startsWith('timeline.'));
    expect(hasTimelineKey).toBeTruthy();

    // Each config should have required fields
    for (const config of configs) {
      expect(config).toHaveProperty('configKey');
      expect(config).toHaveProperty('configValue');
      expect(config['configKey']).toMatch(/^timeline\./);
    }
  });

  test('admin updates a timeline value successfully', async ({ request }) => {
    const configs = await loadConfigs(request);

    const configKey = configs[0]['configKey'];
    const originalValue = configs[0]['configValue'];
    const newValue = originalValue === '30' ? '45' : '30';

    // Update the value
    const putResponse = await request.put(`${TIMELINES}/${configKey}`, {
      data: { value: newValue },
      headers: { 'X-User-Name': 'test-admin' }
    });
    expect(putResponse.ok()).toBeTruthy();

    const updated = await putResponse.json();
    expect(updated.configValue).toBe(newValue);
    expect(updated.configKey).toBe(configKey);

    // Read back through GET rather than trusting the PUT's own echo: the response body is built from
    // the in-memory entity, so it would report the new value even if the row were never committed.
    expect(await currentValue(request, configKey)).toBe(newValue);

    // Restore original value
    await request.put(`${TIMELINES}/${configKey}`, {
      data: { value: originalValue },
      headers: { 'X-User-Name': 'test-admin' }
    });
  });

  test('invalid value (0) returns validation error', async ({ request }) => {
    const configKey = await firstConfigKey(request);
    await expectRejected(request, configKey, '0', /between 1 and 365/i);
  });

  test('invalid value (-1) returns validation error', async ({ request }) => {
    const configKey = await firstConfigKey(request);
    await expectRejected(request, configKey, '-1', /between 1 and 365/i);
  });

  test('invalid value (999) returns validation error', async ({ request }) => {
    const configKey = await firstConfigKey(request);
    // Above the 365 ceiling: the message must say so, not merely "invalid". An operator who typed
    // 999 needs to learn the ceiling exists.
    await expectRejected(request, configKey, '999', /between 1 and 365/i);
  });

  test('non-numeric value returns validation error', async ({ request }) => {
    const configKey = await firstConfigKey(request);
    // A DIFFERENT message from the range failures — the two problems have different fixes, and a
    // single generic string would leave the operator guessing.
    await expectRejected(request, configKey, 'abc', /valid number/i);
  });

  test('an empty value is refused', async ({ request }) => {
    const configKey = await firstConfigKey(request);
    await expectRejected(request, configKey, '', /required/i);
  });

  test('a key outside the timeline namespace cannot be updated', async ({ request }) => {
    // The service guards the prefix, so a caller cannot use this admin screen to rewrite arbitrary
    // SYSTEM_CONFIG rows — including the security-relevant ones.
    const res = await request.put(`${TIMELINES}/cms.reassign.bulk.max_records`, {
      data: { value: '5' },
      headers: { 'X-User-Name': 'test-admin' },
    });
    expect(res.status()).toBe(400);
    expect(String((await res.json()).error)).toMatch(/timeline\./i);
  });

  test('audit log shows the change after update', async ({ request }) => {
    const configs = await loadConfigs(request);

    const configKey = configs[0]['configKey'];
    const originalValue = configs[0]['configValue'];
    // Derived from the value actually read, not from an assumed 30/60 pair. An earlier test in this
    // file writes 45, so a hardcoded "30 means 60, otherwise 30" flipped to a value that was already
    // stored — the update became a no-op and the audit row never carried the expected newValue.
    const newValue = String(Number(originalValue) === 55 ? 56 : 55);

    // Make a change
    const put = await request.put(`${TIMELINES}/${configKey}`, {
      data: { value: newValue },
      headers: { 'X-User-Name': 'audit-test-admin' }
    });
    expect(put.status(), 'the change being audited must itself have succeeded').toBe(200);

    // Check audit log
    const auditResponse = await request.get(`${TIMELINES}/audit/${configKey}`);
    expect(auditResponse.ok()).toBeTruthy();

    const logs = await auditResponse.json();
    expect(Array.isArray(logs)).toBeTruthy();
    expect(logs.length).toBeGreaterThan(0);

    // Most recent log should reflect the change
    const latestLog = logs[0];
    expect(latestLog.configKey).toBe(configKey);
    expect(latestLog.newValue).toBe(newValue);
    expect(latestLog.oldValue).toBe(originalValue);
    expect(latestLog.changedBy).toBe('audit-test-admin');
    expect(latestLog.changedAt).toBeTruthy();

    // Restore original value
    await request.put(`${TIMELINES}/${configKey}`, {
      data: { value: originalValue },
      headers: { 'X-User-Name': 'audit-test-admin' }
    });
  });

  test('a refused change writes no audit row', async ({ request }) => {
    // An audit trail that records attempts that never happened is as misleading as one that misses
    // changes that did.
    const configKey = await firstConfigKey(request);
    const before = await request.get(`${TIMELINES}/audit/${configKey}`);
    const countBefore = ((await before.json()) as unknown[]).length;

    await expectRejected(request, configKey, '999', /between 1 and 365/i);

    const after = await request.get(`${TIMELINES}/audit/${configKey}`);
    expect(((await after.json()) as unknown[]).length).toBe(countBefore);
  });
});
