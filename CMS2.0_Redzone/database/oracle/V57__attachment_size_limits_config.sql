-- ============================================================
-- V57 — Attachment size limits become configuration (item 13.1)
-- Oracle counterpart of MySQL V59. The two directories' V-numbers are NOT in sync;
-- V57 is the next free number in this tree (V56 then jumps to V69).
--
-- Full rationale is in the MySQL counterpart (database/V59). The short version:
--
--   The ruling is 5 MB per file and 25 MB per complaint, both configurable. Neither was implemented.
--   The per-file limit was compiled into four disagreeing places, NO SYSTEM_CONFIG row existed, and
--   the citizen-facing upload hint already promised 5 MB in all ten locales — so the interface
--   contradicted enforcement and a 3 MB attachment was refused after the UI said 5 MB was fine.
--
--   These rows are now the single source of truth, read by UploadLimitsService and served to the
--   browser by GET /api/v1/config/upload-limits, so the hint and the enforcement derive from one value.
--
--   spring.servlet.multipart.max-file-size stays at 50 MB deliberately: it is the outer bound that
--   prevents buffering a hostile multipart body, not the product rule, and it must remain ABOVE the
--   limit below or rejection surfaces as a container error instead of a translated message.
--
-- Re-runnable: every INSERT is guarded on NOT EXISTS.
-- ============================================================

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.attachments.max_file_size_bytes', '5242880',
       'Maximum size of a single attachment, in bytes. Product ruling: 5 MB. Must stay below spring.servlet.multipart.max-file-size.',
       'V57_migration', SYSTIMESTAMP
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.attachments.max_file_size_bytes');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.attachments.max_total_size_bytes', '26214400',
       'Maximum combined size of all attachments on one complaint, in bytes. Product ruling: 25 MB.',
       'V57_migration', SYSTIMESTAMP
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.attachments.max_total_size_bytes');

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_by, updated_at)
SELECT 'cms.attachments.max_file_count', '10',
       'Maximum number of attachments on one complaint (NFR-006).',
       'V57_migration', SYSTIMESTAMP
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG WHERE config_key = 'cms.attachments.max_file_count');

-- The hint text that contradicted enforcement. A baked-in digit cannot track a configuration change,
-- which is the defect; the replacement keys carry a {{size}} placeholder interpolated from the row above.
--
-- A corrective UPDATE is required because seeders are insert-if-absent by key code: a row already
-- holding the wrong text is never fixed by editing the seeder. Scoped BY KEY CODE, since the localized
-- values are in native scripts and an English substring would match none of them.
UPDATE TRANSLATIONS t
SET t.value = REPLACE(t.value, '2 MB', '5 MB')
WHERE t.value LIKE '%2 MB%'
  AND t.translation_key_id IN (
      SELECT k.id FROM TRANSLATION_KEYS k WHERE k.code = 'aa.upload.error_file_too_large');

UPDATE TRANSLATION_KEYS
SET default_value = REPLACE(default_value, '2 MB', '5 MB')
WHERE code = 'aa.upload.error_file_too_large'
  AND default_value LIKE '%2 MB%';

COMMIT;
