-- V28: SIMULATED_EMAILS CC/BCC and audit columns (Oracle)
--
-- Closes a drift between the entity and every Oracle script. SimulatedEmail carries CC_EMAIL,
-- BCC_EMAIL, CREATED_BY and UPDATED_AT — added when the Email Communication tab gained CC/BCC fields
-- and a "created by" attribution — but none of the three competing Oracle definitions of this table has
-- them (V1__complete_schema.sql:365 defines a wholly different 6-column table; V4__complete_oracle_ddl.sql
-- and V5__complete_ddl_dml.sql define 15 columns, without these four).
--
-- This is a startup failure, not a latent nicety: the prod profile runs ddl-auto=validate, so Hibernate
-- refuses to boot cms-backend against a schema missing a mapped column. Apply before booting prod.
--
-- Separate from V29 (the delivery-status lifecycle) on purpose: this repairs existing mapped state and
-- can ship on its own, whereas V29 belongs to a feature.
--
-- CC/BCC are 500 rather than TO_EMAIL's 200 because they hold a comma- or semicolon-separated list.
--
-- MySQL equivalent: database/V28__simulated_emails_cc_bcc_audit_columns.sql
-- Not idempotent: re-running fails with ORA-01430 (column being added already exists).

ALTER TABLE SIMULATED_EMAILS ADD (
    CC_EMAIL   VARCHAR2(500),
    BCC_EMAIL  VARCHAR2(500),
    CREATED_BY VARCHAR2(100),
    UPDATED_AT TIMESTAMP(6)
);

-- Existing rows predate the column, so UPDATED_AT is unknown rather than "now". SENT_AT is the row's
-- creation time (see SimulatedEmail's javadoc), which is the closest truthful value available.
UPDATE SIMULATED_EMAILS SET UPDATED_AT = SENT_AT WHERE UPDATED_AT IS NULL;

COMMIT;
