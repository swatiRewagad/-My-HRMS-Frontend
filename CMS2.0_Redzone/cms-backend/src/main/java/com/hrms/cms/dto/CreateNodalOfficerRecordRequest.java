package com.hrms.cms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

/**
 * UST572-575: the Dealing Officer's "add Nodal Officer record" form.
 *
 * <p>The bean-validation annotations here produce the field-level 400 message the form can render inline.
 * They are not the control: {@code NodalOfficerRecordService.addRecord} re-checks the same five mandatory
 * fields, so a caller that bypasses {@code @Valid} — a test, an internal call, a hand-rolled POST — still
 * cannot persist a record with a missing contact. Duplicated on purpose; the service check is the one that
 * must never be removed.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateNodalOfficerRecordRequest {

    private Long complaintId;

    @NotBlank(message = "Complaint number is required")
    @Size(max = 50, message = "Complaint number must not exceed 50 characters")
    private String complaintNumber;

    @NotBlank(message = "Entity name is required")
    @Size(max = 200, message = "Entity name must not exceed 200 characters")
    private String entityName;

    @NotBlank(message = "Nodal Officer contact name is required")
    @Size(max = 200, message = "Contact name must not exceed 200 characters")
    private String nodalOfficerName;

    @NotBlank(message = "Designation is required")
    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    @Size(max = 200, message = "Email must not exceed 200 characters")
    private String email;

    /**
     * Indian mobile pattern, matching FileComplaintRequest.complainantPhone. A NO contact that cannot be
     * dialled is the same defect as no contact at all, so the format is enforced rather than just the
     * presence.
     */
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    private String phone;

    /** Optional: PNO is free text and is not a mandatory part of the DO's form. */
    @Size(max = 200, message = "PNO name must not exceed 200 characters")
    private String pnoName;

    /** Optional: UST773 office scoping, in OFFICE_CODE_MASTER.officeName form. */
    @Size(max = 100, message = "Processing office must not exceed 100 characters")
    private String processingOffice;
}
