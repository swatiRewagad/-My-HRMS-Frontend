import { test, expect } from '@playwright/test';

// Honours API_BASE_URL like every other spec. Hardcoding 8082 meant this spec always hit whichever
// backend happened to own that port, and failed with ECONNREFUSED when it was down — regardless of
// the instance actually under test.
const API_BASE = process.env['API_BASE_URL'] || 'http://localhost:8082';

test.describe('Timeline Config — Admin API (UST115)', () => {

  test('admin sees current timeline values', async ({ request }) => {
    const response = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    expect(response.ok()).toBeTruthy();

    const configs = await response.json();
    expect(Array.isArray(configs)).toBeTruthy();

    // Should contain at least one timeline config
    const timelineKeys = configs.map((c: any) => c.configKey);
    const hasTimelineKey = timelineKeys.some((key: string) => key.startsWith('timeline.'));
    expect(hasTimelineKey).toBeTruthy();

    // Each config should have required fields
    for (const config of configs) {
      expect(config).toHaveProperty('configKey');
      expect(config).toHaveProperty('configValue');
      expect(config.configKey).toMatch(/^timeline\./);
    }
  });

  test('admin updates a timeline value successfully', async ({ request }) => {
    // First get current configs
    const getResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    const configs = await getResponse.json();
    if (configs.length === 0) {
      test.skip();
      return;
    }

    const configKey = configs[0].configKey;
    const originalValue = configs[0].configValue;
    const newValue = originalValue === '30' ? '45' : '30';

    // Update the value
    const putResponse = await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: newValue },
      headers: { 'X-User-Name': 'test-admin' }
    });
    expect(putResponse.ok()).toBeTruthy();

    const updated = await putResponse.json();
    expect(updated.configValue).toBe(newValue);
    expect(updated.configKey).toBe(configKey);

    // Restore original value
    await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: originalValue },
      headers: { 'X-User-Name': 'test-admin' }
    });
  });

  test('invalid value (0) returns validation error', async ({ request }) => {
    const getResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    const configs = await getResponse.json();
    if (configs.length === 0) {
      test.skip();
      return;
    }

    const configKey = configs[0].configKey;

    const putResponse = await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: '0' },
      headers: { 'X-User-Name': 'test-admin' }
    });
    expect(putResponse.status()).toBe(400);

    const body = await putResponse.json();
    expect(body.error).toBeTruthy();
  });

  test('invalid value (-1) returns validation error', async ({ request }) => {
    const getResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    const configs = await getResponse.json();
    if (configs.length === 0) {
      test.skip();
      return;
    }

    const configKey = configs[0].configKey;

    const putResponse = await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: '-1' },
      headers: { 'X-User-Name': 'test-admin' }
    });
    expect(putResponse.status()).toBe(400);
  });

  test('invalid value (999) returns validation error', async ({ request }) => {
    const getResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    const configs = await getResponse.json();
    if (configs.length === 0) {
      test.skip();
      return;
    }

    const configKey = configs[0].configKey;

    const putResponse = await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: '999' },
      headers: { 'X-User-Name': 'test-admin' }
    });
    expect(putResponse.status()).toBe(400);
  });

  test('non-numeric value returns validation error', async ({ request }) => {
    const getResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    const configs = await getResponse.json();
    if (configs.length === 0) {
      test.skip();
      return;
    }

    const configKey = configs[0].configKey;

    const putResponse = await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: 'abc' },
      headers: { 'X-User-Name': 'test-admin' }
    });
    expect(putResponse.status()).toBe(400);
  });

  test('audit log shows the change after update', async ({ request }) => {
    const getResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines`);
    const configs = await getResponse.json();
    if (configs.length === 0) {
      test.skip();
      return;
    }

    const configKey = configs[0].configKey;
    const originalValue = configs[0].configValue;
    // Derived from the value actually read, not from an assumed 30/60 pair. An earlier test in this
    // file writes 45, so a hardcoded "30 means 60, otherwise 30" flipped to a value that was already
    // stored — the update became a no-op and the audit row never carried the expected newValue.
    const newValue = String(Number(originalValue) === 55 ? 56 : 55);

    // Make a change
    await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: newValue },
      headers: { 'X-User-Name': 'audit-test-admin' }
    });

    // Check audit log
    const auditResponse = await request.get(`${API_BASE}/api/v1/admin/config/timelines/audit/${configKey}`);
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
    await request.put(`${API_BASE}/api/v1/admin/config/timelines/${configKey}`, {
      data: { value: originalValue },
      headers: { 'X-User-Name': 'audit-test-admin' }
    });
  });
});
