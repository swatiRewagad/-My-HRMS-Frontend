package com.rbi.cms.mailintake.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ForceLinkRequest(
        @NotBlank @Size(max = 2000) String reason,
        @NotBlank @Size(max = 50) String targetComplaintId
) {
}
