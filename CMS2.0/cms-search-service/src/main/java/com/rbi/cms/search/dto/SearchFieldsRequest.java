package com.rbi.cms.search.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.rbi.cms.common.enums.Priority;
import com.rbi.cms.search.validation.EnumValue;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Per-column inline search from the complaint grid. Every field is optional and every one of these
 * clears to {@code ""}, so no constraint here may reject null or blank.
 *
 * <p>The text fields feed wildcard queries whose expansion cost scales with pattern length, which is
 * why the {@code @Size} bounds matter beyond tidiness.
 */
public record SearchFieldsRequest(

        @Size(max = 20, message = "must be at most 20 characters")
        String complaintId,

        @Size(max = 50, message = "must be at most 50 characters")
        String complaintNumber,

        @Size(max = 200, message = "must be at most 200 characters")
        String assignedTo,

        @Size(max = 20, message = "must be at most 20 characters")
        String slaBreachIn,

        // Length-bounded only. FilingType declares PORTAL/EMAIL/LETTER while the index holds WEB_PORTAL
        // and PHYSICAL_LETTER, so validating against the enum would reject the real vocabulary.
        @Size(max = 50, message = "must be at most 50 characters")
        String mode,

        @Size(max = 200, message = "must be at most 200 characters")
        String complainantName,

        // Length-bounded only: ComplaintStatus omits statuses cms-backend actually stores, "pending"
        // (the Complaint default) among them, so the enum is not a usable allow-list for this column.
        @Size(max = 30, message = "must be at most 30 characters")
        String status,

        @Size(max = 300, message = "must be at most 300 characters")
        String entityName,

        @Size(max = 200, message = "must be at most 200 characters")
        String complaintCategory,

        // Declared as LocalDate, not String: the query layer already calls toString() on these and
        // compares against the index's ISO-8601 dates, so a raw string in the house dd-MM-yyyy format
        // produced a term query that could never match.
        @PastOrPresent(message = "must not be a future date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate createdDate,

        @PastOrPresent(message = "must not be a future date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate lastUpdatedDate,

        @EnumValue(Priority.class)
        @Size(max = 20, message = "must be at most 20 characters")
        String priority,

        @Size(max = 500, message = "must be at most 500 characters")
        String subject
) { }
