package com.rbi.cms.search.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Every field is an optional filter, so no constraint here may reject null or {@code ""} — a cleared
 * form control sends the empty string and must mean "do not filter", not "400".
 *
 * <p>The {@code @Size} bounds mirror the corresponding {@code @Column(length)} on the complaint
 * entity. They cannot cost recall, because a value longer than the stored column can never match a
 * document, and they are what stops an authenticated caller spending cluster CPU on an unbounded
 * pattern.
 */
public record AdvancedSearchRequest(

        @Size(max = 50, message = "must be at most 50 characters")
        String complaintNumber,

        @Positive(message = "must be a positive id")
        Long id,

        // Length-bounded only, deliberately not @EnumValue(ComplaintStatus.class). ComplaintStatus is
        // not a complete inventory of what is actually stored: cms-backend writes statuses it has no
        // constant for, including "pending", the Complaint entity's own default. Validating against it
        // would reject values that legitimately exist in the index.
        @Size(max = 30, message = "must be at most 30 characters")
        String statusCode,

        @Size(max = 200, message = "must be at most 200 characters")
        String complainantName,

        // Anchored alternation with an empty branch: @Pattern, unlike @Size and @Email, rejects "",
        // so without the ^$ branch clearing this filter would be a 400.
        @Pattern(regexp = "^$|^[0-9+\\-\\s]{10,20}$",
                message = "must be 10 to 20 characters of digits, spaces, '+' or '-'")
        String complainantPhone,

        @Email(message = "must be a valid email address")
        @Size(max = 200, message = "must be at most 200 characters")
        String complainantEmail,

        // Same reason as statusCode: FilingType declares PORTAL/EMAIL/LETTER, but the values written to
        // the index are WEB_PORTAL and PHYSICAL_LETTER, so the enum would reject the real vocabulary.
        @Size(max = 50, message = "must be at most 50 characters")
        String filingType,

        @Size(max = 300, message = "must be at most 300 characters")
        String entityName,

        @Size(max = 500, message = "must be at most 500 characters")
        String subject,

        @Positive(message = "must be a positive id")
        Long categoryId,

        // A complaint cannot be filed in the future, so a future date is a client bug that would
        // otherwise return a well-formed empty page.
        @PastOrPresent(message = "must not be a future date")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd-MM-yyyy")
        LocalDate filedAt,

        @Size(max = 200, message = "must be at most 200 characters")
        String nodalOfficerName,

        @Email(message = "must be a valid email address")
        @Size(max = 200, message = "must be at most 200 characters")
        String fromEmailId
) { }
