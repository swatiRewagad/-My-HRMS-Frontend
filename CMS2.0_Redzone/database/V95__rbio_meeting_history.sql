-- ============================================================
-- V95 — Conciliation meetings as an append-only history, with participants
-- Session S5 (UST496-503, 643-651)
-- ============================================================
--
-- WHY A TABLE AT ALL
--
--   Meeting particulars had exactly two homes before this: COMPLAINTS.conciliation_date (a single
--   scalar) and the free-text `remarks` of a COMPLAINT_TIMELINE row. Both are overwritten by the next
--   schedule, so "the meeting was moved twice" and "the meeting was always on this date" were the same
--   stored fact. UST502-503 require the PREVIOUS meeting details to be RETAINED when a meeting is
--   rescheduled, which that shape cannot express at all.
--
--   Worse, nothing was persisted in practice. The FX_MEETING_DATE side effect read `meetingDate`
--   while the only client sent `hearingDate`, so conciliation_date stayed NULL after every schedule
--   and the meeting time, venue and participants had no column to land in even in principle.
--
-- APPEND-ONLY, MODELLED ON APPEAL_HEARING
--
--   One row per meeting EVENT (SCHEDULED | RESCHEDULED | COMPLETED | CANCELLED). Superseding a row
--   stamps superseded_at / superseded_by_id on it — a LINKAGE, never a rewrite of the particulars.
--   The operative meeting is the single row per complaint with superseded_at IS NULL. An outcome is
--   recorded by APPENDING a COMPLETED row, so the date and participants the parties were actually
--   notified about survive verbatim. This deliberately mirrors APPEAL_HEARING rather than inventing a
--   second history shape: the AA module already proved the pattern for a statutory hearing.
--
-- WHY THE MEETING IS NOT HOSTED HERE
--
--   UST501 is explicit that the meeting is conducted OUTSIDE the CMS application. Only the particulars
--   and the minutes are captured. There is therefore no join/attendance/link column — attendance is an
--   officer's assertion (participant_confirmed), not something the system observes.
--
-- WHY DATE AND TIME ARE SEPARATE COLUMNS
--
--   The officer fills two distinct form controls and both are independently mandatory (UST496). A
--   single timestamp cannot express "date supplied, time missing", which is the exact refusal the
--   story demands. It also avoids the live defect where a date-only 'yyyy-MM-dd' from an <input
--   type=date> failed LocalDateTime.parse and was swallowed at log.debug.
--
-- PARTICIPANTS ARE A CHILD TABLE, CAPPED AT SIX
--
--   UST498 caps ADDITIONAL entity participants at six, "matching the general entity-add limit". The
--   cap is NOT re-implemented here: RbioAdditionalEntityService.assertCapAllowsOneMore is the single
--   owner of that rule and its own javadoc names S5's meeting participants as a required caller. This
--   table stores who was invited and who is confirmed; it does not store a second cap constant.
--
-- ALL COLUMNS NULLABLE EXCEPT THE IDENTIFYING ONES. ddl-auto=update runs against a database shared by
-- seven sessions, so a NOT NULL on anything Hibernate might create differently would be permanent and
-- would break another session's inserts. Nothing is backfilled: inventing a meeting history for a
-- historical complaint would assert meetings that never happened.
--
-- Re-running is safe: MySQL 8.4 has no ADD COLUMN / CREATE INDEX IF NOT EXISTS, so every table is
-- CREATE TABLE IF NOT EXISTS, every ALTER is guarded on information_schema, every seed is
-- INSERT ... WHERE NOT EXISTS.
-- ============================================================

DROP PROCEDURE IF EXISTS s5_mtg_add_column;
DROP PROCEDURE IF EXISTS s5_mtg_add_index;

DELIMITER $$

CREATE PROCEDURE s5_mtg_add_column(
    IN p_table VARCHAR(64), IN p_column VARCHAR(64), IN p_definition TEXT)
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND COLUMN_NAME = p_column) THEN
        SET @ddl = CONCAT('ALTER TABLE ', p_table, ' ADD COLUMN ', p_column, ' ', p_definition);
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

CREATE PROCEDURE s5_mtg_add_index(
    IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_columns VARCHAR(255))
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = p_table
                      AND INDEX_NAME = p_index) THEN
        SET @ddl = CONCAT('CREATE INDEX ', p_index, ' ON ', p_table, ' (', p_columns, ')');
        PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
    END IF;
END $$

DELIMITER ;

-- ─────────────────────────────────────────────────────────────
-- 1. The meeting event history
-- ─────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS RBIO_MEETING (
    ID                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    COMPLAINT_NUMBER    VARCHAR(50)   NOT NULL,

    -- 1-based per complaint. Gives the parties a stable "third meeting" reference that survives
    -- rescheduling, which an id cannot because ids are also consumed by superseded rows.
    SEQUENCE_NO         INT           NULL,

    -- SCHEDULED | RESCHEDULED | COMPLETED | CANCELLED
    EVENT_TYPE          VARCHAR(20)   NOT NULL,

    -- Separate date and time: both are independently mandatory, see header.
    MEETING_DATE        DATE          NULL,
    MEETING_TIME        VARCHAR(10)   NULL,

    -- ENTITY | COMPLAINANT | BOTH (UST496). Stored as the officer's selection; the per-entity
    -- invitee rows live in RBIO_MEETING_PARTICIPANT.
    PARTICIPANTS        VARCHAR(20)   NULL,

    MEETING_MODE        VARCHAR(20)   NULL,
    MEETING_VENUE       VARCHAR(500)  NULL,

    -- Mandatory for RESCHEDULED at the service boundary (UST502). Kept nullable in the schema
    -- because a SCHEDULED row legitimately has none.
    RESCHEDULE_REASON   VARCHAR(1000) NULL,

    -- COMPLETED rows only (UST499). Y | N — the entity's acceptance of the settlement discussed.
    -- Nullable, not defaulted: "not yet recorded" and "the entity said no" must stay distinguishable,
    -- and defaulting to 'N' would fabricate a refusal the entity never made.
    ENTITY_ACCEPTED     CHAR(1)       NULL,

    -- The Minutes of Meeting free text. Mandatory for COMPLETED at the service boundary.
    MINUTES_OF_MEETING  TEXT          NULL,

    -- NULL means this is the operative meeting row.
    SUPERSEDED_AT       DATETIME      NULL,
    -- The event row that replaced this one, so the chain is walkable in either direction.
    SUPERSEDED_BY_ID    BIGINT        NULL,

    PERFORMED_BY        VARCHAR(200)  NULL,
    PERFORMED_BY_ROLE   VARCHAR(50)   NULL,
    PERFORMED_AT        DATETIME      NULL,

    CREATED_AT          DATETIME      NULL
);

-- The operative-row lookup runs on every meeting screen load and every save; the complaint lookup
-- backs the history tab. Without these both scan a table that will hold the national meeting volume.
CALL s5_mtg_add_index('RBIO_MEETING', 'idx_rbio_meeting_complaint', 'COMPLAINT_NUMBER');
CALL s5_mtg_add_index('RBIO_MEETING', 'idx_rbio_meeting_active', 'COMPLAINT_NUMBER, SUPERSEDED_AT');
CALL s5_mtg_add_index('RBIO_MEETING', 'idx_rbio_meeting_date', 'MEETING_DATE');

-- ─────────────────────────────────────────────────────────────
-- 2. Additional entity participants (UST498)
-- ─────────────────────────────────────────────────────────────
-- One row per invited entity beyond the primary. PARTICIPANT_CONFIRMED is what the "participants list
-- displays all confirmed attendee entities" requirement reads: an invitee is not an attendee, and
-- conflating the two would let the MOM letter assert attendance nobody confirmed.
CREATE TABLE IF NOT EXISTS RBIO_MEETING_PARTICIPANT (
    ID                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    MEETING_ID            BIGINT        NULL,
    COMPLAINT_NUMBER      VARCHAR(50)   NOT NULL,

    -- ENTITY | COMPLAINANT | OFFICER | OTHER
    PARTICIPANT_TYPE      VARCHAR(20)   NULL,
    PARTICIPANT_NAME      VARCHAR(250)  NOT NULL,

    -- Set when the participant is a regulated entity carried on the complaint or impleaded into it.
    ENTITY_CODE           VARCHAR(50)   NULL,

    PARTICIPANT_EMAIL     VARCHAR(320)  NULL,
    PARTICIPANT_CONFIRMED CHAR(1)       NULL,

    ADDED_BY              VARCHAR(200)  NULL,
    CREATED_AT            DATETIME      NULL
);

CALL s5_mtg_add_index('RBIO_MEETING_PARTICIPANT', 'idx_rbio_mtg_part_meeting', 'MEETING_ID');
CALL s5_mtg_add_index('RBIO_MEETING_PARTICIPANT', 'idx_rbio_mtg_part_complaint', 'COMPLAINT_NUMBER');

-- ─────────────────────────────────────────────────────────────
-- 3. Which statuses forbid a meeting (UST497, 643, 646, 649)
-- ─────────────────────────────────────────────────────────────
-- The exclusion set is DATA, not a compiled-in list. RBIO_STATUS_MASTER already carries IS_CLOSED and
-- IS_TERMINAL, but neither answers this question: "Advisory Complied" and "Award Passed" are not
-- terminal in the IS_TERMINAL sense used elsewhere, while some terminal statuses are irrelevant here.
--
-- WHY A FLAG AND NOT A FROM-STATUS ENUMERATION IN RBIO_WORKFLOW_TRANSITION. The transition table can
-- only express a positive from-status list, and the requirement is a NOT-IN. Enumerating "every status
-- except six" would silently omit any status a later session introduces, which is the same trap
-- RbioTransitionRegistry documents for REASSIGN. A flag on the status itself inverts correctly: a new
-- status defaults to "meetings allowed" and must be opted out deliberately.
CALL s5_mtg_add_column('RBIO_STATUS_MASTER', 'BLOCKS_MEETING', 'CHAR(1) NULL');

-- The six statuses named by UST497. Set by STATUS_CODE, never by label: labels are localised and an
-- English match finds none of the translated rows.
UPDATE RBIO_STATUS_MASTER
   SET BLOCKS_MEETING = 'Y'
 WHERE STATUS_CODE IN ('ADVISORY_COMPLIED', 'SETTLED', 'WITHDRAWN',
                       'REJECTED', 'AWARD_PASSED', 'OMBUDSMAN_DECISION');

-- Everything else is explicitly allowed rather than left NULL, so the server can tell "not configured"
-- from "allowed" and fail closed on the former.
UPDATE RBIO_STATUS_MASTER
   SET BLOCKS_MEETING = 'N'
 WHERE BLOCKS_MEETING IS NULL;

CALL s5_mtg_add_index('RBIO_STATUS_MASTER', 'idx_rbio_status_blocks_meeting', 'BLOCKS_MEETING');

DROP PROCEDURE IF EXISTS s5_mtg_add_column;
DROP PROCEDURE IF EXISTS s5_mtg_add_index;

-- ─────────────────────────────────────────────────────────────
-- 4. The MOM letter template (UST500, 644, 647, 650)
-- ─────────────────────────────────────────────────────────────
-- Reuses the CommunicationTemplate mechanism that S4's closure letters use, rather than adding a
-- second letter pipeline. Note the DOUBLE braces: CommunicationTemplateService compiles
-- Pattern "\{\{(\w+)}}", so the single-brace {placeholders} in the legacy Oracle seed rows never
-- substitute at all. Using single braces here would emit a letter with literal {complaintNumber} in it.
--
-- SCHEME NAME. 'Reserve Bank - Integrated Ombudsman Scheme, 2021' is the correct and current name.
-- No '(as amended 2026)' text appears here: that amendment does not exist and asserting it in a
-- document a citizen relies on is a legal-text defect, not a cosmetic one.
INSERT INTO COMMUNICATION_TEMPLATES
    (template_name, mode, trigger_condition, scheme_version, subject_template, body_template,
     description, category, active, created_by, created_at, updated_at)
SELECT 'Minutes of Meeting', 'LETTER', 'MEETING_MINUTES', 'BOTH',
       'Minutes of Meeting - Complaint {{complaintNumber}}',
       CONCAT(
         '<p>Reserve Bank of India - Office of the Ombudsman</p>',
         '<p><b>MINUTES OF MEETING</b></p>',
         '<p>Complaint Number: {{complaintNumber}}<br/>',
         'Complainant: {{complainantName}}<br/>',
         'Regulated Entity: {{entityName}}</p>',
         '<p>Meeting Date: {{meetingDate}}<br/>',
         'Meeting Time: {{meetingTime}}<br/>',
         'Mode: {{meetingMode}}<br/>',
         'Venue: {{meetingVenue}}</p>',
         '<p>Participants: {{participants}}</p>',
         '<p>Attendees confirmed: {{confirmedParticipants}}</p>',
         '<p><b>Minutes</b></p><p>{{minutesOfMeeting}}</p>',
         '<p>Entity acceptance of the settlement discussed: {{entityAccepted}}</p>',
         '<p>Recorded by: {{performedBy}} ({{performedByRole}})<br/>',
         'Recorded on: {{recordedAt}}</p>',
         '<p>Issued under the Reserve Bank - Integrated Ombudsman Scheme, 2021.</p>'),
       'Minutes of Meeting letter for an RBIO conciliation meeting (UST500). Rendered by MinutesOfMeetingLetterService through the shared CommunicationTemplate pipeline.',
       'MEETING', TRUE, 'V95_migration', NOW(), NOW()
WHERE NOT EXISTS (
    SELECT 1 FROM COMMUNICATION_TEMPLATES WHERE trigger_condition = 'MEETING_MINUTES');
