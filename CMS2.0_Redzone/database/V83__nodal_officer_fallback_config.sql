-- V83: global Ombudsman Admin fallback token (UST571)
-- MySQL version
--
-- UST571 routes a complaint to the "regional Ombudsman Admin" when the entity has neither a Nodal Officer
-- nor a Principal Nodal Officer on record. RBIO_ADMIN is a global Keycloak role with no region attribute,
-- so "regional" cannot be derived from roles. NodalOfficerResolver therefore derives the regional token
-- from an active OFFICE_THRESHOLD_CONFIG row for the processing office (officeId + '_ADMIN'), and uses
-- this config value only when the office is unknown or inactive.
--
-- It lives in SYSTEM_CONFIG rather than as a Java constant because the alternative — a hardcoded role
-- name — means every unmapped office misroutes to a queue nobody watches until someone ships a patch.
-- The Java default (RBIO_ADMIN) is retained as a last resort so a missing row degrades to today's
-- behaviour instead of a null assignee.

INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_BY, UPDATED_AT)
SELECT 'nodal.officer.fallback.global.admin.role',
       'RBIO_ADMIN',
       'UST571: role token that receives a complaint when the Regulated Entity has no NO/PNO on record and the processing office has no regional admin.',
       'V83_migration',
       NOW()
  FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM (SELECT CONFIG_KEY FROM SYSTEM_CONFIG) c
                    WHERE c.CONFIG_KEY = 'nodal.officer.fallback.global.admin.role');
