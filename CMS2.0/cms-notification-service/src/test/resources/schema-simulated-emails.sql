-- Minimal SIMULATED_EMAILS, holding only the columns OutboundEmailRepository names. Mirrors
-- cms-backend/src/main/resources/db/cms_database_scripts.sql; the rest of the table is irrelevant here.
CREATE TABLE SIMULATED_EMAILS (
    ID                BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    MESSAGE_ID        VARCHAR(100) NOT NULL,
    THREAD_ID         VARCHAR(100),
    FROM_EMAIL        VARCHAR(200),
    TO_EMAIL          VARCHAR(200),
    CC_EMAIL          VARCHAR(500),
    BCC_EMAIL         VARCHAR(500),
    SUBJECT           VARCHAR(500),
    BODY              CLOB,
    DIRECTION         VARCHAR(10),
    STATUS            VARCHAR(20),
    DELIVERY_STATUS   VARCHAR(20),
    LAST_ERROR        VARCHAR(2000),
    DISPATCH_ATTEMPTS INT,
    COMPLAINT_ID      BIGINT,
    COMPLAINT_NUMBER  VARCHAR(50),
    ATTACHMENT_URL    VARCHAR(500),
    CREATED_BY        VARCHAR(100),
    SENT_AT           TIMESTAMP,
    RECEIVED_AT       TIMESTAMP,
    PROCESSED_AT      TIMESTAMP,
    UPDATED_AT        TIMESTAMP
);
