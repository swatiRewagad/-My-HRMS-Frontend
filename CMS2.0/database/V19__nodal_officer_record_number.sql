-- V19: NODAL_OFFICER_RECORDS.record_number, pno_email, pno_phone (MySQL)
--
-- Two endpoints already address nodal officer records by a record number —
-- GET/POST /api/v1/complaints/nodal-records/{recordNumber}/comments, which store it on
-- COMPLAINT_COMMENTS.no_record_number — but no column ever held one, so the officer screen was
-- filling the table from a hardcoded list of invented numbers and the comments were keyed to
-- records that did not exist. This adds the column those endpoints have always assumed.
--
-- record_number is derived from the identity id (NodalOfficerRecordService assigns it as a
-- zero-padded 7-digit string right after insert) rather than from a counter table of its own, so
-- the backfill below reproduces exactly what the service would have written for existing rows.
-- Keep the two in step: changing the format in one place without the other splits the join key.
--
-- pno_email and pno_phone complete the principal nodal officer snapshot. pno_name was already
-- captured at creation but its contact details were only reachable by joining REGULATED_ENTITIES
-- live, which defeats the point of snapshotting — the record has to keep showing who was
-- reachable when the complaint was forwarded.
--
-- Oracle equivalent: database/oracle/V19__nodal_officer_record_number.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing column
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with "duplicate column name".

ALTER TABLE NODAL_OFFICER_RECORDS
    ADD COLUMN record_number VARCHAR(50)  NULL AFTER id,
    ADD COLUMN pno_email     VARCHAR(200) NULL AFTER pno_name,
    ADD COLUMN pno_phone     VARCHAR(20)  NULL AFTER pno_email;

UPDATE NODAL_OFFICER_RECORDS SET record_number = LPAD(id, 7, '0') WHERE record_number IS NULL;

-- Unique rather than a plain index: it is a join key that COMPLAINT_COMMENTS points at with no
-- foreign key of its own, so a duplicate here would silently fan comments across records.
CREATE UNIQUE INDEX uk_no_record_number ON NODAL_OFFICER_RECORDS(record_number);
