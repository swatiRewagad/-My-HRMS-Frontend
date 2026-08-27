package com.rbi.cms.mailintake.admin.dto;

import jakarta.validation.constraints.Size;

/** note is optional on approve (the request's own reason usually says enough), but the service
 *  layer requires it on reject — enforced there, not with @NotBlank here, since the same DTO
 *  backs both endpoints. */
public record DecisionRequest(
        @Size(max = 2000) String note
) {
}
