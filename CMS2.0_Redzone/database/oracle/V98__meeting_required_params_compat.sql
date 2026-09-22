-- ============================================================
-- V98 — Relax the table-level required params on the meeting actions
-- Session S5 (UST496, and backward compatibility). Oracle counterpart of MySQL V100.
-- ============================================================
--
-- See database/V100__meeting_required_params_compat.sql for the full rationale. In brief:
--
--   The SCHEDULE_MEETING row was seeded declaring meetingTime and participants REQUIRED, which refused a
--   caller that was working: the pre-existing conciliation flow posts a full ISO timestamp as meetingDate
--   (already stating the time) and names the attending parties in the remarks, because no participants field
--   existed when it was written. The table's requiredParams check runs before any service logic, so those
--   complete requests answered HTTP 400.
--
--   Both fields remain mandatory under UST496, enforced in RbioMeetingService — which can DERIVE the time
--   from a supplied timestamp and read the parties from the officer's own words. Where nothing states them
--   the action is still refused: defaulting participants would make the Ombudsman's record assert who was
--   summoned when nobody said so.
--
--   A migration is required because the transition seeder is insert-if-absent, deliberately, so that a
--   restart never silently re-points a live workflow arrow. Correcting the Java declaration alone does not
--   rewrite already-seeded rows.
--
-- Scoped BY ACTION_CODE and owner so it cannot touch another session's rows. Re-running is safe.
-- ============================================================

BEGIN
    UPDATE RBIO_WORKFLOW_TRANSITION
       SET REQUIRED_PARAMS = 'meetingDate|hearingDate'
     WHERE ACTION_CODE = 'SCHEDULE_MEETING'
       AND OWNED_BY = 'S5'
       AND REQUIRED_PARAMS <> 'meetingDate|hearingDate';

    -- RESCHEDULE_MEETING keeps its mandatory reason (UST502): a NEW action with no legacy caller, so the
    -- reason is enforced at both levels.
    UPDATE RBIO_WORKFLOW_TRANSITION
       SET REQUIRED_PARAMS = 'meetingDate|hearingDate,rescheduleReason|reason'
     WHERE ACTION_CODE = 'RESCHEDULE_MEETING'
       AND OWNED_BY = 'S5'
       AND REQUIRED_PARAMS <> 'meetingDate|hearingDate,rescheduleReason|reason';

    COMMIT;
END;
/
