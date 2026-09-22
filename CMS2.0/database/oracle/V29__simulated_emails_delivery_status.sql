-- V29: SIMULATED_EMAILS delivery lifecycle (Oracle)
--
-- Backs the outbound mail lifecycle DRAFT -> PENDING -> SENT | FAILED. Pressing Send in the Email
-- Communication tab used to write STATUS='SENT' and stop, which asserted a dispatch that never happened;
-- the row now goes to PENDING and cms-notification-service resolves it after actually sending.
--
-- A NEW column rather than reusing STATUS, because STATUS is overloaded: it holds UNREAD / PROCESSED /
-- RECEIVED for inbound rows (SimulatedEmail's @PrePersist defaults it to UNREAD) alongside DRAFT / SENT
-- for outbound ones. DELIVERY_STATUS is outbound-only and is the single source of truth for the
-- lifecycle; STATUS is kept in step by the same writers so the legacy read surfaces stay correct.
--
-- Nullable, with no default: NULL means "no delivery lifecycle", which is the honest answer for every
-- INBOUND row and the reason the backfill below is restricted to DIRECTION='OUTBOUND'. cms-backend
-- tolerates NULL by falling back to STATUS, so an environment that has not run this still renders.
--
-- DISPATCH_ATTEMPTS counts delivery attempts, not retries of the whole lifecycle; LAST_ERROR is sized to
-- 2000 to match INBOUND_EMAILS.LAST_ERROR, which holds the same kind of truncated exception text.
--
-- The index is not for the dispatch path (the consumer looks rows up by primary key) but for the
-- operational query that matters: "what is stuck", i.e. PENDING rows whose UPDATED_AT has gone stale.
-- Publishing is fire-and-forget, so a broker outage can leave a row PENDING with no automatic recovery.
--
-- MySQL equivalent: database/V29__simulated_emails_delivery_status.sql
-- Requires V28 (UPDATED_AT) to have been applied.
-- Not idempotent: re-running fails with ORA-01430 (column being added already exists).

ALTER TABLE SIMULATED_EMAILS ADD (
    DELIVERY_STATUS   VARCHAR2(20),
    LAST_ERROR        VARCHAR2(2000),
    DISPATCH_ATTEMPTS NUMBER(10)
);

CREATE INDEX IDX_EMAIL_DELIVERY_STATUS ON SIMULATED_EMAILS(DELIVERY_STATUS);

-- Nothing was ever PENDING or FAILED before this migration: an existing outbound row was either a draft
-- or was treated as sent. Inbound rows are deliberately left NULL.
UPDATE SIMULATED_EMAILS
   SET DELIVERY_STATUS = CASE WHEN STATUS = 'DRAFT' THEN 'DRAFT' ELSE 'SENT' END
 WHERE DIRECTION = 'OUTBOUND'
   AND DELIVERY_STATUS IS NULL;

COMMIT;
