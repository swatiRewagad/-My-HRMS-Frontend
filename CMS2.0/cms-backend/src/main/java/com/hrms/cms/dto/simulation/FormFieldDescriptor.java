package com.hrms.cms.dto.simulation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** One field the complainant has to fill in on the emailed complaint form. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FormFieldDescriptor {

    /** Matches the property name on the form-reply request body. */
    private String key;
    private String label;
    /** An HTML input type, or {@code textarea} / {@code select}. */
    private String type;
    private boolean required;
}
