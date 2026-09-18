-- V16: NODAL_OFFICER_RECORDS (MySQL)
--
-- The table and its JPA entity have existed for a while and are read by the RE portal's nodal
-- officer stream and by the staleness reminder scheduler, but nothing ever inserted a row, so the
-- MySQL schema set never needed it. Complaint creation now writes one record per complaint, and
-- the prod profile runs ddl-auto: validate, so the table must exist before deploy.
--
-- Oracle equivalent: already present in database/oracle/V5__complete_ddl_dml.sql.
-- Not idempotent: re-running fails with "table already exists".

CREATE TABLE NODAL_OFFICER_RECORDS (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    complaint_number   VARCHAR(50)  NOT NULL,
    entity_name        VARCHAR(200) NULL,
    nodal_officer_name VARCHAR(200) NULL,
    pno_name           VARCHAR(200) NULL,
    designation        VARCHAR(100) NULL,
    email              VARCHAR(200) NULL,
    phone              VARCHAR(20)  NULL,
    -- INFORMATION_REQUIRED is the state the reminder scheduler chases, and is the state every
    -- record starts in: the RE has not yet supplied its nodal officer details for this complaint.
    status             VARCHAR(30)  NOT NULL DEFAULT 'INFORMATION_REQUIRED',
    assigned_to        VARCHAR(200) NULL,
    created_at         DATETIME(6)  NULL,
    last_modified_at   DATETIME(6)  NULL
);

-- Deliberately not UNIQUE on complaint_number, matching the Oracle set and the entity mapping.
-- NodalOfficerRecordService.findByComplaintNumber is what keeps creation idempotent.
CREATE INDEX idx_no_complaint ON NODAL_OFFICER_RECORDS(complaint_number);
CREATE INDEX idx_no_entity ON NODAL_OFFICER_RECORDS(entity_name);
CREATE INDEX idx_no_status ON NODAL_OFFICER_RECORDS(status);
CREATE INDEX idx_no_last_modified ON NODAL_OFFICER_RECORDS(last_modified_at);
