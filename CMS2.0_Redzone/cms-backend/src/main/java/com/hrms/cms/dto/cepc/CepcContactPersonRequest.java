package com.hrms.cms.dto.cepc;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * The Contact Entity tab's add/edit contact-person form.
 *
 * <p>As with {@code CreateNodalOfficerRecordRequest}, these annotations exist to give the form an inline
 * field-level 400 and are <em>not</em> the control — {@code CepcContactPersonService} re-checks the same
 * rules, so a caller that bypasses {@code @Valid} still cannot persist an unreachable contact.
 *
 * <p>Only the name is mandatory here, unlike the nodal-officer form where all five fields are. A nodal
 * officer must be formally reachable because the Scheme's clocks depend on the entity being written to; a
 * contact person is a note of who the officer spoke to, and refusing to record a name because no email was
 * offered would just push it back into free-text remarks. The service does insist on one of email or phone,
 * so a contact with no way to reach them cannot be saved either.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcContactPersonRequest {

    @NotBlank(message = "Contact person name is required")
    @Size(max = 200, message = "Name must not exceed 200 characters")
    private String name;

    @Size(max = 100, message = "Designation must not exceed 100 characters")
    private String designation;

    @Email(message = "Invalid email format")
    @Size(max = 200, message = "Email must not exceed 200 characters")
    private String email;

    /** Indian mobile pattern, as on the nodal-officer form: a number that cannot be dialled is not a contact. */
    @Pattern(regexp = "^$|^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    private String phone;

    @Size(max = 500, message = "Remarks must not exceed 500 characters")
    private String remarks;
}
