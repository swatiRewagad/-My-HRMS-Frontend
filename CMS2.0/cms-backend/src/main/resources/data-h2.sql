-- Seed extraction rules for rule-based field extraction
INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Phone Number', 'PHONE_REGEX', 'Extract Indian mobile number', 'REGEX', '(?:mobile|phone|contact|mob|cell)[:\s]*([6-9]\d{9})', 'complainantPhone', 1, NULL, 10, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Email Address', 'EMAIL_REGEX', 'Extract email address', 'REGEX', '(?:email|e-mail|mail)[:\s]*([a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,})', 'complainantEmail', 1, 'LOWERCASE', 20, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Pincode', 'PINCODE_REGEX', 'Extract 6-digit Indian pincode', 'REGEX', '(?:pin\s*code|pincode|pin)[:\s]*(\d{6})', 'complainantPincode', 1, NULL, 30, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Name', 'NAME_REGEX', 'Extract complainant name', 'REGEX', '(?:name|complainant|from)[:\s]*([A-Z][a-zA-Z]+(?:\s[A-Z][a-zA-Z]+){1,3})', 'complainantName', 1, NULL, 40, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Amount', 'AMOUNT_REGEX', 'Extract monetary amount', 'REGEX', '(?:amount|rs|inr|₹)[.\s:]*([0-9,]+(?:\.\d{1,2})?)', 'amountInvolved', 1, NULL, 50, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('CPGRAMS Number', 'CPGRAMS_REGEX', 'Extract CPGRAMS reference number', 'REGEX', '(?:CPGRAMS|cpgrams|CPGRM)[:\s#]*([A-Z0-9/-]+)', 'cpgramsNumber', 1, 'UPPERCASE', 55, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Entity Name - Bank', 'ENTITY_BANK_KW', 'Match known bank names', 'KEYWORD_LIST', 'HDFC Bank,ICICI Bank,State Bank of India,SBI,Axis Bank,Kotak Mahindra Bank,Punjab National Bank,PNB,Bank of Baroda,BOB,Canara Bank,Union Bank,Indian Bank,Bank of India,Central Bank of India,IDBI Bank,Yes Bank,IndusInd Bank,Federal Bank,RBL Bank,Bandhan Bank', 'entityName', 0, NULL, 60, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Category - Credit Card', 'CAT_CC_KW', 'Detect credit card complaints', 'KEYWORD_LIST', 'credit card,credit-card,creditcard,card statement,card charge,annual fee,card limit,EMI conversion', 'category', 0, 'UPPERCASE', 70, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Category - Loan', 'CAT_LOAN_KW', 'Detect loan complaints', 'KEYWORD_LIST', 'home loan,personal loan,car loan,vehicle loan,education loan,loan account,EMI,loan closure,foreclosure,prepayment', 'category', 0, 'UPPERCASE', 71, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Category - ATM', 'CAT_ATM_KW', 'Detect ATM complaints', 'KEYWORD_LIST', 'ATM,cash not dispensed,ATM transaction,debit without dispensing,ATM withdrawal,cash stuck', 'category', 0, 'UPPERCASE', 72, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('State', 'STATE_KW', 'Match Indian states', 'KEYWORD_LIST', 'Maharashtra,Karnataka,Tamil Nadu,Delhi,Uttar Pradesh,Gujarat,Rajasthan,West Bengal,Madhya Pradesh,Kerala,Andhra Pradesh,Telangana,Bihar,Punjab,Haryana,Odisha,Assam,Jharkhand,Chhattisgarh,Uttarakhand,Goa,Himachal Pradesh,Jammu and Kashmir', 'complainantState', 0, NULL, 80, true, 'BOTH', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Address', 'ADDRESS_REGEX', 'Extract address after keyword', 'REGEX', '(?:address|residing at|resident of)[:\s]*(.{10,150}?)(?:\.|$)', 'complainantAddress', 1, 'TRIM', 90, true, 'BODY', 'system');

INSERT INTO extraction_rule (rule_name, rule_code, description, pattern_type, pattern, target_field, extract_group, transform, priority, is_active, source_scope, created_by)
VALUES ('Date', 'DATE_REGEX', 'Extract transaction/letter date', 'REGEX', '(?:dated?|on)[:\s]*(\d{1,2}[/\-]\d{1,2}[/\-]\d{2,4})', 'letterDate', 1, NULL, 100, true, 'BOTH', 'system');
