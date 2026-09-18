-- ============================================================================
-- V74: Notification configurability, per-event channel matrix, timeline detail
-- Session S6 (UST594-597, UST601, UST607-612, UST658-668)
--
-- Three things, all additive:
--   1. SYSTEM_CONFIG rows for every notification interval and recipient list that
--      was previously a Java literal.
--   2. NOTIFICATION_EVENT_CHANNEL — the per-event Email/SMS/bell matrix as DATA.
--   3. COMPLAINT_TIMELINE detail columns so a reassignment can show old AND new
--      owner, and a closure can carry its own clause.
--
-- Every DDL statement is guarded via information_schema: MySQL 8.4 has no
-- ADD COLUMN IF NOT EXISTS / CREATE INDEX IF NOT EXISTS, and this script must be
-- safe to re-run. Schema also arrives via Hibernate ddl-auto:update on a SHARED
-- database, so these columns may already exist when this runs.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. COMPLAINT_TIMELINE detail columns (UST596)
--
-- All NULLABLE, without exception: ddl-auto runs against a database shared by
-- several concurrent sessions and pre-existing rows have no recoverable value
-- for any of these. A NOT NULL column would fail the ALTER outright.
-- ---------------------------------------------------------------------------

SET @db := DATABASE();

-- performed_by_role: the actor's role at the time of the event
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_TIMELINE'
      AND COLUMN_NAME = 'performed_by_role') = 0,
  'ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN performed_by_role VARCHAR(50) NULL',
  'SELECT ''COMPLAINT_TIMELINE.performed_by_role already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- field_name / old_value / new_value: the changed-value triple. Generic rather than
-- oldOwner/newOwner because closure clause and destination office need the same
-- shape, and AppealTimeline already established it.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_TIMELINE'
      AND COLUMN_NAME = 'field_name') = 0,
  'ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN field_name VARCHAR(60) NULL',
  'SELECT ''COMPLAINT_TIMELINE.field_name already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_TIMELINE'
      AND COLUMN_NAME = 'old_value') = 0,
  'ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN old_value VARCHAR(500) NULL',
  'SELECT ''COMPLAINT_TIMELINE.old_value already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_TIMELINE'
      AND COLUMN_NAME = 'new_value') = 0,
  'ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN new_value VARCHAR(500) NULL',
  'SELECT ''COMPLAINT_TIMELINE.new_value already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- closure_clause: recorded ON THE EVENT. COMPLAINTS.closure_clause holds only the
-- CURRENT value and is mutable, so a reopen followed by a re-closure under a
-- different clause destroys the original — and that clause is the citizen's
-- statutory basis for appeal.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_TIMELINE'
      AND COLUMN_NAME = 'closure_clause') = 0,
  'ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN closure_clause VARCHAR(100) NULL',
  'SELECT ''COMPLAINT_TIMELINE.closure_clause already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- destination_office: receiving office for a routing/transfer event. Unused by
-- cms-backend's own writers today — inter-office transfer approval writes no
-- timeline row at all. This is the published write contract for that session.
SET @sql := (SELECT IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'COMPLAINT_TIMELINE'
      AND COLUMN_NAME = 'destination_office') = 0,
  'ALTER TABLE COMPLAINT_TIMELINE ADD COLUMN destination_office VARCHAR(100) NULL',
  'SELECT ''COMPLAINT_TIMELINE.destination_office already present'''));
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- ---------------------------------------------------------------------------
-- 2. NOTIFICATION_EVENT_CHANNEL — per-event channel matrix (UST658-661, UST668)
--
-- A table rather than if-branches so that "this event deliberately does not send
-- email" is an assertable row. UST668 requires bell-only WITH NO EMAIL; that
-- cannot be verified by the absence of code, only by EMAIL_ENABLED = 'N' plus the
-- absence of an EMAIL delivery-log row.
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS NOTIFICATION_EVENT_CHANNEL (
  EVENT_TYPE      VARCHAR(50)  NOT NULL,
  IN_APP_ENABLED  VARCHAR(1)   NOT NULL DEFAULT 'Y',
  EMAIL_ENABLED   VARCHAR(1)   NOT NULL DEFAULT 'N',
  SMS_ENABLED     VARCHAR(1)   NOT NULL DEFAULT 'N',
  DESCRIPTION     VARCHAR(500) NULL,
  IS_ACTIVE       VARCHAR(1)   NOT NULL DEFAULT 'Y',
  PRIMARY KEY (EVENT_TYPE)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Seed rows are insert-if-absent so an operator's later edit is never overwritten
-- by a re-run.
--
-- SMS is 'N' EVERYWHERE, deliberately. No SMS gateway exists in any module of this
-- repository — cms-notification-service.sendSms is a log-and-return placeholder and
-- CitizenAuthController's OTP dispatch is a TODO. Seeding SMS_ENABLED='Y' would
-- assert a capability the product does not have and make delivery-log rows claim
-- sends that never happened.
INSERT INTO NOTIFICATION_EVENT_CHANNEL
  (EVENT_TYPE, IN_APP_ENABLED, EMAIL_ENABLED, SMS_ENABLED, DESCRIPTION)
SELECT * FROM (
  SELECT 'PENDING_5DAY'             AS a, 'Y' AS b, 'Y' AS c, 'N' AS d, 'UST612: complaint pending N days without action' AS e UNION ALL
  SELECT 'NO_STATUS_STALE',              'Y', 'Y', 'N', 'UST611: nodal-officer record stale at warn/escalate thresholds' UNION ALL
  SELECT 'COMPLAINANT_REMINDER_14DAY',   'Y', 'Y', 'N', 'UST662: first complainant reminder; stops once the complaint is closed' UNION ALL
  SELECT 'COMPLAINANT_REMINDER_21DAY',   'Y', 'Y', 'N', 'UST662: second complainant reminder; stops once the complaint is closed' UNION ALL
  SELECT 'RE_RESPONSE_OVERDUE',          'Y', 'Y', 'N', 'UST637-638: RE response deadline lapsed, after the configured grace delay' UNION ALL
  SELECT 'NO_RECORD_ASSIGNED',           'Y', 'N', 'N', 'UST609: complaint sitting with no officer assigned' UNION ALL
  SELECT 'ON_LEAVE_PENDING',             'Y', 'N', 'N', 'UST608: officer on leave still holding open complaints' UNION ALL
  SELECT 'DUPLICATE_DETECTED',           'Y', 'N', 'N', 'UST607: complaint flagged as a potential duplicate' UNION ALL
  SELECT 'DOCUMENTS_UPLOADED',           'Y', 'Y', 'N', 'UST602: complainant submitted documents via the secure upload link' UNION ALL
  SELECT 'UPLOAD_LINK_EXPIRED',          'Y', 'N', 'N', 'UST776: secure upload link lapsed without a submission' UNION ALL
  SELECT 'COMPLAINT_FLAGGED',            'Y', 'N', 'N', 'UST666: bell-only by design - includes the flagging user and complaint number' UNION ALL
  SELECT 'COMPLAINT_UPDATED',            'Y', 'N', 'N', 'UST665: bell-only by design - carries the type of update' UNION ALL
  SELECT 'COMPLAINT_CLOSED',             'Y', 'Y', 'N', 'UST663: closure notice, carries complaint number and closure date'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM NOTIFICATION_EVENT_CHANNEL n WHERE n.EVENT_TYPE = seed.a);

-- ---------------------------------------------------------------------------
-- 3. SYSTEM_CONFIG rows for notification intervals and recipients
--
-- Values match the constants the Java code previously hardcoded, so seeding this
-- changes no behaviour — wiring configuration in must not itself alter what the
-- jobs do. Key convention follows the V8 rows: lowercase dotted with a snake_case
-- leaf.
--
-- RE-deadline keys are deliberately ABSENT: timeline.re.response_deadline_days is
-- already seeded by V8 and owned by another session. A parallel
-- notification.re.deadline_days row would be a second source of truth for one
-- deadline.
-- ---------------------------------------------------------------------------

INSERT INTO SYSTEM_CONFIG (config_key, config_value, description, updated_at)
SELECT * FROM (
  SELECT 'notification.pending.nudge_days'                AS k, '5'  AS v, 'UST612: days without a status change before the owner is nudged' AS d, NOW() AS t UNION ALL
  SELECT 'notification.no_record.warn_days',                   '15', 'UST611: first nodal-officer staleness warning, in days', NOW() UNION ALL
  SELECT 'notification.no_record.escalate_days',               '20', 'UST611: escalated nodal-officer staleness, in days', NOW() UNION ALL
  SELECT 'notification.complainant_reminder.first_days',       '14', 'UST662: first complainant reminder, in days after filing', NOW() UNION ALL
  SELECT 'notification.complainant_reminder.second_days',      '21', 'UST662: second complainant reminder, in days after filing', NOW() UNION ALL
  SELECT 'notification.upload_link.expiry_days',               '7',  'UST601: secure upload link lifetime, in days', NOW() UNION ALL
  SELECT 'notification.re_response.alert_delay_days',          '0',  'UST638: grace days after the RE deadline lapses before alerting; 0 = alert on lapse', NOW() UNION ALL
  SELECT 'notification.recipients.pending_nudge',              'COMPLAINT_OWNER', 'UST612 recipients. COMPLAINT_OWNER resolves per complaint', NOW() UNION ALL
  SELECT 'notification.recipients.no_record_warn',             'RBIO_SUPERVISOR', 'UST611 warn-level recipients (roles fan out to pool members)', NOW() UNION ALL
  SELECT 'notification.recipients.no_record_escalate',         'RBIO_ADMIN', 'UST611 escalation recipients', NOW() UNION ALL
  SELECT 'notification.recipients.no_record_assigned',         'SYSTEM_ADMIN', 'UST609 recipients', NOW() UNION ALL
  SELECT 'notification.recipients.on_leave_pending',           'RBIO_SUPERVISOR', 'UST608 recipients', NOW() UNION ALL
  SELECT 'notification.recipients.documents_uploaded',         'COMPLAINT_OWNER', 'UST602 recipients', NOW() UNION ALL
  SELECT 'notification.recipients.upload_link_expired',        'COMPLAINT_OWNER', 'UST776 recipients', NOW() UNION ALL
  SELECT 'notification.scanned_departments',                   'RBIO,CEPC,CRPC', 'Departments the department-scoped notification scans iterate', NOW()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM SYSTEM_CONFIG c WHERE c.config_key = seed.k);
