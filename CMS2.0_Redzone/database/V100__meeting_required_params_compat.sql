-- ============================================================
-- V100 — Relax the table-level required params on the meeting actions
-- Session S5 (UST496, and backward compatibility with the pre-existing conciliation flow)
-- ============================================================
--
-- WHY
--
--   The SCHEDULE_MEETING / RESCHEDULE_MEETING rows were seeded declaring meetingTime and participants as
--   REQUIRED_PARAMS. That broke a caller that was working: the pre-existing conciliation flow posts
--   `meetingDate: '2026-10-01T10:00:00'` — a full ISO timestamp that ALREADY states the time — and names
--   the attending parties in the remarks text, because a participants field did not exist when it was
--   written. The transition table's requiredParams check runs before any service logic, so those requests
--   were refused with HTTP 400 even though they were complete on their own terms.
--
--   Time and participants remain equally mandatory under UST496. They are enforced in RbioMeetingService,
--   which can do what a table row cannot: DERIVE the time from a supplied timestamp, and read the parties
--   from the phrase the officer actually wrote. Where nothing states them, the action is still refused —
--   defaulting participants would make the Ombudsman's own record assert who was summoned when nobody said.
--
-- WHY A MIGRATION AND NOT JUST THE SEEDER
--
--   The transition seeder is insert-if-absent on (ACTION_CODE, ROLE_NAME, FROM_STATUS), deliberately, so
--   that a restart never silently re-points a live workflow arrow. Correcting the Java declaration therefore
--   does NOT rewrite rows already seeded — this UPDATE is the other half, exactly as the seeder's own class
--   comment requires.
--
-- Scoped BY ACTION_CODE and owner, so it cannot touch another session's rows. Re-running is safe: the
-- UPDATE is idempotent and the WHERE clause stops matching once applied.
-- ============================================================

-- SCHEDULE_MEETING: the date alone is table-required; time and participants move to the service.
UPDATE RBIO_WORKFLOW_TRANSITION
   SET REQUIRED_PARAMS = 'meetingDate|hearingDate'
 WHERE ACTION_CODE = 'SCHEDULE_MEETING'
   AND OWNED_BY = 'S5'
   AND REQUIRED_PARAMS <> 'meetingDate|hearingDate';

-- RESCHEDULE_MEETING keeps its mandatory reason (UST502): it is a NEW action with no legacy caller to stay
-- compatible with, so the reason is enforced at both levels.
UPDATE RBIO_WORKFLOW_TRANSITION
   SET REQUIRED_PARAMS = 'meetingDate|hearingDate,rescheduleReason|reason'
 WHERE ACTION_CODE = 'RESCHEDULE_MEETING'
   AND OWNED_BY = 'S5'
   AND REQUIRED_PARAMS <> 'meetingDate|hearingDate,rescheduleReason|reason';
