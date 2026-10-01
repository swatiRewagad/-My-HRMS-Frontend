-- ============================================================================
-- V72 (Oracle): Notification configurability, channel matrix, timeline detail
-- Session S6 (UST594-597, UST601, UST607-612, UST658-668)
-- Oracle counterpart of MySQL database/V74. The two directories' V-numbers are
-- deliberately NOT in sync.
--
-- Guarded via USER_TAB_COLUMNS / USER_TABLES so the script is safe to re-run.
-- Oracle has no ADD COLUMN IF NOT EXISTS, so each DDL is wrapped in a PL/SQL
-- block that checks first. ORA-01430 (column already exists) and ORA-00955
-- (name already used) are additionally tolerated to cover a concurrent run.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 1. COMPLAINT_TIMELINE detail columns (UST596)
--
-- All NULL-able without exception. Existing rows have no recoverable value for
-- any of these, so a NOT NULL column would fail the ALTER outright.
-- ---------------------------------------------------------------------------

DECLARE
  v_count NUMBER;
  PROCEDURE add_col(p_col VARCHAR2, p_ddl VARCHAR2) IS
    v_exists NUMBER;
  BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
     WHERE TABLE_NAME = 'COMPLAINT_TIMELINE' AND COLUMN_NAME = p_col;
    IF v_exists = 0 THEN
      EXECUTE IMMEDIATE p_ddl;
    END IF;
  EXCEPTION
    WHEN OTHERS THEN
      IF SQLCODE = -1430 THEN NULL; ELSE RAISE; END IF;
  END;
BEGIN
  -- The actor's role at the time of the event.
  add_col('PERFORMED_BY_ROLE',
    'ALTER TABLE COMPLAINT_TIMELINE ADD (PERFORMED_BY_ROLE VARCHAR2(50) NULL)');

  -- The changed-value triple. Generic rather than OLD_OWNER/NEW_OWNER because the
  -- same requirement recurs for closure clause and destination office, and because
  -- APPEAL_TIMELINE already established this shape.
  add_col('FIELD_NAME',
    'ALTER TABLE COMPLAINT_TIMELINE ADD (FIELD_NAME VARCHAR2(60) NULL)');
  add_col('OLD_VALUE',
    'ALTER TABLE COMPLAINT_TIMELINE ADD (OLD_VALUE VARCHAR2(500) NULL)');
  add_col('NEW_VALUE',
    'ALTER TABLE COMPLAINT_TIMELINE ADD (NEW_VALUE VARCHAR2(500) NULL)');

  -- Recorded ON THE EVENT: COMPLAINTS.CLOSURE_CLAUSE holds only the CURRENT value
  -- and is mutable, so a reopen followed by a re-closure under a different clause
  -- destroys the original — and that clause is the citizen's statutory basis for
  -- appeal.
  add_col('CLOSURE_CLAUSE',
    'ALTER TABLE COMPLAINT_TIMELINE ADD (CLOSURE_CLAUSE VARCHAR2(100) NULL)');

  -- Receiving office for a routing/transfer event. Unused by cms-backend's own
  -- writers today — inter-office transfer approval writes no timeline row at all.
  -- This is the published write contract for the session that owns transfers.
  add_col('DESTINATION_OFFICE',
    'ALTER TABLE COMPLAINT_TIMELINE ADD (DESTINATION_OFFICE VARCHAR2(100) NULL)');
END;
/

-- ---------------------------------------------------------------------------
-- 2. NOTIFICATION_EVENT_CHANNEL — per-event channel matrix (UST658-661, UST668)
--
-- A table rather than if-branches so "this event deliberately does not send
-- email" is an assertable row. UST668 requires bell-only WITH NO EMAIL, which
-- cannot be verified by the absence of code — only by EMAIL_ENABLED = 'N' plus
-- the absence of an EMAIL delivery-log row.
-- ---------------------------------------------------------------------------

DECLARE
  v_exists NUMBER;
BEGIN
  SELECT COUNT(*) INTO v_exists FROM USER_TABLES
   WHERE TABLE_NAME = 'NOTIFICATION_EVENT_CHANNEL';
  IF v_exists = 0 THEN
    EXECUTE IMMEDIATE '
      CREATE TABLE NOTIFICATION_EVENT_CHANNEL (
        EVENT_TYPE     VARCHAR2(50)  NOT NULL,
        IN_APP_ENABLED VARCHAR2(1)   DEFAULT ''Y'' NOT NULL,
        EMAIL_ENABLED  VARCHAR2(1)   DEFAULT ''N'' NOT NULL,
        SMS_ENABLED    VARCHAR2(1)   DEFAULT ''N'' NOT NULL,
        DESCRIPTION    VARCHAR2(500) NULL,
        IS_ACTIVE      VARCHAR2(1)   DEFAULT ''Y'' NOT NULL,
        CONSTRAINT PK_NOTIF_EVENT_CHANNEL PRIMARY KEY (EVENT_TYPE)
      )';
  END IF;
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE = -955 THEN NULL; ELSE RAISE; END IF;
END;
/

-- Insert-if-absent so an operator's later edit survives a re-run.
--
-- SMS is 'N' EVERYWHERE, deliberately. No SMS gateway exists in ANY module of this
-- repository — cms-notification-service.sendSms is a log-and-return placeholder and
-- CitizenAuthController's OTP dispatch is a TODO. Seeding SMS_ENABLED = 'Y' would
-- assert a capability the product does not have and make delivery-log rows claim
-- sends that never happened.
DECLARE
  TYPE t_row IS RECORD (
    event_type VARCHAR2(50), in_app VARCHAR2(1), email VARCHAR2(1),
    sms VARCHAR2(1), descr VARCHAR2(500));
  TYPE t_tab IS TABLE OF t_row;
  v_rows t_tab := t_tab(
    t_row('PENDING_5DAY','Y','Y','N','UST612: complaint pending N days without action'),
    t_row('NO_STATUS_STALE','Y','Y','N','UST611: nodal-officer record stale at warn/escalate thresholds'),
    t_row('COMPLAINANT_REMINDER_14DAY','Y','Y','N','UST662: first complainant reminder; stops once the complaint is closed'),
    t_row('COMPLAINANT_REMINDER_21DAY','Y','Y','N','UST662: second complainant reminder; stops once the complaint is closed'),
    t_row('RE_RESPONSE_OVERDUE','Y','Y','N','UST637-638: RE response deadline lapsed, after the configured grace delay'),
    t_row('NO_RECORD_ASSIGNED','Y','N','N','UST609: complaint sitting with no officer assigned'),
    t_row('ON_LEAVE_PENDING','Y','N','N','UST608: officer on leave still holding open complaints'),
    t_row('DUPLICATE_DETECTED','Y','N','N','UST607: complaint flagged as a potential duplicate'),
    t_row('DOCUMENTS_UPLOADED','Y','Y','N','UST602: complainant submitted documents via the secure upload link'),
    t_row('UPLOAD_LINK_EXPIRED','Y','N','N','UST776: secure upload link lapsed without a submission'),
    t_row('COMPLAINT_FLAGGED','Y','N','N','UST666: bell-only by design - includes the flagging user and complaint number'),
    t_row('COMPLAINT_UPDATED','Y','N','N','UST665: bell-only by design - carries the type of update'),
    t_row('COMPLAINT_CLOSED','Y','Y','N','UST663: closure notice, carries complaint number and closure date')
  );
  v_exists NUMBER;
BEGIN
  FOR i IN 1 .. v_rows.COUNT LOOP
    SELECT COUNT(*) INTO v_exists FROM NOTIFICATION_EVENT_CHANNEL
     WHERE EVENT_TYPE = v_rows(i).event_type;
    IF v_exists = 0 THEN
      INSERT INTO NOTIFICATION_EVENT_CHANNEL
        (EVENT_TYPE, IN_APP_ENABLED, EMAIL_ENABLED, SMS_ENABLED, DESCRIPTION)
      VALUES (v_rows(i).event_type, v_rows(i).in_app, v_rows(i).email,
              v_rows(i).sms, v_rows(i).descr);
    END IF;
  END LOOP;
  COMMIT;
END;
/

-- ---------------------------------------------------------------------------
-- 3. SYSTEM_CONFIG rows for notification intervals and recipients
--
-- Values match the constants the Java code previously hardcoded, so seeding this
-- changes no behaviour — wiring configuration in must not itself alter what the
-- jobs do. Key convention follows the existing rows: lowercase dotted with a
-- snake_case leaf.
--
-- RE-deadline keys are deliberately ABSENT: timeline.re.response_deadline_days is
-- already seeded and owned by another session. A parallel key would be a second
-- source of truth for one deadline.
-- ---------------------------------------------------------------------------

DECLARE
  TYPE t_row IS RECORD (k VARCHAR2(100), v VARCHAR2(500), d VARCHAR2(500));
  TYPE t_tab IS TABLE OF t_row;
  v_rows t_tab := t_tab(
    t_row('notification.pending.nudge_days','5','UST612: days without a status change before the owner is nudged'),
    t_row('notification.no_record.warn_days','15','UST611: first nodal-officer staleness warning, in days'),
    t_row('notification.no_record.escalate_days','20','UST611: escalated nodal-officer staleness, in days'),
    t_row('notification.complainant_reminder.first_days','14','UST662: first complainant reminder, in days after filing'),
    t_row('notification.complainant_reminder.second_days','21','UST662: second complainant reminder, in days after filing'),
    t_row('notification.upload_link.expiry_days','7','UST601: secure upload link lifetime, in days'),
    t_row('notification.re_response.alert_delay_days','0','UST638: grace days after the RE deadline lapses before alerting; 0 = alert on lapse'),
    t_row('notification.recipients.pending_nudge','COMPLAINT_OWNER','UST612 recipients. COMPLAINT_OWNER resolves per complaint'),
    t_row('notification.recipients.no_record_warn','RBIO_SUPERVISOR','UST611 warn-level recipients (roles fan out to pool members)'),
    t_row('notification.recipients.no_record_escalate','RBIO_ADMIN','UST611 escalation recipients'),
    t_row('notification.recipients.no_record_assigned','SYSTEM_ADMIN','UST609 recipients'),
    t_row('notification.recipients.on_leave_pending','RBIO_SUPERVISOR','UST608 recipients'),
    t_row('notification.recipients.documents_uploaded','COMPLAINT_OWNER','UST602 recipients'),
    t_row('notification.recipients.upload_link_expired','COMPLAINT_OWNER','UST776 recipients'),
    t_row('notification.scanned_departments','RBIO,CEPC,CRPC','Departments the department-scoped notification scans iterate')
  );
  v_exists NUMBER;
BEGIN
  FOR i IN 1 .. v_rows.COUNT LOOP
    SELECT COUNT(*) INTO v_exists FROM SYSTEM_CONFIG
     WHERE CONFIG_KEY = v_rows(i).k;
    IF v_exists = 0 THEN
      INSERT INTO SYSTEM_CONFIG (CONFIG_KEY, CONFIG_VALUE, DESCRIPTION, UPDATED_AT)
      VALUES (v_rows(i).k, v_rows(i).v, v_rows(i).d, SYSTIMESTAMP);
    END IF;
  END LOOP;
  COMMIT;
END;
/
