-- V29: SIMULATED_EMAILS delivery lifecycle (MySQL)
--
-- Backs the outbound mail lifecycle DRAFT -> PENDING -> SENT | FAILED. Pressing Send in the Email
-- Communication tab used to write status='SENT' and stop, which asserted a dispatch that never happened;
-- the row now goes to PENDING and cms-notification-service resolves it after actually sending.
--
-- A NEW column rather than reusing status, because status is overloaded: it holds UNREAD / PROCESSED /
-- RECEIVED for inbound rows (SimulatedEmail's @PrePersist defaults it to UNREAD) alongside DRAFT / SENT
-- for outbound ones. delivery_status is outbound-only and is the single source of truth for the
-- lifecycle; status is kept in step by the same writers so the legacy read surfaces stay correct.
--
-- Nullable, with no default: NULL means "no delivery lifecycle", which is the honest answer for every
-- INBOUND row and the reason the backfill below is restricted to direction='OUTBOUND'. cms-backend
-- tolerates NULL by falling back to status, so an environment that has not run this still renders.
--
-- dispatch_attempts counts delivery attempts, not retries of the whole lifecycle; last_error is sized to
-- 2000 to match the equivalent column in the mail-intake schema, which holds the same kind of truncated
-- exception text.
--
-- The index is not for the dispatch path (the consumer looks rows up by primary key) but for the
-- operational query that matters: "what is stuck", i.e. PENDING rows whose updated_at has gone stale.
-- Publishing is fire-and-forget, so a broker outage can leave a row PENDING with no automatic recovery.
--
-- Oracle equivalent: database/oracle/V29__simulated_emails_delivery_status.sql
-- Requires V28 (updated_at) to have been applied.
-- Not idempotent: re-running fails with "duplicate column name".

ALTER TABLE SIMULATED_EMAILS
    ADD COLUMN delivery_status   VARCHAR(20)   AFTER status,
    ADD COLUMN last_error        VARCHAR(2000) AFTER delivery_status,
    ADD COLUMN dispatch_attempts INT           AFTER last_error,
    ADD INDEX idx_email_delivery_status (delivery_status);

-- Nothing was ever PENDING or FAILED before this migration: an existing outbound row was either a draft
-- or was treated as sent. Inbound rows are deliberately left NULL.
UPDATE SIMULATED_EMAILS
   SET delivery_status = CASE WHEN status = 'DRAFT' THEN 'DRAFT' ELSE 'SENT' END
 WHERE direction = 'OUTBOUND'
   AND delivery_status IS NULL;
