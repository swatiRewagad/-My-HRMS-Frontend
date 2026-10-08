package com.hrms.cms.dto.cepc;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDate;

/**
 * The Legal Case panel's "Update Details" form.
 *
 * <p>Nothing here is mandatory — unlike {@code CepcContactPersonRequest}, which insists on a name and a
 * reachable channel, this is a free-form dossier an officer builds up over time. A blank field simply
 * means that detail is not known yet, not that the save should be refused.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CepcLegalCaseRequest {

    @Size(max = 100, message = "Case No./WP No & Year must not exceed 100 characters")
    @Pattern(regexp = "^[A-Za-z0-9/_\\- ]*$",
            message = "Case No./WP No & Year may contain only letters, digits, spaces, and / - _")
    private String caseNumber;

    @Size(max = 150, message = "Name of the Court must not exceed 150 characters")
    private String courtName;

    @Size(max = 255, message = "Parties of Case must not exceed 255 characters")
    private String partiesOfCase;

    @Size(max = 100, message = "Region of Legal Team Involved must not exceed 100 characters")
    private String regionOfLegalTeam;

    private Boolean rbiFirstRespondent;

    private Boolean appearanceRequired;

    @Size(max = 2000, message = "Brief particular must not exceed 2000 characters")
    private String subjectMatter;

    @Size(max = 100, message = "Name of the Advocate must not exceed 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s]*$", message = "Name of the Advocate may contain only letters and spaces")
    private String advocateName;

    @Size(max = 100, message = "Assistant Legal Advisor must not exceed 100 characters")
    private String assistantLegalAdvisor;

    /** ISO {@code yyyy-MM-dd}, as posted by the form's {@code <input type="date">} — Jackson's default
     * {@code LocalDate} binding needs no extra format annotation for that shape. */
    @FutureOrPresent(message = "Date of next hearing must be today or a later date")
    private LocalDate nextHearingDate;

    @Size(max = 2000, message = "Remarks/Present status must not exceed 2000 characters")
    private String presentStatus;

    @Size(max = 2000, message = "Action taken so far must not exceed 2000 characters")
    private String actionTakenSoFar;

    @Size(max = 2000, message = "Action to be taken must not exceed 2000 characters")
    private String actionToBeTaken;

    @Size(max = 2000, message = "Details of Monetary claim must not exceed 2000 characters")
    private String monetaryClaimDetails;
}
