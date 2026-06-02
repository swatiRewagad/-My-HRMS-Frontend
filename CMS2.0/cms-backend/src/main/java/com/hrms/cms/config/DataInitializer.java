package com.hrms.cms.config;

import com.hrms.cms.entity.Bank;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.entity.FormConfig;
import com.hrms.cms.repository.BankRepository;
import com.hrms.cms.repository.ComplaintCategoryRepository;
import com.hrms.cms.repository.FormConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final ComplaintCategoryRepository categoryRepo;
    private final BankRepository bankRepo;
    private final FormConfigRepository formConfigRepo;

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
