-- V24: COMPLAINT_READ_RECEIPTS (MySQL)
--
-- Read state was a single COMPLAINTS.IS_READ boolean, so the first officer to open a complaint marked
-- it read for the whole office: a supervisor glancing at a case removed the unread styling for the
-- officer it was actually assigned to, and the dashboard's "Unread Only" toggle hid it from them. Read
-- state belongs to the reader, so it moves to one row per (complaint, officer).
--
-- USERNAME holds the Keycloak preferred_username, the same identity COMPLAINTS.ASSIGNED_OFFICER holds,
-- not the JWT subject: the subject appears nowhere in the complaint corpus and could not be correlated.
--
-- No unique constraint on (COMPLAINT_ID, USERNAME), only an index. The same officer double-opening the
-- complaint would otherwise fail the insert and take the whole detail page down with it, while a
-- duplicate row is harmless here because read state is "a receipt exists", not a count. The index is
-- the point anyway: every read is an existence check on exactly this pair.
--
-- No FK to COMPLAINTS, matching the rest of this schema, which carries complaint ids unconstrained.
--
-- COMPLAINTS.IS_READ is deliberately left in place rather than dropped. The column is no longer mapped
-- by the Complaint entity, so nothing reads or writes it; its NOT NULL DEFAULT 0 keeps inserts working
-- without the mapping. Dropping it is a separate, destructive decision for a DBA to make once the new
-- table has been in production long enough to be trusted.
--
-- Existing indexed complaints keep a stale isRead field in OpenSearch that nothing queries any more.
-- Every complaint therefore reads as unread to everyone until it is next opened, which is the only
-- honest starting point: the old global flag cannot be attributed to any particular officer.
--
-- Oracle equivalent: database/oracle/V24__complaint_read_receipts.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing table
-- fails startup rather than misbehaving later.

CREATE TABLE IF NOT EXISTS COMPLAINT_READ_RECEIPTS (
    ID            BIGINT        NOT NULL AUTO_INCREMENT,
    COMPLAINT_ID  BIGINT        NOT NULL,
    USERNAME      VARCHAR(100)  NOT NULL,
    READ_AT       DATETIME      NOT NULL,
    PRIMARY KEY (ID)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX IDX_READ_RECEIPT_LOOKUP ON COMPLAINT_READ_RECEIPTS (COMPLAINT_ID, USERNAME);
