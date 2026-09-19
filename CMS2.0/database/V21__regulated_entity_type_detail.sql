-- V21: REGULATED_ENTITIES.entity_type_detail (MySQL)
--
-- The complaint screens show Module Name, Entity Category and Entity Type as three fields, but the
-- master only ever stored one classification: entity_type, holding the category ("Public Sector Bank",
-- "NBFC"). Module Name is derivable from that category and so stays derived — see
-- RegulatedEntity.moduleNameFor — but Entity Type is RBI's sub-classification below the category
-- ("Loan Company", "Housing Finance Company", "Core Investment Company") and no category implies it.
-- Without this column the Entity Type box had no source at all, so the summary screen was showing the
-- category in it and leaving the category itself blank.
--
-- Named entity_type_detail rather than entity_sub_type to match entityTypeDetail, the name the CRPC
-- physical-letter screen already binds this list to.
--
-- Oracle equivalent: database/oracle/V21__regulated_entity_type_detail.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing column
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with "duplicate column name".

ALTER TABLE REGULATED_ENTITIES
    ADD COLUMN entity_type_detail VARCHAR(100) NULL AFTER entity_type;

-- No backfill. The sub-classification exists in no other table and cannot be inferred from the
-- category — every bank would be wrong whichever value we picked — so NULL is the honest value: "not
-- classified" rather than a guess that reads as reference data.
