package com.hrms.cms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class FileComplaintRequest {

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

    private BigDecimal amountInvolved;

    @Size(max = 200)
    private String bankBranch;

    @Size(max = 50)
    private String accountNumber;

    private Long categoryId;
    private String categoryName;

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
    private String bankComplaintReference;

    private String bankComplaintDate;

    private Boolean priorReComplaint;

    @PastOrPresent
    private LocalDate reComplaintDate;

    @Size(max = 200)
    private String reComplaintReference;

    private Boolean reRepliedAndDissatisfied;

    /** The date the RE's reply reached the complainant. Starts the post-reply filing window. */
    @PastOrPresent
    private LocalDate reReplyDate;

    // UST5/UST79 (DPDP Act 2023 consent). The citizen ticks this before filing.
    /**
     * UST5: the wizard's step-5 declarations. Only ONLINE filings carry them — a complaint arriving by
     * email or physical letter was never shown a checkbox, and rejecting those would drop legitimate
     * intake on the floor.
     */
    private Boolean declarationAccepted;

    /**
     * Server-side gate for the consent declaration (UST5).
     *
     * <p>The checkbox was enforced only by a {@code [disabled]} binding on the browser's Submit button,
     * so a direct POST to the filing endpoint registered a complaint carrying no consent at all — and
     * consent under the DPDP Act is exactly the kind of thing that cannot be assumed from the fact that
     * a request arrived.
     *
     * <p>Only online filing is gated. Email, physical-letter and walk-in intake never showed a checkbox,
     * so requiring it there would reject legitimate complaints that arrived through a channel with no
     * way to tick it. A missing filingType is treated as online: that is the citizen-facing default, and
     * defaulting the other way would let the gate be bypassed by omitting one field.
     */
    @AssertTrue(message = "Consent declaration is mandatory.")
    public boolean isDeclarationAcceptedWhenRequired() {
        boolean onlineFiling = filingType == null || filingType.isBlank()
                || "ONLINE".equalsIgnoreCase(filingType.trim());
        if (!onlineFiling) {
            return true;
        }
        return Boolean.TRUE.equals(declarationAccepted);
    }

    @Size(max = 20)
    private String complainantPincode;

    // ═══ Authorised Representative (D7) ═══
    private Boolean hasAuthRep;

    private Boolean throughAdvocate;

    @Size(max = 200)
    private String repName;

    @Size(max = 200)
    private String repEmail;

    @Size(max = 20)
    private String repPhone;

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

    // Mirrors the wizard, which makes these mandatory once a representative is authorised, rather than
    // accepting a representative with no contact route. Inert for intake channels that never ask:
    // hasAuthRep is null there, so the check passes without them having to send the block.
    @AssertTrue(message = "Representative name, mobile, email, address, state, district, city and pincode are required when a representative is authorised")
    public boolean isRepresentativeComplete() {
        if (!Boolean.TRUE.equals(hasAuthRep)) return true;
        return notBlank(repName) && notBlank(repPhone) && notBlank(repEmail) && notBlank(repAddress)
                && notBlank(repState) && notBlank(repDistrict) && notBlank(repCity) && notBlank(repPincode);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String entityState;
    private String entityDistrict;
    private String entityBranchName;

    // Raw wizard answers, persisted verbatim so the review screen can render every question the
    // citizen answered rather than only the fields the workflow happens to read.
    private Map<String, Object> eligibilityAnswers;
    private Map<String, Object> wizardFormData;

    // Set by the eligibility wizard when a screening question determines the complaint is
    // Non-Maintainable (FR-G-013). Presence of a clause code is what makes fileComplaint() issue a
    // Case ID instead of a Complaint Number.
    @Size(max = 100)
    private String nonMaintainableClauseCode;

    @Size(max = 30)
    private String nonMaintainableStatusCode;

    // Set only when the citizen was shown the duplicate-complaint warning and chose "Proceed Anyway"
    // (UST87 AC5), so the new complaint can be tagged against the one it duplicates.
    @Size(max = 50)
    private String duplicateOfComplaintNumber;
}
