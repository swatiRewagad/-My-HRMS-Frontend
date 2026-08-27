-- ============================================================
-- CMS 2.0 — V12: cms-mail-intake Stage 5 (H2 / MySQL)
--
-- Maker-checker admin actions (replay / force-link) over the
-- Stage 2 INBOUND_EMAIL schema, plus the retention-job tracking
-- column. Same naming convention as V11 — uppercase, matching
-- this module's explicit @Table/@Column entity annotations.
-- ============================================================

-- 1. Retention-job bookkeeping — see InboundEmail#rawPurgedAt.
ALTER TABLE INBOUND_EMAIL ADD COLUMN IF NOT EXISTS RAW_PURGED_AT TIMESTAMP;

-- 2. MAIL_INTAKE_ADMIN_ACTION — one row per replay/force-link request, maker-checker gated.
CREATE TABLE IF NOT EXISTS MAIL_INTAKE_ADMIN_ACTION (
    ID                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    EMAIL_ID            BIGINT       NOT NULL,
    ACTION_TYPE         VARCHAR(20)  NOT NULL,
    STATUS              VARCHAR(20)  DEFAULT 'PENDING' NOT NULL,
    TARGET_COMPLAINT_ID VARCHAR(50),
    REQUESTED_BY        VARCHAR(100) NOT NULL,
    REQUESTED_AT        TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    REQUEST_REASON      VARCHAR(2000) NOT NULL,
    DECIDED_BY          VARCHAR(100),
    DECIDED_AT          TIMESTAMP,
    DECISION_NOTE       VARCHAR(2000),
    CONSTRAINT FK_ADMIN_ACTION_EMAIL FOREIGN KEY (EMAIL_ID) REFERENCES INBOUND_EMAIL (ID)
);

CREATE INDEX IDX_ADMIN_ACTION_EMAIL_ID ON MAIL_INTAKE_ADMIN_ACTION (EMAIL_ID);
CREATE INDEX IDX_ADMIN_ACTION_STATUS   ON MAIL_INTAKE_ADMIN_ACTION (STATUS, REQUESTED_AT);
