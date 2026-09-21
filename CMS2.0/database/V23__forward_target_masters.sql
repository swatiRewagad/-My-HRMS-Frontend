-- V23: REGULATOR_MASTER, RBI_DEPARTMENT_MASTER (MySQL)
--
-- The RBIO Forward tab offers three destinations: another office, another RBI department, or an
-- external regulator. Only the office branch had a backing master (OFFICE_CODE_MASTER); the other two
-- were free-text boxes with a decorative search icon, so the officer typed both the recipient's name
-- and its email address by hand on a path that closes the complaint immediately. A typo there sends
-- the forward nowhere and the complaint is closed anyway.
--
-- Two tables rather than one with a TYPE discriminator: a regulator is a separate organisation and an
-- RBI department is internal, and the two are expected to diverge (a regulator will likely need a
-- jurisdiction/statute column that means nothing for a department).
--
-- EMAIL is nullable on purpose: an admin may need to register a destination before its grievance
-- mailbox is confirmed, and NULL is the honest value for "not known yet". The Forward tab requires a
-- non-empty address before it will submit, so a NULL here blocks the forward instead of silently
-- mailing nobody.
--
-- No seed data. The organisation names are public but their real mailboxes are not in this
-- repository, and a plausible wrong address is worse than an empty table because it reads as
-- reference data. dev-local is seeded by ForwardTargetDevDataLoader with obviously-fake example.com
-- addresses; production populates these through the admin CRUD on /api/v1/masters/regulators and
-- /api/v1/masters/rbi-departments.
--
-- Oracle equivalent: database/oracle/V23__forward_target_masters.sql
-- Must be applied before booting the prod profile: it runs ddl-auto=validate, so a missing table
-- fails startup rather than misbehaving later.

CREATE TABLE IF NOT EXISTS REGULATOR_MASTER (
    ID          BIGINT        NOT NULL AUTO_INCREMENT,
    CODE        VARCHAR(50)   NOT NULL,
    NAME        VARCHAR(200)  NOT NULL,
    EMAIL       VARCHAR(150)  NULL,
    ACTIVE      BIT(1)        NOT NULL DEFAULT b'1',
    SORT_ORDER  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (ID),
    CONSTRAINT UK_REGULATOR_MASTER_CODE UNIQUE (CODE)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS RBI_DEPARTMENT_MASTER (
    ID          BIGINT        NOT NULL AUTO_INCREMENT,
    CODE        VARCHAR(50)   NOT NULL,
    NAME        VARCHAR(200)  NOT NULL,
    EMAIL       VARCHAR(150)  NULL,
    ACTIVE      BIT(1)        NOT NULL DEFAULT b'1',
    SORT_ORDER  INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (ID),
    CONSTRAINT UK_RBI_DEPARTMENT_MASTER_CODE UNIQUE (CODE)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Both lookups filter on ACTIVE and order by SORT_ORDER, then match a LIKE on NAME/CODE.
CREATE INDEX IDX_REGULATOR_MASTER_ACTIVE ON REGULATOR_MASTER (ACTIVE, SORT_ORDER);
CREATE INDEX IDX_RBI_DEPARTMENT_MASTER_ACTIVE ON RBI_DEPARTMENT_MASTER (ACTIVE, SORT_ORDER);
