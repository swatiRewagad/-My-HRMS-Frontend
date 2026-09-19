-- V19: NODAL_OFFICER_RECORDS.RECORD_NUMBER, PNO_EMAIL, PNO_PHONE (Oracle)
--
-- Two endpoints already address nodal officer records by a record number —
-- GET/POST /api/v1/complaints/nodal-records/{recordNumber}/comments, which store it on
-- COMPLAINT_COMMENTS.NO_RECORD_NUMBER — but no column ever held one, so the officer screen was
-- filling the table from a hardcoded list of invented numbers and the comments were keyed to
-- records that did not exist. This adds the column those endpoints have always assumed.
--
-- RECORD_NUMBER is derived from the identity id (NodalOfficerRecordService assigns it as a
-- zero-padded 7-digit string right after insert) rather than from a counter table of its own, so
-- the backfill below reproduces exactly what the service would have written for existing rows.
-- Keep the two in step: changing the format in one place without the other splits the join key.
--
-- PNO_EMAIL and PNO_PHONE complete the principal nodal officer snapshot. PNO_NAME was already
-- captured at creation but its contact details were only reachable by joining REGULATED_ENTITIES
-- live, which defeats the point of snapshotting — the record has to keep showing who was
-- reachable when the complaint was forwarded.
--
-- MySQL equivalent: database/V19__nodal_officer_record_number.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing column
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with ORA-01430 (column being added already exists).

ALTER TABLE NODAL_OFFICER_RECORDS ADD (
    RECORD_NUMBER VARCHAR2(50),
    PNO_EMAIL     VARCHAR2(200),
    PNO_PHONE     VARCHAR2(20)
);

UPDATE NODAL_OFFICER_RECORDS SET RECORD_NUMBER = LPAD(TO_CHAR(ID), 7, '0') WHERE RECORD_NUMBER IS NULL;
COMMIT;

-- Unique rather than a plain index: it is a join key that COMPLAINT_COMMENTS points at with no
-- foreign key of its own, so a duplicate here would silently fan comments across records.
CREATE UNIQUE INDEX UK_NO_RECORD_NUMBER ON NODAL_OFFICER_RECORDS(RECORD_NUMBER);
