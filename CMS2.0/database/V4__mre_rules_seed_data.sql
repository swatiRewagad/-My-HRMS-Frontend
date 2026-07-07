-- ============================================================
-- RBI CMS Phase 4 - MRE Rules Seed Data
-- Version: 4.0.0
-- Description: Initial rule categories, rule definitions,
--              MRE grounds, and window configurations
-- Oracle-compatible INSERT syntax (no MySQL-specific features)
-- ============================================================

-- ============================================================
-- SEED: MRE_RULE_CATEGORY
-- ============================================================

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'ASSIGNMENT', 'Assignment Rules', 'Rules for routing complaints to departments and officers based on category, entity, and jurisdiction', 1, 1, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'ESCALATION', 'Escalation Rules', 'Rules for auto-escalating complaints based on SLA breach, priority, or inactivity', 1, 2, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'PRIORITY', 'Priority Rules', 'Rules for determining complaint priority (Critical/High/Medium/Low) based on complainant profile and amount', 1, 3, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'CATEGORIZATION', 'Categorization Rules', 'Rules for auto-categorizing complaints based on subject keywords and entity type', 1, 4, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'SLA', 'SLA Rules', 'Rules for computing SLA deadlines per category, priority, and entity type', 1, 5, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'NOTIFICATION', 'Notification Rules', 'Rules for triggering SMS, email, and push notifications at complaint lifecycle events', 1, 6, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'MAINTAINABILITY', 'Maintainability Rules', 'Rules for RB-IOS 2026 objective maintainability assessment (Q13/Q16/Q17 grounds)', 1, 7, SYSTIMESTAMP);

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT)
VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'COMPENSATION', 'Compensation Rules', 'Rules for computing compensation bands based on RB-IOS Q22/Q23 caps and precedent', 1, 8, SYSTIMESTAMP);

-- ============================================================
-- SEED: MRE_RULE_DEFINITION
-- ============================================================

-- Assignment Rules
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ASGN-001', 'Route ATM/Debit Card complaints to RBIO', 1,
'rule "Route ATM to RBIO"
  salience 10
  when
    $c : Complaint(categoryCode == "ATM_DEBIT" || categoryCode == "DEBIT_CARD")
  then
    $c.setDepartment("RBIO");
    $c.setAssignedRole("RBIO_OFFICER");
    $c.setJurisdiction(resolveJurisdiction($c.getEntityBranch()));
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ASGN-002', 'Route UPI/IMPS/NEFT complaints to CEPC', 1,
'rule "Route UPI/IMPS/NEFT to CEPC"
  salience 10
  when
    $c : Complaint(categoryCode in ("UPI_MOBILE", "IMPS", "NEFT_RTGS", "NACH"))
  then
    $c.setDepartment("CEPC");
    $c.setAssignedRole("CEPC_DO");
    $c.setJurisdiction(resolveJurisdiction($c.getEntityBranch()));
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ASGN-003', 'Route Credit Card complaints to CEPC', 1,
'rule "Route Credit Card to CEPC"
  salience 10
  when
    $c : Complaint(categoryCode == "CREDIT_CARD")
  then
    $c.setDepartment("CEPC");
    $c.setAssignedRole("CEPC_DO");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ASGN-004', 'Route Loan/Insurance complaints to RBIO', 1,
'rule "Route Loan/Insurance to RBIO"
  salience 10
  when
    $c : Complaint(categoryCode in ("LOAN", "INSURANCE", "DEPOSIT"))
  then
    $c.setDepartment("RBIO");
    $c.setAssignedRole("RBIO_OFFICER");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ASGN-005', 'Default routing to CRPC for uncategorized', 1,
'rule "Default to CRPC"
  salience 1
  when
    $c : Complaint(department == null || department == "")
  then
    $c.setDepartment("CRPC");
    $c.setAssignedRole("CRPC_DEO");
end', 1, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

-- Escalation Rules
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ESC-001', 'Escalate if no action in 7 business days', 2,
'rule "Escalate after 7 days inactivity"
  salience 20
  when
    $c : Complaint(status == "in_progress", businessDaysSinceLastUpdate > 7)
  then
    $c.escalate("AUTO_ESCALATION", "No activity for 7 business days");
    $c.setPriority("HIGH");
end', 20, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ESC-002', 'Auto-escalate high-value complaints (>5L)', 2,
'rule "High value auto-escalation"
  salience 25
  when
    $c : Complaint(amountInvolved > 500000, status == "pending")
  then
    $c.setPriority("HIGH");
    $c.setAssignedRole("RBIO_SUPERVISOR");
    $c.addFlag("HIGH_VALUE_AUTO_ESCALATED");
end', 25, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ESC-003', 'Escalate on SLA breach (75% consumed)', 2,
'rule "SLA breach warning escalation"
  salience 22
  when
    $c : Complaint(slaPercentUsed >= 75, escalationLevel == 0)
  then
    $c.escalate("SLA_WARNING", "SLA 75% consumed - auto escalating");
    $c.setEscalationLevel(1);
end', 22, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

-- Priority Rules
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'PRI-001', 'Senior citizen complaints are HIGH priority', 3,
'rule "Senior citizen high priority"
  salience 15
  when
    $c : Complaint(complainantAge > 60)
  then
    $c.setPriority("HIGH");
    $c.addFlag("SENIOR_CITIZEN");
end', 15, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'PRI-002', 'Fraud-related complaints are CRITICAL priority', 3,
'rule "Fraud critical priority"
  salience 30
  when
    $c : Complaint(categoryCode == "FRAUD" || subjectContains("fraud", "unauthorized", "stolen"))
  then
    $c.setPriority("CRITICAL");
    $c.setEscalationRequired(true);
    $c.addFlag("FRAUD_SUSPECTED");
end', 30, 1, 'DRAFT', SYSTIMESTAMP - 15, 'admin', SYSTIMESTAMP - 15, NULL, NULL);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'PRI-003', 'Disabled/differently-abled complainant HIGH priority', 3,
'rule "Differently-abled high priority"
  salience 15
  when
    $c : Complaint(complainantDisabled == true)
  then
    $c.setPriority("HIGH");
    $c.addFlag("DIFFERENTLY_ABLED");
end', 15, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

-- Categorization Rules
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CAT-001', 'Auto-categorize ATM keywords', 4,
'rule "Categorize ATM complaints"
  salience 5
  when
    $c : Complaint(descriptionMatches(".*ATM.*cash.*not.*dispensed.*|.*ATM.*swallowed.*card.*|.*ATM.*debit.*"))
  then
    $c.setCategoryCode("ATM_DEBIT");
    $c.setProposedCategory("ATM/Debit Card");
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CAT-002', 'Auto-categorize UPI keywords', 4,
'rule "Categorize UPI complaints"
  salience 5
  when
    $c : Complaint(descriptionMatches(".*UPI.*failed.*|.*Google Pay.*|.*PhonePe.*|.*BHIM.*"))
  then
    $c.setCategoryCode("UPI_MOBILE");
    $c.setProposedCategory("UPI/Mobile Banking");
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CAT-003', 'Auto-categorize Loan keywords', 4,
'rule "Categorize Loan complaints"
  salience 5
  when
    $c : Complaint(descriptionMatches(".*loan.*EMI.*|.*interest.*overcharg.*|.*foreclosure.*|.*prepayment.*penalty.*"))
  then
    $c.setCategoryCode("LOAN");
    $c.setProposedCategory("Loan/Advance");
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 28, 'admin', SYSTIMESTAMP - 28, 'supervisor', SYSTIMESTAMP - 27);

-- SLA Rules
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'SLA-001', 'ATM complaints - 7 business day SLA', 5,
'rule "ATM 7-day SLA"
  salience 10
  when
    $c : Complaint(categoryCode == "ATM_DEBIT")
  then
    $c.setSlaDeadlineDays(7);
    $c.setSlaType("BUSINESS_DAYS");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'SLA-002', 'UPI/IMPS complaints - 5 business day SLA', 5,
'rule "UPI/IMPS 5-day SLA"
  salience 10
  when
    $c : Complaint(categoryCode in ("UPI_MOBILE", "IMPS"))
  then
    $c.setSlaDeadlineDays(5);
    $c.setSlaType("BUSINESS_DAYS");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'SLA-003', 'Default SLA - 30 calendar days (RB-IOS)', 5,
'rule "Default 30-day SLA"
  salience 1
  when
    $c : Complaint(slaDeadlineDays == null || slaDeadlineDays == 0)
  then
    $c.setSlaDeadlineDays(30);
    $c.setSlaType("CALENDAR_DAYS");
end', 1, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

-- Notification Rules
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'NOT-001', 'SMS notification on complaint registration', 6,
'rule "Registration SMS"
  salience 5
  when
    $c : Complaint(status == "PENDING", isNew == true, complainantPhone != null)
  then
    notify("SMS", $c.getComplainantPhone(),
      "Dear " + $c.getComplainantName() + ", your complaint " + $c.getComplaintNumber() + " has been registered with RBI. Track at https://cms.rbi.org.in/track/" + $c.getComplaintNumber());
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'NOT-002', 'Email notification on status change', 6,
'rule "Status change email"
  salience 5
  when
    $c : Complaint(statusChanged == true, complainantEmail != null)
  then
    notify("EMAIL", $c.getComplainantEmail(),
      "Complaint " + $c.getComplaintNumber() + " status updated to: " + $c.getStatus());
end', 5, 1, 'INACTIVE', SYSTIMESTAMP - 20, 'admin', SYSTIMESTAMP - 20, NULL, NULL);

-- Maintainability Rules (RB-IOS 2026 Q13/Q16/Q17)
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-001', 'Check entity coverage under RB-IOS 2026 (Q13)', 7,
'rule "Entity coverage check - Q13"
  salience 100
  when
    $c : Complaint(entityCode != null)
    $e : EntityRegistry(code == $c.entityCode, coveredUnderScheme == false)
  then
    $c.addMreGround("ENTITY_NOT_COVERED", "FAIL", "Q13",
      "Entity " + $c.getEntityCode() + " is not covered under RB-IOS 2026 Scheme");
    $c.setObjectivelyNonMaintainable(true);
end', 100, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-002', 'Check prior RE complaint requirement (Q16)', 7,
'rule "Prior RE complaint check - Q16"
  salience 95
  when
    $c : Complaint(priorReComplaint == false || priorReComplaint == null)
  then
    $c.addMreGround("NO_PRIOR_RE_COMPLAINT", "FAIL", "Q16",
      "Complainant has not first approached the Regulated Entity as required by Q16");
    $c.setObjectivelyNonMaintainable(true);
end', 95, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-003', 'Check RE window elapsed (Q17 - 30 days)', 7,
'rule "RE window not elapsed - Q17"
  salience 90
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   reRepliedAndDissatisfied == false,
                   daysSinceReComplaint < applicableWindowDays)
  then
    $c.addMreGround("FILED_BEFORE_WINDOW", "FAIL", "Q17",
      "Filed " + $c.getDaysSinceReComplaint() + " days after RE complaint but " +
      $c.getApplicableWindowDays() + "-day window has not elapsed");
    $c.setObjectivelyNonMaintainable(true);
end', 90, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-004', 'Check filing deadline (365 days per Q16)', 7,
'rule "Filing deadline exceeded - Q16"
  salience 85
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   daysSinceWindowExpiry > 365)
  then
    $c.addMreGround("FILED_BEYOND_DEADLINE", "FAIL", "Q16/Q17",
      "Filed " + $c.getDaysSinceWindowExpiry() + " days after deadline (limit: 365 days)");
    $c.setObjectivelyNonMaintainable(true);
end', 85, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-005', 'Check Limitation Act period (3 years)', 7,
'rule "Limitation period exceeded - Q16"
  salience 80
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   yearsSinceReComplaint > 3)
  then
    $c.addMreGround("RE_COMPLAINT_BEYOND_LIMITATION", "FAIL", "Q16",
      "RE complaint date exceeds 3-year Limitation Act 1963 period");
    $c.setObjectivelyNonMaintainable(true);
end', 80, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-006', 'Check duplicate/pending grievance (Q16)', 7,
'rule "Duplicate grievance check - Q16"
  salience 75
  when
    $c : Complaint(sameGrievancePendingOrDecided == true)
  then
    $c.addMreGround("SAME_GRIEVANCE_PENDING", "FAIL", "Q16",
      "Same grievance already pending or decided by the Ombudsman or a court/tribunal");
    $c.setObjectivelyNonMaintainable(true);
end', 75, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

-- Compensation Rules (RB-IOS Q22/Q23)
INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-001', 'Consequential loss cap - Rs 30 lakh (Q22)', 8,
'rule "Consequential loss cap"
  salience 10
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE")
    $award : AwardCalculation(consequentialLoss > 3000000)
  then
    $award.setConsequentialLoss(3000000);
    $award.addNote("Capped at Rs 30,00,000 per RB-IOS Q22");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-002', 'Time/harassment compensation cap - Rs 3 lakh (Q23)', 8,
'rule "Time and harassment cap"
  salience 10
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE")
    $award : AwardCalculation(timeHarassmentAmount > 300000)
  then
    $award.setTimeHarassmentAmount(300000);
    $award.addNote("Capped at Rs 3,00,000 per RB-IOS Q23");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

-- ============================================================
-- SEED: MRE_GROUND_CONFIG
-- ============================================================

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('ENTITY_NOT_COVERED', 'Q13', 'Entity not covered under RB-IOS 2026 Scheme', 1, 1,
  'Entity is not a Regulated Entity covered under the Integrated Ombudsman Scheme',
  'Entity is covered under the RB-IOS 2026 Scheme');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('NO_PRIOR_RE_COMPLAINT', 'Q16', 'No prior complaint to the Regulated Entity', 1, 2,
  'Complainant has not first complained to the Regulated Entity as required',
  'Complainant has lodged prior complaint with the RE');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_BEFORE_WINDOW', 'Q17', 'Filed before the RE response window has elapsed', 1, 3,
  'Complaint filed before the mandatory waiting period has elapsed',
  'Filed after the waiting period — window requirement satisfied');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_BEYOND_DEADLINE', 'Q16/Q17', 'Filed beyond the maximum filing deadline', 1, 4,
  'Complaint filed beyond the maximum permissible deadline after window expiry',
  'Filed within the deadline period');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('RE_COMPLAINT_BEYOND_LIMITATION', 'Q16', 'RE complaint made beyond Limitation Act 1963 period', 1, 5,
  'The complaint to the Regulated Entity was made beyond the Limitation Act 1963 period (3 years)',
  'RE complaint is within the Limitation Act period');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('SAME_GRIEVANCE_PENDING', 'Q16', 'Same grievance already pending or decided', 1, 6,
  'Same grievance is already pending or has been decided by the Ombudsman or a court/tribunal',
  'No duplicate grievance found pending or decided');

-- ============================================================
-- SEED: MRE_WINDOW_CONFIG
-- ============================================================

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEFAULT', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('UPI_MOBILE', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('IMPS', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('NACH', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('CREDIT_CARD', 'ALL', 60, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEBIT_CARD', 'ALL', 60, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('ATM_DEBIT', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('LOAN', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('INSURANCE', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEPOSIT', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('NEFT_RTGS', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-01-01', 'admin');

-- ============================================================
-- SEED: MRE_RULE_HISTORY (initial creation records)
-- ============================================================

INSERT INTO MRE_RULE_HISTORY (ID, RULE_ID, VERSION, DRL_CONTENT, CHANGE_REASON, ACTION, CHANGED_BY, CHANGED_AT)
SELECT MRE_RULE_HISTORY_SEQ.NEXTVAL, r.ID, 1, r.DRL_CONTENT, 'Initial seed creation', 'CREATED', r.CREATED_BY, r.CREATED_AT
FROM MRE_RULE_DEFINITION r;

COMMIT;
