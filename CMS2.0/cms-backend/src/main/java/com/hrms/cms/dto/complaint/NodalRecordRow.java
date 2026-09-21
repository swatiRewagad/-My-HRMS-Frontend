package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * A row of the nodal officer worklist, returned both by the list endpoint and by the forward-to-RE
 * endpoint so the screen does not have to refetch after acting.
 *
 * <p>Deliberately not {@code @JsonInclude(NON_NULL)}: a record is keyed on a complaint number with no
 * foreign key behind it, so a missing complaint has to degrade to a sparse row rather than drop out of
 * the worklist. The screen binds to every field, so the keys must survive as nulls.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodalRecordRow {

    private Long id;
    private String recordNumber;
    private String complaintNumber;
    private String status;
    private String assignedTo;
    /** Age of the complaint in the officer's hands — what the worklist's breach badge reads. */
    private Long slaDays;
    private String receiptDate;

    private String subject;
    private String complainant;
    private String mobile;
    private String email;
    private String bankName;
    private String bankCategory;
    private String branchCategory;
    private String branchName;
    private String pincode;
    private String city;
    private String district;
    private String state;
    private String designatedOffice;
    private String processingOffice;

    private String moduleName;
    private String country;
    private String atmComplaint;

    private String noName;
    private String noMobile;
    private String noEmail;
    private String noDesignation;
    private String pnoName;
    private String pnoMobile;
    private String pnoEmail;

    /**
     * The saved assessment, so reopening a record shows what was actually stored. The date inputs on the
     * screen are native {@code <input type="date">}, which only accepts ISO, so these stay unformatted
     * while {@link #notice131ComplyDate} is display-only and follows the rest of the screen.
     */
    private String advisoryComplianceDate;
    private BigDecimal disputeAmount;
    private BigDecimal compensationLoss;
    private BigDecimal compensationMental;
    private String awardImplementationDate;
    private String awardAcceptanceDate;
    private String notice131ComplyDate;
    private String forwardedToReAt;
}
