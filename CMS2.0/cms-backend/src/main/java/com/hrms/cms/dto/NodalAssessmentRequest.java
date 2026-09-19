package com.hrms.cms.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The RBIO officer's assessment of a nodal officer record, submitted when the record is forwarded to
 * the regulated entity. Which of the optional fields are required depends on {@link #status}, so that
 * rule lives in NodalOfficerRecordService rather than in annotations here.
 *
 * <p>Digits mirrors the precision=15 scale=2 columns the values land in: without it an oversized
 * amount fails on the insert as a data-truncation error rather than as a readable 400.</p>
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class NodalAssessmentRequest {

    @NotBlank(message = "Status code is required")
    private String status;

    private LocalDate advisoryComplianceDate;

    @DecimalMin(value = "0", message = "Dispute amount must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Dispute amount is larger than this field can store")
    private BigDecimal disputeAmount;

    @DecimalMin(value = "0", message = "Compensation for loss must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Compensation for loss is larger than this field can store")
    private BigDecimal compensationLoss;

    @DecimalMin(value = "0", message = "Compensation for mental harassment must not be negative")
    @Digits(integer = 13, fraction = 2, message = "Compensation for mental harassment is larger than this field can store")
    private BigDecimal compensationMental;

    private LocalDate awardImplementationDate;

    private LocalDate awardAcceptanceDate;
}
