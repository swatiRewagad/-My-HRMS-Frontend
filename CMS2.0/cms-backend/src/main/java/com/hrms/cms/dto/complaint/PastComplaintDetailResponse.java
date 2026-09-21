package com.hrms.cms.dto.complaint;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * A past complaint opened up from a history or similar-case list — read-only context for an officer
 * working a new complaint, which is why it carries no workflow or action fields.
 *
 * <p>Deliberately not {@code @JsonInclude(NON_NULL)}: {@link #resolvedAt}, {@link #closedAt} and
 * {@link #escalatedAt} are null for a complaint still in flight and the keys have to keep appearing, as
 * they did when this was a {@code LinkedHashMap}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PastComplaintDetailResponse {

    /** The human-facing complaint number, not the database id. */
    private String complaintId;
    private String subject;
    private String description;
    private String status;
    private String priority;
    /** Carries the category <em>id</em>, not a display label — so it serializes as a number. */
    private Long category;

    private String complainantName;
    private String complainantEmail;
    private String complainantPhone;

    private String entityCode;
    private String department;
    private String assignedRole;
    private String assignedOfficer;
    private String filingType;

    /** Pre-formatted for display; empty string when the record has no created timestamp. */
    private String filedDate;
    /** Null while the complaint is still open. */
    private String resolvedAt;
    /** Null unless the complaint was closed. */
    private String closedAt;
    /** Null unless the complaint was escalated. */
    private String escalatedAt;

    private String reliefSought;

    @Builder.Default
    private List<PastComplaintTimelineEntry> timeline = List.of();
}
