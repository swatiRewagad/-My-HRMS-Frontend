# CMS Portal Frontend — User Stories (EPC1 - Citizen Portal)

> Working file for application testing by user story. Screenshots are pasted 2 user stories at a time; this file is updated incrementally as more are attached.
> Status: UST2–UST50 captured so far (50 user stories). More to be appended as remaining screenshots are provided.

---

## UST2 — CMS Portal should be easily discoverable via search engines

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the CMS portal to be searchable on major search engines so that I can easily find it.

**In Scope:**
- Implementation of SEO best practices (metadata, keywords, sitemap).
- Ensuring portal is indexed by major search engines (Google, Bing, Yahoo).
- Testing searchability with common complaint-related keywords.
- Accessibility validation across desktop and mobile browsers.

**Out of Scope:**
- Paid search engine promotions or advertisements.
- Non-English keyword optimization.
- Integration with third-party complaint portals.

**Acceptance Criteria:**
1. **Portal indexed by search engines** — Given the CMS portal is live and accessible on the internet, when a user searches with keywords like "RBI complaint portal" or "CMS RBI", then the CMS portal should appear in the search results on Google, Bing, and other major search engines.
2. **Metadata and SEO compliance** — Given the CMS portal pages are published, when search engines crawl the portal, then the portal should have proper metadata, titles, and descriptions to ensure visibility.
3. **Accessibility across devices** — Given a user searches from desktop or mobile, when they enter relevant keywords, then the CMS portal should be discoverable and accessible across devices.

---

## UST3 — Login into the CMS portal via Mobile Number and OTP

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to log in to the CMS portal using my mobile number and a one-time password (OTP) so that I can securely access the portal without needing to remember complex credentials.

**In Scope:**
- Login option using mobile number and OTP.
- OTP delivery via SMS to registered mobile number.
- OTP validity period (e.g., 5 minutes).
- Secure verification of OTP before granting access.

**Out of Scope:**
- Email-based login.
- Social media login.
- Biometric authentication.

**Acceptance Criteria:**
1. **OTP generation and delivery** — Given the complainant enters a valid registered mobile number, when they request to log in, then the system should generate an OTP and send it via SMS to the provided mobile number.
2. **OTP validation** — Given the complainant has received the OTP, when they enter the OTP within the validity period, then the system should authenticate the complainant and grant access to the CMS portal.
3. **Expired OTP** — Given the complainant enters an OTP after the validity period has expired, when they attempt to log in, then the system should reject the OTP and prompt the complainant to request a new one.

---

## UST4 — Complainant must enter Captcha for login

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter a Captcha while logging into the CMS portal so that I can prove I am a human user and prevent automated bots from accessing the system.

**In Scope:**
- Captcha field displayed on login page.
- Captcha validated along with mobile number and OTP.
- Support for both text-based and image-based Captcha.
- Error handling for incorrect Captcha entries.
- Audio Captcha or accessibility-specific Captcha.

**Out of Scope:**
- Advanced Captcha types like reCAPTCHA v3.

**Acceptance Criteria:**
1. **Captcha displayed on login page** — Given the page loads, when the complainant navigates to the CMS portal login page, then a Captcha field should be displayed along with the mobile number and OTP fields.
2. **Correct Captcha entry** — Given the complainant enters the Captcha correctly, when they submit login credentials, then the system should validate the Captcha and allow login if OTP is also correct.
3. **Captcha refresh option** — Given the complainant cannot read the Captcha, when they click on the refresh button, then a new Captcha should be generated and displayed.

---

## UST5 — Login Declaration – Consent Before OTP

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to confirm my consent before sending OTP so that the CMS portal can ensure compliance with applicable laws and the Digital Personal Data Protection Act, 2023.

**In Scope:**
- Display of a mandatory declaration checkbox before OTP request.
- Declaration text: "I consent to RBI collecting and using my personal data for registering and resolving my complaint, in line with applicable laws and the Digital Personal Data Protection Act, 2023."
- Complainant must check the box before clicking Send OTP.
- Validation to ensure declaration is selected before OTP is triggered.
- Error handling if declaration is not checked.

**Out of Scope:**
_(not captured in screenshot)_

**Acceptance Criteria:**
1. **Valid Selection – OTP Trigger** — Given the complainant enters mobile number and captcha, when they check the declaration box and click "Send OTP", then the system should accept the request and send the OTP.
2. **Mandatory Field Error – Declaration Not Checked** — Given the complainant enters mobile number and captcha, when they click "Send OTP" without checking the declaration box, then the system should reject the request and display "Consent declaration is mandatory."
3. **Audit Logging – Consent Capture** — Given the complainant checks the declaration box and requests OTP, when the system processes the request, then the system should log the consent capture along with timestamp for audit purposes.

---

## UST6 — Login should fail on incorrect OTP or Captcha

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the CMS portal to reject login attempts when an incorrect OTP or Captcha is entered so that unauthorized users and bots cannot gain access.

**In Scope:**
- Validation of OTP entered by the complainant.
- Validation of Captcha input during login.
- Error messages displayed for incorrect OTP or Captcha.
- Prevention of portal access until correct credentials are provided.
- Accessibility-specific Captcha types (audio Captcha).

**Out of Scope:**
- Account lockout or suspension after repeated failed attempts.
- Alternative login methods (email, biometrics, social login).
- Advanced Captcha types like reCAPTCHA v3.

**Acceptance Criteria:**
1. **Incorrect OTP entered** — Given the complainant enters an invalid OTP, when they attempt to log in, then the system should reject the login attempt and display an error message prompting them to re-enter the OTP.
2. **Incorrect Captcha entered** — Given the complainant enters an invalid Captcha, when they attempt to log in, then the system should reject the login attempt and display an error message prompting them to re-enter the Captcha.
3. **Multiple failed attempts** — Given the complainant repeatedly enters incorrect OTP or Captcha, when they attempt to log in 3 times, then the system should continue to deny access and may prompt additional verification.

---

## UST7 — OTP should expire after 5 minutes

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the OTP to be valid only for 5 minutes so that my login process remains secure and unauthorized access is prevented if the OTP is misused or delayed.

**In Scope:**
- OTP validity restricted to 5 minutes.
- Automatic expiry of OTP after the defined time window.
- Error message displayed when an expired OTP is used.
- Option to request a new OTP after expiry.

**Out of Scope:**
- Customizable OTP validity duration (longer or shorter than 5 minutes).
- Alternative delivery channels beyond SMS (e.g., email, WhatsApp).
- Account lockout rules after repeated failed OTP attempts.

**Acceptance Criteria:**
1. **Valid OTP within 5 minutes** — Given the complainant receives an OTP, when they enter the OTP within 5 minutes, then the system should validate and allow login if Captcha is also correct.
2. **Expired OTP after 5 minutes** — Given the complainant receives an OTP, when they attempt to use the OTP after 5 minutes, then the system should reject the OTP and display a message indicating that the OTP has expired.
3. **Requesting new OTP after expiry** — Given the complainant's OTP has expired, when they request a new OTP, then the system should generate and send a fresh OTP to the complainant's registered mobile number.

---

## UST8 — Regenerate OTP after 2 minutes

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the ability to regenerate a new OTP after 2 minutes so that I can continue the login process securely if the original OTP is delayed or not received.

**In Scope:**
- Option to regenerate OTP after a minimum wait time of 2 minutes.
- System generates and sends a fresh OTP to the registered mobile number.
- Expiry rules applied to each regenerated OTP (valid for 5 minutes).
- Error message displayed if regeneration is attempted before 2 minutes.

**Out of Scope:**
- Customizable regeneration wait time (other than 2 minutes).
- OTP delivery through channels other than SMS (e.g., email, WhatsApp).

**Acceptance Criteria:**
1. **Regenerate OTP after 2 minutes** — Given the complainant has requested an OTP, when 2 minutes have passed since the last OTP request, then the system should allow the complainant to regenerate a new OTP and send it to their registered mobile number.
2. **Attempt to regenerate OTP before 2 minutes have passed** — Given the complainant has requested an OTP, when they attempt to regenerate OTP before 2 minutes have passed, then the system should reject the request and display a message indicating that OTP can only be regenerated after 2 minutes.
3. **Validity of regenerated OTP** — Given the complainant has regenerated a new OTP after 2 minutes, when they enter the OTP within 5 minutes, then the system should validate and allow login if Captcha is also correct.

---

## UST9 — Change Phone Number Option on OTP Screen

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want a Change Phone Number option on the OTP screen so that I can go back and enter a different number for login.

**In Scope:**
- Display of "Change Phone Number" button next to the Verify button on the OTP screen.
- Navigation back to the previous screen (Enter Phone Number + Captcha + Request OTP).
- Allowing the complainant to re-enter a new phone number and captcha, and request a fresh OTP.

**Out of Scope:**
- Any alternative login methods beyond phone number + OTP + captcha.
- Auto-saving of previously entered OTPs when phone number is changed.

**Acceptance Criteria:**
1. **Given the complainant is on the OTP verification screen** — when they click "Change Phone Number", then the system should navigate back to the previous screen (Enter Phone Number + Captcha + Request OTP).
2. **Given the complainant navigates back to the previous screen** — when they enter a new phone number and captcha and request OTP, then the system should generate a fresh OTP for the new number.
3. **(Exception)** Given the complainant clicks "Change Phone Number" but they do not enter a new phone number, when they try to request OTP, then the system should reject the request and display a mandatory field error.

---

## UST10 — Complainant session should remain active for 15 minutes

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want my session on the CMS portal to remain active for 15 minutes so that I can raise new complaints, provide feedback, track /withdraw/appeal complaints without being asked to log in each time.

**In Scope:**
- Session timeout configured to 15 minutes of inactivity.
- Complainant can perform multiple actions (raise complaint, provide feedback, track/withdraw/appeal complaint) within the active session.
- Automatic logout after 15 minutes of inactivity.
- Display of a warning message before session expiry.

**Out of Scope:**
- Customizable session duration (other than 15 minutes).
- "Remember me" or persistent login beyond 15 minutes.
- Multi-device session synchronization.

**Acceptance Criteria:**
1. **Session remains active for 15 minutes** — Given the complainant has successfully logged in, when they perform actions like raising a complaint, providing feedback or tracking/withdrawing/appealing a complaint within 15 minutes, then the system should allow these actions without requiring re-login.
2. **Session expires after 15 minutes of inactivity** — Given the complainant has logged in, when they remain inactive for 15 minutes, then the system should automatically log them out and prompt for re-login.
3. **Warning before session expiry** — Given the complainant has been inactive for 14 minutes, when the system detects nearing session timeout, then a warning message should be displayed indicating that the session is about to expire.

---

## UST11 — File Complaint with RBI – Eligibility After Approaching RE

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to file a complaint with RBI so that RBI intervention happens only if the regulated entity (RE) fails to resolve my grievance within the stipulated time or provides an unsatisfactory reply.

**In Scope:**
- Complainant must have first filed a complaint with the RE.
- Eligibility to file with RBI arises if:
  - 30 days have elapsed since filing with RE and no satisfactory reply received.
  - A reply is received within 30 days of filing with RE but is deemed unsatisfactory by the complainant.

**Out of Scope:**
- Restriction of complaint filing with RBI until eligibility criteria are met.

**Acceptance Criteria:**
1. **Eligible – 30 Days Elapsed** — Given the complainant filed a complaint with RE, when 30 days have passed without a satisfactory reply, then the system should allow filing complaint with RBI.
2. **Eligible – Unsatisfactory Reply** — Given the complainant filed a complaint with RE, when the RE provides a reply marked unsatisfactory by the complainant within the stipulated time, then the system should allow filing complaint with RBI.
3. **Not Eligible – Less Than 30 Days** — Given the complainant filed a complaint with RE, when less than 30 days have passed and no reply is received, then the system should display as per appendix 8 in BRD.

---

## UST12 — File Complaint with RBI – Timelines for Filing

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to file a complaint with RBI within defined timelines so that the grievance redress process remains valid and enforceable.

**In Scope:**
- Complaint filing with RBI must occur within:
  - 30 days from the date of filing with RE, OR
  - 90 days from the date of receiving a response from RE.
- Restriction of complaint filing beyond defined eligibility.
- Error handling for expired timelines.

**Out of Scope:**
- Filing complaints beyond 310 days or 90 days (system should block).

**Acceptance Criteria:**
1. **Eligible – Within 310 Days** — Given the complainant filed a complaint with RE, when they attempt to file with RBI within 310 days of that filing, then the system should allow filing.
2. **Eligible – Within 90 Days of Response** — Given the complainant received a response from RE, when they attempt to file with RBI within 90 days of that response, then the system should allow filing.
3. **Not Eligible – Beyond Timelines** — Given the complainant attempts to file RBI, when more than 310 days have passed since RE filing or more than 90 days since RE's response, then the system should block.

---

## UST13 — FR-G-009 – File a Complaint – Regulated Entity Details – Regulated Entity Name

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the regulated entity name from a dropdown list in the File a Complaint form so that the CMS portal can identify the entity against which I am filing a complaint.

**In Scope:**
- Dropdown list of regulated entities displayed in the Regulated Entity Details section.
- Search functionality by name or entity type.
- Mandatory selection before proceeding to the next section.

**Out of Scope:**
- Additional search parameters beyond name or entity type.
- Manual entry of regulated entity names outside the dropdown list.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a valid regulated entity name from the dropdown, when they submit the form, then the system should accept the input and proceed to the next section.
2. **Mandatory Field Error** — Given the complainant does not select any entity, when they attempt to submit the form, then the system should reject the submission and display an error message: "Regulated Entity Name is mandatory."
3. **Search Functionality** — Given the complainant searches by name or entity type, when they enter the search term, then the system should display matching regulated entities in the dropdown list.

---

## UST14 — FR-G-009 – File a Complaint – Eligibility Check – Have you filed a written/electronic complaint with the Regulated Entity?

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether I have filed a written/electronic complaint with the regulated entity so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "No" is selected, complaint auto-closed with pop-up alert and closure letter as per Appendix 8 (Clause 10(1)(e)) of the BRD.
- If "Yes" is selected, system proceeds to the next eligibility question.

**Out of Scope:**
- Any eligibility logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Valid – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should proceed to the next eligibility question.
2. **Exception – No** — Given the complainant selects "No", when they submit the form, then the system should display a pop-up alert and closure letter as per Appendix 8 (Clause 10(1)(e) of the BRD) and auto-close the complaint upon confirmation.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST15 — FR-G-009 – File a Complaint – Eligibility Check – Date on which the complaint was first filed with the Regulated Entity

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the date on which the complaint was first filed with the regulated entity so that the CMS portal can validate eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Calendar date picker displayed under Eligibility Check.
- Mandatory entry if the previous question ("Have you filed a written/electronic complaint with the regulated entity?") is answered "Yes".
- Validation of date format automatically enforced by the calendar control (DD/MM/YYYY).

**Out of Scope:**
- Any date logic beyond the defined dependency.
- Manual text entry of dates outside the calendar picker.

**Acceptance Criteria:**
1. **Valid Entry – Yes** — Given the complainant selects "Yes" in the previous question, when they choose a valid date from the calendar picker, then the system should accept the input and proceed.
2. **Mandatory Field Error** — Given the complainant selects "Yes" in the previous question, when they do not select any date, then the system should reject the submission and display an error message: "Date of complaint is mandatory."
3. **Invalid Format Prevention** — Given the complainant uses the calendar picker, when they select a date, then the system should enforce the correct format automatically and prevent invalid date formats.

---

## UST16 — FR-G-009 – File a Complaint – Eligibility Check – Upload a copy of the complaint filed to the Regulated Entity

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to upload a copy of the complaint filed with the regulated entity so that the CMS portal has supporting documentation to validate eligibility.

**In Scope:**
- Upload field displayed under Eligibility Check if the previous question ("Have you filed a written/electronic complaint with the Regulated Entity?") is answered "Yes".
- Mandatory upload of complaint copy.
- File type restrictions as defined in BRD Appendix 8.
- File size restrictions: maximum 2 MB on portal, 5 MB on application.

**Out of Scope:**
- Upload of unsupported file types or sizes.
- Upload through channels other than the portal/application.

**Acceptance Criteria:**
1. **Valid Upload** — Given the complainant selects "Yes" in the previous question, when they upload a valid file type within the size limits, then the system should accept the file and proceed.
2. **Invalid File** — Given the complainant uploads an unsupported file type or a file exceeding size limits, when they submit the form, then the system should reject the upload and display an error message: "Invalid file type or size exceeded."
3. **Mandatory Field Error** — Given the complainant answers "Yes" in the previous question, when they do not upload any file, then the system should reject the submission and display an error message: "Complaint copy upload is mandatory."

---

## UST17 — FR-G-009 – Eligibility Check – RE Acknowledgement Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to provide the RE Acknowledgement Number when confirming eligibility so that the CMS portal can validate my complaint against the regulated entity's records.

**In Scope:**
- Display of a text field labeled "RE Acknowledgement Number."
- Field properties:
  - Type: Text
  - Format: Alphanumeric
  - Length: Maximum 100 characters
  - Optional field
- Field should be visible only if the complainant selects "Yes" to the preceding eligibility question.
- Validation of character length and alphanumeric input.
- Storage of acknowledgement number along with complaint record.

**Out of Scope:**
- Automated verification of acknowledgement number against RE systems.
- Mandatory enforcement of this field (optional only).
- Use of special characters beyond alphanumeric.

**Acceptance Criteria:**
1. **Field Visibility – Yes Selected** — Given the complainant answers "Yes" to the eligibility question, when the form is displayed, then the system should show the "RE Acknowledgement Number" text field.
2. **Field Hidden – No Selected** — Given the complainant answers "No" to the eligibility question, when the form is displayed, then the system should hide the RE Acknowledgement Number text field.
3. **Valid Input – Alphanumeric** — Given the complainant enters an acknowledgement number, when the input is alphanumeric and ≤ 100 characters, then the system should accept the value.
4. **Invalid Input – Special Characters** — Given the complainant enters special characters in the acknowledgement number field, when they attempt to submit, then the system should display "Only alphanumeric characters are allowed."
5. **Optional Field – Empty** — Given the complainant leaves the acknowledgement number field blank, when they submit the form, then the system should accept the submission without error.

---

## UST18 — FR-G-009 – File a Complaint – Eligibility Check – Have you received a reply from the Entity?

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether I have received a reply from the regulated entity so that the CMS portal can determine complaint status in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "No" is selected and the date of complaint is less than 30 days, complaint auto-closed with notification message as per Appendix 8 of the BRD.
- If "Yes" is selected, system proceeds with complaint processing.

**Out of Scope:**
- Any eligibility logic beyond the defined 30-day rule.

**Acceptance Criteria:**
1. **Valid – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should proceed with complaint processing.
2. **Exception – No, <30 days** — Given the complainant selects "No" and the date of complaint is less than 30 days, when they submit the form, then the system should auto-close the complaint and display a notification message as per Appendix 8 of the BRD.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST19 — FR-G-009 – File a Complaint – Eligibility Check – If Yes, Date of Reply

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the date of reply received from the regulated entity using a calendar field so that the CMS portal can track the response timeline in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Calendar date picker displayed under Eligibility Check.
- Mandatory entry if the previous question ("Have you received any reply from the Entity?") is answered "Yes".
- Validation of date format automatically enforced by the calendar control (DD/MM/YYYY).

**Out of Scope:**
- Manual text entry of dates outside the calendar picker.
- Any date logic beyond the defined dependency.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant selects "Yes" in the previous question, when they choose a valid date from the calendar picker, then the system should accept the input and proceed.
2. **Mandatory Field Error** — Given the complainant selects "Yes" in the previous question, when they do not select any date, then the system should reject the submission and display an error message: "Date of reply is mandatory."
3. **Invalid Format Prevention** — Given the complainant uses the calendar picker, when they select a date, then the system should enforce the correct format automatically and prevent invalid date formats.

---

## UST20 — FR-G-009 – File a Complaint – Eligibility Check – Upload Reply Copy

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to upload a copy of the reply received from the regulated entity so that the CMS portal has supporting documentation of the entity's response.

**In Scope:**
- Upload field displayed under Eligibility Check if the previous question ("Have you received any reply from the Entity?") is answered "Yes".
- Mandatory upload of reply copy.
- File type restrictions as defined per Appendix 8 in BRD.
- File size restrictions: maximum 2 MB on portal, 5 MB on application.

**Out of Scope:**
- Upload of unsupported file types or sizes.
- Upload through channels other than the portal/application.

**Acceptance Criteria:**
1. **Valid Upload** — Given the complainant selects "Yes" in the previous question, when they upload a valid file type within the size limits, then the system should accept the file and proceed.
2. **Invalid File** — Given the complainant uploads an unsupported file type or a file exceeding size limits, when they submit the form, then the system should reject the upload and display an error message: "Invalid file type or size exceeded."
3. **Mandatory Field Error** — Given the complainant does not upload any file, when they submit the form, then the system should reject the submission and display an error message: "Reply upload is mandatory."

---

## UST21 — FR-G-009 – File a Complaint – Eligibility Check – Have you sent any reminder to the Regulated Entity?

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether I have sent any reminder to the regulated entity so that the CMS portal can track follow-up actions taken in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the system should proceed to capture reminder details.
- If "No" is selected, the system should proceed without capturing reminder details.

**Out of Scope:**
- Any reminder logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Valid – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should proceed to capture reminder details.
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed without capturing reminder details.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST22 — FR-G-009 – File a Complaint – Eligibility Check – If Yes, Date of Reminder

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the Date of Reminder sent to the Regulated Entity in the File a Complaint form so that the CMS portal can track follow-up timelines.

**In Scope:**
- Calendar date picker displayed under Eligibility Check.
- Mandatory entry if previous question ("Have you sent any reminder?") is Yes.
- Validation of date format automatically enforced by the calendar control (DD/MM/YYYY).

**Out of Scope:**
- Any date logic beyond the defined dependency.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant selects "Yes" in the previous question, when they choose a valid date from the calendar picker, then the system should accept the input and proceed.
2. **Mandatory Field Error** — Given the complainant selects "Yes" in the previous question, when they do not select any date, then the system should reject the submission and display an error message: "Date of reminder is mandatory."
3. **Invalid Format Prevention** — Given the complainant uses the calendar picker, when they select a date, then the system should enforce the correct format automatically and prevent invalid date formats.

---

## UST23 — FR-G-009 – File a Complaint – Eligibility Check – Upload Reminder Copy

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to Upload a Reminder Copy sent to the Regulated Entity in the File a Complaint form so that the CMS portal has supporting documentation of my follow-up.

**In Scope:**
- Upload field displayed under Eligibility Check if the previous question ("Have you sent any reminder to the Regulated Entity?") is answered "Yes".
- Mandatory upload of reminder copy.
- File type restrictions as defined in BRD.
- File size restrictions (2MB on portal, 5MB on application).

**Out of Scope:**
- Upload of unsupported file types or sizes.
- Upload through channels other than the portal/application.

**Acceptance Criteria:**
1. **Valid Upload** — Given the complainant selects "Yes" in the previous question, when they upload a valid file type within the size limits, then the system should accept the file and proceed.
2. **Invalid File** — Given the complainant uploads an unsupported file type or a file exceeding size limits, when they submit the form, then the system should reject the upload and display an error message: "Invalid file type or size exceeded."
3. **Mandatory Field Error** — Given the complainant does not upload any file, when they submit the form, then the system should reject the submission and display an error message: "Reminder copy upload is mandatory."

---

## UST24 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Pending Before Judicial/Quasi-Judicial Forum

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether the complaint relates to the same grievance already pending before any Court, Tribunal, Arbitrator, or other judicial/quasi-judicial forum (excluding criminal proceedings or police investigations) so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint is auto-closed with pop-up alert and closure message as per Appendix 3 of the BRD (Clause 10(1)(j)).
- If "No" is selected, the system proceeds to the next question.
- Field visible only if RBIO Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Appendix 3 of the BRD.
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST25 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Pending Already Settled/Dealt Before Judicial/Quasi-Judicial Forum

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether my complaint relates to the same grievance already settled or dealt before by any Court, Tribunal, Arbitrator, or other judicial/quasi-judicial forum (excluding criminal proceedings or police investigations) so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint is auto-closed with pop-up alert and closure message as per Appendix 3 of the BRD (Clause 10(1)(k)).
- If "No" is selected, the system proceeds to the next question.
- Field visible only if RBIO Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Appendix 3 of the BRD.
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST26 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Filed Through Advocate

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether my complaint is being made through an advocate so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the system should display the follow-up question ("If Yes, then are you the Complainant?").
- If "No" is selected, the system should skip the advocate branch and continue to the next general eligibility question.
- Field visible only if RBIO Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Valid – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should display the follow-up question "If Yes, then are you the Complainant?".
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should skip the advocate-specific question and proceed to the next general eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST27 — FR-G-009 – File a Complaint – Eligibility Check – Advocate Path – Complainant Confirmation

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to confirm whether I am the complainant when filing through an advocate so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If the user selects "Yes," the system should proceed to the next eligibility question.
- If the user selects "No," the complaint should be auto-closed with the relevant pop-up alert and closure message as per Appendix 8 of the BRD (Clause 10(1)(b)).
- Field visible only if the previous question ("Whether your complaint is being made through an advocate?") is answered "Yes".

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Valid – Yes** — Given the user selects "Yes", when they submit the form, then the system should proceed to the next eligibility question.
2. **Exception – No** — Given the user selects "No", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Appendix 8 of the BRD (Clause 10(1)(b)).
3. **Mandatory Field Error** — Given the user does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST28 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Pending Before Ombudsman

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether my complaint relates to the same grievance already pending before the Ombudsman so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint should be auto-closed with pop-up alert and closure message as per Clause 10(1)(h).
- If "No" is selected, the system should proceed to the next eligibility question.
- Field visible only if RBIO Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Clause 10(1)(h).
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST29 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Settled/Dealt With on Merits by Ombudsman

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether my complaint relates to the same grievance already settled or dealt with on merits by the Ombudsman so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options displayed under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint should be auto-closed with pop-up alert and closure message as per Clause 10(1)(i).
- If "No" is selected, the system should proceed to the next eligibility question.
- Field visible only if RBIO Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Clause 10(1)(i).
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST30 — FR-G-009 – File a Complaint – Eligibility Check – Employer-Employee Relationship

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether I am a staff member of the Regulated Entity and my complaint involves an employer-employee relationship so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint should be auto-closed with pop-up alert and closure message as per Clause 10(2)(g) of the BRD.
- If "No" is selected, the system should proceed to the next eligibility question.
- Field visible only if RBIO Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Clause 10(2)(g).
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST31 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Previously Filed with CEPC/RBI

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether I have filed a complaint on the same matter with the CEPC or RBI previously so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint should be auto-closed with pop-up alert and closure message as per Appendix 4 of the BRD.
- If "No" is selected, the system should proceed to the next eligibility question.
- Field visible only if CEPC Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Appendix 4 of the BRD.
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST32 — FR-G-009 – File a Complaint – Eligibility Check – Employee of Regulated Entity

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether I am or was an employee of the Regulated Entity against whom this complaint is being filed so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint should be auto-closed with pop-up alert and closure message as per Appendix 4 of the BRD (employee-employer relationship clause).
- If "No" is selected, the system should proceed to the next eligibility question.
- Field visible only if CEPC Regulated Entity is selected.

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Appendix 4 of the BRD (employee-employer relationship clause).
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST33 — FR-G-009 – File a Complaint – Eligibility Check – Complaint Involving Employee-Employer Relationship

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to answer whether my complaint involves the employee-employer relationship of the Regulated Entity so that the CMS portal can determine eligibility in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Checkbox field with Yes/No options under Eligibility Check.
- Mandatory response before proceeding.
- If "Yes" is selected, the complaint should be auto-closed with pop-up alert and closure message as per Appendix 4 of the BRD (Case II – employee-employer relationship).
- If "No" is selected, the system should proceed to the next eligibility question.
- Field visible only if the previous question (Field 19) was answered "Yes".

**Out of Scope:**
- Any logic beyond the defined Yes/No behaviour.

**Acceptance Criteria:**
1. **Exception – Yes** — Given the complainant selects "Yes", when they submit the form, then the system should auto-close the complaint and display the pop-up alert and closure message as per Appendix 4 of the BRD.
2. **Valid – No** — Given the complainant selects "No", when they submit the form, then the system should proceed to the next eligibility question.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST34 — FR-G-009 – File a Complaint – Complainant Details – Complainant Category

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select my category from a predefined list so that the CMS portal can capture my details accurately and classify the complaint in line with the Reserve Bank – Integrated Ombudsman Scheme.

**In Scope:**
- Drop-down field under Complainant Details.
- Mandatory response before proceeding.
- Options available in the drop-down:
  - Individual
  - Person with Disabilities
  - Senior Citizen
  - Individual – Business
  - Proprietorship
  - Partnership
  - MSME
  - Association
  - Trust
  - Limited Company
  - Government Department
  - PSU

**Out of Scope:**
- Any logic beyond capturing the selected category.
- No auto-closure or eligibility check logic tied to this field.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a category from the drop-down list, when they submit the form, then the system should accept the selection and proceed to the next section.
2. **Mandatory Field Error** — Given the complainant does not select any category, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST35 — FR-G-009 – File a Complaint – Complainant Details – First Name

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my first name so that the CMS portal can capture my identity correctly.

**In Scope:**
- Text field for First Name.
- Mandatory response if Complainant Category is Individual or Senior Citizen.
- Only alphabets allowed (no numbers or special characters).
- Maximum length: 150 characters (per data dictionary).

**Out of Scope:**
- Any logic beyond validation of letters and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid first name using only letters, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Numbers/Special Characters** — Given the complainant enters numbers or special characters in the first name field, when they submit the form, then the system should reject the entry and display an error message: "Only letters are allowed."
3. **Mandatory Field Error** — Given the complainant leaves the first name field blank, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST36 — FR-G-009 – File a Complaint – Complainant Details – Middle Name

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter my middle name so that the CMS portal can capture my identity correctly.

**In Scope:**
- Text field for Middle Name.
- Optional field if Complainant Category is Individual or Senior Citizen.
- Only alphabets allowed.
- Maximum length: 150 characters.

**Out of Scope:**
- Any logic beyond validation of letters and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid middle name using only letters, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Numbers/Special Characters** — Given the complainant enters numbers or special characters in the middle name field, when they submit the form, then the system should reject the entry and display an error message: "Only letters are allowed."
3. **Optional Field** — Given the complainant leaves the middle name field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST37 — FR-G-009 – File a Complaint – Complainant Details – Surname

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my surname so that the CMS portal can capture my identity correctly.

**In Scope:**
- Text field for Surname.
- Mandatory response if Complainant Category is Individual or Senior Citizen.
- Only alphabets allowed.
- Maximum length: 150 characters.

**Out of Scope:**
- Any logic beyond validation of letters and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid surname using only letters, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Numbers/Special Characters** — Given the complainant enters numbers or special characters in the surname field, when they submit the form, then the system should reject the entry and display an error message: "Only letters are allowed."
3. **Mandatory Field Error** — Given the complainant leaves the surname field blank, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST38 — FR-G-009 – File a Complaint – Complainant Details – Age

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my age so that the CMS portal can validate eligibility for Senior Citizen category.

**In Scope:**
- Text field for Age.
- Non-mandatory response if Complainant Category is Individual or Senior Citizen.
- Numeric values only.
- For Senior Citizen, age must be ≥ 60.
- No specific validation for Individual.

**Out of Scope:**
- Any logic beyond numeric validation and senior citizen threshold.

**Acceptance Criteria:**
1. **Valid Entry – Senior Citizen** — Given the complainant selects Senior Citizen and enters age ≥ 60, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Senior Citizen** — Given the complainant selects Senior Citizen and enters age < 60, when they submit the form, then the system should reject the entry and display an error message: "Age must be 60 or above for Senior Citizen category."
3. **Valid Entry – Individual** — Given the complainant selects Individual and enters any numeric age, when they submit the form, then the system should accept the entry and proceed.
4. **Invalid Entry – Non-Numeric** — Given the complainant enters letters or special characters in the age field, when they submit the form, then the system should reject the entry and display an error message: "Only numbers are allowed."

---

## UST39 — FR-G-009 – File a Complaint – Complainant Details – Gender

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select my gender from a predefined list so that the CMS portal can capture my demographic details accurately.

**In Scope:**
- Drop-down field for Gender.
- Non-mandatory response if Complainant Category is Individual or Senior Citizen.
- Options available:
  - Male
  - Female
  - Transgender
  - Do not wish to disclose
  - Other

**Out of Scope:**
- Any logic beyond capturing the selected option.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a gender option from the drop-down list, when they submit the form, then the system should accept the selection and proceed.

---

## UST40 — FR-G-009 – File a Complaint – Complainant Details – Name of Complainant (Organization Name)

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the name of my organization so that the CMS portal can capture the complainant details correctly.

**In Scope:**
- Text field for Organization Name.
- Mandatory if Complainant Category is other than Individual or Senior Citizen.
- Only alphabets allowed.
- Maximum length: 150 characters.

**Out of Scope:**
- Any logic beyond validation of letters and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid organization name using only letters, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Numbers/Special Characters** — Given the complainant enters numbers or special characters in the organization name field, when they submit the form, then the system should reject the entry and display an error message: "Only letters are allowed."
3. **Mandatory Field Error** — Given the complainant leaves the organization name field blank, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST41 — FR-G-009 – File a Complaint – Complainant Details – Mobile Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my mobile number so that the CMS portal can capture my contact details correctly.

**In Scope:**
- Text field for Mobile Number.
- Mandatory field.
- Numeric values only.
- Must be exactly 10 digits.

**Out of Scope:**
- Any logic beyond numeric validation and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a 10-digit numeric mobile number, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Non-Numeric** — Given the complainant enters letters or special characters in the mobile number field, when they submit the form, then the system should reject the entry and display an error message: "Only numbers are allowed."
3. **Invalid Entry – Length** — Given the complainant enters fewer or more than 10 digits, when they submit the form, then the system should reject the entry and display an error message: "Mobile number must be 10 digits."
4. **Mandatory Field Error** — Given the complainant leaves the mobile number field blank, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST42 — FR-G-009 – File a Complaint – Complainant Details – Organization Landline Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter my organization's landline number so that the CMS portal can capture additional contact details.

**In Scope:**
- Text field for Organization Landline Number.
- Optional field.
- Numeric values only.
- Maximum length: 10 digits.
- Visible only if Complainant Category is other than Individual or Senior Citizen.

**Out of Scope:**
- Any logic beyond numeric validation and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid 10-digit numeric landline number, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Non-Numeric** — Given the complainant enters letters or special characters in the landline number field, when they submit the form, then the system should reject the entry and display an error message: "Only numbers are allowed."
3. **Optional Field** — Given the complainant leaves the landline number field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST43 — FR-G-009 – File a Complaint – Complainant Details – Email Address

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my email address so that the CMS portal can capture my contact details correctly.

**In Scope:**
- Text field for Email Address.
- Optional field.
- Alphanumeric values.
- Maximum length: 64 characters.
- Must be a valid email format (e.g., name@domain.com).

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid email address in correct format, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Format** — Given the complainant enters an invalid email format, when they submit the form, then the system should reject the entry and display an error message: "Enter a valid email address."
3. **Optional Field** — Given the complainant leaves the email address field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST44 — FR-G-009 – File a Complaint – Complainant Details – State of Residence

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select my state of residence so that the CMS portal can capture my location details correctly.

**In Scope:**
- Drop-down field with list of states.
- Mandatory response.

**Out of Scope:**
- Any logic beyond capturing the selected state.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a state from the drop-down list, when they submit the form, then the system should accept the selection and proceed.
2. **Mandatory Field Error** — Given the complainant does not select any state, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST45 — FR-G-009 – File a Complaint – Complainant Details – District of Residence

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select my district of residence so that the CMS portal can capture my location details correctly.

**In Scope:**
- Drop-down field with list of districts.
- Mandatory response.

**Out of Scope:**
- Any logic beyond capturing the selected district.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a district from the drop-down list, when they submit the form, then the system should accept the selection and proceed.
2. **Mandatory Field Error** — Given the complainant does not select any district, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST46 — FR-G-009 – File a Complaint – Complainant Details – Address

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my address so that the CMS portal can capture my residence details correctly.

**In Scope:**
- Text field for Address.
- Mandatory response.
- Alphanumeric values allowed.
- Maximum length: 100 characters (per data dictionary).

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric address within 100 characters, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Address cannot exceed 100 characters."
3. **Mandatory Field Error** — Given the complainant leaves the address field blank, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST47 — FR-G-009 – File a Complaint – Complainant Details – Pincode

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select my pincode so that the CMS portal can capture my residence details correctly.

**In Scope:**
- Drop-down field with list of pincodes.
- Mandatory response.
- Numeric values only.
- Maximum length: 6 digits.

**Out of Scope:**
- Any logic beyond numeric validation and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant selects a valid 6-digit pincode, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Length** — Given the complainant enters fewer or more than 6 digits, when they submit the form, then the system should reject the entry and display "Pincode must be 6 digits."
3. **Mandatory Field Error** — Given the complainant does not select any pincode, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST48 — FR-G-009 – File a Complaint – Regulated Entity Details – Credit Card Complaint

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to specify whether my complaint relates to a credit card complaint so that the CMS portal can determine the applicable entity details.

**In Scope:**
- Checkbox field with Yes/No options.
- Mandatory response.
- Visible if Entity selected is RBIO regulated.

**Out of Scope:**
- Any logic beyond Yes/No behaviour.

**Acceptance Criteria:**
1. **Yes** — Given the complainant selects "Yes", when they submit the form, then the system should capture the response and proceed accordingly.
2. **No** — Given the complainant selects "No", when they submit the form, then the system should display Entity State, District, and Branch fields.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST49 — FR-G-009 – File a Complaint – Regulated Entity Details – Entity State

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the state of the regulated entity so that the CMS portal can capture the entity details correctly.

**In Scope:**
- Drop-down field with list of states.
- Mandatory response.
- Visible only if Credit Card Complaint = No.

**Out of Scope:**
- Any logic beyond capturing the selected state.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a state from the drop-down list, when they submit the form, then the system should accept the selection and proceed.
2. **Mandatory Field Error** — Given the complainant does not select any state, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST50 — FR-G-009 – File a Complaint – Regulated Entity Details – Entity District

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the district of the regulated entity so that the CMS portal can capture the entity details correctly.

**In Scope:**
- Drop-down field with list of districts.
- Mandatory response.
- Visible only if Credit Card Complaint = No.

**Out of Scope:**
- Any logic beyond capturing the selected district.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a district from the drop-down list, when they submit the form, then the system should accept the selection and proceed.
2. **Mandatory Field Error** — Given the complainant does not select any district, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST51 — FR-G-009 – File a Complaint – Regulated Entity Details – Entity Branch

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the branch of the regulated entity so that the CMS portal can capture the entity details correctly.

**In Scope:**
- Drop-down field with list of branches.
- Mandatory response.
- Visible only if Credit Card Complaint = No.

**Out of Scope:**
- Any logic beyond capturing the selected branch.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a branch from the drop-down list, when they submit the form, then the system should accept the selection and proceed.
2. **Mandatory Field Error** — Given the complainant does not select any branch, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST52 — FR-G-009 – File a Complaint – Complaint Details – Complaint Category

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select a complaint category so that the CMS portal can classify my grievance correctly.

**In Scope:**
- Drop-down field with predefined categories: ATM/CDM/Debit card, Credit Card, Loans and Advances, Mobile/Electronic Banking, Notes and Coins, Opening/Operation of Deposit accounts, Para-Banking, Remittance and collection of instruments, Pension related, Other products and services.
- Mandatory response.

**Out of Scope:**
- Any logic beyond capturing the selected category.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a category from the drop-down list, when they submit the form, then the system should accept the selection and proceed.
2. **Mandatory Field Error** — Given the complainant does not select any category, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST53 — FR-G-009 – File a Complaint – Complaint Details – Facts of the Complaint

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the facts of my complaint so that the CMS portal can capture the grievance details comprehensively and accurately.

**In Scope:**
- Text field for Facts of the Complaint.
- Mandatory response.
- Alphanumeric values allowed.
- Maximum length: 5000 characters (per data dictionary).

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters valid alphanumeric text within 5000 characters, when they submit the form, then the system should accept the entry and proceed.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 5000 characters in the facts field, when they submit the form, then the system should reject the entry and display an error message: "Facts of the complaint cannot exceed 5000 characters."
3. **Mandatory Field Error** — Given the complainant leaves the facts field blank, when they attempt to submit the form, then the system should reject the submission and display an error message: "Response is mandatory."

---

## UST54 — FR-G-009 – File a Complaint – Complaint Details – Date of Disputed Transaction

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the date of the disputed transaction so that the CMS portal can validate the complaint timeline.

**In Scope:**
- Date field.
- Mandatory response.
- Date should not be greater than the date of complaint filing.

**Out of Scope:**
- Any logic beyond date validation.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a date less than or equal to the complaint filing date, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Future Date** — Given the complainant enters a date greater than the complaint filing date, when they submit the form, then the system should reject the entry and display "Date cannot be greater than complaint filing date."
3. **Mandatory Field Error** — Given the complainant leaves the date field blank, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST55 — FR-G-009 – File a Complaint – Complaint Details – Do you have an account with RE?

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to specify whether I have an account with the regulated entity so that the CMS portal can determine applicable account details.

**In Scope:**
- Checkbox field with Yes/No options.
- Mandatory response as per RBIOS 2026 (not mandatory in extant scheme).

**Out of Scope:**
- Any logic beyond Yes/No behaviour.

**Acceptance Criteria:**
1. **Yes** — Given the complainant selects "Yes", when they submit the form, then the system should display the Type of Account with RE field.
2. **No** — Given the complainant selects "No", when they submit the form, then the system should proceed without displaying account type fields.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST56 — FR-G-009 – File a Complaint – Complaint Details – Type of Account with RE

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the type of account I hold with the regulated entity so that the CMS portal can capture account details correctly.

**In Scope:**
- Drop-down field with multiple selection allowed.
- Options: Savings Account, Loan Account, ATM/Debit Card, Credit Card.
- Mandatory response if "Do you have an account with RE?" = Yes.

**Out of Scope:**
- Any logic beyond capturing selected account types.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects one or more account types, when they submit the form, then the system should accept the selection and display corresponding account number fields.
2. **Mandatory Field Error** — Given the complainant does not select any account type, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST57 — FR-G-009 – File a Complaint – Complaint Details – Savings Account Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my savings account number so that the CMS portal can capture account details correctly.

**In Scope:**
- Text field for Savings Account Number.
- Mandatory if account type = Savings Account.
- Alphanumeric values allowed.
- Maximum length: 100 characters.

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric savings account number within 100 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Account number cannot exceed 100 characters."
3. **Mandatory Field Error** — Given the complainant leaves the field blank when Savings Account is selected, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST58 — FR-G-009 – File a Complaint – Complaint Details – Loan Account Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my loan account number so that the CMS portal can capture account details correctly.

**In Scope:**
- Text field for Loan Account Number.
- Mandatory if account type = Loan Account.
- Alphanumeric values allowed.
- Maximum length: 100 characters.

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric loan account number within 100 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Account number cannot exceed 100 characters."
3. **Mandatory Field Error** — Given the complainant leaves the field blank when Loan Account is selected, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST59 — FR-G-009 – File a Complaint – Complaint Details – ATM/Debit Card Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my ATM/Debit Card Number so that the CMS portal can capture account details correctly.

**In Scope:**
- Text field for ATM/Debit card number.
- Mandatory if account type = ATM/Debit Card.
- Alphanumeric values allowed.
- Maximum length: 100 characters.

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric ATM/Debit card number within 100 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Card number cannot exceed 100 characters."
3. **Mandatory Field Error** — Given the complainant leaves the field blank when ATM/Debit Card is selected, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST60 — FR-G-009 – File a Complaint – Complaint Details – Credit Card Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter my credit card number so that the CMS portal can capture account details correctly.

**In Scope:**
- Text field for Credit Card Number.
- Mandatory if account type = Credit Card.
- Alphanumeric values allowed.
- Maximum length: 100 characters.

**Out of Scope:**
- Any logic beyond validation of format and length.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric credit card number within 100 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Card number cannot exceed 100 characters."
3. **Mandatory Field Error** — Given the complainant leaves the field blank when Credit Card is selected, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST61 — FR-G-009 – File a Complaint – Complaint Details – Complaint against Wallet

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to specify whether my complaint is against the wallet of the regulated entity so that the CMS portal can capture wallet details.

**In Scope:**
- Checkbox field with Yes/No options.
- Mandatory response.
- If "Yes" is selected, additional fields (Name of Wallet and Transaction/Reference Number) should be displayed.

**Out of Scope:**
- Any logic beyond Yes/No behaviour.
- No validation of wallet provider details beyond capturing the entered values.

**Acceptance Criteria:**
1. **Yes** — Given the complainant selects "Yes", when they submit the form, then the system should display Name of Wallet and Transaction/Reference Number fields.
2. **No** — Given the complainant selects "No", when they submit the form, then the system should proceed without displaying wallet fields.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST62 — FR-G-009 – File a Complaint – Complaint Details – Name of Wallet

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter the name of the wallet so that the CMS portal can capture wallet details.

**In Scope:**
- Text field for Wallet Name.
- Optional field.
- Alphanumeric values allowed.
- Maximum length: 100 characters.
- Visible only if Wallet Complaint = Yes.

**Out of Scope:**
- Any logic beyond validation of format and length.
- No verification of wallet provider details against external systems.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric wallet name within 100 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Wallet name cannot exceed 100 characters."
3. **Optional Field** — Given the complainant leaves the wallet name field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST63 — FR-G-009 – File a Complaint – Complaint Details – Transaction/Reference Number

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter the transaction or reference number so that the CMS portal can capture specific details of the disputed wallet transaction.

**In Scope:**
- Text field for Transaction/Reference Number.
- Optional field.
- Alphanumeric values allowed.
- Maximum length: 150 characters (per data dictionary).
- Visible only if Wallet Complaint = Yes.

**Out of Scope:**
- Any logic beyond validation of format and length.
- No verification of transaction/reference number against external systems.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric transaction/reference number within 150 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 150 characters, when they submit the form, then the system should reject the entry and display "Transaction/Reference number cannot exceed 150 characters."
3. **Optional Field** — Given the complainant leaves the transaction/reference number field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST64 — FR-G-009 – File a Complaint – Complaint Details – Complaint against Business Correspondent

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to specify whether my complaint is against a Business Correspondent of the regulated entity so that the CMS portal can capture the correct complaint type.

**In Scope:**
- Checkbox field with Yes/No options.
- Mandatory response.

**Out of Scope:**
- Any logic beyond Yes/No behaviour.
- No validation of Business Correspondent details beyond capturing the response.

**Acceptance Criteria:**
1. **Yes** — Given the complainant selects "Yes", when they submit the form, then the system should capture the response and proceed accordingly.
2. **No** — Given the complainant selects "No", when they submit the form, then the system should proceed without additional Business Correspondent fields.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST65 — FR-G-009 – File a Complaint – Complaint Details – Amount Involved in Transaction/Dispute

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter the amount involved in the disputed transaction so that the CMS portal can capture financial details.

**In Scope:**
- Text field for amount.
- Optional field.
- Numeric values only.
- Character limit as per data dictionary.

**Out of Scope:**
- Any logic beyond numeric validation and length.
- No automatic verification against transaction records.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a numeric amount within the allowed limit, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Non-Numeric** — Given the complainant enters letters or special characters, when they submit the form, then the system should reject the entry and display "Only numbers are allowed."
3. **Optional Field** — Given the complainant leaves the field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST66 — FR-G-009 – File a Complaint – Complaint Details – Compensation Sought for Dispute (Consequential Loss)

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter compensation sought for consequential loss so that the CMS portal can capture financial claims in line with RBIOS 2026.

**In Scope:**
- Text field for compensation amount.
- Optional field.
- Numeric values only.
- Must be ≤ ₹30 lakh.
- Character limit as per data dictionary.

**Out of Scope:**
- Any logic beyond numeric validation and threshold.
- No automatic calculation of compensation entitlement.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a numeric amount ≤ ₹30 lakh, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Above Limit** — Given the complainant enters an amount > ₹30 lakh, when they submit the form, then the system should reject the entry and display "Compensation for consequential loss can be awarded only up to ₹30 lakh. Please enter an amount up to ₹30 lakh."
3. **Optional Field** — Given the complainant leaves the field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST67 — FR-G-009 – File a Complaint – Complaint Details – Compensation Sought for Expenses/Harassment/Mental Anguish

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter compensation sought for expenses, harassment, or mental anguish so that the CMS portal can capture financial claims in line with RBIOS 2026.

**In Scope:**
- Text field for compensation amount.
- Optional field.
- Numeric values only.
- Must be ≤ ₹1 lakh.
- Character limit as per data dictionary.

**Out of Scope:**
- Any logic beyond numeric validation and threshold.
- No automatic calculation of compensation entitlement.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a numeric amount ≤ ₹1 lakh, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Above Limit** — Given the complainant enters an amount > ₹1 lakh, when they submit the form, then the system should reject the entry and display "Compensation for expenses, harassment, and mental anguish can be awarded only up to ₹1 lakh. Please enter an amount up to ₹1 lakh."
3. **Optional Field** — Given the complainant leaves the field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST68 — FR-G-009 – File a Complaint – Complaint Details – Upload Additional Documents

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally upload supporting documents so that the CMS portal can capture evidence relevant to my complaint.

**In Scope:**
- Upload field for documents.
- Optional field.
- Supported file types: JPG, PDF, DOCX; size per MB limits.

**Out of Scope:**
- Any logic beyond file type and size validation.

**Acceptance Criteria:**
1. **Valid Upload** — Given the complainant uploads a file of supported type within the allowed size, when they submit the form, then the system should accept the upload.
2. **Invalid Upload – Unsupported Type** — Given the complainant uploads a file type not supported, when they submit the form, then the system should reject the upload and display "Unsupported file type."
3. **Invalid Upload – Exceeds Size** — Given the complainant uploads a file larger than the allowed size, when they submit the form, then the system should reject the upload and display "File size exceeds limit."
4. **Optional Field** — Given the complainant does not upload any file, when they submit the form, then the system should accept the submission and proceed.

---

## UST69 — FR-G-009 – File a Complaint – Representative Authorization – Authorize Representative?

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to specify whether I authorize a representative so that the CMS portal can capture representative details if applicable.

**In Scope:**
- Checkbox field with Yes/No options.
- Mandatory response.
- If "Yes" is selected, additional fields (Name, Email) should be displayed.

**Out of Scope:**
- Any logic beyond Yes/No behaviour.
- No validation of representative's credentials beyond capturing the entered details.

**Acceptance Criteria:**
1. **Yes** — Given the complainant selects "Yes", when they submit the form, then the system should display Name and Email fields for representative details.
2. **No** — Given the complainant selects "No", when they submit the form, then the system should proceed without displaying representative fields.
3. **Mandatory Field Error** — Given the complainant does not select any option, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST70 — FR-G-009 – File a Complaint – Representative Authorization – Name of Representative

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the name of my authorized representative so that the CMS portal can capture representative details correctly.

**In Scope:**
- Text field for Name.
- Mandatory if Authorize Representative = Yes.
- Only alphabets allowed.
- Maximum length: 150 characters (per data dictionary).

**Out of Scope:**
- Any logic beyond validation of letters and length.
- No verification of representative identity against external systems.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid representative name using only letters within 150 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Numbers/Special Characters** — Given the complainant enters numbers or special characters in the name field, when they submit the form, then the system should reject the entry and display "Only letters are allowed."
3. **Mandatory Field Error** — Given the complainant leaves the name field blank when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST71 — FR-G-009 – File a Complaint – Representative Authorization – Email of Representative

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally enter the email address of my authorized representative so that the CMS portal can capture representative contact details correctly.

**In Scope:**
- Text field for Email.
- Optional field.
- Alphanumeric values allowed.
- Maximum length: 64 characters (per data dictionary).
- Must be a valid email format (e.g., name@domain.com).
- Visible only if Authorize Representative = Yes.

**Out of Scope:**
- Any logic beyond validation of format and length.
- No verification of email address against external systems.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid email address in correct format within 64 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Format** — Given the complainant enters an invalid email format, when they submit the form, then the system should reject the entry and display "Enter a valid email address."
3. **Optional Field** — Given the complainant leaves the email field blank, when they submit the form, then the system should accept the submission and proceed.

---

## UST72 — FR-G-009 – File a Complaint – Representative Authorization – Address

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the address of my authorized representative so that the CMS portal can capture representative details correctly.

**In Scope:**
- Text field for Address.
- Mandatory if Authorize Representative = Yes.
- Alphanumeric values allowed.
- Maximum length: 100 characters (per data dictionary).

**Out of Scope:**
- Any logic beyond validation of format and length.
- No verification of address against external systems.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid alphanumeric address within 100 characters, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Exceeds Limit** — Given the complainant enters more than 100 characters, when they submit the form, then the system should reject the entry and display "Address cannot exceed 100 characters."
3. **Mandatory Field Error** — Given the complainant leaves the address field blank when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST73 — FR-G-009 – File a Complaint – Representative Authorization – State

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the state of my authorized representative so that the CMS portal can capture representative location details correctly.

**In Scope:**
- Drop-down field with list of states.
- Mandatory if Authorize Representative = Yes.

**Out of Scope:**
- Any logic beyond capturing the selected state.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a state from the drop-down list, when they submit the form, then the system should accept the selection.
2. **Mandatory Field Error** — Given the complainant does not select any state when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST74 — FR-G-009 – File a Complaint – Representative Authorization – District

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the district of my authorized representative so that the CMS portal can capture representative location details correctly.

**In Scope:**
- Drop-down field with list of districts.
- Mandatory if Authorize Representative = Yes.

**Out of Scope:**
- Any logic beyond capturing the selected district.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a district from the drop-down list, when they submit the form, then the system should accept the selection.
2. **Mandatory Field Error** — Given the complainant does not select any district when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST75 — FR-G-009 – File a Complaint – Representative Authorization – City

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the city of my authorized representative so that the CMS portal can capture representative location details correctly.

**In Scope:**
- Drop-down field with list of cities.
- Mandatory if Authorize Representative = Yes.

**Out of Scope:**
- Any logic beyond capturing the selected city.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a city from the drop-down list, when they submit the form, then the system should accept the selection.
2. **Mandatory Field Error** — Given the complainant does not select any city when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST76 — FR-G-009 – File a Complaint – Representative Authorization – Pincode

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to select the pincode of my authorized representative so that the CMS portal can capture representative location details correctly.

**In Scope:**
- Drop-down field with list of pincodes.
- Mandatory if Authorize Representative = Yes.

**Out of Scope:**
- Any logic beyond capturing the selected pincode.
- No verification of pincode against postal records.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant selects a pincode from the drop-down list, when they submit the form, then the system should accept the selection.
2. **Mandatory Field Error** — Given the complainant does not select any pincode when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST77 — FR-G-009 – File a Complaint – Representative Authorization – Mobile

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to enter the mobile number of my authorized representative so that the CMS portal can capture representative contact details correctly.

**In Scope:**
- Text field for Mobile.
- Mandatory if Authorize Representative = Yes.
- Numeric values only.
- Must be exactly 10 digits.
- Character limit as per data dictionary.

**Out of Scope:**
- Any logic beyond numeric validation and length.
- No verification of mobile number against telecom records.

**Acceptance Criteria:**
1. **Valid Entry** — Given the complainant enters a valid 10-digit numeric mobile number, when they submit the form, then the system should accept the entry.
2. **Invalid Entry – Non-Numeric** — Given the complainant enters letters or special characters in the mobile field, when they submit the form, then the system should reject the entry and display "Only numbers are allowed."
3. **Invalid Entry – Length** — Given the complainant enters fewer or more than 10 digits, when they submit the form, then the system should reject the entry and display "Mobile number must be 10 digits."
4. **Mandatory Field Error** — Given the complainant leaves the mobile field blank when Authorize Representative = Yes, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST78 — FR-G-009 – File a Complaint – Representative Authorization – Upload Authorization

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to optionally upload an authorization document so that the CMS portal can capture proof of representative authorization.

**In Scope:**
- Upload field for authorization document.
- Optional field.
- Visible only if Authorize Representative = Yes.
- Supported file types and size limits as per CMS application/portal standards.

**Out of Scope:**
- Any logic beyond file type and size validation.
- No automatic verification of authorization content.

**Acceptance Criteria:**
1. **Valid Upload** — Given the complainant uploads a file of supported type within the allowed size, when they submit the form, then the system should accept the upload.
2. **Invalid Upload – Unsupported Type** — Given the complainant uploads a file type not supported, when they submit the form, then the system should reject the upload and display "Unsupported file type."
3. **Invalid Upload – Exceeds Size** — Given the complainant uploads a file larger than the allowed size, when they submit the form, then the system should reject the upload and display "File size exceeds limit."
4. **Optional Field** — Given the complainant does not upload any file, when they submit the form, then the system should accept the submission and proceed.

---

## UST79 — FR-G-009 – File a Complaint – Declaration

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to confirm my declaration so that the CMS portal can ensure compliance with RBIOS 2026.

**In Scope:**
- Checkbox field for declaration.
- Mandatory response.
- Declaration text includes: information furnished is true and correct; complaint filed within one year as per clause 10(2).

**Out of Scope:**
- Any logic beyond capturing the checkbox response.
- No automated verification of declaration content.

**Acceptance Criteria:**
1. **Valid Selection** — Given the complainant checks the declaration box, when they submit the form, then the system should accept the response and proceed.
2. **Mandatory Field Error** — Given the complainant does not check the declaration box, when they attempt to submit the form, then the system should reject the submission and display "Response is mandatory."

---

## UST80 — FR-G-011 – File a Complaint – Save Complaint as Draft

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to save my complaint as a draft so that I can pause filling the form and return later to complete and submit it, ensuring I don't lose partially entered information.

**In Scope:**
- Ability to save the complaint form at any stage as a draft.
- Storage of all entered details up to the point of saving.
- Draft retrieval when the complainant logs back into the portal.
- Option to edit and continue filling the form until submission.
- Clear indication that the complaint is in "Draft" status until formally submitted.

**Out of Scope:**
- Automatic submission of draft complaints.
- Sharing draft complaints with third parties.
- Offline saving outside the CMS portal.

**Acceptance Criteria:**
1. **Save Draft Successfully** — Given the complainant has partially filled the complaint form, when they select "Save as Draft", then the system should store all entered details and mark the complaint as "Draft."
2. **Retrieve Draft** — Given the complainant has previously saved a draft, when they log back into the portal and access "My Complaints", then the system should display the draft with the option to edit and continue.
3. **(Scenario numbered 5 in source) Draft Status Visibility** — Given the complainant views their list of complaints, when a draft exists, then the system should clearly show the complaint status as "Draft" until submission.

---

## UST81 — FR-G-012 – File a Complaint – Tooltips for Form Fields

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want tooltips to be provided for each field in the complaint form so that I can easily understand what information is required and fill the form correctly without confusion.

**In Scope:**
- Display of tooltips for all form fields defined in FR-G-009.
- Tooltip content should explain: purpose of the field; expected input format (e.g., numeric, alphanumeric, character limits); mandatory or optional requirement.
- Tooltips should be accessible (hover, click, or info icon).
- Consistency of tooltip style across the portal.

**Out of Scope:**
- Dynamic help beyond static tooltip text.
- Integration with external knowledge bases.
- Context-sensitive AI guidance (only static tooltips are required).

**Acceptance Criteria:**
1. **Tooltip Visibility** — Given the complainant views any field in the complaint form, when they hover over or click the tooltip icon, then the system should display the tooltip text explaining the field.
2. **Tooltip Content** — Given the complainant opens a tooltip for a field, when the tooltip is displayed, then the text should clearly explain the purpose, input format, and mandatory/optional requirement.
3. **Consistency** — Given the complainant navigates across different sections of the form, when tooltips are displayed, then the style, placement, and behavior of tooltips should remain consistent.
4. **Accessibility** — Given the complainant uses assistive technology (e.g., screen reader), when tooltips are accessed, then the tooltip content should be readable and accessible.

---

## UST82 — FR-G-013 – File a Complaint – Non-Maintainable Complaint Handling

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the system to handle non-maintainable complaints differently so that I am informed of closure without generating a complaint number, while still having a Case ID and access to the closure letter for reference.

**In Scope:**
- Identification of non-maintainable complaints based on clauses listed in Appendix 2 of the BRD.
- Generation of a Case ID (unique identifier) for tracking purposes.
- No generation of a Complaint Number for non-maintainable complaints.
- Ability for complainant to download the closure letter from the portal.
- Clear communication of closure status to the complainant.

**Out of Scope:**
- Manual verification of non-maintainable clauses outside the system.
- Appeal or escalation process (covered in separate requirements).
- Automatic redirection to other grievance redressal mechanisms.

**Acceptance Criteria:**
1. **Complaint Closed as Non-Maintainable** — Given the complainant submits a complaint that falls under non-maintainable clauses, when the system validates the complaint, then the system should close the complaint, generate a Case ID, and not generate a Complaint Number.
2. **Closure Letter Availability** — Given the complaint is closed as non-maintainable, when the complainant views the case details on the portal, then the system should provide a downloadable closure letter in PDF format.
3. **Case ID Visibility** — Given the complaint is closed as non-maintainable, when the complainant checks the case status, then the system should display the Case ID clearly for reference.
4. **Complaint Number Not Generated** — Given the complaint is closed as non-maintainable, when the complainant reviews the case details, then the system should not display any Complaint Number.

---

## UST83 — FR-G-014 – File a Complaint – Pop-up Display for Non-Maintainable Complaints (RBIO/CEPC Entity)

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want a pop-up notification to be displayed if my complaint is identified as non-maintainable so that I am immediately informed of the closure reason in line with RBIOS 2026, and I can understand why the complaint cannot proceed.

**In Scope:**
- Display of pop-up messages when a complaint is flagged as non-maintainable in the BRD.
- Use of templates defined in the BRD: RBIO Entity (Appendix 3 – Pop-up for Non-Maintainable Complaints); CEPC Entity (Appendix 4 – Pop-up for Non-Maintainable Complaints).
- Pop-up should clearly state the reason for non-maintainability based on the applicable clause.
- Pop-up should provide guidance on next steps (e.g., download closure letter, refer to other grievance channels).

**Out of Scope:**
- Manual intervention in determining non-maintainability.
- Escalation or appeal process (covered separately).
- Customization of pop-up text beyond the approved templates.

**Acceptance Criteria:**
1. **RBIO Entity – Non-Maintainable** — Given the complainant files a complaint against an RBIO entity that falls under non-maintainable clauses, when the system validates the complaint, then the system should display the RBIO Entity pop-up template (Appendix 3) explaining the closure reason.
2. **CEPC Entity – Non-Maintainable** — Given the complainant files a complaint against a CEPC entity that falls under non-maintainable clauses, when the system validates the complaint, then the system should display the CEPC Entity pop-up template (Appendix 4) explaining the closure reason.
3. **Pop-up Visibility** — Given the complaint is flagged as non-maintainable, when the pop-up is displayed, then the complainant should be able to view the message clearly before proceeding further.
4. **Closure Guidance** — Given the complainant receives the non-maintainable pop-up, when they read the message, then the pop-up should provide guidance on downloading the closure letter or referring to other channels.

---

## UST84 — FR-G-015 – File a Complaint – Pop-up for Non-Maintainable Complaints (FRC Entity)

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want a pop-up notification to be displayed if my complaint is identified as non-maintainable under FRC clauses so that I am immediately informed of the closure reason, in line with RBIOS 2026, and can understand why the complaint cannot proceed.

**In Scope:**
- Display of pop-up messages when a complaint is flagged as non-maintainable under FRC.
- Use of template defined in Appendix 8 of the BRD for FRC Entity.
- Pop-up should clearly state the reason for non-maintainability based on the applicable clause.
- Pop-up should provide guidance on next steps (e.g., download closure letter, refer to other grievance channels).

**Out of Scope:**
- Manual intervention in determining non-maintainability.
- Escalation or appeal process (covered separately).
- Dynamic customization of pop-up text beyond the approved FRC template.

**Acceptance Criteria:**
1. **FRC Entity – Non-Maintainable** — Given the complainant files a complaint against an FRC entity that falls under non-maintainable clauses, when the system validates the complaint, then the system should display the FRC Entity pop-up template (Appendix 8) explaining the closure reason.
2. **Pop-up Visibility** — Given the complaint is flagged as non-maintainable, when the pop-up is displayed, then the complainant should be able to view the message clearly before proceeding further.
3. **Closure Guidance** — Given the complainant receives the non-maintainable pop-up, when they read the message, then the pop-up should provide guidance on downloading the closure letter or referring to other channels.
4. **No Pop-up for Maintainable Complaints** — Given the complainant files a valid maintainable complaint, when the system validates the complaint, then no non-maintainable pop-up should be displayed.

---

## UST85 — FR-G-016 – File a Complaint – Preview Uploaded Documents

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to preview the documents I upload so that I can verify the correct file has been selected and ensure the content is legible before submitting my complaint.

**In Scope:**
- Ability to preview uploaded documents directly within the CMS portal.
- Supported file types for preview: PDF, JPG, PNG, DOCX (as per portal standards).
- Preview should open in a secure viewer (inline or modal pop-up).
- Option to close the preview and continue editing the complaint form.
- Option to remove and re-upload a document if incorrect.

**Out of Scope:**
- Editing the uploaded document within the portal.
- Preview of unsupported file types (e.g., ZIP, WMV).
- External sharing or downloading of documents beyond the complainant's own access.

**Acceptance Criteria:**
1. **Valid Preview** — Given the complainant uploads a supported file type (PDF, JPG, PNG, DOCX), when they click "Preview", then the system should display the document in a viewer for verification.
2. **Unsupported File Type** — Given the complainant uploads a file type not supported for preview (e.g., ZIP), when they click "Preview", then the system should display "Preview not available for this file type."
3. **Remove and Re-upload** — Given the complainant previews a document and realizes it is incorrect, when they select "Remove", then the system should delete the uploaded file and allow re-upload.
4. **Accessibility** — Given the complainant uses assistive technology, when they preview a document, then the system should ensure the preview viewer is accessible (screen reader compatible, zoom options).
5. **Optional Field** — Given the complainant does not upload any document, when they submit the complaint, then the system should accept the submission without requiring a preview.

---

## UST86 — FR-G-017 – File a Complaint – Speech-to-Text Functionality for Complaint Facts

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want speech-to-text functionality while entering the facts of my complaint so that I can dictate my grievance instead of typing, making the process faster, easier, and more accessible.

**In Scope:**
- Enable speech-to-text input for the Facts of Complaint field.
- Support for dictation in commonly used languages (as per portal standards).
- Real-time conversion of spoken words into text.
- Option to edit the transcribed text before submission.
- Clear UI controls: start, pause, stop dictation.

**Out of Scope:**
- Translation of speech into other languages.
- Voice biometrics or authentication.
- Storage of audio recordings (only text is retained).

**Acceptance Criteria:**
1. **Start Dictation** — Given the complainant is on the Facts of Complaint field, when they click the "Speech-to-Text" icon and start speaking, then the system should capture the audio and transcribe it into text in real-time.
2. **Pause/Stop Dictation** — Given the complainant is dictating, when they click "Pause" or "Stop", then the system should stop capturing audio and retain the transcribed text.
3. **Edit Transcribed Text** — Given the complainant has dictated their complaint facts, when they review the transcribed text, then they should be able to manually edit the text before submission.
4. **Unsupported Language** — Given the complainant speaks in a language not supported by the speech-to-text engine, when they attempt dictation, then the system should display "Language not supported for speech-to-text."

---

## UST87 — FR-G-020 – File a Complaint – Duplicate Complaint Identification

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the CMS portal to identify duplicate complaints based on key parameters so that the same grievance and prevents multiple complaints of the same issue and ensures efficiency in complaint handling.

**In Scope:**
- System check for duplicate complaints using the following combinations:
  - Complainant Mobile Number + Entity Name + Complaint Category
  - Complainant Email + Entity Name + Date of disputed transaction + Complaint Category
- Automated detection of duplicates at the time of submission.
- Display of a clear message if a duplicate is identified.
- Option for complainant to review and confirm whether they still wish to proceed.

**Out of Scope:**
- Manual verification of duplicates outside the system.
- Cross-checking against external databases beyond CMS.
- Automatic merging of duplicate complaints (handled separately).

**Acceptance Criteria:**
1. **Duplicate Complaint – Mobile Number Based** — Given the complainant submits a complaint with the same mobile number, entity name, and complaint category as an existing complaint, when they attempt to submit, then the system should flag as a duplicate complaint and display "Duplicate complaint detected based on mobile number."
2. **Duplicate Complaint – Email Based** — Given the complainant submits a complaint with the same email, name, entity name, date of disputed transaction, and complaint category as an existing complaint, when they attempt to submit, then the system should flag as a duplicate complaint and display "Duplicate complaint detected based on email."
3. **Unique Complaint** — Given the complainant submits a complaint with unique values for the parameters, when they attempt to submit, then the system should accept the complaint and generate a complaint number.
4. **Duplicate Complaint Confirmation** — Given the complainant reviews the message, when they choose to cancel submission or proceed (as per RBIOS policy), then the system should have the option to cancel submission or proceed as per RBIOS policy.
5. **Duplicate Complaint Tagging** — Given the complainant proceeds with submission of a duplicate complaint, when the new complaint created with the previously identified duplicate, then the new complaint should be tagged as a duplicate complaint to the previously identified complaint.

---

## UST88 — FR-G-021 – File a Complaint – Pop-up for Duplicate Complaint Detection

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want a pop-up message to be displayed if my complaint is detected as a duplicate so that I am immediately informed and can decide whether to cancel or continue submission.

**In Scope:**
- Display of a pop-up message when duplicate complaint parameters match (as defined in FR-G-020).
- Pop-up should clearly state that the complaint appears to be a duplicate.
- Pop-up should provide options: "Cancel Submission" (return to form) or "Proceed Anyway" (submit despite duplicate warning).
- Consistent design and behavior of pop-up across all complaint categories.

**Out of Scope:**
- Automatic merging of duplicate complaints.
- Manual verification of duplicates outside the system.
- Escalation handling (covered separately).

**Acceptance Criteria:**
1. **Duplicate Detected – Mobile Based** — Given the complainant submits a complaint with the same mobile number, name, and complaint category as an existing complaint, when they attempt to submit, then the system should display a pop-up message: "Duplicate complaint detected based on mobile number."
2. **Duplicate Detected – Email Based** — Given the complainant submits a complaint with the same email, name, entity name, date of disputed transaction, and complaint category as an existing complaint, when they attempt to submit, then the system should display a pop-up message: "Duplicate complaint detected based on email."
3. **Complainant Cancels Submission** — Given the complainant sees the duplicate complaint pop-up, when they select "Cancel Submission", then the system should return them to the form without registering the complaint.
4. **Complainant Proceeds Anyway** — Given the complainant sees the duplicate complaint pop-up, when they select "Proceed Anyway", then the system should register the complaint and generate a complaint number.
5. **No Duplicate** — Given the complainant submits a unique complaint, when they attempt to submit, then no duplicate pop-up should be displayed, and the complaint should be registered normally.

---

## UST89 — FR-G-022 – File a Complaint – Tabbed Navigation for Form and Preview

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want tabs to be displayed while filling and previewing the complaint form so that I can easily navigate between different sections, review my input, and ensure the form is complete before submission.

**In Scope:**
- Tabbed interface for complaint form sections: Regulated Entity Name, Complaint Eligibility Checks, Complainant Details, Regulated Entity Details, Complainant Details, Authorized Representative, Declaration & Submission.
- Tabs should be visible during both form filling and preview stages.
- Tabs should allow quick navigation between sections without losing entered data.
- Active tab should be highlighted for clarity.
- Tabs should maintain sequence but allow back and forward navigation for corrections.

**Out of Scope:**
- Dynamic rearrangement of tabs by complainant.
- Multi-form tabbing (only complaint form navigation is covered).
- Advanced features like bookmarking or saving tab positions (covered separately under draft functionality).

**Acceptance Criteria:**
1. **Tabs Visible During Form Filling** — Given the complainant is filling the complaint form, when they navigate through sections, then the system should display tabs for each section, with the active tab highlighted and the completed tab marked with a check.
2. **Tabs Visible During Preview** — Given the complainant has completed the form and is previewing the complaint, when they view the preview screen, then the system should display tabs for each section, allowing navigation to review sections.
3. **Back/Forward Navigation** — Given the complainant is on a later tab (e.g., Declaration), when they click on an earlier tab (e.g., Complaint Eligibility), then the system should navigate back without losing previously entered data.
4. **Forward Navigation** — Given the complainant is on an earlier tab (e.g., Complainant Details), when they click on a later tab (e.g., Declaration), then the system should allow navigation forward only if all mandatory fields in prior tabs are completed.
5. **Consistency** — Given the complainant navigates across switching tabs, then the tab design, labels, and behavior should remain consistent across form filling and preview.

---

## UST90 — FR-G-023 – File a Complaint – View Uploaded Documents in Complaint Preview

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to be able to view the documents I uploaded during form filling and previewing my complaint so that I can confirm the correct files are attached and ensure completeness before final submission.

**In Scope:**
- Display of uploaded documents in the complaint preview page.
- Supported file types for viewing: PDF, JPG, PNG, DOCX (as per portal standards).
- Inline viewer or download option for each uploaded document.
- Clear labeling of each document (e.g., "Authorization Letter," "Transaction Proof").
- Ability to open multiple documents one by one during preview.

**Out of Scope:**
- Editing uploaded documents within the preview.
- Real-time translation of document content.
- Sharing documents outside the complainant's own access.

**Acceptance Criteria:**
1. **View Uploaded Document** — Given the complainant has uploaded documents during form filling, when they open the complaint preview page, then the system should display a list of uploaded documents with options to view each.
2. **Supported File Type** — Given the complainant uploaded a supported file type (PDF, JPG, PNG, DOCX), when they click "View" in the preview page, then the system should open the document in a viewer or download option.
3. **Unsupported File Type** — Given the complainant uploaded a file type not supported for viewing, when they click "View" in the preview page, then the system should display "Preview not available for this file type" but allow download.
4. **No Documents Uploaded** — Given the complainant did not upload any documents, when they open the complaint preview page, then the system should display "No documents uploaded."
5. **Accessibility** — Given the complainant uses assistive technology, when they view documents in the preview page, then the system should ensure the viewer is accessible (screen reader compatible, zoom options).

---

## UST91 — FR-G-024 – File a Complaint – Complaint Number Generation

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the CMS portal to generate a unique complaint number once my complaint is submitted so that I can track, reference, and follow up on my grievance issue.

**In Scope:**
- Automatic generation of a unique complaint number upon successful submission.
- Complaint number should be displayed on the confirmation screen.
- Complaint number should be included in the acknowledgement receipt/confirmation email/SMS.
- Complaint number should be linked to the complainant's case record in the system.

**Out of Scope:**
- Complaint number generation for non-maintainable complaints (covered in FR-G-013).
- Manual assignment of complaint numbers.
- Editing or reissuing complaint numbers after generation.

**Current Logic for generating complaint number:**
It is a 15-digit alphanumeric number starting with the alphabet "N", followed by the financial year (e.g. 2026|27), followed by 3-digit region code (e.g. Chandigarh id is 007), and ending with a 5-digit sequential incremental number (e.g. 00001). The three digit region code is identified from the complainant's state and district.

**Acceptance Criteria:**
1. **Successful Submission** — Given the complainant fills all mandatory fields and submits the complaint, when the system validates and accepts the complaint, then the system should generate a unique complaint number and display it on the confirmation screen.
2. **Acknowledgement Receipt** — Given the complaint number is generated, when the system sends the acknowledgement (email/SMS/portal notification), then the complaint number should be included in the receipt.
3. **Complaint Tracking** — Given the complaint number is generated, when the complainant logs into the portal to check complaint status, then the system should allow tracking using the complaint number.
4. **Non-Maintainable Complaint** — Given the complaint is closed as non-maintainable, when the complainant attempts submission, then the system should generate only a Case ID (per FR-G-013) and not a complaint number.

---

## UST92 — FR-G-025 – File a Complaint – Acknowledgement Letter Format

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the CMS portal to generate an acknowledgement letter in the prescribed format for different entities so that I receive a clear, standardized confirmation of my submission, both on the portal and via email.

**In Scope:**
- Generation of acknowledgement letters in the formats defined in the BRD: RBIO Entity (Appendix 3); CEPC Entity (Appendix 4).
- Display of acknowledgement letter on the portal after complaint submission.
- Sharing the acknowledgement template via email (if complainant has provided an email address).
- Inclusion of complaint number, complainant details, entity details, submission date in the acknowledgement.
- Consistent formatting across the portal and email.

**Out of Scope:**
- Customization of acknowledgement letter by complainant.
- Editing acknowledgement letter after generation.

**Acceptance Criteria:**
1. **RBIO Entity Acknowledgement** — Given the complainant submits a complaint against an RBIO entity, when the complaint is successfully registered, then the system should generate an acknowledgement letter in the RBIO template (Appendix 3) and display it in the portal.
2. **CEPC Entity Acknowledgement** — Given the complainant submits a complaint against a CEPC entity, when the complaint is successfully registered, then the system should generate an acknowledgement letter in the CEPC template (Appendix 4) and display it on the portal.
3. **Email Delivery** — Given the complainant has provided a valid email address, when the acknowledgement letter is generated, then the system should send the same template in the body of the email to the complainant.
4. **No Email Provided** — Given the complainant has not provided an email address, when the acknowledgement letter is generated, then the system should only display the acknowledgement on the portal without sending an email.

---

## UST93 — FR-G-026 – File a Complaint – Auto-Save Complaint

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the CMS portal to provide auto-save options while filling the complaint form so that I don't lose my progress and can return later to complete and submit my complaint.

**In Scope:**
- System automatically saves progress at regular intervals (e.g., every 2 minutes) or upon navigation between tabs.
- Saved complaints should be stored in "Draft" status until submission.
- Complainant can resume from the last saved point.
- Clear indication of last saved timestamp.

**Out of Scope:**
- Offline saving outside the CMS portal.
- Customization of auto-save frequency by complainant.
- Saving complaints across multiple devices simultaneously (covered separately under synchronization requirements).

**Acceptance Criteria:**
1. **Auto-Save** — Given the complainant is filling the complaint form, when they continue typing or switch tabs, then the system should automatically save progress at defined intervals and display "Last saved at [timestamp]."
2. **Resume Draft** — Given the complainant has saved or auto-saved a complaint, when they log back into the portal, then the system should allow them to resume from the last saved point.

---

## UST94 — FR-G-027 – File a Complaint – SMS Acknowledgement after Complaint Submission

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to receive an SMS acknowledgement after submitting my complaint so that I have immediate confirmation and a reference to my complaint number without needing to log back into the portal.

**In Scope:**
- Automatic SMS sent to complainant's registered mobile number after successful complaint submission.
- SMS content should follow the template defined in Appendix 7 of the BRD.

**Out of Scope:**
- SMS delivery to international numbers not supported by the system.
- Customization of SMS content by complainant.
- Sending SMS for draft complaints (only after submission).

**Acceptance Criteria:**
1. **Maintainable Complaint – SMS Sent** — Given the complainant submits a valid maintainable complaint, when the complaint number is generated, then the system should send an SMS acknowledgement using the Appendix 7 template, including the complaint number.

---

## UST95 — FR-G-028 – File a Complaint – Digital Signature on Closure and Acknowledgement Letters

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the auto-generated closure and acknowledgement letters to carry a digital signature so that I can be assured of their authenticity and validity when referencing them for future communication or escalation.

**In Scope:**
- Application of digital signature on: Auto-closure letters (for non-maintainable complaints, per FR-G-013); Acknowledgement letters (for maintainable complaints, per FR-G-025).
- Signature should be applied automatically by the system at the time of letter generation.
- Signature must comply with applicable standards (e.g., PKI, e-sign guidelines).
- Signed letters should be available for download in PDF format.
- Signed letters should be sent via email (if provided) with the signature intact.

**Out of Scope:**
- Manual signing of letters.
- Use of handwritten or scanned signatures.
- Signature on SMS acknowledgements (covered in FR-G-027).

**Acceptance Criteria:**
1. **Acknowledgement Letter – Digital Signature** — Given the complainant submits a maintainable complaint, when the acknowledgement letter is generated, then the system should apply a digital signature and make the signed letter available for download and email.
2. **Closure Letter – Digital Signature** — Given the complainant submits a non-maintainable complaint, when the closure letter is generated, then the system should apply a digital signature and make the signed letter available for download and email.
3. **Verification of Signature** — Given the complainant downloads a signed letter, when they open the PDF in a standard viewer, then the digital signature should be verifiable and show "Valid Signature."
4. **Error Handling** — Given the system fails to apply a digital signature due to technical error, when the letter is generated, then the system should display "Digital signature application failed, please contact support."
5. **Consistency Across Channels** — Given the letter is digitally signed, when it is downloaded from the portal or received via email, then the signature should remain intact and verifiable in both formats.

---

## UST96 — File a Complaint – Download PDF Before Submission

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to download a PDF of my complaint details before submission so that I can review the information I have entered and ensure accuracy before filing.

**In Scope:**
- PDF generation option available before complaint submission.
- PDF must include all complaint details entered so far (fields filled in the form).
- Complaint Number will not be visible since it is generated only after submission.
- PDF must be formatted consistently with headers, footers, and clear sectioning.
- Audit logging of pre-submission PDF download.
- Watermark should be present in the downloaded pdf.

**Out of Scope:**
- Complaint number population before submission.
- Editing complaint details directly in the PDF.
- Exporting to formats other than PDF.

**Acceptance Criteria:**
1. **Download PDF – Before Submission** — Given the complainant has filled complaint details but not submitted, when they click "Download PDF", then the system should generate a PDF with all entered details except complaint number.
2. **PDF Content – Mandatory Fields** — Given the complainant downloads the PDF before submission, when the file is opened, then it should display all entered complaint details.
3. **Audit Logging** — Given the complainant downloads the PDF before submission, when the system processes the request, then the system should log the activity for audit purposes.

---

## UST97 — File a Complaint – Download PDF After Submission

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to download a PDF of my complaint details after submission so that I can keep a verifiable record including the complaint number.

**In Scope:**
- PDF generation option available after complaint submission.
- PDF must include all complaint details entered plus the Complaint Number generated by the system.
- PDF must be formatted consistently with headers, footers, and clear sectioning.
- PDF should be available for both open and closed complaints.
- Audit logging of post-submission PDF download.
- Watermark should be present in the downloaded pdf.

**Out of Scope:**
- Editing complaint details directly in the PDF.
- Exporting to formats other than PDF.

**Acceptance Criteria:**
1. **Download PDF – After Submission** — Given the complainant has submitted a complaint, when they click "Download PDF", then the system should generate a PDF with all complaint details including the complaint number.
2. **PDF Content – Complaint Number** — Given the complainant downloads the PDF after submission, when the file is opened, then it should display the complaint number along with all other complaint details.
3. **Audit Logging** — Given the complainant downloads the PDF after submission, when the system processes the request, then the system should log the activity for audit purposes.

---

## UST98 — FR-G-029 – Track a Complaint – Complaint Tracking via Mobile Number & OTP

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to track the status of my complaints using my mobile number and OTP so that I can securely view only valid complaints with a genuine complaint number.

**In Scope:**
- Secure login to complaint tracking using mobile + OTP verification.
- OTP auto-generation and delivery to the registered mobile number.
- Display of all complaints linked to the verified mobile number.
- Each complaint entry must show: Complaint Number, Entity Name, and Submission Date.
- Ability to click on any listed complaint to view its detailed status.
- Restriction: Only complaints with a Complaint Number (per FR-G-024) are trackable.
- Real-time reflection of complaint status updates (e.g., Submitted → Under Review → Closed).
- Error handling for invalid complaint numbers or incorrect OTP entries.
- System logging of tracking activity for audit purposes.

**Out of Scope:**
- Tracking complaints using Case ID (non-maintainable complaints).
- Tracking via email or other identifiers (covered separately under FR-G-030).
- Escalation or appeal process (covered separately in FR-G-035).
- Manual intervention for OTP generation or complaint retrieval.

**Acceptance Criteria:**
1. **Start Tracking – OTP Request** — Given the complainant clicks on "Track Your Complaint", when they enter their mobile number and request OTP, then the system should send an OTP to the registered mobile number.
2. **OTP Verification – Successful Login** — Given the complainant receives the OTP, when they enter it correctly, then the system should authenticate and display a list of complaints linked to the verified mobile number.
3. **Complaint List Display** — Given the complainant is authenticated, when the list of complaints is displayed, then each complaint should show complaint number, entity name, and submission date.
4. **View Complaint Status** — Given the complainant clicks on a listed complaint, when the system retrieves the record, then it should display the current status (e.g., Submitted, Under Review, Closed).
5. **Invalid OTP – Error Handling** — Given the complainant enters an incorrect OTP, when they attempt login, then the system should display "Invalid OTP, please try again."
6. **Non-Maintainable Complaint – Case ID** — Given the complainant has a Case ID but no complaint number, when they attempt to track, then the system should display "Tracking available only for complaints with a Complaint Number."

---

## UST99 — FR-G-030 – Track a Complaint – Seamless Access for Logged-in Users

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to track my complaints without being asked to login again if I am already logged into the portal so that I can access complaint status quickly and without redundant authentication.

**In Scope:**
- Direct access to complaint tracking for users already logged in through the authenticated account without requiring mobile number or OTP again.
- Retrieval of complaint list linked to the authenticated account (per FR-G-031).
- Display of complaint list in table view (per FR-G-031).
- Ability to click on any listed complaint to view its detailed status and timeline (per FR-G-033).
- Seamless navigation between filing a complaint and tracking complaints within the same session.
- Session management to ensure security while avoiding unnecessary re-authentication.

**Out of Scope:**
- Tracking complaints without login or OTP verification.
- Cross-account access if complaints must belong to the logged-in account.
- Escalation or appeal process (covered separately in FR-G-035).
- Manual bypass of authentication or session expiry rules.

**Acceptance Criteria:**
1. **Logged-in User – Direct Access** — Given the complainant is already logged into the CMS portal, when they click "Track Your Complaint", then the system should directly list all complaints linked to their account without asking for login or OTP again.
2. **Complaint List Display** — Given the complainant is logged in and clicks "Track Your Complaint", when the system retrieves the list, then it should show complaint number, entity name, and submission date.
3. **View Complaint Status** — Given the complainant clicks on a listed complaint, when the system retrieves the record, then it should display the detailed status and timeline.
4. **Session Expired** — Given the complainant is logged in but the session has expired, when they try "Track Your Complaint" again, then the system should prompt them to log in again.
5. **Consistency Across Features** — Given the complainant navigates between filing and tracking complaints, then the system should maintain a seamless experience without repeated authentication prompts.

---

## UST100 — FR-G-031 – Track a Complaint – Table View of Complaints

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to view all complaints I have raised in a table view so that I can easily scan, compare, and access details in an organized format.

**In Scope:**
- Display of all complaints raised by the complainant in a tabular format.
- Each row in the table represents one complaint.
- Ability to click on any row to view the detailed status and timeline of that complaint (per FR-G-033).
- Sorting and filtering options for easier navigation (e.g., by date, status).
- Handling of cases where no complaints exist (showing "No complaints found").

**Out of Scope:**
- Exporting table data to external formats (CSV, Excel, etc.).
- Editing or withdrawing complaints directly from the table (covered in filing requirements).
- Adding new complaints from the table view (covered separately in filing requirements).

**Acceptance Criteria:**
1. **Complaint List Display** — Given the complainant accesses the tracking interface, when they view all complaints, then the system should display the list of complaints in a table view.
2. **Click to View Status** — Given the complainant sees the table of complaints, when they click on a row, then the system should display the detailed status and timeline of that complaint (per FR-G-033).
3. **Sorting Complaints** — Given the complainant views the table, when they apply sort by date or status, then the system should display the results sorted accordingly.
4. **Filtering Complaints** — Given the complainant views the table, when they apply a filter (e.g., "Closed Complaints"), then the system should display only the filtered results.
5. **No Complaints Found** — Given the complainant has not raised any complaints, when they access the tracking interface, then the system should display "No complaints found."

---

## UST101 — FR-G-032 – Track a Complaint – Table Columns

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want the table view to display specific columns so that I can understand the key details of each complaint.

**In Scope:**
- Table must include the following columns:
  - Complaint Number
  - Date of Complaint Registration
  - Complaint Status
  - Regulated Entity Name
  - Closure Clause
  - Complaint Closure Date
- Each column should be populated with accurate data from the complaint record.
- Consistent formatting across all columns.

**Out of Scope:**
- Additional columns beyond those specified.
- Customization of columns by complainant.
- Display of internal system fields not relevant to complainant.

**Acceptance Criteria:**
1. **Table Columns Display** — Given the complainant views the table of complaints, when the system displays all the table, then each row should display the key details of each complaint.
2. **Data Accuracy** — Given the complainant views the table, when the system retrieves complaint records, then each column should display correct data for that complaint.
3. **Formatting Consistency** — Given multiple complaints are displayed, when the table is rendered, then all columns should follow consistent formatting (e.g., date format, text alignment).
4. **Accessibility of Columns** — Given the complainant uses assistive technology, when they navigate the table, then each column should be clearly labeled and accessible.

---

## UST102 — FR-G-033 – Track a Complaint – Complaint Status Timeline

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to view the status of any complaint so that I can understand its progress and current stage in the resolution process.

**In Scope:**
- Ability to click on any listed complaint in the table view (per FR-G-031/032) to view a timeline of the complaint's status.
- Display of the complaint's current stage in a timeline format showing all four stages.
- Timeline must include the following stages: Complaint Registered; Complaint with RBI; Complaint with Bank; Complaint Closed.
- Display of closure date when the complaint is marked as closed.
- Real-time updates to reflect changes in complaint status.
- Accessibility compliance for the timeline view (labels, screen reader compatibility).

**Out of Scope:**
- Additional statuses beyond the defined four stages.
- Editing or modifying complaint status by the complainant.
- Escalation or appeal process (covered separately in FR-G-035).
- Offline tracking of complaints outside the portal.

**Acceptance Criteria:**
1. **View Complaint Status – Timeline Display** — Given the complainant clicks on a listed complaint in the table view, when the system retrieves the record, then the system should display the complaint's status in a timeline format showing all four stages.
2. **Current Stage Highlight** — Given the complainant views the timeline, when the complaint is in progress, then the system should highlight the current stage (e.g., "Complaint with Bank") while showing past stages as completed.
3. **Closed Complaint – Closure Date** — Given the complainant views a closed complaint, when the timeline is displayed, then the system should show the "Complaint Closed" stage along with the closure date.
4. **Real-Time Updates** — Given the complainant is tracking a complaint, when the complaint status changes (e.g., from "Complaint with RBI" to "Complaint with Bank"), then the system should immediately update the timeline to reflect the new stage.
5. **Error Handling – Complaint Not Found** — Given the complainant clicks on a complaint that does not exist or has been deleted, when the system attempts to retrieve the record, then the system should display "Complaint not found, please check again."
6. **Accessibility – Inclusive Design** — Given the complainant uses assistive technology, when they view the timeline, then the system should ensure the timeline is accessible with clear labels and screen reader compatibility.

---

## UST103 — FR-G-033 – Track a Complaint – Complaint Timeline View

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to view the timeline of my complaint so that I can clearly understand its progress through each stage until closure.

**In Scope:**
- Display of complaint status in a timeline format.
- Timeline must include the following stages in sequence:
  1. Complaint Registered (when complaint is submitted)
  2. Complaint with RBI (when complaint is being processed by RBI)
  3. Complaint with Bank (when complaint is being processed by RE)
  4. Complaint Closed (when complaint is closed)
- Highlighting of the current stage when the complaint is marked as closed.
- Display of closure date when the complaint is marked as closed.
- Real-time updates to reflect changes in complaint status.
- Accessibility compliance for the timeline view (labels, screen reader compatibility).

**Out of Scope:**
- Additional statuses beyond the defined four stages.
- Editing or modifying complaint status by the complainant.
- Escalation or appeal process (covered in FR-G-035).
- Offline tracking of complaints outside the portal.

**Acceptance Criteria:**
1. **Timeline Display – All Stages** — Given the complainant clicks on a listed complaint in the table view, when the system retrieves the record, then the system should display the complaint's timeline with all four stages visible.
2. **Current Stage Highlight** — Given the complainant views the timeline, when the complaint is in progress, then the system should highlight the current stage (e.g., "Complaint with Bank").
3. **Closed Complaint – Closure Date** — Given the complainant views a closed complaint, when the timeline is displayed, then the system should show the "Complaint Closed" stage along with the closure date.
4. **Real-Time Updates** — Given the complainant is tracking a complaint, when the complaint status changes (e.g., from "Complaint with RBI" to "Complaint with Bank"), then the system should immediately update the timeline to reflect the new stage.

---

## UST104 — FR-G-034 – Track a Complaint – Complaint Details in PDF

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to download the details of my complaint in PDF format while tracking so that I can keep a verifiable record for reference or escalation.

**In Scope:**
- Generation of a PDF document containing full complaint details.
- PDF must include: Complaint Number, Date of Registration, Complaint Status, Regulated Entity Name, Closure Clause, and Closure Date (per FR-G-032).
- PDF should also include the complaint timeline (per FR-G-033).
- PDF download option available directly from the tracking interface.
- PDF must be formatted consistently with headers, footers, and clear sectioning.
- PDF should be available for both open and closed complaints.
- System should log PDF download activity for audit purposes.

**Out of Scope:**
- Exporting complaint details in formats other than PDF (e.g., Word, Excel).
- Manual editing of complaint details in the PDF.
- Automatic emailing of PDF (unless covered separately in communication requirements).
- Offline generation of complaint details outside the portal.

**Acceptance Criteria:**
1. **PDF Download – Valid Complaint** — Given the complainant is tracking a valid complaint, when they click "Download PDF", then the system should generate a PDF containing all complaint details and make it available for download.
2. **PDF Content – Mandatory Fields** — Given the complainant downloads the PDF, when the file is opened, then it should display Complaint Number, Date of Registration, Complaint Status, Regulated Entity Name, Closure Clause, and Closure Date.
3. **PDF Content – Timeline** — Given the complainant downloads the PDF, when the file is opened, then it should display the complaint timeline (Registered → RBI → Bank → Closed) with the current stage highlighted.
4. **Closed Complaint – Closure Date** — Given the complainant downloads a PDF for a closed complaint, when the file is opened, then it should display the "Complaint Closed" stage and closure date.

---

## UST105 — FR-G-035 – Track a Complaint – Withdraw Complaint

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to either withdraw my complaint while tracking so that I can manage the complaint lifecycle directly from the tracking interface.

**In Scope:**
- Option to withdraw a complaint from the tracking interface.
- Withdrawal should require confirmation before final submission.
- System should update complaint status immediately after withdrawal.
- Complaint can be withdrawn for all statuses except Complaint Closed, Sent to other Department, Sent to other Regulatory Bodies.

**Out of Scope:**
- Editing complaint details directly (covered in filing process).
- Filing new complaints (covered in FR-G-025).
- Offline withdrawal outside the portal.

**Acceptance Criteria:**
1. **Withdraw Complaint – Authentication** — Given the complainant is tracking an active complaint, when they click "Withdraw Complaint", then the system should prompt to input mandatory reasons for withdrawing complaint, optional upload of documents and upon confirmation, mark the complaint as withdrawn.
2. **Withdraw Complaint – Status Update** — Given the complainant withdraws a complaint, when the system processes the request, then the complaint status should update to "Withdrawn" and be reflected in the tracking table and timeline.
3. **Invalid Withdrawal – Error Handling** — Given the complainant attempts to withdraw a complaint in status closed/Sent to other department/Sent to other regulatory body, when they attempt to withdraw, then the system should display "Complaints cannot be withdrawn."

---

## UST106 — FR-G-035 – Track a Complaint – File Appeal

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to file an appeal while tracking so that I can manage the complaint lifecycle directly from the tracking interface.

**In Scope:**
- Option to file an appeal against a closed complaint from the tracking interface.
- Appeal filing should capture necessary details (grounds for appeal, supporting documents if applicable).
- System should update complaint status immediately after appeal submission.

**Out of Scope:**
- Editing complaint details directly (covered in filing process).
- Filing new complaints (covered in FR-G-025).
- Appeal processing workflow beyond submission (handled by backend complaint resolution process).
- Offline withdrawal or appeal outside the portal.

**Acceptance Criteria:**
1. **File Appeal – Closed Complaint** — Given the complainant is tracking a closed complaint, when they click "File Appeal", then the system should open an appeal submission form capturing grounds for appeal and supporting details.
2. **File Appeal – Status Update** — Given the complainant submits an appeal, when the system processes the request, then the complaint status should update to "Appeal Filed" and be reflected in the tracking table and timeline.
3. **Duplicate Appeal – Error Handling** — Given the complainant has already filed an appeal for this complaint, when they attempt to file another appeal, then the system should display "Appeal already filed for this complaint."

---

## UST107 — FR-G-036 – Withdraw a Complaint – Reason and Document Upload

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to provide a reason and upload supporting documents when withdrawing a complaint so that the withdrawal request is properly justified and traceable.

**In Scope:**
- Display of a mandatory text box for entering the reason for withdrawal.
- Ability to upload supporting documents (optional) along with the withdrawal request.
- Support for multiple file formats (e.g., PDF, DOC, JPG, PNG).
- Validation of file size and type before submission.
- Storage of withdrawal reason and uploaded documents in the complaint record.
- Linking of withdrawal reason and documents to audit logs for traceability.
- Integration with the withdrawal flow (per FR-G-035).
- Can be withdrawn for all statuses except Complaint Closed, Sent to other Department, Sent to other Regulatory Bodies.

**Out of Scope:**
- Editing complaint details beyond withdrawal reason.
- Uploading documents unrelated to withdrawal.
- Offline submission of withdrawal requests.
- Automatic approval or rejection of withdrawal requests (system only records and updates status).

**Acceptance Criteria:**
1. **Provide Reason – Mandatory Field** — Given the complainant chooses to withdraw a complaint, when the withdrawal form is displayed, then the system should require the complainant to enter a reason in the text box before submission.
2. **Upload Documents – Optional** — Given the complainant chooses to withdraw a complaint, when the withdrawal form is displayed, then the system should allow the complainant to upload supporting documents but not make it mandatory.
3. **File Validation – Error Handling** — Given the complainant uploads a document, when the file exceeds the size limit or is in an unsupported format, then the system should display "Invalid file type or size, please upload a valid document."
4. **Successful Withdrawal – Record Update** — Given the complainant provides a reason and submits the withdrawal request, when the system processes the request, then the complaint status should update to "Withdrawn" and the reason plus uploaded documents should be stored in the record.
5. **No Reason Provided – Error Handling** — Given the complainant attempts to withdraw without entering a reason, when they click submit, then the system should display "Reason for withdrawal is required."

---

## UST108 — FR-G-037 – Withdraw a Complaint – Notifications to Officers & RE

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want notifications to be sent to the processing officer (DO) and the NOFPNO of the concerned regulated entity when I immediately withdraw a complaint so that all responsible parties are immediately informed of the withdrawal.

**In Scope:**
- Automatic notification generation once a complaint is withdrawn.
- Notifications must be sent to:
  - The processing officer (DO) handling the complaint.
  - The Nodal Officer (NO)/Principal Nodal Officer (PNO) of the concerned regulated entity.
- Notifications should include: Complaint Number, Complainant Name, Date of Withdrawal, Reason for Withdrawal, and any uploaded documents (per FR-G-036).
- Notifications must be delivered via the system's standard communication channel (internal portal alert).

**Out of Scope:**
- Notifications to complainant.
- Notifications to departments or regulatory bodies other than the concerned RE.
- Manual notification sending by administrator.

**Acceptance Criteria:**
1. **Notification to Processing Officer** — Given the complainant withdraws a complaint, when the system updates the complaint status to "Withdrawn", then the system should send a notification to the processing officer with complaint details and withdrawal reason.
2. **Notification to NO/PNO** — Given the complainant withdraws a complaint, when the system updates the complaint status to "Withdrawn", then the system should send a notification to the NO/PNO of the concerned regulated entity with complaint details and withdrawal reason.
3. **Notification Content – Mandatory Details** — Given a withdrawal notification is sent, when the recipient opens the notification, then it should display Complaint Number, Complainant Name, Date of Withdrawal, Reason for Withdrawal, and any uploaded documents.

---

## UST109 — FR-G-039 – Submit Feedback – Feedback Questionnaire

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to provide structured feedback on my complaint experience so that the CMS portal can evaluate and improve the grievance redress process.

**In Scope:**
- Feedback form accessible via "Submit Feedback" option in the portal.
- Authentication using mobile number + OTP before feedback submission.
- Display of only closed complaints for feedback submission.
- Feedback questionnaire needs to include:
  1. Ease of filing and tracking the complaint (rating from 1-5) – mandatory question
  2. Grievance redressal within the rules/timeline (rating from 1-5) – mandatory question
  3. Overall satisfaction with the resolution provided (rating from 1-5) – mandatory question
  4. Any other feedback (limit 500 characters, no special characters) – optional question
  5. Source of information about the grievance redress mechanism from RBI, RE, Electronic/Media/Internet, Bank/, Word of Mouth, Others – limit 500 characters, no special characters – mandatory question
  6. Whether CMS portal helped increase awareness about grievance redress at RBI – optional question

**Out of Scope:**
- Feedback for complaints not yet closed.
- Anonymous feedback without submission.
- Feedback processing workflow (covered separately at RBI end).

**Acceptance Criteria:**
1. **Access Feedback Form – Authentication** — Given the complainant clicks "Submit Feedback", when they enter mobile number and OTP, then the system should authenticate and allow access to the feedback form.
2. **Select Complaint for Feedback** — Given the complainant is authenticated, when they click on a closed complaint, then the system should display the feedback questionnaire.
3. **Provide Feedback – Mandatory Questions** — Given the complainant is filling the feedback form, when they answer the mandatory questions (rating from 1-5), then the system should accept and store the response.
4. **Other Feedback – Character Limit** — Given the complainant enters additional feedback, when the text exceeds 500 characters or contains special characters, then the system should reject the entry and display "Input must be within 500 characters and cannot contain special characters."
5. **Source of Information – Character Limit** — Given the complainant selects "Other" or source of information, when they enter text exceeding 500 characters or containing special characters, then the system should display "Input must be within 500 characters and cannot contain special characters."
6. **Feedback Submission – Record Update** — Given the complainant completes the feedback form, then the system should store the feedback linked to the specific complaint record.

---

## UST110 — FR-G-039 – Submit Feedback – Visibility to RBI

**Epic:** Citizen Portal

**Value Proposition:**
As an RBI office user, I want feedback submitted by complainants to be visible to me for monitoring and identify service improvements.

**In Scope:**
- Feedback submitted by complainants should be visible to:
  - The supportive RBI Ombudsman/CEPC office that processed the complaint.
  - The CEPC-Bhopal cell for oversight of all offices.
- Feedback visibility restricted to the office(s) that handled the complaint (e.g., REBIO-Bhopal cell handled complaints of Bhopal only).
- Feedback must include all responses provided by the complainant.
- Feedback records should be accessible via the admin dashboard.
- Audit logging of feedback visibility and access.

**Out of Scope:**
- Feedback visibility to complainants after submission.
- Editing or deleting feedback by admin score.

**Acceptance Criteria:**
1. **Access Feedback – Authorization** — Given a complainant submitted feedback for a closed complaint, when the supportive RBI Ombudsman/CEPC admin user accesses the feedback, then the system should display it to REBIO-Bhopal admin user.
2. **Feedback Visibility – CEPC Admin** — Given a complainant submitted feedback for a closed complaint, when the system processes the complaint, then the feedback should be visible to CEPC admin users.
3. **Feedback Content – Accuracy** — Given RBI office and CEPC admins access the record, when they open the record, then the system should display all responses provided by the complainant.
4. **Access Control – Restricted Visibility** — Given feedback is submitted for a complaint handled by one RBI office, when another RBI office user attempts to access it, then the system should display "Feedback not visible for this office."
5. **Audit Logging** — Given the feedback is accessed by RBI office or CEPC admin, when the record is accessed, then the system should log the access details (user, time, timestamp, complaint ID).

---

## UST111 — FR-G-040 – File an Appeal – Eligibility After Complaint Closure

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to raise an appeal after my complaint is closed so that I can challenge the closure decision if I am dissatisfied.

**In Scope:**
- Appeal option available only for closed complaints under clauses 15(1)(a) and 15(1)(b).
- Authentication via mobile number + OTP before starting appeal.
- Display of only appealable complaints in the list.
- Eligibility check based on closure date and configurable timelines (per FR-G-043).

**Out of Scope:**
- Appeals for complaints that are pending or redirected to other departments/regulatory bodies.
- Anonymous appeals without authentication.

**Acceptance Criteria:**
1. **Appealable Complaint – Within 30 Days** — Given the complainant selects a closed complaint within 30 days of closure, when they click "File Appeal", then the system should allow them to proceed to the appeal form.
2. **Appealable Complaint – 31 to 60 Days** — Given the complainant selects a closed complaint between 31 and 60 days of closure, when they click "File Appeal", then the system should require them to provide a "Reason for Delay" before proceeding.
3. **Not Eligible – Beyond 60 Days** — Given the complainant selects a closed complaint beyond 60 days of closure, when they click "File Appeal", then the system should display "Not eligible for filing appeal."

---

## UST112 — File an Appeal – Display Speaking Order and Complaint Details

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to view the speaking order and complaint details when filing an appeal so that I have full context of the decision and can provide accurate grounds for my appeal.

**In Scope:**
- Display of the speaking order document associated with the closed complaint.
- Display of complaint details.
- Both speaking order and complaint details must be shown before the appeal form is filled.
- Read-only format (no editing of complaint details or speaking order).

**Out of Scope:**
- Editing or modifying complaint details or speaking order.
- Downloading speaking order.

**Acceptance Criteria:**
1. **Display Speaking Order – Closed Complaint** — Given the complainant selects a closed complaint for filing an appeal, when the system retrieves the record, then the system should display the speaking order associated with that complaint.
2. **Display Complaint Details – Context** — Given the complainant selects a closed complaint, when the system retrieves the record, then the system should display complaint details.
3. **Read-Only Format** — Given the complainant views the speaking order and complaint details, when they attempt to interact with the fields, then the system should restrict editing and allow only viewing.

---

## UST113 — FR-G-041 – File an Appeal – Appeal Details Submission

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant, I want to provide detailed information when filing an appeal so that the appellate authority has complete context to review my case.

**In Scope:**
- Appeal form fields:
  - Appellant Name (alphabets, max 100 chars, mandatory).
  - Appellant Comments (alphanumeric, max 500 chars, mandatory).
  - Appellant Mobile Number (numeric, 10 digits, mandatory).
  - Appellant Email ID (standard format, optional).
  - Date of Receipt of Communication of Award (date picker, optional).
  - File Upload (max 5 MB, allowed types: JPG, PDF, DOCX).
- Validation of mandatory fields, character limits, and formats.
- Storage of appeal details linked to the complaint record.
- Notification to Designated Officer (DO) of appellate authority upon submission.

**Out of Scope:**
- Editing appeal details after submission.
- Uploading unsupported file types or oversized files.
- Appeal processing workflow beyond submission.

**Acceptance Criteria:**
1. **Mandatory Fields – Validation** — Given the complainant fills the appeal form, when they omit a mandatory field (e.g., Appellant Name, Comments, Mobile Number, Date of Receipt), then the system should display "This field is required."
2. **Character Limit – Validation** — Given the complainant enters text exceeding the character limit, when they attempt to submit, then the system should display "Input must be within allowed character length."
3. **File Upload – Validation** — Given the complainant uploads a file, when the file exceeds 5 MB or is in an unsupported format, then the system should display "Invalid file type or size, please upload a valid document."
4. **Successful Submission – Notification** — Given the complainant completes the appeal form correctly, when they click submit, then the system should save the appeal details and send a notification to the DO of the appellate authority.

---

## UST114 — FR-G-042 – File an Appeal – Reason for Delay

**Epic:** Citizen Portal

**Value Proposition:**
As a complainant or regulated entity, I want to provide a reason for delay if I file an appeal between 31 and 60 days after closure so that the appellate authority can consider my justification.

**In Scope:**
- Free text field for "Reason for Delay."
- Mandatory field if appeal is filed between 31 and 60 days.
- Validation of character length (max 500 chars).
- Storage of reason along with appeal details.
- Appeals beyond 60 days (not eligible).

**Out of Scope:**
- Optional reason entry for appeals within 30 days.

**Acceptance Criteria:**
1. **Reason for Delay – Mandatory** — Given the complainant files an appeal between 31 and 60 days of closure, when they attempt to submit without entering a reason, then the system should display "Reason for delay is required."
2. **Reason for Delay – Validation** — Given the complainant enters a reason exceeding 500 characters, when they attempt to submit, then the system should display "Reason must be within 500 characters."
3. **Successful Submission – Record Update** — Given the complainant provides a valid reason for delay, when they submit the appeal, then the system should save the reason along with the appeal details.

---

## UST115 — FR-G-043 – Configurable Timelines

**Epic:** Citizen Portal

**Value Proposition:**
As an admin user, I want to configure timelines for filing complaints and appeals so that the system can enforce eligibility rules based on updated policies.

**In Scope:**
- Admin interface to configure timelines for:
  - Complaint filing window (default: 30 days, extended up to 60 days with reason).
  - Appeal filing window.
- System enforcement of the configured timelines during complaint and appeal submission.
- Validation of eligibility based on configured values.
- Audit logging of timeline changes by admin.

**Out of Scope:**
- Timeline configuration by complainants or REs.
- Automatic extension beyond configured limits.

**Acceptance Criteria:**
1. **Configure Timelines – Admin** — Given the admin user accesses the configuration screen, when they update the complaint or appeal filing window, then the system should save and enforce the new values.
2. **Audit Logging – Timeline Changes** — Given the admin updates the filing timeline, when the system processes the change, then the system should log the update with admin details and timestamp.

---

**End of current batch (UST2–UST115). Awaiting remaining screenshots to append further user stories.**
