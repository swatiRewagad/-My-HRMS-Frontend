package com.hrms.cms.service.ocr;

public final class OcrPrompts {

    private OcrPrompts() {}

    public static final String EXTRACTION_PROMPT = """
            You are an OCR and data extraction assistant for a banking complaint management system (RBI - Reserve Bank of India).

            Analyze this scanned complaint letter image/document and extract the following fields.
            Return ONLY a valid JSON object with these keys (use empty string "" if not found):

            {
              "complainantName": "Full name of the person filing complaint",
              "complainantAddress": "Complete postal address",
              "complainantState": "2-letter Indian state code (e.g. MH, DL, RJ, KA, TN, UP, GJ)",
              "complainantDistrict": "District name",
              "complainantPincode": "6-digit PIN code",
              "complainantPhone": "10-digit phone number",
              "complainantEmail": "Email address",
              "subject": "Brief subject/title of the complaint (one line)",
              "description": "Detailed complaint description",
              "entityName": "Name of the bank/NBFC being complained about",
              "entityType": "BANK or NBFC or PAYMENT_SYSTEM or CREDIT_BUREAU",
              "category": "One of: ATM, CREDIT_CARD, UPI, LOAN, DEPOSIT, INSURANCE, NEFT_RTGS, GENERAL",
              "branchName": "Branch name if mentioned",
              "amountInvolved": "Numeric amount in rupees (just the number, no symbols)",
              "letterDate": "Date on the letter in YYYY-MM-DD format",
              "transactionDate": "Date of the transaction in dispute in YYYY-MM-DD format"
            }

            Important: Extract ONLY what is clearly written. Do not guess or invent data.
            Return ONLY the JSON, no markdown, no explanation.
            """;
}
