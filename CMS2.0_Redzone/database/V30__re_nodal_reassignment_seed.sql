-- V30: Seed the RE nodal officer directory and reassignable records (UST838, UST841)
-- MySQL version
--
-- Why a seed migration is needed at all: ENTITY_USERS is the candidate directory the reassignment
-- popup reads, and it is sourced from the database rather than Keycloak because the `cms` realm
-- contains no RE_* users and no RE_* roles. With the table empty, every candidate list is empty and
-- the feature cannot be exercised or demonstrated even though the code is correct.
--
-- The entity codes below are read from COMPLAINTS.ENTITY_CODE rather than typed in, so the seeded
-- officers belong to entities that actually exist in this database. Note that COMPLAINTS.ENTITY_CODE
-- currently holds entity *names* ("State Bank of India"), not short codes — that is the existing
-- convention in this schema, and the reassignment code treats the value as an opaque scoping key, so
-- it works either way. Flagged rather than "corrected": renaming that value estate-wide is a separate
-- change affecting the RE portal, the query threads and the activity ladder.
--
-- Re-running is safe: every insert is INSERT ... WHERE NOT EXISTS.

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. Nodal officer directory for the two entities with the most complaints.
--    Three roles per entity so the UST841 role filter has something to filter, and one inactive
--    officer so the "inactive officers are never offered as a target" rule is testable.
-- ═══════════════════════════════════════════════════════════════════════════
DROP PROCEDURE IF EXISTS seed_entity_users;
DELIMITER //
CREATE PROCEDURE seed_entity_users()
BEGIN
    DECLARE v_entity VARCHAR(50);
    DECLARE v_done INT DEFAULT 0;

    -- Seed for whichever entities actually have complaints, so this migration does not depend on a
    -- particular demo dataset being loaded.
    DECLARE cur CURSOR FOR
        SELECT DISTINCT entity_code FROM COMPLAINTS
         WHERE entity_code IS NOT NULL AND entity_code <> ''
         ORDER BY entity_code
         LIMIT 5;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;
    seed_loop: LOOP
        FETCH cur INTO v_entity;
        IF v_done = 1 THEN
            LEAVE seed_loop;
        END IF;

        INSERT INTO ENTITY_USERS (user_id, entity_code, display_name, email, designation,
                                  re_role, active, territory, created_at, last_modified_at)
        SELECT CONCAT('re.no1.', MD5(v_entity)), v_entity, 'Nodal Officer One',
               'nodal1@example.test', 'Deputy Manager', 'NODAL_OFFICER', 1, 'West', NOW(6), NOW(6)
         WHERE NOT EXISTS (SELECT 1 FROM (SELECT user_id, entity_code FROM ENTITY_USERS) e
                            WHERE e.user_id = CONCAT('re.no1.', MD5(v_entity))
                              AND e.entity_code = v_entity);

        INSERT INTO ENTITY_USERS (user_id, entity_code, display_name, email, designation,
                                  re_role, active, territory, created_at, last_modified_at)
        SELECT CONCAT('re.no2.', MD5(v_entity)), v_entity, 'Nodal Officer Two',
               'nodal2@example.test', 'Manager', 'NODAL_OFFICER', 1, 'South', NOW(6), NOW(6)
         WHERE NOT EXISTS (SELECT 1 FROM (SELECT user_id, entity_code FROM ENTITY_USERS) e
                            WHERE e.user_id = CONCAT('re.no2.', MD5(v_entity))
                              AND e.entity_code = v_entity);

        INSERT INTO ENTITY_USERS (user_id, entity_code, display_name, email, designation,
                                  re_role, active, territory, created_at, last_modified_at)
        SELECT CONCAT('re.cp1.', MD5(v_entity)), v_entity, 'Contact Person One',
               'contact1@example.test', 'Assistant Manager', 'CONTACT_PERSON', 1, 'West', NOW(6), NOW(6)
         WHERE NOT EXISTS (SELECT 1 FROM (SELECT user_id, entity_code FROM ENTITY_USERS) e
                            WHERE e.user_id = CONCAT('re.cp1.', MD5(v_entity))
                              AND e.entity_code = v_entity);

        INSERT INTO ENTITY_USERS (user_id, entity_code, display_name, email, designation,
                                  re_role, active, territory, created_at, last_modified_at)
        SELECT CONCAT('re.pno.', MD5(v_entity)), v_entity, 'Principal Nodal Officer',
               'pno@example.test', 'General Manager', 'PNO', 1, 'HO', NOW(6), NOW(6)
         WHERE NOT EXISTS (SELECT 1 FROM (SELECT user_id, entity_code FROM ENTITY_USERS) e
                            WHERE e.user_id = CONCAT('re.pno.', MD5(v_entity))
                              AND e.entity_code = v_entity);

        -- Inactive on purpose: proves the target-inactive rejection path is reachable.
        INSERT INTO ENTITY_USERS (user_id, entity_code, display_name, email, designation,
                                  re_role, active, territory, created_at, last_modified_at)
        SELECT CONCAT('re.left.', MD5(v_entity)), v_entity, 'Former Officer',
               'former@example.test', 'Manager', 'NODAL_OFFICER', 0, 'East', NOW(6), NOW(6)
         WHERE NOT EXISTS (SELECT 1 FROM (SELECT user_id, entity_code FROM ENTITY_USERS) e
                            WHERE e.user_id = CONCAT('re.left.', MD5(v_entity))
                              AND e.entity_code = v_entity);
    END LOOP;
    CLOSE cur;
END //
DELIMITER ;

CALL seed_entity_users();
DROP PROCEDURE IF EXISTS seed_entity_users;

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. Nodal officer records to reassign, one per complaint, owned by the first nodal officer of the
--    complaint's entity. ENTITY_CODE is set from the complaint, so the scoping rule is exercised.
--
--    Statuses are spread across an open value and an excluded (closed) value so the UST838 workload
--    count has something to exclude — a seed where every record counted would let a broken exclusion
--    filter pass unnoticed.
-- ═══════════════════════════════════════════════════════════════════════════
INSERT INTO NODAL_OFFICER_RECORDS (complaint_number, entity_name, entity_code, nodal_officer_name,
                                   pno_name, designation, email, phone, status, assigned_to,
                                   version, created_at, last_modified_at)
SELECT c.complaint_number,
       c.entity_code,
       c.entity_code,
       'Nodal Officer One',
       'Principal Nodal Officer',
       'Deputy Manager',
       'nodal1@example.test',
       '9999900001',
       CASE WHEN c.status IN ('closed', 'resolved', 'rejected', 'withdrawn')
            THEN 'CLOSED' ELSE 'INFORMATION_REQUIRED' END,
       CONCAT('re.no1.', MD5(c.entity_code)),
       0,
       NOW(6), NOW(6)
  FROM COMPLAINTS c
 WHERE c.entity_code IS NOT NULL
   AND c.entity_code <> ''
   AND EXISTS (SELECT 1 FROM ENTITY_USERS e WHERE e.entity_code = c.entity_code)
   AND NOT EXISTS (SELECT 1 FROM (SELECT complaint_number FROM NODAL_OFFICER_RECORDS) n
                    WHERE n.complaint_number = c.complaint_number);
