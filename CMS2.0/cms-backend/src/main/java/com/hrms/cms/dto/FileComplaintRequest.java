package com.hrms.cms.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDate;

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

    private Long bankId;

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
    private String bankComplaintReference;

    private String bankComplaintDate;

    private Boolean priorReComplaint;

    @PastOrPresent
    private LocalDate reComplaintDate;

    @Size(max = 200)
    private String reComplaintReference;

    private Boolean reRepliedAndDissatisfied;
}
