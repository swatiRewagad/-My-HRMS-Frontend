package com.hrms.cms.dto.cepc;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The CEPC dashboard search body, exactly as {@code cepc-dashboard.component.ts} builds it.
 *
 * <p><b>There is deliberately no {@code @JsonNaming(SnakeCaseStrategy)} here.</b> The old
 * {@code cms-search-service} put one on its request DTO, which meant every camelCase key the frontend sent —
 * {@code statusCode}, {@code withoutAttachments}, the whole of {@code advancedSearch} — silently failed to
 * bind and arrived as null. The endpoint returned 200 with a plausible-looking unfiltered page, so nothing
 * ever surfaced as an error. Plain Jackson binding is what makes this contract actually hold.
 *
 * <p>The single snake_case key, {@code kpi_cards}, is handled by {@link JsonProperty} on that one field
 * rather than by a naming strategy over all of them.
 *
 * <p>Every field is optional. The frontend strips nulls, blanks and empty collections before sending
 * ({@code cleanPayloadKeys}), so a quiet dashboard posts {@code {"tabs":"All","unread":false,...}} and
 * nothing else — absent must mean "unfiltered", not "match nothing".
 *
 * <p><b>There is deliberately no office field here.</b> CEPC listings are scoped by department and office
 * together, but the office is the caller's, resolved from {@code WF_OFFICER_POOL} — see
 * {@code CepcComplaintSearchService.officeOf}. Taking it from the request would let any officer list another
 * office's complaints by editing one key in the payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CepcSearchRequest(
        AdvancedSearch advancedSearch,
        Filters filters,

        /**
         * The status dropdown selection, as a normalised {@code ROLE_STATUS_MAPPING} code. A SCOPE selector
         * as often as a status — {@code COMPLAINT_ASSIGNED_TO_ME} constrains the caller rather than the
         * complaint. Defaults to {@code ALL_COMPLAINTS}.
         *
         * <p>The only status axis on this request. The advance-search panel used to carry a second one,
         * applied independently, so the dropdown and the panel could each restrict the status and neither
         * reflected the other.
         */
        String statusCode,

        /** The clicked KPI card, by its display id. Absent when no card is active. */
        @JsonProperty("kpi_cards") String kpiCards,

        /** The active tab, by the id {@code translateTabIdToStatus} emits; defaults to {@code "All"}. */
        String tabs,

        /** Restrict to complaints this caller has not opened. */
        Boolean unread,

        /** Restrict to complaints with no attachment of any kind. */
        Boolean withoutAttachments,

        /** Per-column grid filters, which narrow within whatever the tab and status already selected. */
        ColumnFilters search) {

    /**
     * The advance-search panel.
     *
     * <h2>Which fields are partial and which are exact</h2>
     * Name, entity name and subject are substring matches — an officer looking for "Sharma" cannot be
     * expected to know the full recorded name. Phone and email are EXACT, and that is not an oversight: a
     * partial match over a phone number is a PII enumeration primitive, letting a caller harvest
     * complainants' numbers prefix by prefix and confirm whether a particular citizen has complained. The
     * RBIO and AA searches made the same call for the same reason.
     *
     * <p><b>No {@code statusCode} here.</b> The panel used to carry one, applied by a separate predicate from
     * the dropdown's, so the two competed: picking a status in the panel and a different one in the dropdown
     * asked for complaints in two statuses at once and returned nothing. Status now has exactly one axis, the
     * top-level {@code statusCode}, which the panel narrows rather than contradicts. A {@code statusCode} key
     * that still arrives inside this object is ignored by {@code ignoreUnknown}.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AdvancedSearch(
            String complaintNumber,
            String complainantName,
            /** The numeric primary key, sent as a number by the frontend. */
            Long id,
            String complainantPhone,
            String complainantEmail,
            String filingType,
            String entityName,
            String subject,
            Long categoryId,
            /** Filing date, as a single day. Accepts {@code yyyy-MM-dd} or {@code dd-MM-yyyy}. */
            String filedAt,
            String nodalOfficerName,
            String fromEmailId) {
    }

    /** The multi-select filter panel. Empty lists mean "no constraint on this dimension". */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Filters(
            List<String> states,
            List<String> districts,
            List<Integer> years,
            List<String> quarters,
            List<String> meetingTypes,
            List<String> documentTypes) {
    }

    /**
     * The per-column filter row of the grid. Field names mirror the grid's own column keys, which is why
     * {@code complaintId} is a String here — it is whatever the officer typed into that box, and an
     * unparseable value must return no rows rather than a 500.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ColumnFilters(
            String complaintId,
            String complaintNumber,
            String assignedTo,
            String slaBreachIn,
            String mode,
            String complainantName,
            String status,
            String entityName,
            String complaintCategory,
            String createdDate,
            String lastUpdatedDate,
            String priority,
            String subject) {
    }
}
