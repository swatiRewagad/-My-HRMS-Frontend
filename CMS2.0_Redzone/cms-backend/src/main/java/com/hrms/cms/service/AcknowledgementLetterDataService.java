package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.OfficeCodeMasterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the {{placeholder}} -> value map for the citizen acknowledgement letter from a
 * Complaint. One map serves both the English and Hindi template bodies for the same
 * complaint — only the surrounding wording differs between languages, not the data.
 */
@Service
@RequiredArgsConstructor
public class AcknowledgementLetterDataService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private final OfficeCodeMasterRepository officeCodeRepository;
    private final ComplaintNumberGeneratorService complaintNumberGeneratorService;

    @Value("${cms.letters.default-rbio-office:New Delhi-I}")
    private String defaultRbioOffice;

    @Value("${cms.letters.default-cepc-office:New Delhi}")
    private String defaultCepcOffice;

    @Transactional(readOnly = true)
    public Map<String, String> buildVariables(Complaint c) {
        Map<String, String> vars = new HashMap<>();
        vars.put("complaintRefNo", nvl(c.getComplaintNumber()));
        vars.put("currentFY", formatFinancialYear(complaintNumberGeneratorService.computeFinancialYear(LocalDate.now())));
        vars.put("currentDate", LocalDate.now().format(DATE));
        vars.put("complainantName", nvl(c.getComplainantName()));
        vars.put("addressLine1", nvl(c.getComplainantAddress()));
        vars.put("stateDistrict", joinStateDistrict(c.getComplainantState(), c.getComplainantDistrict()));
        vars.put("pincode", nvl(c.getComplainantPincode()));
        vars.put("mobileNumber", nvl(c.getComplainantPhone()));
        vars.put("emailId", nvl(c.getComplainantEmail()));
        vars.put("entityName", nvl(c.getEntityName()));
        vars.put("modeOfReceipt", modeOfReceiptLabel(c.getFilingType()));
        vars.put("complaintCategory", nvl(c.getCategoryName()));
        vars.put("officeName", resolveOfficeName(c, "BO", defaultRbioOffice));
        vars.put("cepcName", resolveOfficeName(c, "CEPC", defaultCepcOffice));
        return vars;
    }

    /** "202526" (as stored for complaint numbering) -> "2025-26" (as printed on the letter). */
    private String formatFinancialYear(String raw) {
        if (raw == null || raw.length() != 6) return nvl(raw);
        return raw.substring(0, 4) + "-" + raw.substring(4);
    }

    private String joinStateDistrict(String state, String district) {
        boolean hasState = state != null && !state.isBlank();
        boolean hasDistrict = district != null && !district.isBlank();
        if (hasState && hasDistrict) return district + ", " + state;
        if (hasState) return state;
        if (hasDistrict) return district;
        return "-";
    }

    private String modeOfReceiptLabel(String filingType) {
        if (filingType == null) return "-";
        return switch (filingType) {
            case "WEB_PORTAL" -> "CMS Portal";
            case "EMAIL" -> "Email";
            case "PHYSICAL_LETTER" -> "Physical Letter";
            case "CPGRAMS" -> "CPGRAMS";
            default -> filingType;
        };
    }

    /**
     * The office/CEPC name printed on the letterhead. {@code rbioOfficeCode} is the only
     * office key the complaint carries, so the same column is tried against whichever
     * office type the caller asks for; a complaint with no office code yet (not routed to
     * an office, or CEPC which does not allocate one) falls back to the configured default
     * rather than leaving the letterhead blank.
     */
    private String resolveOfficeName(Complaint c, String officeType, String fallback) {
        String code = c.getRbioOfficeCode();
        if (code == null || code.isBlank()) return fallback;
        return officeCodeRepository.findByOfficeCodeAndIsActiveTrue(code)
                .filter(office -> officeType.equals(office.getOfficeType()))
                .map(office -> office.getOfficeName())
                .orElse(fallback);
    }

    private String nvl(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
