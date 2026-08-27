-- ============================================================
-- CMS 2.0 — V11: cms-mail-intake schema (H2 / MySQL)
--
-- Three new tables backing the embedded SMTP receiver. Table and
-- column names are uppercase, matching this module's own entity
-- annotations exactly (com.rbi.cms.mailintake.entity.* uses
-- explicit @Table/@Column names, unlike cms-backend's entities —
-- see the V10 script for that distinction). Note that
-- cms-mail-intake's own dev-local profile uses ddl-auto:
-- create-drop against an ephemeral H2 database and does not
-- actually need this script to run locally — it's provided for
-- anyone running the module against a persistent H2/MySQL
-- instance instead, matching the incremental-script convention
-- used elsewhere in this repo.
-- ============================================================

-- 1. INBOUND_EMAIL — one row per accepted SMTP transaction
CREATE TABLE IF NOT EXISTS INBOUND_EMAIL (
    ID                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    SMTP_MESSAGE_ID     VARCHAR(500),
    CONTENT_SHA256      VARCHAR(64)  NOT NULL UNIQUE,
    ENVELOPE_FROM       VARCHAR(320),
    ENVELOPE_TO         VARCHAR(320) NOT NULL,
    REMOTE_IP           VARCHAR(45)  NOT NULL,
    RECEIVED_AT         TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    RAW_STORE_URI       VARCHAR(500) NOT NULL,
    RAW_SIZE_BYTES      BIGINT       NOT NULL,
    STATUS              VARCHAR(20)  DEFAULT 'RECEIVED' NOT NULL,
    FAILED_STAGE        VARCHAR(20),
    QUARANTINE_REASON   VARCHAR(30),
    ATTEMPT_COUNT       INT          DEFAULT 0 NOT NULL,
    NEXT_ATTEMPT_AT     TIMESTAMP,
    LAST_ERROR          VARCHAR(2000),
    ORIGINAL_FROM       VARCHAR(320),
    ORIGINAL_SUBJECT    VARCHAR(1000),
    ORIGINAL_SENT_AT    TIMESTAMP,
    RESOLVED_BY         VARCHAR(30),
    COMPLAINT_REF       VARCHAR(100),
    LINKED_COMPLAINT_ID VARCHAR(50),
    CREATED_AT          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    UPDATED_AT          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX IDX_INBOUND_EMAIL_MSGID        ON INBOUND_EMAIL (SMTP_MESSAGE_ID);
CREATE INDEX IDX_INBOUND_EMAIL_STATUS       ON INBOUND_EMAIL (STATUS);
CREATE INDEX IDX_INBOUND_EMAIL_NEXT_ATTEMPT ON INBOUND_EMAIL (STATUS, NEXT_ATTEMPT_AT);
CREATE INDEX IDX_INBOUND_EMAIL_COMPLAINT_REF ON INBOUND_EMAIL (COMPLAINT_REF);

-- 2. INBOUND_EMAIL_ATTACHMENT
CREATE TABLE IF NOT EXISTS INBOUND_EMAIL_ATTACHMENT (
    ID                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    EMAIL_ID              BIGINT       NOT NULL,
    FILENAME              VARCHAR(500),
    DECLARED_CONTENT_TYPE VARCHAR(200),
    DETECTED_CONTENT_TYPE VARCHAR(200),
    SIZE_BYTES            BIGINT       NOT NULL,
    CONTENT_SHA256        VARCHAR(64),
    STORE_URI             VARCHAR(500) NOT NULL,
    SCAN_STATUS           VARCHAR(20)  DEFAULT 'PENDING' NOT NULL,
    EXTRACTED_TEXT_URI    VARCHAR(500),
    CREATED_AT            TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT FK_ATTACHMENT_EMAIL FOREIGN KEY (EMAIL_ID) REFERENCES INBOUND_EMAIL (ID)
);

CREATE INDEX IDX_ATTACHMENT_EMAIL_ID ON INBOUND_EMAIL_ATTACHMENT (EMAIL_ID);

-- 3. INBOUND_EMAIL_EVENT — append-only audit trail
CREATE TABLE IF NOT EXISTS INBOUND_EMAIL_EVENT (
    ID          BIGINT AUTO_INCREMENT PRIMARY KEY,
    EMAIL_ID    BIGINT       NOT NULL,
    EVENT_AT    TIMESTAMP    DEFAULT CURRENT_TIMESTAMP NOT NULL,
    FROM_STATUS VARCHAR(20),
    TO_STATUS   VARCHAR(20)  NOT NULL,
    ACTOR       VARCHAR(100) NOT NULL,
    DETAIL      VARCHAR(2000),
    CONSTRAINT FK_EVENT_EMAIL FOREIGN KEY (EMAIL_ID) REFERENCES INBOUND_EMAIL (ID)
);

CREATE INDEX IDX_EVENT_EMAIL_ID ON INBOUND_EMAIL_EVENT (EMAIL_ID);
