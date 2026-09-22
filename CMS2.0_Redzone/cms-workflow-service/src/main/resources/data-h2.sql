-- Seed data for dev-local H2 (officer pool)

-- CRPC DEOs
INSERT INTO WF_OFFICER_POOL (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD) VALUES
('deo.raghav', 'Raghav Sharma', 'CRPC_DEO', 'MUMBAI', true, false, 0, 50),
('deo.priya', 'Priya Nair', 'CRPC_DEO', 'MUMBAI', true, false, 0, 50),
('deo.amit', 'Amit Kulkarni', 'CRPC_DEO', 'DELHI', true, false, 0, 50),
('deo.sunita', 'Sunita Desai', 'CRPC_DEO', 'CHENNAI', true, false, 0, 50);

-- CRPC Reviewers
INSERT INTO WF_OFFICER_POOL (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD) VALUES
('rev.radhika', 'Radhika Rao', 'CRPC_REVIEWER', 'MUMBAI', true, false, 0, 30),
('rev.bhupinder', 'Bhupinder Singh', 'CRPC_REVIEWER', 'DELHI', true, false, 0, 30),
('rev.meena', 'Meena Iyer', 'CRPC_REVIEWER', 'CHENNAI', true, false, 0, 30);

-- RBIO Officers
INSERT INTO WF_OFFICER_POOL (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD) VALUES
('rbio.officer1', 'Vikram Mehta', 'RBIO_OFFICER', 'MUMBAI', true, false, 0, 40),
('rbio.officer2', 'Anjali Gupta', 'RBIO_OFFICER', 'MUMBAI', true, false, 0, 40),
('rbio.officer3', 'Suresh Kumar', 'RBIO_OFFICER', 'DELHI', true, false, 0, 40),
('rbio.officer4', 'Kavita Reddy', 'RBIO_OFFICER', 'CHENNAI', true, false, 0, 40);

-- CEPC Officers
INSERT INTO WF_OFFICER_POOL (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD) VALUES
('cepc.officer1', 'Rohan Patil', 'CEPC_OFFICER', 'MUMBAI', true, false, 0, 40),
('cepc.officer2', 'Deepa Krishnan', 'CEPC_OFFICER', 'MUMBAI', true, false, 0, 40),
('cepc.officer3', 'Manish Tiwari', 'CEPC_OFFICER', 'DELHI', true, false, 0, 40);

-- RBIO Supervisors
INSERT INTO WF_OFFICER_POOL (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD) VALUES
('rbio.super1', 'Rajesh Verma', 'RBIO_SUPERVISOR', 'MUMBAI', true, false, 0, 20),
('rbio.super2', 'Lakshmi Pillai', 'RBIO_SUPERVISOR', 'DELHI', true, false, 0, 20);

-- CEPC Supervisors
INSERT INTO WF_OFFICER_POOL (USER_ID, DISPLAY_NAME, ROLE_GROUP, REGIONAL_OFFICE, IS_ACTIVE, IS_ON_LEAVE, CURRENT_WORKLOAD, MAX_WORKLOAD) VALUES
('cepc.super1', 'Arun Joshi', 'CEPC_SUPERVISOR', 'MUMBAI', true, false, 0, 20);

-- Assignment Counters (start at 0)
INSERT INTO WF_ASSIGNMENT_COUNTER (ROLE_GROUP, LAST_ASSIGNED_INDEX, UPDATED_AT) VALUES
('CRPC_DEO', 0, CURRENT_TIMESTAMP),
('CRPC_REVIEWER', 0, CURRENT_TIMESTAMP),
('RBIO_OFFICER', 0, CURRENT_TIMESTAMP),
('CEPC_OFFICER', 0, CURRENT_TIMESTAMP),
('RBIO_SUPERVISOR', 0, CURRENT_TIMESTAMP),
('CEPC_SUPERVISOR', 0, CURRENT_TIMESTAMP);
