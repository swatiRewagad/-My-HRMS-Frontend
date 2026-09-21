package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of resetting the round-robin DEO assignment pointer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PointerResetResponse {

    private String message;
    private Integer pointer;
}
