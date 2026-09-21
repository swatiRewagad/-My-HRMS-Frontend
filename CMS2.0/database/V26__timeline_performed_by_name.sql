-- V26: PERFORMED_BY_NAME on COMPLAINT_TIMELINE and APPEAL_TIMELINE (MySQL)
--
-- The audit endpoints returned PERFORMED_BY, which is the Keycloak login username — a credential, and
-- the same string an attacker needs to start guessing a password. The timeline still has to name who
-- acted, so the name travels beside the username instead of replacing it: the username stays for
-- correlation with COMPLAINTS.ASSIGNED_OFFICER and the audit log, and PERFORMED_BY_NAME is what the
-- API now returns.
--
-- Captured at write time from WF_OFFICER_POOL.DISPLAY_NAME rather than joined at read time, for two
-- reasons. A timeline is an audit record: it must keep showing the name the officer had when they
-- acted, even after a later marital, legal or structural rename, which a join would silently rewrite.
-- And the read path renders an entire history in one response, where a join would cost a lookup per
-- row on a table that has no index on USER_ID until V25.
--
-- NULL is expected and is not a defect. It covers every row written before this column existed, and
-- every actor that is not a person — the workflow writes 'System', 'system' and role names such as
-- 'REVIEWER' as the actor. No backfill from WF_OFFICER_POOL is attempted: for historical rows the
-- pool's current DISPLAY_NAME is not the name that was in force at the time, which is the exact
-- guarantee this column exists to provide. Readers fall back to PERFORMED_BY when this is NULL.
--
-- VARCHAR(250) exceeds WF_OFFICER_POOL.DISPLAY_NAME's VARCHAR(200) so a later widening of the source
-- column cannot truncate an audit record.
--
-- Oracle equivalent: database/oracle/V26__timeline_performed_by_name.sql
-- MySQL has no ADD COLUMN IF NOT EXISTS, so re-running fails with error 1060 (duplicate column name).

ALTER TABLE COMPLAINT_TIMELINE
    ADD COLUMN PERFORMED_BY_NAME  VARCHAR(250)  NULL;

ALTER TABLE APPEAL_TIMELINE
    ADD COLUMN PERFORMED_BY_NAME  VARCHAR(250)  NULL;
