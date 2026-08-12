-- ============================================================
-- RBI CMS Phase 5 - Ombudsman Office Master Table
-- Version: 5.0.0
-- Description: Master table for RBI Ombudsman Office and
--              State/UT territorial jurisdiction mapping
-- MySQL-compatible syntax
-- ============================================================

-- ============================================================
-- TABLE: OMBUDSMAN_OFFICE_MASTER
-- ============================================================

CREATE TABLE IF NOT EXISTS OMBUDSMAN_OFFICE_MASTER (
    ID INT PRIMARY KEY,
    OFFICE_NAME VARCHAR(100) NOT NULL,
    JURISDICTION VARCHAR(1000) NOT NULL,
    IS_ACTIVE TINYINT(1) NOT NULL DEFAULT 1,
    CREATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UPDATED_AT TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- ============================================================
-- SEED: OMBUDSMAN_OFFICE_MASTER
-- ============================================================

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
