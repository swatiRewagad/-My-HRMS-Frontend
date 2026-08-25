-- ============================================================
-- CMS 2.0 — V9: Complaint Comments, Officer Availability,
--               and Closure Fields (H2 compatible)
-- ============================================================

-- 1. COMPLAINT_COMMENTS table
CREATE TABLE IF NOT EXISTS COMPLAINT_COMMENTS (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_number    VARCHAR(100)  NOT NULL,
    author              VARCHAR(100)  NOT NULL,
    initials            VARCHAR(10),
    text                VARCHAR(2000) NOT NULL,
    role                VARCHAR(50),
    color               VARCHAR(10)   DEFAULT '#6366f1',
    created_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_comment_complaint ON COMPLAINT_COMMENTS (complaint_number);
CREATE INDEX IF NOT EXISTS idx_comment_created   ON COMPLAINT_COMMENTS (created_at);

-- 2. OFFICER_AVAILABILITY table
CREATE TABLE IF NOT EXISTS OFFICER_AVAILABILITY (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id             VARCHAR(100)  NOT NULL,
    role                VARCHAR(50)   NOT NULL,
    active              BOOLEAN       DEFAULT TRUE NOT NULL,
    on_leave            BOOLEAN       DEFAULT FALSE NOT NULL,
    leave_start_date    DATE,
    leave_end_date      DATE,
    leave_reason        VARCHAR(200),
    current_workload    INT           DEFAULT 0,
    max_workload        INT           DEFAULT 20,
    office_code         VARCHAR(50),
    updated_at          TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_officer_user_id ON OFFICER_AVAILABILITY (user_id);
CREATE INDEX IF NOT EXISTS idx_officer_role    ON OFFICER_AVAILABILITY (role);

-- 3. Add entity_name and category_name to COMPLAINTS
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS entity_name VARCHAR(300);
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS category_name VARCHAR(200);

-- 4. Add closure fields to COMPLAINTS
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS closure_clause_description TEXT;
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS complaint_status_on_portal VARCHAR(100);
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS speaking_order_generated VARCHAR(10);
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS gist_of_case TEXT;
ALTER TABLE COMPLAINTS ADD COLUMN IF NOT EXISTS gist_of_case_regional TEXT;
