-- ============================================================
-- V59 — Attachment size limits become configuration (item 13.1)
-- ============================================================
--
-- THE PROBLEM THIS FIXES
--
--   The product ruling is 5 MB per file and 25 MB per complaint, BOTH CONFIGURABLE. None of that was
--   implemented. The per-file limit was compiled into four places that did not agree with the ruling:
--
--     FileStorageConfig.maxFileSize            2 MB
--     application.yml cms.attachments          2 MB
--     IntakeAttachmentValidator @Value          2 MB
--     environment{,.prod,.openshift}.ts         maxFileSizeMB: 2
--
--   and NO row existed in SYSTEM_CONFIG at all, so "configurable" was not true in any sense — changing
--   the limit required editing four files and cutting a release.
--
--   Worse, the citizen-facing hint `eligibility.upload_hint` is seeded in all ten locales saying 5MB.
--   So the interface promised 5 MB while the server rejected at 2 MB. A citizen attaching a 3 MB bank
--   statement was refused by a system that had just told them 5 MB was fine.
--
-- WHY A CONFIG ROW RATHER THAN A CONSTANT
--
--   These rows are now the single source of truth, read by UploadLimitsService. The yml values survive
--   only as the fallback for a database with no row. The frontend no longer compiles a copy either; it
--   reads GET /api/v1/config/upload-limits. That is what makes the hint text and the enforcement
--   incapable of disagreeing again — both derive from this row.
--
-- WHAT IS DELIBERATELY NOT CHANGED
--
--   spring.servlet.multipart.max-file-size stays at 50 MB. That is the outer bound that stops a
--   malicious multipart body from being buffered, NOT the product rule. It must remain ABOVE the limit
--   below, otherwise a 6 MB upload would fail as a raw container error instead of the translated
--   "each file must be 5 MB or smaller" message.
--
-- RE-RUNNABLE: every INSERT is guarded with WHERE NOT EXISTS, so replaying this file is a no-op rather
-- than a duplicate-key failure. V5/V6 are not re-runnable for exactly the lack of this guard.

-- 5 MB per file.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.attachments.max_file_size_bytes', '5242880',
       'Maximum size of a single attachment, in bytes. Product ruling: 5 MB. Must stay below spring.servlet.multipart.max-file-size.',
       'V59_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.attachments.max_file_size_bytes');

-- 25 MB across every attachment on one complaint.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.attachments.max_total_size_bytes', '26214400',
       'Maximum combined size of all attachments on one complaint, in bytes. Product ruling: 25 MB.',
       'V59_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.attachments.max_total_size_bytes');

-- File count was already 10 and already consistent; it is moved here only so all three limits are
-- administered in one place rather than two.
INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.attachments.max_file_count', '10',
       'Maximum number of attachments on one complaint (NFR-006).',
       'V59_migration', NOW()
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.attachments.max_file_count');

-- ── The hint text that contradicted enforcement ──────────────────────────────────────────────
--
-- `eligibility.upload_hint` and the AA upload errors bake a DIGIT into prose ("2 MB", "5MB"). A baked
-- digit cannot track a configuration change, which is the whole defect. The replacement keys added by
-- UiShellTranslationSeeder carry a {{size}} placeholder instead and are interpolated from the config
-- row above.
--
-- Corrective UPDATE, not an insert: seeders are insert-if-absent by key code, so a row that already
-- holds the wrong text is never corrected by editing the seeder. Scoped BY KEY CODE because the
-- localized values are in native scripts and an English substring match would find none of them.
UPDATE TRANSLATIONS t
JOIN TRANSLATION_KEYS k ON k.id = t.translation_key_id
SET t.value = REPLACE(t.value, '2 MB', '5 MB')
WHERE k.code = 'aa.upload.error_file_too_large'
  AND t.value LIKE '%2 MB%';

UPDATE TRANSLATION_KEYS
SET default_value = REPLACE(default_value, '2 MB', '5 MB')
WHERE code = 'aa.upload.error_file_too_large'
  AND default_value LIKE '%2 MB%';
