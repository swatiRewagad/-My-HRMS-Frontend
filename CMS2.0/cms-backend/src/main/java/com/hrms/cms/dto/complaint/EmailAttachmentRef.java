package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code size} is always blank: a simulated email stores only the attachment URL, and the byte count
 * would need a fetch the list view does not do.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachmentRef {

    private String name;
    private String size;
}
