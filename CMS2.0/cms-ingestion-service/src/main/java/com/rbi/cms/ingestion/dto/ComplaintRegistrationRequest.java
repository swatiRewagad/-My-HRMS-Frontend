package com.rbi.cms.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Complaint registration request")
public class ComplaintRegistrationRequest {

    @NotBlank(message = "Channel is required")
    @Schema(description = "Channel: WEB_PORTAL, EMAIL, API_CLIENT", example = "WEB_PORTAL")
    private String channel;

    @NotBlank(message = "Category is required")
    @Schema(description = "Complaint category", example = "ATM")
    private String category;

    @NotBlank(message = "Complainant name is required")
    @Size(max = 200)
    @Schema(description = "Full name of the complainant")
    private String complainantName;

    @Email(message = "Invalid email format")
    @Schema(description = "Email address of the complainant")
    private String complainantEmail;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    @Schema(description = "Phone number of the complainant")
    private String complainantPhone;

    @NotBlank(message = "Entity name is required")
    @Size(max = 200)
    @Schema(description = "Name of bank/financial institution")
    private String entityName;

    @Schema(description = "Type of entity: BANK, NBFC, COOPERATIVE_BANK")
    private String entityType;

    @NotBlank(message = "Subject is required")
    @Size(max = 500)
    @Schema(description = "Brief subject of the complaint")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 4000)
    @Schema(description = "Detailed description of the complaint")
    private String description;

    @Schema(description = "Amount involved in the transaction")
    private Double amountInvolved;

    @Schema(description = "Date of the transaction in question")
    private LocalDate transactionDate;

    @Schema(description = "Jurisdiction code")
    private String jurisdictionCode;
}
