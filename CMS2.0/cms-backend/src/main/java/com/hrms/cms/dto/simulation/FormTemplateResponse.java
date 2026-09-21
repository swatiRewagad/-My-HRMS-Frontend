package com.hrms.cms.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** The complaint form a complainant is asked to complete by email. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormTemplateResponse {

    private String complaintNumber;
    private String complainantEmail;

    @Builder.Default
    private List<FormFieldDescriptor> fields = List.of();
}
