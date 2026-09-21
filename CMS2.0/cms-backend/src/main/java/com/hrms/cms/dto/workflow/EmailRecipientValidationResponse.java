package com.hrms.cms.dto.workflow;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * NON_NULL so {@code error} stays absent when every recipient passes, as the Map version did.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmailRecipientValidationResponse {

    private boolean valid;
    private List<String> validEmails;
    private List<String> invalidEmails;
    private String error;
}
