package com.hrms.cms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FileComplaintRequest {

    /**
     * UST17: the reference is echoed back to the Regulated Entity, so it is restricted to the characters
     * REs actually issue. Blank is allowed because the field itself is optional.
     */
    static final String RE_REFERENCE_PATTERN = "^[A-Za-z0-9/_-]*$";
    static final String RE_REFERENCE_MESSAGE =
            "Reference number may contain only letters, digits, hyphen, underscore and slash";

    @NotBlank(message = "Complainant name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String complainantName;

    @Email(message = "Invalid email format")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String complainantEmail;

    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    private String complainantPhone;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String complainantAddress;

    @Size(max = 100)
    private String complainantState;

    @Size(max = 100)
    private String complainantDistrict;

    private Long bankId;

    private Long regulatedEntityId;

    @Size(max = 300)
    private String entityName;

    @Size(max = 100)
    private String entityType;

    @Size(max = 200)
    private String bankBranch;

    @Size(max = 50)
    private String accountNumber;

    private Long categoryId;

    @NotBlank(message = "Subject is required")
    @Size(max = 500, message = "Subject must not exceed 500 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    private String description;

    @Size(max = 2000, message = "Relief sought must not exceed 2000 characters")
    private String reliefSought;

    @Pattern(regexp = "^(low|medium|high|critical)?$", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Priority must be low, medium, high, or critical")
    private String priority;

    @Pattern(regexp = "^(ONLINE|PHYSICAL_LETTER|EMAIL|WALK_IN)?$", message = "Invalid filing type")
    private String filingType;

    @Size(max = 100)
    @Pattern(regexp = RE_REFERENCE_PATTERN, message = RE_REFERENCE_MESSAGE)
    private String bankComplaintReference;

    private String bankComplaintDate;

    private Boolean priorReComplaint;

    @PastOrPresent
    private LocalDate reComplaintDate;

    @Size(max = 100, message = "Reference number must not exceed 100 characters")
    @Pattern(regexp = RE_REFERENCE_PATTERN, message = RE_REFERENCE_MESSAGE)
    private String reComplaintReference;

    private Boolean reRepliedAndDissatisfied;

    /** The date the RE's reply reached the complainant. Starts the post-reply filing window. */
    @PastOrPresent
    private LocalDate reReplyDate;

    /**
     * UST5: the wizard's step-5 declarations. Only ONLINE filings carry them — a complaint arriving by
     * email or physical letter was never shown a checkbox, and rejecting those would drop legitimate
     * intake on the floor.
     */
    private Boolean declarationAccepted;

    @AssertTrue(message = "You must accept the declaration and data processing consent to file a complaint")
    public boolean isDeclarationAcceptedWhenRequired() {
        if (filingType != null && !"ONLINE".equalsIgnoreCase(filingType)) return true;
        return Boolean.TRUE.equals(declarationAccepted);
    }

    // ═══ Authorised Representative (D7) ═══
    // The wizard already enforces these as mandatory once hasAuthRep is "yes", so the server mirrors
    // that rather than accepting a representative with no contact route.
    private Boolean hasAuthRep;

    private Boolean throughAdvocate;

    @Size(max = 200, message = "Representative name must not exceed 200 characters")
    private String repName;

    @Pattern(regexp = "^([6-9]\\d{9})?$", message = "Invalid representative mobile number")
    private String repPhone;

    @Email(message = "Invalid representative email format")
    @Size(max = 254, message = "Representative email must not exceed 254 characters")
    private String repEmail;

    @Size(max = 500, message = "Representative address must not exceed 500 characters")
    private String repAddress;

    @Size(max = 100)
    private String repState;

    @Size(max = 100)
    private String repDistrict;

    @Size(max = 100)
    private String repCity;

    @Pattern(regexp = "^(\\d{6})?$", message = "Representative pincode must be 6 digits")
    private String repPincode;

    @AssertTrue(message = "Representative name, mobile, email, address, state, district, city and pincode are required when a representative is authorised")
    public boolean isRepresentativeComplete() {
        if (!Boolean.TRUE.equals(hasAuthRep)) return true;
        return notBlank(repName) && notBlank(repPhone) && notBlank(repEmail) && notBlank(repAddress)
                && notBlank(repState) && notBlank(repDistrict) && notBlank(repCity) && notBlank(repPincode);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
