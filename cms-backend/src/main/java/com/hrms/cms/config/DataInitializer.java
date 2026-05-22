package com.hrms.cms.config;

import com.hrms.cms.entity.*;
import com.hrms.cms.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ComplaintCategoryRepository categoryRepo;
    private final BankRepository bankRepo;
    private final FormConfigRepository formConfigRepo;
    private final ComplaintRepository complaintRepo;
    private final CategorizationRuleRepository ruleRepo;

    @Override
    public void run(String... args) {
        if (categoryRepo.count() == 0) {
            seedCategories();
        }
        if (bankRepo.count() == 0) {
            seedBanks();
        }
        if (formConfigRepo.count() == 0) {
            seedComplaintForm();
        }
        if (complaintRepo.count() == 0) {
            seedSampleComplaints();
        }
        if (ruleRepo.count() == 0) {
            seedCategorizationRules();
        }
    }

    private void seedSampleComplaints() {
        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260401-A1B2C3")
            .complainantName("Rajesh Kumar")
            .complainantEmail("rajesh@gmail.com")
            .complainantPhone("9876543210")
            .complainantAddress("Shivaji Nagar, Pune, Maharashtra")
            .bankBranch("SBI Pune Main Branch")
            .accountNumber("1234567890")
            .subject("ATM cash not dispensed but account debited Rs 5000")
            .description("On 15th April I tried to withdraw Rs 5000 from ATM at MG Road branch. Cash was not dispensed but amount was debited. Bank has not resolved in 30 days despite multiple follow-ups.")
            .reliefSought("RESOLVED: Bank was directed to credit Rs 5000 back to customer account with Rs 100/day compensation for delay as per RBI circular. Bank failed to reverse within T+5 days as mandated. Total compensation: Rs 5000 + Rs 3000 delay penalty.")
            .status("resolved")
            .priority("high")
            .filingType("physical")
            .build());

        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260312-D4E5F6")
            .complainantName("Sunita Devi")
            .complainantEmail("sunita@gmail.com")
            .complainantPhone("7654321098")
            .complainantAddress("Andheri West, Mumbai")
            .bankBranch("HDFC Mumbai Andheri Branch")
            .accountNumber("5678901234")
            .subject("Unauthorized credit card transaction of Rs 12000")
            .description("An unauthorized transaction of Rs 12000 on my credit card on 10th March. Reported to bank within 24 hours. No resolution in 45 days. Card was in my possession.")
            .reliefSought("RESOLVED: Bank failed to prove customer authorization. As per RBI zero-liability policy, customer reported within 3 days hence entitled to full reversal. Bank directed to reverse Rs 12000 + waive interest charged on disputed amount + Rs 2000 compensation for mental agony.")
            .status("resolved")
            .priority("high")
            .filingType("online")
            .build());

        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260225-G7H8I9")
            .complainantName("Amit Verma")
            .complainantEmail("amit@gmail.com")
            .complainantPhone("8123456789")
            .complainantAddress("Gomti Nagar, Lucknow")
            .bankBranch("ICICI Lucknow Branch")
            .accountNumber("3456789012")
            .subject("Home loan EMI interest rate increased without notice")
            .description("Bank unilaterally increased home loan interest from 8.5% to 11% without prior notice. Rs 25000 extra charged over 3 months. Written complaint ignored.")
            .reliefSought("")
            .status("pending")
            .priority("medium")
            .filingType("physical")
            .build());

        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260320-J1K2L3")
            .complainantName("Priya Sharma")
            .complainantEmail("priya@email.com")
            .complainantPhone("8765432109")
            .complainantAddress("Mumbai Fort")
            .bankBranch("HDFC Mumbai Fort Branch")
            .accountNumber("9876543210")
            .subject("Unauthorized UPI debit of Rs 15000 from savings account")
            .description("Rs 15000 debited via UPI without authorization on 20th March. Suspected fraud. Bank complaint filed on 22nd March but no refund or action taken in 30 days.")
            .reliefSought("RESOLVED: Investigation confirmed unauthorized transaction - no OTP was generated on customer's registered mobile. Bank directed to reverse Rs 15000 within 10 days + Rs 5000 compensation. Bank also instructed to strengthen UPI security protocols.")
            .status("resolved")
            .priority("high")
            .filingType("online")
            .build());

        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260410-M4N5O6")
            .complainantName("Vikram Singh")
            .complainantEmail("vikram@email.com")
            .complainantPhone("9012345678")
            .complainantAddress("Malviya Nagar, Jaipur")
            .bankBranch("Axis Bank Jaipur MI Road")
            .accountNumber("7890123456")
            .subject("Recovery agent harassment and abusive threatening calls")
            .description("Receiving abusive and threatening calls from recovery agents despite making regular EMI payments on time. Rs 8000 wrongly charged as penalty. Agents visited home and threatened family.")
            .reliefSought("")
            .status("pending")
            .priority("high")
            .filingType("physical")
            .build());

        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260118-P7Q8R9")
            .complainantName("Meena Patel")
            .complainantEmail("meena@email.com")
            .complainantPhone("7890123456")
            .complainantAddress("Navrangpura, Ahmedabad")
            .bankBranch("Bank of Baroda Ahmedabad")
            .accountNumber("4567890123")
            .subject("Fixed deposit premature closure without consent")
            .description("Bank closed my FD of Rs 5 lakh prematurely without my consent and adjusted against a loan I had already repaid. Interest loss of Rs 18000. No proper explanation given.")
            .reliefSought("REJECTED: Upon investigation, bank produced signed lien authorization form from account opening. Customer had given standing instruction for auto-adjustment. Complaint dismissed as bank acted within terms of agreement.")
            .status("rejected")
            .priority("medium")
            .filingType("online")
            .build());

        complaintRepo.save(Complaint.builder()
            .complaintNumber("CMS-20260505-S1T2U3")
            .complainantName("Deepak Joshi")
            .complainantEmail("deepak@email.com")
            .complainantPhone("8901234567")
            .complainantAddress("Koregaon Park, Pune")
            .bankBranch("Kotak Mahindra Pune Branch")
            .accountNumber("6789012345")
            .subject("Internet banking fund transfer failed but amount debited")
            .description("NEFT transfer of Rs 50000 failed on 5th May but amount debited from account. Beneficiary did not receive funds. Bank says it will take 7 working days but 15 days have passed.")
            .reliefSought("")
            .status("pending")
            .priority("medium")
            .filingType("online")
            .build());
    }

    private void seedComplaintForm() {
        String schema = """
        {
          "formTitle": "Raise a Complaint",
          "steps": [
            {
              "stepNumber": 1,
              "title": "Tell Us About You",
              "description": "Share some basic details about yourself to help us contact you regarding your complaint.",
              "helpText": "Why are we asking this?",
              "fields": [
                {
                  "key": "complainantCategory",
                  "label": "Complainant Category",
                  "type": "select",
                  "required": true,
                  "fullWidth": true,
                  "options": [
                    {"label": "Individual", "value": "individual"},
                    {"label": "Business", "value": "business"},
                    {"label": "Other", "value": "other"}
                  ]
                },
                {
                  "key": "name",
                  "label": "Name",
                  "type": "text",
                  "required": true,
                  "placeholder": "Enter your full name"
                },
                {
                  "key": "mobileNumber",
                  "label": "Mobile Number",
                  "type": "tel",
                  "required": true,
                  "placeholder": "Enter Mobile Number",
                  "prefix": "+91",
                  "hasVerify": true
                },
                {
                  "key": "email",
                  "label": "Email (Optional)",
                  "type": "email",
                  "required": false,
                  "placeholder": "Enter email address"
                },
                {
                  "key": "pincode",
                  "label": "Pincode",
                  "type": "text",
                  "required": true,
                  "placeholder": "Enter",
                  "maxLength": 6
                },
                {
                  "key": "district",
                  "label": "District",
                  "type": "select",
                  "required": true,
                  "optionsSource": "districts"
                },
                {
                  "key": "state",
                  "label": "State",
                  "type": "select",
                  "required": true,
                  "optionsSource": "states"
                },
                {
                  "key": "address",
                  "label": "Address",
                  "type": "textarea",
                  "required": true,
                  "fullWidth": true,
                  "placeholder": "Enter",
                  "rows": 3
                }
              ]
            },
            {
              "stepNumber": 2,
              "title": "Entity Details",
              "description": "Tell us about the bank or financial institution you want to complain against.",
              "helpText": "Why are we asking this?",
              "fields": [
                {
                  "key": "entityType",
                  "label": "Entity Type",
                  "type": "select",
                  "required": true,
                  "options": [
                    {"label": "Bank", "value": "bank"},
                    {"label": "NBFC", "value": "nbfc"},
                    {"label": "Payment System", "value": "payment"}
                  ]
                },
                {
                  "key": "entityName",
                  "label": "Entity Name",
                  "type": "select",
                  "required": true,
                  "optionsSource": "banks"
                },
                {
                  "key": "branch",
                  "label": "Branch (if applicable)",
                  "type": "text",
                  "required": false,
                  "placeholder": "Enter branch name"
                },
                {
                  "key": "accountNumber",
                  "label": "Account Number (if applicable)",
                  "type": "text",
                  "required": false,
                  "placeholder": "Enter account number"
                }
              ]
            },
            {
              "stepNumber": 3,
              "title": "Final Step: Share Your Complaint",
              "description": "Describe your complaint, actions taken, responses received, and include supporting documents.",
              "helpText": "Why are we asking this?",
              "fields": [
                {
                  "key": "complaintCategory",
                  "label": "Complaint Category",
                  "type": "select",
                  "required": true,
                  "optionsSource": "categories"
                },
                {
                  "key": "subCategory1",
                  "label": "Complaint Sub-Category 1",
                  "type": "select",
                  "required": false,
                  "optionsSource": "subCategories1",
                  "dependsOn": "complaintCategory"
                },
                {
                  "key": "subCategory2",
                  "label": "Complaint Sub-Category 2",
                  "type": "select",
                  "required": false,
                  "optionsSource": "subCategories2",
                  "dependsOn": "subCategory1"
                },
                {
                  "key": "facts",
                  "label": "Facts of the complaint",
                  "type": "textarea",
                  "required": true,
                  "fullWidth": true,
                  "placeholder": "Enter",
                  "rows": 4
                },
                {
                  "key": "isSubJudice",
                  "label": "Is your complaint sub-judice/under arbitration/already dealt with on merits by a Court/Tribunal/Arbitrator/Authority?",
                  "type": "radio",
                  "required": true,
                  "fullWidth": true,
                  "options": [
                    {"label": "Yes", "value": true},
                    {"label": "No", "value": false}
                  ]
                },
                {
                  "key": "throughAdvocate",
                  "label": "Is your complaint made through an advocate (unless you are yourself an advocate)?",
                  "type": "radio",
                  "required": true,
                  "fullWidth": true,
                  "options": [
                    {"label": "Yes", "value": true},
                    {"label": "No", "value": false}
                  ]
                },
                {
                  "key": "alreadyWithOmbudsman",
                  "label": "Has your complaint already been dealt with or is under process on the same ground with the Ombudsman?",
                  "type": "radio",
                  "required": true,
                  "fullWidth": true,
                  "options": [
                    {"label": "Yes", "value": true},
                    {"label": "No", "value": false}
                  ]
                },
                {
                  "key": "regulatedEntityStaff",
                  "label": "Is complaint from the staff of a regulated entity and involves employer employee relationship?",
                  "type": "radio",
                  "required": true,
                  "fullWidth": true,
                  "options": [
                    {"label": "Yes", "value": true},
                    {"label": "No", "value": false}
                  ]
                },
                {
                  "key": "attachments",
                  "label": "Attachments",
                  "type": "file",
                  "required": false,
                  "fullWidth": true,
                  "accept": ".pdf,.jpg,.png",
                  "multiple": true,
                  "maxSize": 5242880,
                  "hint": "Support formats: PDF, JPG, PNG. Maximum size: 5MB"
                },
                {
                  "key": "authorizeRepresentative",
                  "label": "If you want to authorize a representative to appear and make submission on your behalf before the Ombudsman, please select 'Yes' and furnish the details of the Authorized Representative",
                  "type": "radio",
                  "required": false,
                  "fullWidth": true,
                  "options": [
                    {"label": "Yes", "value": true},
                    {"label": "No", "value": false}
                  ]
                }
              ]
            }
          ],
          "preFilingModal": {
            "title": "Before Filing a Complaint",
            "subtitle": "SELECT WHICHEVER IS APPLICABLE",
            "options": [
              {
                "id": "not_contacted",
                "number": 1,
                "title": "I have not contacted my bank or financial institution",
                "description": "Select this option if you have not filed a complaint with your bank or financial institution yet."
              },
              {
                "id": "already_filed",
                "number": 2,
                "title": "I have filed a complaint with bank or financial institution",
                "description": "Select this option if you are not satisfied with the reply provided by your bank or financial institute or if they have not provided a response to your complaint in 30 days.",
                "conditionalFields": [
                  {
                    "key": "bankComplaintDate",
                    "label": "When did you first file the complaint with your bank or financial institution?",
                    "type": "date",
                    "required": true
                  },
                  {
                    "key": "receivedReply",
                    "label": "Have you received any reply from your bank or financial institution?",
                    "type": "radio",
                    "required": true,
                    "options": [
                      {"label": "Yes", "value": "yes"},
                      {"label": "No", "value": "no"}
                    ]
                  }
                ]
              }
            ]
          }
        }
        """;

        formConfigRepo.save(FormConfig.builder()
                .formKey("raise-complaint")
                .formName("Raise a Complaint")
                .schemaJson(schema)
                .active(true)
                .version("1.0")
                .build());
    }

    private void seedCategories() {
        categoryRepo.save(ComplaintCategory.builder().name("ATM / Debit Card").description("Issues related to ATM transactions and debit cards").sortOrder(1).build());
        categoryRepo.save(ComplaintCategory.builder().name("Credit Card").description("Issues related to credit card transactions and billing").sortOrder(2).build());
        categoryRepo.save(ComplaintCategory.builder().name("Internet Banking").description("Issues with online banking services").sortOrder(3).build());
        categoryRepo.save(ComplaintCategory.builder().name("Mobile Banking / UPI").description("Issues with mobile banking apps and UPI transactions").sortOrder(4).build());
        categoryRepo.save(ComplaintCategory.builder().name("Loan / Advances").description("Issues with loan processing, EMI, interest rates").sortOrder(5).build());
        categoryRepo.save(ComplaintCategory.builder().name("Deposit Accounts").description("Issues with savings, current, or fixed deposit accounts").sortOrder(6).build());
        categoryRepo.save(ComplaintCategory.builder().name("Pension").description("Pension related grievances").sortOrder(7).build());
        categoryRepo.save(ComplaintCategory.builder().name("Remittance / Transfer").description("Issues with fund transfers, NEFT, RTGS, IMPS").sortOrder(8).build());
        categoryRepo.save(ComplaintCategory.builder().name("Insurance").description("Insurance related complaints").sortOrder(9).build());
        categoryRepo.save(ComplaintCategory.builder().name("Others").description("Other banking related complaints").sortOrder(10).build());
    }

    private void seedBanks() {
        bankRepo.save(Bank.builder().name("State Bank of India").code("SBI").type("public").build());
        bankRepo.save(Bank.builder().name("Punjab National Bank").code("PNB").type("public").build());
        bankRepo.save(Bank.builder().name("Bank of Baroda").code("BOB").type("public").build());
        bankRepo.save(Bank.builder().name("Canara Bank").code("CANARA").type("public").build());
        bankRepo.save(Bank.builder().name("Union Bank of India").code("UNION").type("public").build());
        bankRepo.save(Bank.builder().name("HDFC Bank").code("HDFC").type("private").build());
        bankRepo.save(Bank.builder().name("ICICI Bank").code("ICICI").type("private").build());
        bankRepo.save(Bank.builder().name("Axis Bank").code("AXIS").type("private").build());
        bankRepo.save(Bank.builder().name("Kotak Mahindra Bank").code("KOTAK").type("private").build());
        bankRepo.save(Bank.builder().name("IndusInd Bank").code("INDUSIND").type("private").build());
        bankRepo.save(Bank.builder().name("Yes Bank").code("YES").type("private").build());
        bankRepo.save(Bank.builder().name("IDBI Bank").code("IDBI").type("public").build());
    }

    private void seedCategorizationRules() {
        // ATM / Debit Card rules
        ruleRepo.save(CategorizationRule.builder().ruleName("ATM Cash Not Dispensed").keywords("ATM,cash not dispensed,amount debited,money not received,ATM failed,cash stuck").categoryId(1L).priority("high").source("all").description("Complaints about ATM transactions where cash was not dispensed but account was debited").ruleOrder(1).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Debit Card Fraud").keywords("debit card,unauthorized,skimming,cloned,stolen card,debit fraud,card misuse").categoryId(1L).priority("high").source("all").description("Fraudulent debit card transactions").ruleOrder(2).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("ATM Card Blocked").keywords("ATM card blocked,card swallowed,card retained,card stuck in ATM,blocked debit card").categoryId(1L).priority("medium").source("all").description("ATM card blocked or retained by machine").ruleOrder(3).build());

        // Credit Card rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Unauthorized Credit Card Transaction").keywords("credit card,unauthorized transaction,fraud charge,unknown transaction,credit card misuse").categoryId(2L).priority("high").source("all").description("Unauthorized charges on credit card").ruleOrder(4).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Credit Card Billing Issue").keywords("credit card bill,wrong charge,excess interest,annual fee,late fee,billing dispute,overcharged").categoryId(2L).priority("medium").source("all").description("Billing disputes on credit card").ruleOrder(5).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Credit Card Closure Issue").keywords("card closure,cancel credit card,not cancelled,still charging,closed but charged").categoryId(2L).priority("medium").source("all").description("Issues with credit card closure requests").ruleOrder(6).build());

        // Internet Banking rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Net Banking Fraud").keywords("internet banking,netbanking,online fraud,hacked account,phishing,online banking unauthorized").categoryId(3L).priority("high").source("all").description("Unauthorized internet banking transactions").ruleOrder(7).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Net Banking Access Issue").keywords("login failed,cannot access,password locked,netbanking blocked,OTP not received").categoryId(3L).priority("low").source("all").description("Internet banking access problems").ruleOrder(8).build());

        // Mobile Banking / UPI rules
        ruleRepo.save(CategorizationRule.builder().ruleName("UPI Fraud").keywords("UPI,unauthorized UPI,UPI fraud,wrong UPI,money sent wrong person,UPI debit without consent").categoryId(4L).priority("high").source("all").description("Unauthorized UPI transactions").ruleOrder(9).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("UPI Transaction Failed").keywords("UPI failed,UPI pending,money deducted UPI,transaction failed but debited,UPI not credited").categoryId(4L).priority("high").source("all").description("UPI transaction failures with money deducted").ruleOrder(10).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Mobile Banking Issue").keywords("mobile banking,app not working,mobile app,PhonePe,Google Pay,Paytm,BHIM").categoryId(4L).priority("medium").source("all").description("Mobile banking application issues").ruleOrder(11).build());

        // Loan / Advances rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Loan Interest Rate Issue").keywords("loan interest,rate increased,excess interest,wrong interest rate,EMI increased,interest overcharged").categoryId(5L).priority("high").source("all").description("Loan interest rate disputes").ruleOrder(12).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Recovery Agent Harassment").keywords("recovery agent,harassment,threatening calls,abusive,recovery harassment,debt collector,threaten").categoryId(5L).priority("high").source("all").description("Complaints about recovery agent misconduct").ruleOrder(13).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Loan Processing Issue").keywords("loan rejected,loan delay,loan not sanctioned,processing fee,loan application,pre-closure,foreclosure charge").categoryId(5L).priority("medium").source("all").description("Loan processing and sanction issues").ruleOrder(14).build());

        // Deposit Account rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Account Debit Without Consent").keywords("unauthorized debit,money deducted,account debited,without consent,unknown deduction,savings debited").categoryId(6L).priority("high").source("all").description("Unauthorized debits from deposit accounts").ruleOrder(15).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Fixed Deposit Issue").keywords("fixed deposit,FD,premature closure,FD interest,maturity not credited,FD broken").categoryId(6L).priority("medium").source("all").description("Fixed deposit related complaints").ruleOrder(16).build());
        ruleRepo.save(CategorizationRule.builder().ruleName("Account Closure Issue").keywords("account closure,close account,not closing,minimum balance,maintenance charge").categoryId(6L).priority("low").source("all").description("Account closure and maintenance issues").ruleOrder(17).build());

        // Pension rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Pension Not Credited").keywords("pension,pension not received,pension delayed,pension stopped,retirement,pension credit").categoryId(7L).priority("high").source("all").description("Pension credit failures").ruleOrder(18).build());

        // Remittance / Transfer rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Fund Transfer Failed").keywords("NEFT,RTGS,IMPS,fund transfer,transfer failed,money not received,remittance,beneficiary not credited").categoryId(8L).priority("high").source("all").description("Failed fund transfers").ruleOrder(19).build());

        // Insurance rules
        ruleRepo.save(CategorizationRule.builder().ruleName("Insurance Mis-selling").keywords("insurance,mis-sold,policy without consent,premium deducted,insurance fraud,ULIP").categoryId(9L).priority("high").source("all").description("Insurance mis-selling complaints").ruleOrder(20).build());
    }
}
