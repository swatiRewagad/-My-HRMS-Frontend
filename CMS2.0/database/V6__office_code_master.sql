-- ============================================================
-- RBI CMS Phase 6 - Office Code Master Table
-- Version: 6.0.0
-- Description: Master table for Office Type and Code mapping
-- MySQL-compatible syntax
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

-- ============================================================
-- SEED: OFFICE_CODE_MASTER
-- ============================================================

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
