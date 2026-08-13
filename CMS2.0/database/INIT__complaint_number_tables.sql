-- ============================================================
-- RBI CMS - Complaint Number Generation: CONSOLIDATED INIT SCRIPT
-- Combines: V5, V6, V7, V8
-- Purpose: Fresh deployment init for all complaint number tables
-- MySQL-compatible syntax
-- ============================================================

-- ============================================================
-- 1. OMBUDSMAN_OFFICE_MASTER
--    Maps 24 RBI Ombudsman offices to territorial jurisdictions
-- ============================================================

CREATE TABLE IF NOT EXISTS OMBUDSMAN_OFFICE_MASTER (
    ID INT PRIMARY KEY,
    OFFICE_NAME VARCHAR(100) NOT NULL,
    JURISDICTION VARCHAR(1000) NOT NULL,
    IS_ACTIVE TINYINT(1) NOT NULL DEFAULT 1,
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO OMBUDSMAN_OFFICE_MASTER (ID, OFFICE_NAME, JURISDICTION) VALUES
(1, 'Ahmedabad', 'Gujarat, Union Territories of Dadra and Nagar Haveli, Daman and Diu'),
(2, 'Bengaluru', 'Karnataka'),
(3, 'Bhopal', 'Madhya Pradesh'),
(4, 'Bhubaneswar', 'Odisha'),
(5, 'Chandigarh', 'Punjab and Union Territory of Chandigarh'),
(6, 'Chennai-I', 'Nine districts of Tamil Nadu viz., Thiruvallur, Chennai, Vellore, Ranipet, Kancheepuram, Chengalpattu, Krishnagiri, Tirupathur and Tiruvannamalai; and Union Territory of Andaman and Nicobar Islands'),
(7, 'Chennai-II', 'Tamil Nadu (excluding Districts of Thiruvallur, Chennai, Vellore, Ranipet, Kancheepuram, Chengalpattu, Krishnagiri, Tirupathur and Tiruvannamalai); and Union Territory of Puducherry (except Mahe Region)'),
(8, 'Dehradun', 'Uttarakhand and seven districts of Uttar Pradesh viz., Saharanpur, Shamli (Prabudh Nagar), Muzaffarnagar, Baghpat, Meerut, Bijnor and Amroha (Jyotiba Phule Nagar)'),
(9, 'Guwahati', 'Assam, Arunachal Pradesh, Manipur, Meghalaya, Mizoram, Nagaland and Tripura'),
(10, 'Hyderabad', 'Andhra Pradesh and Telangana'),
(11, 'Jaipur', 'Rajasthan'),
(12, 'Jammu', 'Union Territories of Jammu & Kashmir and Ladakh'),
(13, 'Kanpur', 'Uttar Pradesh (excluding Districts of Ghaziabad, Gautam Buddha Nagar, Saharanpur, Shamli (Prabudh Nagar), Muzaffarnagar, Baghpat, Meerut, Bijnor and Amroha (Jyotiba Phule Nagar))'),
(14, 'Kolkata-I', 'Three districts of West Bengal viz., Kolkata, South 24 Parganas, Howrah; and Sikkim'),
(15, 'Kolkata-II', 'West Bengal (excluding districts of Kolkata, South 24 Parganas and Howrah)'),
(16, 'Mumbai-I', 'Districts of Mumbai, Mumbai Suburban and Thane'),
(17, 'Mumbai-II', 'Goa and Maharashtra (except the districts of Mumbai, Mumbai Suburban and Thane)'),
(18, 'New Delhi-I', 'Delhi'),
(19, 'New Delhi-II', 'Haryana and Ghaziabad and Gautam Buddha Nagar districts of Uttar Pradesh'),
(20, 'Patna', 'Bihar'),
(21, 'Raipur', 'Chhattisgarh'),
(22, 'Ranchi', 'Jharkhand'),
(23, 'Shimla', 'Himachal Pradesh'),
(24, 'Thiruvananthapuram', 'Kerala, Union Territory of Lakshadweep and Union Territory of Puducherry (only Mahe Region)');

-- ============================================================
-- 2. OFFICE_CODE_MASTER
--    Maps office names to 3-digit codes + threshold counter
-- ============================================================

CREATE TABLE IF NOT EXISTS OFFICE_CODE_MASTER (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    OFFICE_TYPE VARCHAR(10) NOT NULL,
    OFFICE_NAME VARCHAR(100) NOT NULL,
    OFFICE_CODE VARCHAR(10) NOT NULL UNIQUE,
    COUNTER INT NOT NULL DEFAULT 0,
    THRESHOLD INT NOT NULL DEFAULT 2,
    IS_ACTIVE TINYINT(1) NOT NULL DEFAULT 1,
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO OFFICE_CODE_MASTER (OFFICE_TYPE, OFFICE_NAME, OFFICE_CODE) VALUES
('BO', 'Ahmedabad', '001'),
('BO', 'Bengaluru', '002'),
('BO', 'Bhubaneswar', '003'),
('BO', 'Bhopal', '004'),
('BO', 'Kolkata-I', '005'),
('BO', 'Chennai-I', '006'),
('BO', 'Chandigarh', '007'),
('BO', 'Guwahati', '008'),
('BO', 'Hyderabad', '009'),
('BO', 'Jaipur', '010'),
('BO', 'Kanpur', '011'),
('BO', 'Patna', '012'),
('BO', 'Mumbai-I', '013'),
('BO', 'New Delhi-I', '014'),
('BO', 'Thiruvananthapuram', '015'),
('BO', 'New Delhi-II', '016'),
('BO', 'Dehradun', '017'),
('BO', 'Ranchi', '018'),
('BO', 'Raipur', '019'),
('BO', 'Jammu', '020'),
('BO', 'Mumbai-II', '021'),
('BO', 'Kolkata-II', '022'),
('BO', 'New Delhi-III', '023'),
('BO', 'Chennai-II', '024'),
('BO', 'Shimla', '025');

-- ============================================================
-- 3. COMPLAINT_NUMBER_SEQUENCE
--    Tracks sequential complaint numbers per office + FY
-- ============================================================

CREATE TABLE IF NOT EXISTS COMPLAINT_NUMBER_SEQUENCE (
    ID INT AUTO_INCREMENT PRIMARY KEY,
    OFFICE_CODE VARCHAR(10) NOT NULL,
    FINANCIAL_YEAR VARCHAR(6) NOT NULL,
    LAST_SEQUENCE INT NOT NULL DEFAULT 0,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_office_fy (OFFICE_CODE, FINANCIAL_YEAR)
);

-- ============================================================
-- 4. OFFICE_OVERFLOW_MAPPING
--    Priority-based overflow routing when threshold is met
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

-- ============================================================
-- 5. OFFICE_GLOBAL_THRESHOLD_CONFIG
--    Single-row configurable global threshold (Super Admin)
-- ============================================================

CREATE TABLE IF NOT EXISTS OFFICE_GLOBAL_THRESHOLD_CONFIG (
    ID INT PRIMARY KEY DEFAULT 1,
    THRESHOLD_VALUE INT NOT NULL DEFAULT 2,
    UPDATED_BY VARCHAR(200),
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT chk_single_row CHECK (ID = 1)
);

INSERT INTO OFFICE_GLOBAL_THRESHOLD_CONFIG (ID, THRESHOLD_VALUE, UPDATED_BY) VALUES (1, 2, 'SYSTEM');
