package com.rbi.cms.ingestion.email.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailIngestRequest {

    @NotBlank(message = "Sender email is required")
    @Email(message = "Invalid sender email format")
    private String senderEmail;

    private String subject;

    private String body;

    private String messageId;

    private List<String> attachmentPaths;
}
