package com.hrms.cms.config;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.entity.FormConfig;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.FormConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ComplaintCategoryRepository categoryRepo;
    private final BankRepository bankRepo;
    private final FormConfigRepository formConfigRepo;
    private final ComplaintRepository complaintRepo;

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
            seedComplaints();
        }
    }

    private void seedComplaints() {
        LocalDateTime now = LocalDateTime.now();

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260601-A1B2C3")
                .complainantName("Rajesh Kumar")
                .complainantEmail("rajesh.kumar@email.com")
                .complainantPhone("9876543210")
                .bankId(1L).categoryId(1L)
                .subject("ATM did not dispense cash but account debited Rs. 10,000")
                .description("Tried to withdraw Rs 10,000 from SBI ATM near MG Road. Machine showed error but amount was debited.")
                .status("pending").priority("high")
                .filingType("WEB_PORTAL")
                .createdAt(now.minusDays(2)).updatedAt(now.minusDays(2)).filedAt(now.minusDays(2))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260601-D4E5F6")
                .complainantName("Priya Sharma")
                .complainantEmail("priya.sharma@email.com")
                .complainantPhone("9123456789")
                .bankId(6L).categoryId(4L)
                .subject("UPI transaction failed but Rs. 5,000 debited from account")
                .description("Made UPI payment to merchant. Transaction shows failed but money debited. No refund received in 7 days.")
                .status("in_progress").priority("high")
                .filingType("WEB_PORTAL").assignedOfficer("Team Alpha")
                .createdAt(now.minusDays(5)).updatedAt(now.minusDays(1)).filedAt(now.minusDays(5))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260530-G7H8I9")
                .complainantName("Amit Patel")
                .complainantEmail("amit.patel@email.com")
                .complainantPhone("9988776655")
                .bankId(7L).categoryId(5L)
                .subject("Excessive interest charged on home loan EMI")
                .description("Bank increased interest rate without prior notice. EMI increased by Rs 3,500 per month.")
                .status("in_progress").priority("medium")
                .filingType("WEB_PORTAL").assignedOfficer("Team Beta")
                .createdAt(now.minusDays(8)).updatedAt(now.minusDays(3)).filedAt(now.minusDays(8))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260528-J1K2L3")
                .complainantName("Sunita Devi")
                .complainantEmail("sunita.d@email.com")
                .complainantPhone("9876501234")
                .bankId(2L).categoryId(2L)
                .subject("Unauthorized credit card transaction of Rs. 25,000")
                .description("Found unauthorized online transaction on credit card statement. Card was in my possession.")
                .status("escalated").priority("high")
                .filingType("EMAIL").assignedOfficer("Escalation Desk")
                .createdAt(now.minusDays(12)).updatedAt(now.minusDays(1)).filedAt(now.minusDays(12))
                .escalatedAt(now.minusDays(2))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260525-M4N5O6")
                .complainantName("Mohammed Farooq")
                .complainantEmail("farooq.m@email.com")
                .complainantPhone("9012345678")
                .bankId(3L).categoryId(6L)
                .subject("Fixed deposit maturity amount not credited")
                .description("FD matured on 15th May but amount still not credited to savings account. Multiple branch visits done.")
                .status("resolved").priority("medium")
                .filingType("WEB_PORTAL").assignedOfficer("Team Gamma")
                .createdAt(now.minusDays(20)).updatedAt(now.minusDays(5)).filedAt(now.minusDays(20))
                .resolvedAt(now.minusDays(5))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260520-P7Q8R9")
                .complainantName("Kavita Reddy")
                .complainantEmail("kavita.r@email.com")
                .complainantPhone("9876509876")
                .bankId(8L).categoryId(3L)
                .subject("Internet banking account locked without reason")
                .description("Online banking access blocked. Branch says technical issue. Unable to access account for 10 days.")
                .status("resolved").priority("low")
                .filingType("WEB_PORTAL").assignedOfficer("Team Alpha")
                .createdAt(now.minusDays(25)).updatedAt(now.minusDays(10)).filedAt(now.minusDays(25))
                .resolvedAt(now.minusDays(10))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260518-S1T2U3")
                .complainantName("Deepak Gupta")
                .complainantEmail("deepak.g@email.com")
                .complainantPhone("9011223344")
                .bankId(1L).categoryId(8L)
                .subject("NEFT transfer of Rs. 2,00,000 not credited to beneficiary")
                .description("Initiated NEFT transfer 5 days ago. Amount debited from my account but not received by beneficiary.")
                .status("closed").priority("high")
                .filingType("WEB_PORTAL").assignedOfficer("Team Beta")
                .createdAt(now.minusDays(30)).updatedAt(now.minusDays(15)).filedAt(now.minusDays(30))
                .resolvedAt(now.minusDays(18)).closedAt(now.minusDays(15))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260515-V4W5X6")
                .complainantName("Anita Mishra")
                .complainantEmail("anita.m@email.com")
                .complainantPhone("9988001122")
                .bankId(4L).categoryId(7L)
                .subject("Pension amount not credited for 3 months")
                .description("Monthly pension from PNB not received for April, May, and June. Visited branch multiple times.")
                .status("escalated").priority("high")
                .filingType("PHYSICAL_LETTER").assignedOfficer("Escalation Desk")
                .createdAt(now.minusDays(35)).updatedAt(now.minusDays(3)).filedAt(now.minusDays(35))
                .escalatedAt(now.minusDays(5))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260601-Y7Z8A9")
                .complainantName("Vikram Singh")
                .complainantEmail("vikram.s@email.com")
                .complainantPhone("9876512345")
                .bankId(9L).categoryId(4L)
                .subject("Unauthorized UPI debit of Rs. 15,000 from savings account")
                .description("Received SMS about Rs 15,000 UPI debit. Did not authorize any transaction. Suspect fraud.")
                .status("pending").priority("high")
                .filingType("WEB_PORTAL")
                .createdAt(now.minusHours(6)).updatedAt(now.minusHours(6)).filedAt(now.minusHours(6))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260531-B1C2D3")
                .complainantName("Lakshmi Iyer")
                .complainantEmail("lakshmi.i@email.com")
                .complainantPhone("9090909090")
                .bankId(5L).categoryId(9L)
                .subject("Insurance claim rejected without valid reason")
                .description("Health insurance claim for hospitalization rejected citing pre-existing condition. Policy is 5 years old.")
                .status("under_review").priority("medium")
                .filingType("WEB_PORTAL").assignedOfficer("Team Gamma")
                .createdAt(now.minusDays(4)).updatedAt(now.minusDays(1)).filedAt(now.minusDays(4))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260529-E4F5G6")
                .complainantName("Ravi Shankar")
                .complainantEmail("ravi.s@email.com")
                .complainantPhone("9876567890")
                .bankId(10L).categoryId(5L)
                .subject("Harassment by loan recovery agents")
                .description("Recovery agents visiting home at odd hours, using abusive language. Loan EMI was delayed by only 5 days.")
                .status("in_progress").priority("high")
                .filingType("WEB_PORTAL").assignedOfficer("Team Alpha")
                .createdAt(now.minusDays(7)).updatedAt(now.minusDays(2)).filedAt(now.minusDays(7))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260527-H7I8J9")
                .complainantName("Neha Kapoor")
                .complainantEmail("neha.k@email.com")
                .complainantPhone("9123498765")
                .bankId(6L).categoryId(10L)
                .subject("Bank refusing to close savings account")
                .description("Requested account closure 3 weeks ago. Bank keeps asking for additional documents and delaying.")
                .status("pending").priority("low")
                .filingType("WEB_PORTAL")
                .createdAt(now.minusDays(10)).updatedAt(now.minusDays(10)).filedAt(now.minusDays(10))
                .build());

        complaintRepo.save(Complaint.builder()
                .complaintNumber("CMS-20260522-K1L2M3")
                .complainantName("Suresh Babu")
                .complainantEmail("suresh.b@email.com")
                .complainantPhone("9000112233")
                .bankId(2L).categoryId(1L)
                .subject("Debit card cloned - multiple unauthorized transactions")
                .description("Found 8 unauthorized ATM withdrawals from different cities. Total loss Rs 80,000. Card was with me.")
                .status("closed").priority("high")
                .filingType("EMAIL").assignedOfficer("Team Beta")
                .createdAt(now.minusDays(40)).updatedAt(now.minusDays(20)).filedAt(now.minusDays(40))
                .resolvedAt(now.minusDays(25)).closedAt(now.minusDays(20))
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
}
