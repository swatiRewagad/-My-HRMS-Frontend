-- ============================================================
-- RBI CMS 2.0 — MRE Rules Engine + India Post Pincode Seed
-- Database: Oracle 21c XE / Enterprise
-- Scheme: RB-IOS 2026 (effective July 1, 2026)
-- ============================================================

-- ============================================================
-- 1. MRE_RULE_CATEGORY
-- ============================================================

INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'ASSIGNMENT', 'Assignment Rules', 'Rules for routing complaints to departments and officers based on category, entity, and jurisdiction', 1, 1, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'ESCALATION', 'Escalation Rules', 'Rules for auto-escalating complaints based on SLA breach, priority, or inactivity', 1, 2, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'PRIORITY', 'Priority Rules', 'Rules for determining complaint priority (Critical/High/Medium/Low) based on complainant profile and amount', 1, 3, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'CATEGORIZATION', 'Categorization Rules', 'Rules for auto-categorizing complaints based on subject keywords and entity type', 1, 4, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'SLA', 'SLA Rules', 'Rules for computing SLA deadlines per category, priority, and entity type', 1, 5, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'NOTIFICATION', 'Notification Rules', 'Rules for triggering SMS, email, and push notifications at complaint lifecycle events', 1, 6, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'MAINTAINABILITY', 'Maintainability Rules', 'Rules for RB-IOS 2026 objective maintainability assessment (Clause 10 grounds)', 1, 7, SYSTIMESTAMP);
INSERT INTO MRE_RULE_CATEGORY (ID, CATEGORY_CODE, CATEGORY_NAME, DESCRIPTION, IS_ACTIVE, DISPLAY_ORDER, CREATED_AT) VALUES (MRE_RULE_CATEGORY_SEQ.NEXTVAL, 'COMPENSATION', 'Compensation Rules', 'Rules for computing compensation bands based on RB-IOS Clause 18/19 caps and precedent', 1, 8, SYSTIMESTAMP);

-- ============================================================
-- 2. MRE_RULE_DEFINITION — Assignment Rules
-- ============================================================

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
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ASGN-004', 'Route Loan/Insurance/Deposit complaints to RBIO', 1,
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

-- ============================================================
-- 3. MRE_RULE_DEFINITION — Escalation Rules
-- ============================================================

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

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'ESC-004', 'Escalate to Deputy Ombudsman on SLA 100% breach', 2,
'rule "Deputy Ombudsman escalation"
  salience 30
  when
    $c : Complaint(slaPercentUsed >= 100, escalationLevel < 2)
  then
    $c.escalate("DEPUTY_OMBUDSMAN", "SLA fully breached - escalating to Deputy Ombudsman");
    $c.setEscalationLevel(2);
    $c.setAssignedRole("DEPUTY_OMBUDSMAN");
end', 30, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

-- ============================================================
-- 4. MRE_RULE_DEFINITION — Priority Rules
-- ============================================================

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
end', 30, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'PRI-003', 'Differently-abled complainant HIGH priority', 3,
'rule "Differently-abled high priority"
  salience 15
  when
    $c : Complaint(complainantDisabled == true)
  then
    $c.setPriority("HIGH");
    $c.addFlag("DIFFERENTLY_ABLED");
end', 15, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'PRI-004', 'Women complainant with harassment - HIGH priority', 3,
'rule "Women harassment high priority"
  salience 15
  when
    $c : Complaint(complainantGender == "FEMALE", subjectContains("harass", "threat", "abuse"))
  then
    $c.setPriority("HIGH");
    $c.addFlag("WOMEN_HARASSMENT");
end', 15, 1, 'ACTIVE', SYSTIMESTAMP - 20, 'admin', SYSTIMESTAMP - 20, 'supervisor', SYSTIMESTAMP - 19);

-- ============================================================
-- 5. MRE_RULE_DEFINITION — Categorization Rules
-- ============================================================

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

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CAT-004', 'Auto-categorize Credit Card keywords', 4,
'rule "Categorize Credit Card complaints"
  salience 5
  when
    $c : Complaint(descriptionMatches(".*credit card.*charge.*|.*annual fee.*|.*credit limit.*|.*EMI.*conversion.*"))
  then
    $c.setCategoryCode("CREDIT_CARD");
    $c.setProposedCategory("Credit Card");
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 28, 'admin', SYSTIMESTAMP - 28, 'supervisor', SYSTIMESTAMP - 27);

-- ============================================================
-- 6. MRE_RULE_DEFINITION — SLA Rules
-- ============================================================

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
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'SLA-003', 'Default SLA - 30 calendar days per RB-IOS', 5,
'rule "Default 30-day SLA"
  salience 1
  when
    $c : Complaint(slaDeadlineDays == null || slaDeadlineDays == 0)
  then
    $c.setSlaDeadlineDays(30);
    $c.setSlaType("CALENDAR_DAYS");
end', 1, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

-- ============================================================
-- 7. MRE_RULE_DEFINITION — Notification Rules
-- ============================================================

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'NOT-001', 'SMS notification on complaint registration', 6,
'rule "Registration SMS"
  salience 5
  when
    $c : Complaint(status == "PENDING", isNew == true, complainantPhone != null)
  then
    notify("SMS", $c.getComplainantPhone(),
      "Your complaint " + $c.getComplaintNumber() + " registered with RBI Ombudsman.");
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
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'NOT-003', 'Notify RE on complaint forwarding', 6,
'rule "RE forwarding notification"
  salience 5
  when
    $c : Complaint(forwardedToRE == true, reEmail != null)
  then
    notify("EMAIL", $c.getReEmail(),
      "Complaint " + $c.getComplaintNumber() + " forwarded. Respond within " + $c.getWindowDays() + " days.");
end', 5, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

-- ============================================================
-- 8. MRE_RULE_DEFINITION — Maintainability Rules (RB-IOS 2026 Clause 10)
-- ============================================================

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-001', 'Check entity coverage under RB-IOS 2026 (Clause 10(1)(a))', 7,
'rule "Entity coverage check - Clause 10(1)(a)"
  salience 100
  when
    $c : Complaint(entityCode != null)
    $e : EntityRegistry(code == $c.entityCode, coveredUnderScheme == false)
  then
    $c.addMreGround("ENTITY_NOT_COVERED", "FAIL", "Cl.10(1)(a)",
      "Entity " + $c.getEntityCode() + " is not a Regulated Entity under RB-IOS 2026");
    $c.setObjectivelyNonMaintainable(true);
end', 100, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-002', 'Check prior RE complaint requirement (Clause 10(1)(b))', 7,
'rule "Prior RE complaint check - Clause 10(1)(b)"
  salience 95
  when
    $c : Complaint(priorReComplaint == false || priorReComplaint == null)
  then
    $c.addMreGround("NO_PRIOR_RE_COMPLAINT", "FAIL", "Cl.10(1)(b)",
      "Complainant has not first approached the Regulated Entity as required");
    $c.setObjectivelyNonMaintainable(true);
end', 95, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-003', 'Check RE window not elapsed (Clause 10(1)(c) - 30 days)', 7,
'rule "RE window not elapsed - Clause 10(1)(c)"
  salience 90
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   reRepliedAndDissatisfied == false,
                   daysSinceReComplaint < applicableWindowDays)
  then
    $c.addMreGround("FILED_BEFORE_WINDOW", "FAIL", "Cl.10(1)(c)",
      "Filed " + $c.getDaysSinceReComplaint() + " days after RE complaint but " +
      $c.getApplicableWindowDays() + "-day window has not elapsed");
    $c.setObjectivelyNonMaintainable(true);
end', 90, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-004', 'Check filing deadline exceeded (Clause 10(1)(d) - 365 days)', 7,
'rule "Filing deadline exceeded - Clause 10(1)(d)"
  salience 85
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   daysSinceWindowExpiry > filingDeadlineDays)
  then
    $c.addMreGround("FILED_BEYOND_DEADLINE", "FAIL", "Cl.10(1)(d)",
      "Filed " + $c.getDaysSinceWindowExpiry() + " days after window expiry (limit: " + $c.getFilingDeadlineDays() + " days)");
    $c.setObjectivelyNonMaintainable(true);
end', 85, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-005', 'Check Limitation Act 1963 period (Clause 10(1)(e) - 3 years)', 7,
'rule "Limitation period exceeded - Clause 10(1)(e)"
  salience 80
  when
    $c : Complaint(priorReComplaint == true, reComplaintDate != null,
                   yearsSinceReComplaint > 3)
  then
    $c.addMreGround("RE_COMPLAINT_BEYOND_LIMITATION", "FAIL", "Cl.10(1)(e)",
      "RE complaint date exceeds 3-year Limitation Act 1963 period");
    $c.setObjectivelyNonMaintainable(true);
end', 80, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-006', 'Check duplicate/pending grievance (Clause 10(1)(f))', 7,
'rule "Duplicate grievance check - Clause 10(1)(f)"
  salience 75
  when
    $c : Complaint(sameGrievancePendingOrDecided == true)
  then
    $c.addMreGround("SAME_GRIEVANCE_PENDING", "FAIL", "Cl.10(1)(f)",
      "Same grievance already pending or decided by the Ombudsman or court/tribunal");
    $c.setObjectivelyNonMaintainable(true);
end', 75, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-007', 'Check excluded subject matter (Clause 10(2))', 7,
'rule "Excluded subject matter - Clause 10(2)"
  salience 70
  when
    $c : Complaint(subjectMatterExcluded == true)
  then
    $c.addMreGround("EXCLUDED_SUBJECT", "FAIL", "Cl.10(2)",
      "Subject matter excluded under Clause 10(2): " + $c.getExclusionReason());
    $c.setObjectivelyNonMaintainable(true);
end', 70, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-008', 'Check court/tribunal proceedings (Clause 10(1)(g))', 7,
'rule "Court proceedings check - Clause 10(1)(g)"
  salience 65
  when
    $c : Complaint(courtProceedingsPending == true)
  then
    $c.addMreGround("COURT_PROCEEDINGS_PENDING", "FAIL", "Cl.10(1)(g)",
      "Proceedings pending before a court, tribunal, or Consumer Forum on same subject");
    $c.setObjectivelyNonMaintainable(true);
end', 65, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-009', 'Check frivolous/vexatious complaint (Clause 10(1)(h))', 7,
'rule "Frivolous complaint check - Clause 10(1)(h)"
  salience 60
  when
    $c : Complaint(flaggedFrivolous == true)
  then
    $c.addMreGround("FRIVOLOUS_VEXATIOUS", "FAIL", "Cl.10(1)(h)",
      "Complaint is frivolous, vexatious, or made with malafide intent");
    $c.setObjectivelyNonMaintainable(true);
end', 60, 1, 'DRAFT', SYSTIMESTAMP - 15, 'admin', SYSTIMESTAMP - 15, NULL, NULL);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'MRE-010', 'All maintainability grounds pass - MAINTAINABLE', 7,
'rule "All grounds pass - MAINTAINABLE"
  salience 1
  when
    $c : Complaint(objectivelyNonMaintainable == false)
  then
    $c.setMaintainabilityDetermination("MAINTAINABLE");
    $c.setTriageSignal("GREEN");
end', 1, 1, 'ACTIVE', SYSTIMESTAMP - 30, 'admin', SYSTIMESTAMP - 30, 'supervisor', SYSTIMESTAMP - 29);

-- ============================================================
-- 9. MRE_RULE_DEFINITION — Compensation Rules (RB-IOS Clause 18/19)
-- ============================================================

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-001', 'Consequential loss cap - Rs 30 lakh (Clause 18)', 8,
'rule "Consequential loss cap"
  salience 10
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE")
    $award : AwardCalculation(consequentialLoss > 3000000)
  then
    $award.setConsequentialLoss(3000000);
    $award.addNote("Capped at Rs 30,00,000 per RB-IOS Clause 18");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-002', 'Time/harassment compensation cap - Rs 3 lakh (Clause 19)', 8,
'rule "Time and harassment cap"
  salience 10
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE")
    $award : AwardCalculation(timeHarassmentAmount > 300000)
  then
    $award.setTimeHarassmentAmount(300000);
    $award.addNote("Capped at Rs 3,00,000 per RB-IOS Clause 19");
end', 10, 1, 'ACTIVE', SYSTIMESTAMP - 25, 'admin', SYSTIMESTAMP - 25, 'supervisor', SYSTIMESTAMP - 24);

INSERT INTO MRE_RULE_DEFINITION (ID, RULE_CODE, RULE_NAME, CATEGORY_ID, DRL_CONTENT, SALIENCE, VERSION, STATUS, EFFECTIVE_FROM, CREATED_BY, CREATED_AT, APPROVED_BY, APPROVED_AT)
VALUES (MRE_RULE_DEFINITION_SEQ.NEXTVAL, 'CMP-003', 'Interest compensation for delayed refunds', 8,
'rule "Interest on delayed refund"
  salience 8
  when
    $c : Complaint(maintainabilityDetermination == "MAINTAINABLE", categoryCode in ("ATM_DEBIT", "UPI_MOBILE", "IMPS"))
    $award : AwardCalculation(delayDays > 0)
  then
    $award.setInterestRate(9.0);
    $award.computeInterest($award.getPrincipal(), $award.getDelayDays());
    $award.addNote("Interest at 9% p.a. for " + $award.getDelayDays() + " days delay");
end', 8, 1, 'ACTIVE', SYSTIMESTAMP - 20, 'admin', SYSTIMESTAMP - 20, 'supervisor', SYSTIMESTAMP - 19);

-- ============================================================
-- 10. MRE_GROUND_CONFIG
-- ============================================================

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('ENTITY_NOT_COVERED', 'Cl.10(1)(a)', 'Entity not covered under RB-IOS 2026 Scheme', 1, 1,
  'Entity is not a Regulated Entity covered under the Integrated Ombudsman Scheme',
  'Entity is covered under the RB-IOS 2026 Scheme');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('NO_PRIOR_RE_COMPLAINT', 'Cl.10(1)(b)', 'No prior complaint to the Regulated Entity', 1, 2,
  'Complainant has not first complained to the Regulated Entity as required',
  'Complainant has lodged prior complaint with the RE');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_BEFORE_WINDOW', 'Cl.10(1)(c)', 'Filed before the RE response window has elapsed', 1, 3,
  'Complaint filed before the mandatory waiting period (30/60 days) has elapsed',
  'Filed after the waiting period - window requirement satisfied');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('FILED_BEYOND_DEADLINE', 'Cl.10(1)(d)', 'Filed beyond the maximum filing deadline', 1, 4,
  'Complaint filed beyond the maximum permissible deadline (365 days) after window expiry',
  'Filed within the deadline period');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('RE_COMPLAINT_BEYOND_LIMITATION', 'Cl.10(1)(e)', 'RE complaint made beyond Limitation Act 1963 period', 1, 5,
  'The complaint to the RE was made beyond the 3-year Limitation Act 1963 period',
  'RE complaint is within the Limitation Act period');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('SAME_GRIEVANCE_PENDING', 'Cl.10(1)(f)', 'Same grievance already pending or decided', 1, 6,
  'Same grievance is already pending or has been decided by the Ombudsman or court/tribunal',
  'No duplicate grievance found pending or decided');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('EXCLUDED_SUBJECT', 'Cl.10(2)', 'Subject matter excluded from scheme coverage', 1, 7,
  'Subject matter is explicitly excluded under Clause 10(2) of RB-IOS 2026',
  'Subject matter is within scheme coverage');

INSERT INTO MRE_GROUND_CONFIG (GROUND_CODE, CLAUSE_REF, DESCRIPTION, IS_ACTIVE, EVALUATION_ORDER, FAIL_MESSAGE, PASS_MESSAGE)
VALUES ('COURT_PROCEEDINGS_PENDING', 'Cl.10(1)(g)', 'Court/tribunal proceedings pending on same subject', 1, 8,
  'Proceedings on the same subject are pending before a court, tribunal, or Consumer Forum',
  'No court/tribunal proceedings pending');

-- ============================================================
-- 11. MRE_WINDOW_CONFIG
-- ============================================================

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEFAULT', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('UPI_MOBILE', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('IMPS', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('NACH', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('CREDIT_CARD', 'ALL', 60, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEBIT_CARD', 'ALL', 60, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('ATM_DEBIT', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('LOAN', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('INSURANCE', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('DEPOSIT', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('NEFT_RTGS', 'ALL', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('CREDIT_CARD', 'CARD_NETWORK', 60, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

INSERT INTO MRE_WINDOW_CONFIG (CATEGORY_CODE, ENTITY_TYPE, WINDOW_DAYS, FILING_DEADLINE, WINDOW_BASIS, IS_ACTIVE, EFFECTIVE_FROM, CREATED_BY)
VALUES ('UPI_MOBILE', 'PAYMENT_SYSTEM_OPERATOR', 30, 365, 'CALENDAR', 1, DATE '2026-07-01', 'admin');

-- ============================================================
-- 12. MRE_RULE_HISTORY (initial creation audit)
-- ============================================================

INSERT INTO MRE_RULE_HISTORY (ID, RULE_ID, VERSION, DRL_CONTENT, CHANGE_REASON, ACTION, CHANGED_BY, CHANGED_AT)
SELECT MRE_RULE_HISTORY_SEQ.NEXTVAL, r.ID, 1, r.DRL_CONTENT, 'Initial seed creation for RB-IOS 2026', 'CREATED', r.CREATED_BY, r.CREATED_AT
FROM MRE_RULE_DEFINITION r;

-- ============================================================
-- 13. INDIA POST PINCODE MASTER (Representative sample - all states)
-- ============================================================

-- Maharashtra
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '400001', 'Mumbai GPO', 'HO', 'Delivery', 'Mumbai', 'Mumbai', 'Maharashtra', 'Mumbai', 'Mumbai', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '400050', 'Bandra West', 'SO', 'Delivery', 'Mumbai', 'Mumbai', 'Maharashtra', 'Andheri', 'Mumbai Suburban', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '400058', 'Andheri West', 'SO', 'Delivery', 'Mumbai', 'Mumbai', 'Maharashtra', 'Andheri', 'Mumbai Suburban', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '400069', 'Andheri East', 'SO', 'Delivery', 'Mumbai', 'Mumbai', 'Maharashtra', 'Andheri', 'Mumbai Suburban', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '400076', 'Powai', 'SO', 'Delivery', 'Mumbai', 'Mumbai', 'Maharashtra', 'Andheri', 'Mumbai Suburban', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '411001', 'Pune GPO', 'HO', 'Delivery', 'Pune', 'Pune', 'Maharashtra', 'Pune City', 'Pune', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '411016', 'Model Colony', 'SO', 'Delivery', 'Pune', 'Pune', 'Maharashtra', 'Pune City', 'Pune', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '411038', 'Hinjewadi', 'SO', 'Delivery', 'Pune', 'Pune', 'Maharashtra', 'Mulshi', 'Pune', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '440001', 'Nagpur GPO', 'HO', 'Delivery', 'Nagpur', 'Nagpur', 'Maharashtra', 'Nagpur Urban', 'Nagpur', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '422001', 'Nashik', 'HO', 'Delivery', 'Nashik', 'Nashik', 'Maharashtra', 'Nashik', 'Nashik', 'Maharashtra');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '400601', 'Thane', 'HO', 'Delivery', 'Thane', 'Mumbai', 'Maharashtra', 'Thane', 'Thane', 'Maharashtra');

-- Delhi / NCR
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '110001', 'New Delhi GPO', 'HO', 'Delivery', 'New Delhi', 'Delhi', 'Delhi', 'New Delhi', 'New Delhi', 'Delhi');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '110017', 'Saket', 'SO', 'Delivery', 'South Delhi', 'Delhi', 'Delhi', 'South Delhi', 'South Delhi', 'Delhi');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '110025', 'Lajpat Nagar', 'SO', 'Delivery', 'South Delhi', 'Delhi', 'Delhi', 'South Delhi', 'South East Delhi', 'Delhi');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '110034', 'Pitampura', 'SO', 'Delivery', 'North West Delhi', 'Delhi', 'Delhi', 'North West', 'North West Delhi', 'Delhi');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '110048', 'Defence Colony', 'SO', 'Delivery', 'South Delhi', 'Delhi', 'Delhi', 'South Delhi', 'South Delhi', 'Delhi');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '201301', 'Noida', 'HO', 'Delivery', 'Ghaziabad', 'Lucknow', 'Uttar Pradesh', 'Dadri', 'Gautam Buddha Nagar', 'Uttar Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '122001', 'Gurgaon', 'HO', 'Delivery', 'Gurgaon', 'Ambala', 'Haryana', 'Gurgaon', 'Gurgaon', 'Haryana');

-- Karnataka
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '560001', 'Bangalore GPO', 'HO', 'Delivery', 'Bangalore', 'Bangalore', 'Karnataka', 'Bangalore North', 'Bangalore Urban', 'Karnataka');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '560066', 'Whitefield', 'SO', 'Delivery', 'Bangalore', 'Bangalore', 'Karnataka', 'Bangalore East', 'Bangalore Urban', 'Karnataka');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '560100', 'Electronic City', 'SO', 'Delivery', 'Bangalore', 'Bangalore', 'Karnataka', 'Bangalore South', 'Bangalore Urban', 'Karnataka');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '570001', 'Mysore', 'HO', 'Delivery', 'Mysore', 'Mysore', 'Karnataka', 'Mysore', 'Mysuru', 'Karnataka');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '580001', 'Hubli', 'HO', 'Delivery', 'Hubli', 'Hubli', 'Karnataka', 'Hubli', 'Dharwad', 'Karnataka');

-- Tamil Nadu
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '600001', 'Chennai GPO', 'HO', 'Delivery', 'Chennai', 'Chennai', 'Tamil Nadu', 'Chennai', 'Chennai', 'Tamil Nadu');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '600040', 'Anna Nagar', 'SO', 'Delivery', 'Chennai', 'Chennai', 'Tamil Nadu', 'Ambattur', 'Chennai', 'Tamil Nadu');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '600119', 'Sholinganallur', 'SO', 'Delivery', 'Chennai', 'Chennai', 'Tamil Nadu', 'Tambaram', 'Kancheepuram', 'Tamil Nadu');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '641001', 'Coimbatore', 'HO', 'Delivery', 'Coimbatore', 'Coimbatore', 'Tamil Nadu', 'Coimbatore North', 'Coimbatore', 'Tamil Nadu');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '625001', 'Madurai', 'HO', 'Delivery', 'Madurai', 'Madurai', 'Tamil Nadu', 'Madurai North', 'Madurai', 'Tamil Nadu');

-- Telangana
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '500001', 'Hyderabad GPO', 'HO', 'Delivery', 'Hyderabad', 'Hyderabad', 'Telangana', 'Hyderabad', 'Hyderabad', 'Telangana');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '500081', 'Madhapur', 'SO', 'Delivery', 'Hyderabad', 'Hyderabad', 'Telangana', 'Serilingampally', 'Rangareddy', 'Telangana');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '500032', 'Jubilee Hills', 'SO', 'Delivery', 'Hyderabad', 'Hyderabad', 'Telangana', 'Hyderabad', 'Hyderabad', 'Telangana');

-- West Bengal
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '700001', 'Kolkata GPO', 'HO', 'Delivery', 'Kolkata', 'Kolkata', 'West Bengal', 'Kolkata', 'Kolkata', 'West Bengal');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '700091', 'Salt Lake City', 'SO', 'Delivery', 'Kolkata', 'Kolkata', 'West Bengal', 'Bidhannagar', 'North 24 Parganas', 'West Bengal');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '700156', 'New Town', 'SO', 'Delivery', 'Kolkata', 'Kolkata', 'West Bengal', 'Bidhannagar', 'North 24 Parganas', 'West Bengal');

-- Gujarat
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '380001', 'Ahmedabad GPO', 'HO', 'Delivery', 'Ahmedabad', 'Ahmedabad', 'Gujarat', 'Ahmedabad City', 'Ahmedabad', 'Gujarat');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '380015', 'Vastrapur', 'SO', 'Delivery', 'Ahmedabad', 'Ahmedabad', 'Gujarat', 'Ahmedabad City', 'Ahmedabad', 'Gujarat');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '390001', 'Vadodara', 'HO', 'Delivery', 'Vadodara', 'Vadodara', 'Gujarat', 'Vadodara City', 'Vadodara', 'Gujarat');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '395001', 'Surat', 'HO', 'Delivery', 'Surat', 'Surat', 'Gujarat', 'Surat City', 'Surat', 'Gujarat');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '360001', 'Rajkot', 'HO', 'Delivery', 'Rajkot', 'Rajkot', 'Gujarat', 'Rajkot', 'Rajkot', 'Gujarat');

-- Uttar Pradesh
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '226001', 'Lucknow GPO', 'HO', 'Delivery', 'Lucknow', 'Lucknow', 'Uttar Pradesh', 'Lucknow', 'Lucknow', 'Uttar Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '208001', 'Kanpur', 'HO', 'Delivery', 'Kanpur', 'Lucknow', 'Uttar Pradesh', 'Kanpur', 'Kanpur Nagar', 'Uttar Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '221001', 'Varanasi', 'HO', 'Delivery', 'Varanasi', 'Varanasi', 'Uttar Pradesh', 'Varanasi', 'Varanasi', 'Uttar Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '211001', 'Allahabad', 'HO', 'Delivery', 'Allahabad', 'Allahabad', 'Uttar Pradesh', 'Allahabad', 'Prayagraj', 'Uttar Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '282001', 'Agra', 'HO', 'Delivery', 'Agra', 'Agra', 'Uttar Pradesh', 'Agra', 'Agra', 'Uttar Pradesh');

-- Rajasthan
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '302001', 'Jaipur GPO', 'HO', 'Delivery', 'Jaipur', 'Jaipur', 'Rajasthan', 'Jaipur', 'Jaipur', 'Rajasthan');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '313001', 'Udaipur', 'HO', 'Delivery', 'Udaipur', 'Jaipur', 'Rajasthan', 'Girwa', 'Udaipur', 'Rajasthan');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '342001', 'Jodhpur', 'HO', 'Delivery', 'Jodhpur', 'Jodhpur', 'Rajasthan', 'Jodhpur', 'Jodhpur', 'Rajasthan');

-- Kerala
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '682001', 'Kochi', 'HO', 'Delivery', 'Kochi', 'Kochi', 'Kerala', 'Kochi', 'Ernakulam', 'Kerala');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '695001', 'Thiruvananthapuram', 'HO', 'Delivery', 'Thiruvananthapuram', 'Thiruvananthapuram', 'Kerala', 'Thiruvananthapuram', 'Thiruvananthapuram', 'Kerala');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '673001', 'Kozhikode', 'HO', 'Delivery', 'Kozhikode', 'Kozhikode', 'Kerala', 'Kozhikode', 'Kozhikode', 'Kerala');

-- Punjab
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '160001', 'Chandigarh', 'HO', 'Delivery', 'Chandigarh', 'Chandigarh', 'Punjab', 'Chandigarh', 'Chandigarh', 'Chandigarh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '141001', 'Ludhiana', 'HO', 'Delivery', 'Ludhiana', 'Ludhiana', 'Punjab', 'Ludhiana', 'Ludhiana', 'Punjab');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '143001', 'Amritsar', 'HO', 'Delivery', 'Amritsar', 'Amritsar', 'Punjab', 'Amritsar', 'Amritsar', 'Punjab');

-- Madhya Pradesh
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '462001', 'Bhopal', 'HO', 'Delivery', 'Bhopal', 'Bhopal', 'Madhya Pradesh', 'Bhopal', 'Bhopal', 'Madhya Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '452001', 'Indore', 'HO', 'Delivery', 'Indore', 'Indore', 'Madhya Pradesh', 'Indore', 'Indore', 'Madhya Pradesh');

-- Bihar
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '800001', 'Patna GPO', 'HO', 'Delivery', 'Patna', 'Patna', 'Bihar', 'Patna City', 'Patna', 'Bihar');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '842001', 'Muzaffarpur', 'HO', 'Delivery', 'Muzaffarpur', 'Patna', 'Bihar', 'Muzaffarpur', 'Muzaffarpur', 'Bihar');

-- Odisha
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '751001', 'Bhubaneswar', 'HO', 'Delivery', 'Bhubaneswar', 'Bhubaneswar', 'Odisha', 'Bhubaneswar', 'Khordha', 'Odisha');

-- Assam
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '781001', 'Guwahati', 'HO', 'Delivery', 'Guwahati', 'Guwahati', 'Assam', 'Kamrup Metro', 'Kamrup Metropolitan', 'Assam');

-- Jharkhand
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '834001', 'Ranchi', 'HO', 'Delivery', 'Ranchi', 'Ranchi', 'Jharkhand', 'Ranchi', 'Ranchi', 'Jharkhand');

-- Chhattisgarh
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '492001', 'Raipur', 'HO', 'Delivery', 'Raipur', 'Raipur', 'Chhattisgarh', 'Raipur', 'Raipur', 'Chhattisgarh');

-- Uttarakhand
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '248001', 'Dehradun', 'HO', 'Delivery', 'Dehradun', 'Dehradun', 'Uttarakhand', 'Dehradun', 'Dehradun', 'Uttarakhand');

-- Himachal Pradesh
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '171001', 'Shimla', 'HO', 'Delivery', 'Shimla', 'Shimla', 'Himachal Pradesh', 'Shimla', 'Shimla', 'Himachal Pradesh');

-- Jammu & Kashmir
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '180001', 'Jammu', 'HO', 'Delivery', 'Jammu', 'Jammu', 'Jammu & Kashmir', 'Jammu', 'Jammu', 'Jammu & Kashmir');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '190001', 'Srinagar', 'HO', 'Delivery', 'Srinagar', 'Srinagar', 'Jammu & Kashmir', 'Srinagar', 'Srinagar', 'Jammu & Kashmir');

-- Goa
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '403001', 'Panaji', 'HO', 'Delivery', 'Panaji', 'Panaji', 'Goa', 'Tiswadi', 'North Goa', 'Goa');

-- Andhra Pradesh
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '520001', 'Vijayawada', 'HO', 'Delivery', 'Vijayawada', 'Vijayawada', 'Andhra Pradesh', 'Vijayawada', 'Krishna', 'Andhra Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '530001', 'Visakhapatnam', 'HO', 'Delivery', 'Visakhapatnam', 'Visakhapatnam', 'Andhra Pradesh', 'Visakhapatnam', 'Visakhapatnam', 'Andhra Pradesh');

-- Haryana (additional)
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '121001', 'Faridabad', 'HO', 'Delivery', 'Faridabad', 'Ambala', 'Haryana', 'Faridabad', 'Faridabad', 'Haryana');

-- North East
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '795001', 'Imphal', 'HO', 'Delivery', 'Imphal', 'Guwahati', 'Manipur', 'Imphal West', 'Imphal West', 'Manipur');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '793001', 'Shillong', 'HO', 'Delivery', 'Shillong', 'Guwahati', 'Meghalaya', 'East Khasi Hills', 'East Khasi Hills', 'Meghalaya');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '799001', 'Agartala', 'HO', 'Delivery', 'Agartala', 'Guwahati', 'Tripura', 'Agartala', 'West Tripura', 'Tripura');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '797001', 'Kohima', 'HO', 'Delivery', 'Kohima', 'Guwahati', 'Nagaland', 'Kohima', 'Kohima', 'Nagaland');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '791001', 'Itanagar', 'HO', 'Delivery', 'Itanagar', 'Guwahati', 'Arunachal Pradesh', 'Naharlagun', 'Papum Pare', 'Arunachal Pradesh');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '737101', 'Gangtok', 'HO', 'Delivery', 'Gangtok', 'Guwahati', 'Sikkim', 'Gangtok', 'East Sikkim', 'Sikkim');
INSERT INTO PINCODE_MASTER (ID, PINCODE, OFFICE_NAME, OFFICE_TYPE, DELIVERY_STATUS, DIVISION_NAME, REGION_NAME, CIRCLE_NAME, TALUK, DISTRICT, STATE) VALUES (PINCODE_MASTER_SEQ.NEXTVAL, '796001', 'Aizawl', 'HO', 'Delivery', 'Aizawl', 'Guwahati', 'Mizoram', 'Aizawl', 'Aizawl', 'Mizoram');

-- ============================================================
-- 14. EXTRACTION_RULES (email parsing patterns)
-- ============================================================

INSERT INTO EXTRACTION_RULES (ID, NAME, FIELD_NAME, PATTERN, PRIORITY, IS_ACTIVE) VALUES (EXTRACTION_RULES_SEQ.NEXTVAL, 'Extract complaint number', 'complaintNumber', 'CMS/\d{4}/[A-Z]{3}/\d{6}', 1, 1);
INSERT INTO EXTRACTION_RULES (ID, NAME, FIELD_NAME, PATTERN, PRIORITY, IS_ACTIVE) VALUES (EXTRACTION_RULES_SEQ.NEXTVAL, 'Extract phone number', 'phone', '[6-9]\d{9}', 2, 1);
INSERT INTO EXTRACTION_RULES (ID, NAME, FIELD_NAME, PATTERN, PRIORITY, IS_ACTIVE) VALUES (EXTRACTION_RULES_SEQ.NEXTVAL, 'Extract email', 'email', '[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}', 3, 1);
INSERT INTO EXTRACTION_RULES (ID, NAME, FIELD_NAME, PATTERN, PRIORITY, IS_ACTIVE) VALUES (EXTRACTION_RULES_SEQ.NEXTVAL, 'Extract amount (Rs)', 'amount', 'Rs\.?\s*[\d,]+\.?\d*', 4, 1);
INSERT INTO EXTRACTION_RULES (ID, NAME, FIELD_NAME, PATTERN, PRIORITY, IS_ACTIVE) VALUES (EXTRACTION_RULES_SEQ.NEXTVAL, 'Extract account number', 'accountNumber', '\d{9,18}', 5, 1);
INSERT INTO EXTRACTION_RULES (ID, NAME, FIELD_NAME, PATTERN, PRIORITY, IS_ACTIVE) VALUES (EXTRACTION_RULES_SEQ.NEXTVAL, 'Extract date (DD-MM-YYYY)', 'date', '\d{2}[-/]\d{2}[-/]\d{4}', 6, 1);

COMMIT;
