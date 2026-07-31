-- Seed data for MySQL (officer pool for workflow assignment)

-- CRPC DEOs
INSERT IGNORE INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload) VALUES
('deo.raghav', 'Raghav Sharma', 'CRPC_DEO', 'MUMBAI', 1, 0, 0, 50),
('deo.priya', 'Priya Nair', 'CRPC_DEO', 'MUMBAI', 1, 0, 0, 50),
('deo.amit', 'Amit Kulkarni', 'CRPC_DEO', 'DELHI', 1, 0, 0, 50),
('deo.sunita', 'Sunita Desai', 'CRPC_DEO', 'CHENNAI', 1, 0, 0, 50);

-- CRPC Reviewers
INSERT IGNORE INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload) VALUES
('rev.radhika', 'Radhika Rao', 'CRPC_REVIEWER', 'MUMBAI', 1, 0, 0, 30),
('rev.bhupinder', 'Bhupinder Singh', 'CRPC_REVIEWER', 'DELHI', 1, 0, 0, 30),
('rev.meena', 'Meena Iyer', 'CRPC_REVIEWER', 'CHENNAI', 1, 0, 0, 30);

-- RBIO Officers
INSERT IGNORE INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload) VALUES
('rbio.officer1', 'Vikram Mehta', 'RBIO_OFFICER', 'MUMBAI', 1, 0, 0, 40),
('rbio.officer2', 'Anjali Gupta', 'RBIO_OFFICER', 'MUMBAI', 1, 0, 0, 40),
('rbio.officer3', 'Suresh Kumar', 'RBIO_OFFICER', 'DELHI', 1, 0, 0, 40),
('rbio.officer4', 'Kavita Reddy', 'RBIO_OFFICER', 'CHENNAI', 1, 0, 0, 40);

-- CEPC Officers
INSERT IGNORE INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload) VALUES
('cepc.officer1', 'Rohan Patil', 'CEPC_OFFICER', 'MUMBAI', 1, 0, 0, 40),
('cepc.officer2', 'Deepa Krishnan', 'CEPC_OFFICER', 'MUMBAI', 1, 0, 0, 40),
('cepc.officer3', 'Manish Tiwari', 'CEPC_OFFICER', 'DELHI', 1, 0, 0, 40);

-- RBIO Supervisors
INSERT IGNORE INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload) VALUES
('rbio.super1', 'Rajesh Verma', 'RBIO_SUPERVISOR', 'MUMBAI', 1, 0, 0, 20),
('rbio.super2', 'Lakshmi Pillai', 'RBIO_SUPERVISOR', 'DELHI', 1, 0, 0, 20);

-- CEPC Supervisors
INSERT IGNORE INTO wf_officer_pool (user_id, display_name, role_group, regional_office, is_active, is_on_leave, current_workload, max_workload) VALUES
('cepc.super1', 'Arun Joshi', 'CEPC_SUPERVISOR', 'MUMBAI', 1, 0, 0, 20);

-- Assignment Counters
INSERT IGNORE INTO wf_assignment_counter (role_group, last_assigned_index, updated_at) VALUES
('CRPC_DEO', 0, NOW()),
('CRPC_REVIEWER', 0, NOW()),
('RBIO_OFFICER', 0, NOW()),
('CEPC_OFFICER', 0, NOW()),
('RBIO_SUPERVISOR', 0, NOW()),
('CEPC_SUPERVISOR', 0, NOW());
