package com.rbi.cms.mailintake.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A reason is mandatory — this is a maker-checker request, not a direct action, and the checker
 *  (and the audit trail) needs to know why the maker is asking. */
public record ReplayRequest(
        @NotBlank @Size(max = 2000) String reason
) {
}
