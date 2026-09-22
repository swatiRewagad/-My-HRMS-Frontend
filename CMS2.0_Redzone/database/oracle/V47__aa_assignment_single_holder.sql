-- ═══════════════════════════════════════════════════════════════════════════════════════════════
-- V47 — enforce "one live holder per appeal" in the database. Oracle counterpart of MySQL V49.
--
-- The engine's row lock is per ROLE GROUP, so two concurrent assignments of the SAME appeal into
-- DIFFERENT role groups (AA_DO and AA_REVIEWER — a real transition here) never contend and can both
-- insert a live holder row. Application-level checking cannot close that, so it is a constraint.
--
-- Oracle needs no generated column: a function-based unique index over
-- CASE WHEN RELEASED_AT IS NULL THEN APPEAL_NUMBER END is exactly the partial index required, and NULLs
-- are excluded from a unique index, so released rows never collide.
--
-- Re-runnable.
-- ═══════════════════════════════════════════════════════════════════════════════════════════════

-- ── Release pre-existing duplicates first, or the index cannot be created ────────────────────────
-- The newest placement is kept live because it reflects the most recent decision; older unreleased rows
-- are marked released rather than deleted, so the placement history survives.
DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_TABLES WHERE TABLE_NAME = 'AA_ASSIGNMENT_RECORD';
    IF v_exists = 0 THEN
        RETURN;
    END IF;

    UPDATE AA_ASSIGNMENT_RECORD r
       SET RELEASED_AT = SYSTIMESTAMP
     WHERE r.RELEASED_AT IS NULL
       AND r.ID <> (SELECT MAX(x.ID)
                      FROM AA_ASSIGNMENT_RECORD x
                     WHERE x.APPEAL_NUMBER = r.APPEAL_NUMBER
                       AND x.RELEASED_AT IS NULL);
    COMMIT;
END;
/

DECLARE
    v_exists NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_exists FROM USER_INDEXES WHERE INDEX_NAME = 'UK_AAAR_SINGLE_HOLDER';
    IF v_exists = 0 THEN
        EXECUTE IMMEDIATE '
            CREATE UNIQUE INDEX UK_AAAR_SINGLE_HOLDER ON AA_ASSIGNMENT_RECORD
                (CASE WHEN RELEASED_AT IS NULL THEN APPEAL_NUMBER ELSE NULL END)';
    END IF;
END;
/
