-- V10: COMPLAINTS authorised-representative columns (UST-public / D7)
-- Oracle version
--
-- Context: the citizen filing wizard's step 4 validates nine representative fields as mandatory once
-- the complainant answers "yes" to hasAuthRep, but performSubmit() never put them in the payload and
-- the server had nowhere to store them. The details were therefore collected, enforced, and then
-- discarded — leaving an officer with no way to contact the representative or verify their authority.
--
-- FileComplaintRequest now carries the fields (with an @AssertTrue mirroring the wizard's rule) and
-- ComplaintService maps them onto Complaint.

DECLARE
    v_count NUMBER;

    PROCEDURE add_col(p_name VARCHAR2, p_type VARCHAR2) IS
        v_exists NUMBER;
    BEGIN
        SELECT COUNT(*) INTO v_exists FROM USER_TAB_COLUMNS
         WHERE TABLE_NAME = 'COMPLAINTS' AND COLUMN_NAME = p_name;
        IF v_exists = 0 THEN
            EXECUTE IMMEDIATE 'ALTER TABLE COMPLAINTS ADD ' || p_name || ' ' || p_type;
        END IF;
    END;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_TABLES WHERE TABLE_NAME = 'COMPLAINTS';
    IF v_count = 0 THEN
        RETURN;
    END IF;

    add_col('HAS_AUTH_REP',     'NUMBER(1)');
    add_col('THROUGH_ADVOCATE', 'NUMBER(1)');
    add_col('REP_NAME',         'VARCHAR2(200)');
    add_col('REP_PHONE',        'VARCHAR2(20)');
    add_col('REP_EMAIL',        'VARCHAR2(254)');
    add_col('REP_ADDRESS',      'VARCHAR2(500)');
    add_col('REP_STATE',        'VARCHAR2(100)');
    add_col('REP_DISTRICT',     'VARCHAR2(100)');
    add_col('REP_CITY',         'VARCHAR2(100)');
    add_col('REP_PINCODE',      'VARCHAR2(10)');
END;
/

-- Officers filter their queue by whether a representative is on record, so index the flag.
DECLARE
    v_count NUMBER;
BEGIN
    SELECT COUNT(*) INTO v_count FROM USER_INDEXES
     WHERE INDEX_NAME = 'IDX_COMPLAINT_HAS_AUTH_REP';
    IF v_count = 0 THEN
        EXECUTE IMMEDIATE 'CREATE INDEX IDX_COMPLAINT_HAS_AUTH_REP ON COMPLAINTS(HAS_AUTH_REP)';
    END IF;
END;
/
