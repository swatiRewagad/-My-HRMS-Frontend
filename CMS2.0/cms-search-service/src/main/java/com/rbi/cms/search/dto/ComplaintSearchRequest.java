package com.rbi.cms.search.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * {@code @Valid} on the three nested components is load-bearing: without it Bean Validation does not
 * traverse into them and every constraint they declare is silently skipped.
 *
 * <p>Violation keys are the Java property path ({@code search.status}), not the snake_case wire name,
 * because {@code FieldError#getField} reports the Java path regardless of Jackson naming.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ComplaintSearchRequest(
        @Valid AdvancedSearchRequest advancedSearch,
        @Valid FilterSearchRequest filters,

        // Deliberately not @EnumValue: this carries a UI phrase resolved by applyStatusCode
        // ("All Complaints", "Complaint Assigned To Me", "New Complaints"), which is a different
        // vocabulary from ComplaintStatus. AdvancedSearchRequest.statusCode is the real status field.
        @Size(max = 60, message = "must be at most 60 characters")
        String statusCode,

        @Size(max = 60, message = "must be at most 60 characters")
        String kpiCards,

        @Size(max = 60, message = "must be at most 60 characters")
        String tabs,

        Boolean unread,
        Boolean withoutAttachments,

        @Valid SearchFieldsRequest search
) { }
