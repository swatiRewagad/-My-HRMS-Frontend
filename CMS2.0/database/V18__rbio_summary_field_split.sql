-- V18: COMPLAINT_RBIO_FORM_DATA.additional_comments (MySQL)
--
-- The RBIO officer summary form has two independent free-text boxes: "Comments" in Basic Details and
-- "Comments" in the Additional Information section much further down. Both were mapped onto the
-- single comments column added in V15, so GET /api/complaints/rbio/{id}/summary returned the same
-- string for both and PUT silently overwrote whichever one the officer had not just edited. Splitting
-- them is what makes the summary round-trip losslessly.
--
-- comments keeps backing Basic Details; the new column backs Additional Information. No backfill:
-- existing rows only ever held one of the two values and there is no way to tell which box it came
-- from, so it stays where it is rather than being duplicated into both.
--
-- Oracle equivalent: database/oracle/V18__rbio_summary_field_split.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing column
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with "duplicate column name".

ALTER TABLE COMPLAINT_RBIO_FORM_DATA ADD COLUMN additional_comments VARCHAR(4000) AFTER vernacular_language;
