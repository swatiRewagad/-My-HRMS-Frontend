package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A complaint as it appears in a "this complainant has been here before" or "similar cases" list.
 *
 * <p>One shape serves both the complainant-history lookup and the similar-case search, including the
 * keyword fallback used when the AI matcher is unavailable — so a caller cannot tell from the row shape
 * which path produced it.
 *
 * <p>{@link #filedDate} was called {@code date} while this was a Map. Nothing read {@code date}, while
 * the screens that render these rows all read {@code filedDate} and were getting undefined — so the key
 * is renamed to the one already in use, which also matches what the detail payload calls it.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PastComplaintSummary {

    /** The human-facing complaint number, not the database id. */
    private String complaintId;
    private String subject;
    private String status;
    private String complainantName;
    /** Filing date, pre-formatted for display. Empty string when the record has no created timestamp. */
    private String filedDate;
    private String department;
    private String entityCode;
}
