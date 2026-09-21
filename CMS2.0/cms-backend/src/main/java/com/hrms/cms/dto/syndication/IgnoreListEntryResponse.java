package com.hrms.cms.dto.syndication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A sender pattern that ingestion auto-closes on sight.
 *
 * <p>The Map this replaced also carried {@code pattern}/{@code type} as aliases of
 * {@code emailPattern}/{@code patternType}, plus a {@code status} string that duplicated
 * {@link #isActive}. No caller read any of the three, so they are not reproduced here.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IgnoreListEntryResponse {

    private Integer id;
    private String emailPattern;
    /** EXACT, DOMAIN or CONTAINS. */
    private String patternType;
    private String reason;
    private String addedBy;
    private Boolean isActive;
    private String createdAt;
}
