-- V20: NODAL_OFFICER_RECORDS assessment columns (Oracle)
--
-- The nodal officer record screen has always had an Assessment panel — status code, advisory
-- compliance date, dispute amount, the two compensation figures and the two award dates — but none of
-- it had anywhere to go. The inputs were bound to component state only, so the officer's assessment
-- was lost on navigation, and opening a second record showed the first record's figures still sitting
-- in the boxes. These are the columns that panel writes to, via
-- POST /api/v1/complaints/nodal-records/{recordNumber}/forward-to-re.
--
-- The amounts are capped by RbioCompensationService (consequential loss 30 Lakh, mental harassment
-- 3 Lakh, combined 30 Lakh) before the insert, so NUMBER(15,2) is only the storage bound and not the
-- business rule. The business rule lives in one place on purpose.
--
-- NOTICE_131_COMPLY_DATE is Clause 13(1)'s 15-day window. It is written once, when the notice is
-- issued, and deliberately not recomputed afterwards: the screen used to derive it as "today + 15
-- days" on every page load, which moved a deadline the nodal officer had already been served with.
--
-- FORWARDED_TO_RE_AT is the record's own evidence of the forward. It duplicates nothing:
-- RE_RESPONSE_TRACKER is keyed by complaint and owned by the RE responsiveness breach sweep, whereas
-- this is per nodal officer record.
--
-- MySQL equivalent: database/V20__nodal_officer_assessment.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing column
-- fails startup rather than misbehaving later.
-- Not idempotent: re-running fails with ORA-01430 (column being added already exists).

ALTER TABLE NODAL_OFFICER_RECORDS ADD (
    ADVISORY_COMPLIANCE_DATE  DATE,
    DISPUTE_AMOUNT            NUMBER(15, 2),
    COMPENSATION_LOSS         NUMBER(15, 2),
    COMPENSATION_MENTAL       NUMBER(15, 2),
    AWARD_IMPLEMENTATION_DATE DATE,
    AWARD_ACCEPTANCE_DATE     DATE,
    NOTICE_131_COMPLY_DATE    DATE,
    FORWARDED_TO_RE_AT        TIMESTAMP
);

-- No backfill. Every existing row predates the officer being able to record an assessment, so NULL is
-- the honest value: "not assessed" rather than a zero that would read as "assessed at nil".
