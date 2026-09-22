-- V27: COMPLAINT_RBIO_FORM_DATA.REPLY_WITHIN_30_DAYS (Oracle)
--
-- "Received any Reply within 30 Days (or within time stipulated by RBI / NPCI / Card Network)" is
-- rendered in the Complaint Linkage section of the RBIO officer summary form, but had no column
-- behind it: GET /api/complaints/rbio/{id}/summary returned nothing for it and PUT silently dropped
-- the officer's answer. The CRPC email draft already captures the same answer
-- (EMAIL_DRAFT.RECEIVED_REPLY_WITHIN30DAYS), so this column is also the backfill target at
-- draft->complaint conversion.
--
-- Tri-state, hence VARCHAR2 and not a boolean: YES / NO / NOT_APPLICABLE, NULL meaning never answered.
-- Token spelling matches EMAIL_DRAFT so the backfill is a direct copy.
--
-- MySQL equivalent: database/V27__rbio_reply_within_30_days.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing column
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with ORA-01430 (column being added already exists).

ALTER TABLE COMPLAINT_RBIO_FORM_DATA ADD (REPLY_WITHIN_30_DAYS VARCHAR2(20));
