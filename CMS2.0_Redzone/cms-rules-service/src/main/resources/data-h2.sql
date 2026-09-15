-- Rule Categories
INSERT INTO RULE_CATEGORY (id, code, name, description, created_at) VALUES
(1, 'ASSIGNMENT', 'Assignment Rules', 'Rules for auto-assigning complaints to teams based on category, priority, and jurisdiction', CURRENT_TIMESTAMP),
(2, 'ELIGIBILITY', 'Eligibility Rules', 'Pre-complaint eligibility validation rules per RBI BO Regulation 2021', CURRENT_TIMESTAMP),
(3, 'ESCALATION', 'Escalation Rules', 'SLA-based escalation and high-value complaint routing rules', CURRENT_TIMESTAMP);

-- Assignment Rules
INSERT INTO RULE_DEFINITION (id, rule_code, rule_name, category_id, drl_content, salience, version, status, created_by, created_at, approved_by, approved_at) VALUES
(1, 'ASSIGN_ATM_TEAM', 'ATM Complaints - Assign to ATM Team', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "ATM Complaints - Assign to ATM Team"
    salience 100
    when
        $fact : AssignmentFact(category == "ATM")
    then
        $fact.setAssignedTeam("ATM_TEAM");
        $fact.setAssignedOfficer(null);
        update($fact);
end', 100, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(2, 'ASSIGN_UPI_TEAM', 'UPI Complaints - Assign to Digital Team', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "UPI Complaints - Assign to Digital Team"
    salience 100
    when
        $fact : AssignmentFact(category == "UPI")
    then
        $fact.setAssignedTeam("DIGITAL_TEAM");
        $fact.setAssignedOfficer(null);
        update($fact);
end', 100, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(3, 'ASSIGN_NEFT_RTGS_TEAM', 'NEFT/RTGS Complaints - Assign to Payment Systems Team', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "NEFT/RTGS Complaints - Assign to Payment Systems Team"
    salience 100
    when
        $fact : AssignmentFact(category == "NEFT_RTGS")
    then
        $fact.setAssignedTeam("PAYMENT_SYSTEMS_TEAM");
        $fact.setAssignedOfficer(null);
        update($fact);
end', 100, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(4, 'ASSIGN_LOAN_TEAM', 'Loan Complaints - Assign to Lending Team', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "Loan Complaints - Assign to Lending Team"
    salience 100
    when
        $fact : AssignmentFact(category == "LOAN")
    then
        $fact.setAssignedTeam("LENDING_TEAM");
        $fact.setAssignedOfficer(null);
        update($fact);
end', 100, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(5, 'ASSIGN_CREDIT_CARD_TEAM', 'Credit Card Complaints - Assign to Cards Team', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "Credit Card Complaints - Assign to Cards Team"
    salience 100
    when
        $fact : AssignmentFact(category == "CREDIT_CARD")
    then
        $fact.setAssignedTeam("CARDS_TEAM");
        $fact.setAssignedOfficer(null);
        update($fact);
end', 100, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(6, 'ASSIGN_HIGH_PRIORITY_ESCALATE', 'High Priority - Auto Escalate to Senior Officer', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "High Priority - Auto Escalate to Senior Officer"
    salience 50
    when
        $fact : AssignmentFact(priority == "HIGH" || priority == "CRITICAL")
    then
        $fact.setEscalated(true);
        $fact.setEscalationLevel(1);
        update($fact);
end', 50, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(7, 'ASSIGN_DEFAULT_TEAM', 'Default Assignment - General Team', 1,
'package com.rbi.cms.rules.assignment;
import com.rbi.cms.common.dto.AssignmentFact;

rule "Default Assignment - General Team"
    salience 10
    when
        $fact : AssignmentFact(assignedTeam == null)
    then
        $fact.setAssignedTeam("GENERAL_TEAM");
        update($fact);
end', 10, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP);

-- Escalation Rules
INSERT INTO RULE_DEFINITION (id, rule_code, rule_name, category_id, drl_content, salience, version, status, created_by, created_at, approved_by, approved_at) VALUES
(8, 'ESCALATE_SLA_LEVEL1', 'SLA Breach Level 1 - 80% elapsed', 3,
'package com.rbi.cms.rules.escalation;
import com.rbi.cms.common.dto.EscalationFact;

rule "SLA Breach Level 1 - 80% elapsed"
    salience 100
    when
        $fact : EscalationFact(slaPercentElapsed >= 80, slaPercentElapsed < 100, escalationLevel == 0)
    then
        $fact.setEscalationLevel(1);
        $fact.setEscalationAction("NOTIFY_OFFICER");
        $fact.setEscalationMessage("SLA approaching breach - 80% time elapsed for complaint " + $fact.getComplaintId());
        update($fact);
end', 100, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(9, 'ESCALATE_SLA_LEVEL2', 'SLA Breach Level 2 - 100% elapsed', 3,
'package com.rbi.cms.rules.escalation;
import com.rbi.cms.common.dto.EscalationFact;

rule "SLA Breach Level 2 - 100% elapsed"
    salience 90
    when
        $fact : EscalationFact(slaPercentElapsed >= 100, escalationLevel < 2)
    then
        $fact.setEscalationLevel(2);
        $fact.setEscalationAction("REASSIGN_SUPERVISOR");
        $fact.setEscalationMessage("SLA breached for complaint " + $fact.getComplaintId() + ". Reassigning to supervisor.");
        update($fact);
end', 90, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(10, 'ESCALATE_SLA_LEVEL3', 'SLA Breach Level 3 - 150% elapsed (Critical)', 3,
'package com.rbi.cms.rules.escalation;
import com.rbi.cms.common.dto.EscalationFact;

rule "SLA Breach Level 3 - 150% elapsed (Critical)"
    salience 80
    when
        $fact : EscalationFact(slaPercentElapsed >= 150, escalationLevel < 3)
    then
        $fact.setEscalationLevel(3);
        $fact.setEscalationAction("ESCALATE_MANAGEMENT");
        $fact.setEscalationMessage("Critical SLA breach for complaint " + $fact.getComplaintId() + ". Escalating to management.");
        update($fact);
end', 80, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP),

(11, 'ESCALATE_HIGH_VALUE', 'High Value Complaint Escalation (> 10 Lakh)', 3,
'package com.rbi.cms.rules.escalation;
import com.rbi.cms.common.dto.EscalationFact;

rule "High Value Complaint Escalation"
    salience 70
    when
        $fact : EscalationFact(amountInvolved > 1000000, escalationLevel < 2)
    then
        $fact.setEscalationLevel(2);
        $fact.setEscalationAction("ASSIGN_SENIOR_OFFICER");
        $fact.setEscalationMessage("High value complaint (> 10L) requires senior officer attention.");
        update($fact);
end', 70, 1, 'ACTIVE', 'admin', CURRENT_TIMESTAMP, 'supervisor', CURRENT_TIMESTAMP);

-- Version History for initial seeded rules
INSERT INTO RULE_VERSION_HISTORY (rule_id, version, drl_content, change_reason, changed_by, changed_at, action) VALUES
(1, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(2, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(3, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(4, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(5, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(6, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(7, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(8, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(9, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(10, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED'),
(11, 1, 'Initial', 'Initial seed', 'admin', CURRENT_TIMESTAMP, 'CREATED');

-- Reset identity sequences past seeded values
ALTER TABLE RULE_CATEGORY ALTER COLUMN id RESTART WITH 100;
ALTER TABLE RULE_DEFINITION ALTER COLUMN id RESTART WITH 100;
ALTER TABLE RULE_VERSION_HISTORY ALTER COLUMN id RESTART WITH 100;
ALTER TABLE RULE_DEPLOYMENT_LOG ALTER COLUMN id RESTART WITH 100;
