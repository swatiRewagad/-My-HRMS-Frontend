-- V11: COMPLAINTS authorised-representative columns (UST-public / D7)
-- MySQL version
--
-- Context: the citizen filing wizard's step 4 validates nine representative fields as mandatory once
-- the complainant answers "yes" to hasAuthRep, but performSubmit() never put them in the payload and
-- the server had nowhere to store them. The details were therefore collected, enforced, and then
-- discarded — leaving an officer with no way to contact the representative or verify their authority.
--
-- FileComplaintRequest now carries the fields (with an @AssertTrue mirroring the wizard's rule) and
-- ComplaintService maps them onto Complaint.

-- MySQL 8.4 has no ADD COLUMN IF NOT EXISTS, so each column is guarded on information_schema.
DROP PROCEDURE IF EXISTS add_complaint_rep_columns;

DELIMITER //
CREATE PROCEDURE add_complaint_rep_columns()
BEGIN
    DECLARE col_missing INT;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'has_auth_rep');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN has_auth_rep BIT(1) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'through_advocate');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN through_advocate BIT(1) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_name');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_name VARCHAR(200) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_phone');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_phone VARCHAR(20) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_email');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_email VARCHAR(254) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_address');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_address VARCHAR(500) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_state');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_state VARCHAR(100) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_district');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_district VARCHAR(100) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_city');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_city VARCHAR(100) NULL;
    END IF;

    SET col_missing := (SELECT COUNT(*) = 0 FROM information_schema.COLUMNS
                        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
                          AND COLUMN_NAME = 'rep_pincode');
    IF col_missing THEN
        ALTER TABLE COMPLAINTS ADD COLUMN rep_pincode VARCHAR(10) NULL;
    END IF;
END //
DELIMITER ;

CALL add_complaint_rep_columns();
DROP PROCEDURE add_complaint_rep_columns;

-- Officers filter their queue by whether a representative is on record, so index the flag.
SET @idx := (SELECT COUNT(*) FROM information_schema.STATISTICS
             WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'COMPLAINTS'
               AND INDEX_NAME = 'idx_complaint_has_auth_rep');
SET @sql := IF(@idx = 0,
               'CREATE INDEX idx_complaint_has_auth_rep ON COMPLAINTS(has_auth_rep)',
               'DO 0');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
