package com.rbi.cms.portal.dto.request;

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
@Schema(description = "Complaint submission from web portal")
public class ComplaintSubmitRequestDto {

    @NotBlank(message = "Category is required")
    @Schema(description = "Complaint category: ATM, UPI, NEFT_RTGS, LOAN, CREDIT_CARD, etc.", example = "ATM")
    private String category;

    @NotBlank(message = "Complainant name is required")
    @Size(max = 200)
    private String complainantName;

    @Email(message = "Invalid email")
    private String complainantEmail;

    @Pattern(regexp = "^[0-9]{10}$", message = "Phone must be 10 digits")
    private String complainantPhone;

    @NotBlank(message = "Bank/institution name is required")
    @Size(max = 200)
    private String entityName;

    @Schema(description = "BANK, NBFC, COOPERATIVE_BANK")
    private String entityType;

    @NotBlank(message = "Subject is required")
    @Size(max = 500)
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 4000)
    private String description;

    @Schema(description = "Amount in dispute")
    private Double amountInvolved;

    @Schema(description = "Date of disputed transaction")
    private LocalDate transactionDate;

    @Schema(description = "Jurisdiction/state code")
    private String jurisdictionCode;
}
