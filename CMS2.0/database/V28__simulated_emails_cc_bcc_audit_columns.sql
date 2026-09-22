-- V28: SIMULATED_EMAILS CC/BCC and audit columns (MySQL)
--
-- Closes a drift between the entity and the Oracle scripts. SimulatedEmail carries cc_email, bcc_email,
-- created_by and updated_at — added when the Email Communication tab gained CC/BCC fields and a
-- "created by" attribution. The MySQL create-script (cms-backend/src/main/resources/db/
-- cms_database_scripts.sql) was updated at the time, so a freshly created MySQL schema already has
-- these; this migration exists for MySQL schemas created before that, and for parity with the Oracle
-- half, which was missed entirely.
--
-- dev-local runs ddl-auto=update and will have added these silently — this is for any MySQL environment
-- that does not.
--
-- Separate from V29 (the delivery-status lifecycle) on purpose: this repairs existing mapped state and
-- can ship on its own, whereas V29 belongs to a feature.
--
-- cc/bcc are 500 rather than to_email's 200 because they hold a comma- or semicolon-separated list.
--
-- Oracle equivalent: database/oracle/V28__simulated_emails_cc_bcc_audit_columns.sql
-- Not idempotent: re-running fails with "duplicate column name".

ALTER TABLE SIMULATED_EMAILS
    ADD COLUMN cc_email   VARCHAR(500) AFTER to_email,
    ADD COLUMN bcc_email  VARCHAR(500) AFTER cc_email,
    ADD COLUMN created_by VARCHAR(100) AFTER attachment_url,
    ADD COLUMN updated_at DATETIME(6)  AFTER processed_at;

-- Existing rows predate the column, so updated_at is unknown rather than "now". sent_at is the row's
-- creation time (see SimulatedEmail's javadoc), which is the closest truthful value available.
UPDATE SIMULATED_EMAILS SET updated_at = sent_at WHERE updated_at IS NULL;
