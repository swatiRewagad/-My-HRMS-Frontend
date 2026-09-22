-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V72 — S3: reopen authority + reason vocabulary as CONFIGURATION (UST550-551)
--
-- UST550 restricts reopening to the Ombudsman, and UST551 requires a reason drawn from a fixed list plus
-- a free-text justification. Both are expressed as SYSTEM_CONFIG rows rather than as an enum or a role
-- literal, so that adding a reason — or delegating the authority to another rank — is an operational
-- change and not a release.
--
-- These rows are the SOURCE, but they are not the only line of defence: RbioWorkflowService carries the
-- same two values as compiled-in fallbacks, so a database missing these rows still restricts reopening
-- to the Ombudsman rather than allowing everyone. A missing configuration row must never widen an
-- authority.
--
-- Insert-if-absent, so re-running is safe and — importantly — so this does NOT overwrite a value an
-- operator has since changed. That is why there is no UPDATE here.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT * FROM (
    SELECT 'cms.rbio.reopen.roles' AS k,
           'RBIO_OMBUDSMAN,RBIO_ADMIN' AS v,
           'Roles permitted to reopen a closed RBIO complaint (UST550). RBIO_ADMIN retains the ability it held before the restriction so administrative correction of a mis-closure remains possible.' AS d,
           'V72' AS u,
           NOW() AS t
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM SYSTEM_CONFIG c WHERE c.CONFIG_KEY = seed.k
);

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT * FROM (
    SELECT 'cms.rbio.reopen.reasons' AS k,
           'APPELLATE_AUTHORITY,COURT_ORDER,CORRECTION_REQUIRED' AS v,
           'Permitted reopen reasons (UST551). The UI renders these as its dropdown; the server refuses any value outside the list.' AS d,
           'V72' AS u,
           NOW() AS t
) AS seed
WHERE NOT EXISTS (
    SELECT 1 FROM SYSTEM_CONFIG c WHERE c.CONFIG_KEY = seed.k
);
