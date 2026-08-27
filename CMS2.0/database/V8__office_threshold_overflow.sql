-- ============================================================
-- RBI CMS Phase 8 - Office Threshold & Overflow Mapping
-- Description: Add counter/threshold to office code master,
--              create overflow sequence mapping table
-- MySQL-compatible syntax
-- ============================================================

-- Add counter and threshold columns to OFFICE_CODE_MASTER
ALTER TABLE OFFICE_CODE_MASTER
    ADD COLUMN COUNTER INT NOT NULL DEFAULT 0,
    ADD COLUMN THRESHOLD INT NOT NULL DEFAULT 2;

-- ============================================================
-- TABLE: OFFICE_OVERFLOW_MAPPING
-- Defines priority-based overflow sequence for each office
-- ============================================================

CREATE TABLE IF NOT EXISTS OFFICE_OVERFLOW_MAPPING (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    OFFICE_CODE VARCHAR(10) NOT NULL,
    OFFICE_NAME VARCHAR(100) NOT NULL,
    PRIORITY1_OFFICE_NAME VARCHAR(100) NOT NULL,
    PRIORITY2_OFFICE_NAME VARCHAR(100) NOT NULL,
    IS_ACTIVE TINYINT(1) NOT NULL DEFAULT 1,
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_office_overflow (OFFICE_CODE)
);

-- ============================================================
-- TABLE: OFFICE_GLOBAL_THRESHOLD_CONFIG
-- Single configurable threshold for all RBIO offices
-- ============================================================

CREATE TABLE IF NOT EXISTS OFFICE_GLOBAL_THRESHOLD_CONFIG (
    ID INT PRIMARY KEY DEFAULT 1,
    THRESHOLD_VALUE INT NOT NULL DEFAULT 2,
    UPDATED_BY VARCHAR(200),
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_single_row CHECK (ID = 1)
);

INSERT INTO OFFICE_GLOBAL_THRESHOLD_CONFIG (ID, THRESHOLD_VALUE, UPDATED_BY) VALUES (1, 2, 'SYSTEM');

-- ============================================================
-- SEED: OFFICE_OVERFLOW_MAPPING
-- ============================================================

INSERT INTO OFFICE_OVERFLOW_MAPPING (OFFICE_CODE, OFFICE_NAME, PRIORITY1_OFFICE_NAME, PRIORITY2_OFFICE_NAME) VALUES
('003', 'Bhubaneswar', 'Kolkata-II', 'Patna'),
('008', 'Guwahati', 'Bhubaneswar', 'Kolkata-I'),
('005', 'Kolkata-I', 'Ranchi', 'Kolkata-II'),
('022', 'Kolkata-II', 'Kolkata-I', 'Guwahati'),
('012', 'Patna', 'Guwahati', 'Ranchi'),
('018', 'Ranchi', 'Patna', 'Bhubaneswar'),
('007', 'Chandigarh', 'Dehradun', 'New Delhi-I'),
('017', 'Dehradun', 'Chandigarh', 'Kanpur'),
('010', 'Jaipur', 'Kanpur', 'Chandigarh'),
('020', 'Jammu', 'New Delhi-II', 'Jaipur'),
('011', 'Kanpur', 'Jaipur', 'Dehradun'),
('014', 'New Delhi-I', 'Shimla', 'New Delhi-II'),
('016', 'New Delhi-II', 'Jammu', 'Shimla'),
('023', 'Shimla', 'New Delhi-I', 'Jammu'),
('002', 'Bengaluru', 'Thiruvananthapuram', 'Hyderabad'),
('006', 'Chennai-I', 'Chennai-II', 'Bengaluru'),
('024', 'Chennai-II', 'Hyderabad', 'Chennai-I'),
('009', 'Hyderabad', 'Chennai-I', 'Thiruvananthapuram'),
('015', 'Thiruvananthapuram', 'Bengaluru', 'Chennai-II'),
('001', 'Ahmedabad', 'Raipur', 'Mumbai-I'),
('004', 'Bhopal', 'Ahmedabad', 'Mumbai-II'),
('013', 'Mumbai-I', 'Mumbai-II', 'Bhopal'),
('021', 'Mumbai-II', 'Mumbai-I', 'Raipur'),
('019', 'Raipur', 'Bhopal', 'Ahmedabad');
