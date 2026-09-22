/**
 * S2C — every user-facing string the assignment engine emits must exist in all ten locales.
 *
 * Acceptance criteria for this session were written as translation KEYS rather than English text, so
 * this suite is what makes that meaningful: it asserts each key resolves in all ten locales AND that
 * the nine non-English values are not the English string copied through, which is the failure mode a
 * simple "key exists" check misses.
 */
import { test, expect } from '@playwright/test';
import {
  assertKeysTranslatedInAllLocales,
  evictTranslationCache,
  fetchTranslations,
  TEN_LOCALES,
} from '../aa/aa-shared-fixtures';

/** Keys returned as messageKey by the engine, the pool admin API, reassignment and escalation. */
const OUTCOME_KEYS = [
  'aa.assignment.assigned',
  'aa.assignment.assigned_under_grace',
  'aa.assignment.vernacular_override',
  'aa.assignment.manual_override',
  'aa.assignment.pool_empty',
  'aa.assignment.pool_exhausted',
  'aa.assignment.error_reason_required',
  'aa.assignment.error_target_not_in_pool',
  'aa.assignment.error_target_unavailable',
];

const POOL_ADMIN_KEYS = [
  'aa.pool.threshold_updated',
  'aa.pool.threshold_updated_rebalance_pending',
  'aa.pool.skills_updated',
  'aa.pool.activation_updated',
  'aa.pool.activation_unchanged',
  'aa.pool.bulk_activation_complete',
  'aa.pool.bulk_activation_partial',
  'aa.pool.rebalance_complete',
  'aa.pool.deactivate_warning_pending_work',
  'aa.pool.deactivate_no_pending_work',
  'aa.pool.notify_deactivated_with_pending',
  'aa.pool.error_officer_not_found',
  'aa.pool.error_threshold_negative',
  'aa.pool.error_threshold_required',
  'aa.pool.error_no_officers_selected',
  'aa.pool.error_confirmation_required',
  'aa.pool.error_role_group_required',
  'aa.pool.error_unknown',
];

const REASSIGN_KEYS = [
  'aa.reassign.submitted',
  'aa.reassign.auto_approved',
  'aa.reassign.approved',
  'aa.reassign.rejected',
  'aa.reassign.withdrawn',
  'aa.reassign.notify_approved',
  'aa.reassign.notify_rejected',
  'aa.reassign.notify_pending_approval',
  'aa.reassign.error_appeal_required',
  'aa.reassign.error_reason_required',
  'aa.reassign.error_identity_unresolved',
  'aa.reassign.error_not_assigned',
  'aa.reassign.error_not_holder',
  'aa.reassign.error_already_pending',
  'aa.reassign.error_request_not_found',
  'aa.reassign.error_not_pending',
  'aa.reassign.error_not_requester',
];

const ESCALATION_KEYS = [
  'aa.escalation.unclaimed_draft',
  'aa.escalation.claimed',
  'aa.escalation.claim_not_holder',
  'aa.escalation.sweep_complete',
];

/** Every label rendered by the AA admin console template. */
const CONSOLE_KEYS = [
  'aa.admin.console_title',
  'aa.admin.officer_pool',
  'aa.admin.threshold',
  'aa.admin.current_workload',
  'aa.admin.status_active',
  'aa.admin.status_inactive',
  'aa.admin.status_on_leave',
  'aa.admin.at_threshold',
  'aa.admin.eligible',
  'aa.admin.bulk_activate',
  'aa.admin.bulk_deactivate',
  'aa.admin.edit_threshold',
  'aa.admin.skill_languages',
  'aa.admin.reason',
  'aa.admin.confirm',
  'aa.admin.cancel',
  'aa.admin.select_all',
  'aa.admin.pending_approvals',
  'aa.admin.approve',
  'aa.admin.reject',
  'aa.admin.rebalance',
  'aa.admin.audit_trail',
  'aa.admin.no_officers',
];

const ALL_KEYS = [
  ...OUTCOME_KEYS, ...POOL_ADMIN_KEYS, ...REASSIGN_KEYS, ...ESCALATION_KEYS, ...CONSOLE_KEYS,
];

test.describe('S2C assignment i18n', () => {
  test.beforeAll(async ({ request }) => {
    // The translation map is cached, so a freshly seeded key is otherwise invisible to the API.
    await evictTranslationCache(request);
  });

  test('assignment outcome keys resolve in all ten locales', async ({ request }) => {
    await assertKeysTranslatedInAllLocales(request, OUTCOME_KEYS);
  });

  test('pool administration keys resolve in all ten locales', async ({ request }) => {
    await assertKeysTranslatedInAllLocales(request, POOL_ADMIN_KEYS);
  });

  test('reassignment keys resolve in all ten locales', async ({ request }) => {
    await assertKeysTranslatedInAllLocales(request, REASSIGN_KEYS);
  });

  test('escalation keys resolve in all ten locales', async ({ request }) => {
    await assertKeysTranslatedInAllLocales(request, ESCALATION_KEYS);
  });

  test('admin console labels resolve in all ten locales', async ({ request }) => {
    await assertKeysTranslatedInAllLocales(request, CONSOLE_KEYS);
  });

  test('every locale carries the complete key set, so none silently falls back to English',
    async ({ request }) => {
      // Guards against a locale being partially seeded: a missing key would render as the raw key or
      // as English, and neither is acceptable for a citizen-facing legal workflow.
      for (const locale of TEN_LOCALES) {
        const map = await fetchTranslations(request, locale);
        const missing = ALL_KEYS.filter(key => !map[key] || map[key].trim() === '');
        expect(missing, `locale ${locale} is missing keys: ${missing.join(', ')}`).toEqual([]);
      }
    });

  test('non-English values are genuinely translated, not English copied through',
    async ({ request }) => {
      const english = await fetchTranslations(request, 'en');
      const copied: string[] = [];

      for (const locale of TEN_LOCALES.filter(l => l !== 'en')) {
        const map = await fetchTranslations(request, locale);
        for (const key of ALL_KEYS) {
          if (map[key] && english[key] && map[key].trim() === english[key].trim()) {
            copied.push(`${locale}:${key}`);
          }
        }
      }

      expect(copied, `these values are the English string verbatim: ${copied.join(', ')}`)
        .toEqual([]);
    });
});
