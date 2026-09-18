package com.rbi.cms.search.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Multi-select facet filters. Each list goes straight to a {@code terms} clause, so an unbounded list
 * is the same class of exposure as an unbounded wildcard — hence the size caps.
 *
 * <p>The compact constructor null-normalizes to {@code List.of()}, so {@code @Size(max)} composes
 * without needing to tolerate null.
 */
public record FilterSearchRequest(
        @Size(max = 200, message = "must contain at most 200 values")
        List<String> states,

        @Size(max = 200, message = "must contain at most 200 values")
        List<String> districts,

        @Size(max = 200, message = "must contain at most 200 values")
        List<String> years,

        @Size(max = 200, message = "must contain at most 200 values")
        List<String> quarters,

        @Size(max = 200, message = "must contain at most 200 values")
        List<String> meetingTypes,

        @Size(max = 200, message = "must contain at most 200 values")
        List<String> documentTypes
) {
    public FilterSearchRequest {
        states = states != null ? List.copyOf(states) : List.of();
        districts = districts != null ? List.copyOf(districts) : List.of();
        years = years != null ? List.copyOf(years) : List.of();
        quarters = quarters != null ? List.copyOf(quarters) : List.of();
        meetingTypes = meetingTypes != null ? List.copyOf(meetingTypes) : List.of();
        documentTypes = documentTypes != null ? List.copyOf(documentTypes) : List.of();
    }
}
