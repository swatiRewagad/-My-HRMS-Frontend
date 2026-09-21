package com.rbi.cms.search.service;

import com.rbi.cms.common.dto.PagedResponse;
import com.rbi.cms.common.exception.CmsException;
import com.rbi.cms.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.json.JsonData;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.ScriptSortType;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.opensearch.client.opensearch.core.search.TotalHits;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.rbi.cms.common.enums.ComplaintStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintFilterSearchService {

    private final OpenSearchClient openSearchClient;
    private final SearchAggregationService aggregationService;
    private final OfficerScopePolicy scopePolicy;

    @Value("${cms.search.max-page-size:100}")
    private int maxPageSize;

    @Value("${spring.data.web.pageable.default-page-size:10}")
    private int defaultPageSize;

    /** Mirrors the index's {@code index.max_result_window}; deep paging past it is an error there. */
    @Value("${cms.search.max-result-window:10000}")
    private int maxResultWindow;

    /** The dynamically-mapped keyword subfield; a term query against the analyzed parent matches nothing. */
    private static final String READ_BY_KEYWORD = ComplaintDocumentNormalizer.FIELD_READ_BY + ".keyword";

    private static final List<String> CLOSED_STATUSES = List.of(
            COMPLAINT_CLOSED.name(), COMPLAINT_SETTLED.name(), COMPLAINT_WITHDRAWN.name(), COMPLAINT_REJECTED.name()
    );

    private static final List<String> SENT_BACK_STATUSES = List.of(
            SENT_BACK_TO_DO.name(), SENT_BACK_TO_REVIEWER.name(), SENT_BACK_TO_DEPUTY_OMBUDSMAN.name()
    );

    private ComplaintQueryBuilder determineParentQueryBuilder(ComplaintSearchRequest request, OfficerPrincipal officer) {
        var qb = new ComplaintQueryBuilder();

        if (request.advancedSearch() != null) {
            applyAdvancedSearch(qb, request.advancedSearch());
        } else if (request.filters() != null) {
            applyFilters(qb, request.filters());
        } else if (request.statusCode() != null) {
            applyStatusCode(qb, request.statusCode(), officer);
        }

        return qb;
    }

    public ComplaintSearchResponse search(ComplaintSearchRequest request, OfficerPrincipal officer, Pageable pageable) {
        var qb = determineParentQueryBuilder(request, officer);

        if (qb.isEmpty()) {
            log.warn("Blocking execution: No valid parent search scope was provided.");
            int fallbackSize = pageable != null ? pageable.getPageSize() : 10;
            return buildEmptyErrorFallbackResponse(pageable, fallbackSize);
        }

        // Must come after the emptiness gate and before aggregationBaseQuery is captured: applying it
        // earlier would make every request look non-empty and kill the gate, applying it later would
        // leave the KPI and tab counters unscoped.
        scopePolicy.apply(qb, officer);

        Query aggregationBaseQuery = Query.of(q -> q.bool(qb.build()));

        applyTabsFilter(qb, request.tabs(), officer);
        applyInlineSearch(qb, request.search());

        if (Boolean.TRUE.equals(request.unread())) {
            // Unread to this officer, not unread to everyone: a mustNot rather than a term on a global
            // flag, and it correctly matches documents with no readBy field at all.
            qb.mustNotTerm(READ_BY_KEYWORD, officer.getUserName());
        }
        if (Boolean.TRUE.equals(request.withoutAttachments())) {
            qb.boolFilter("hasAttachments", false);
        }

        Query searchIntentQuery = Query.of(q -> q.bool(qb.build()));

        Map<String, Aggregation> aggregations = aggregationService.buildAggregations(officer, aggregationBaseQuery);

        return executeSearchWithNodalJoin(searchIntentQuery, aggregations, pageable, officer.getUserName());
    }

    /**
     * Not reachable today — nothing calls this, so a KPI card click does not narrow the grid. Left in
     * place because the aggregation side already counts these same cards, but note the clauses target
     * {@code .keyword} subfields: {@code status} itself is analyzed text, and a {@code term} query is
     * not analyzed, so filtering on the bare field silently matches nothing — which would have made
     * the "meeting scheduled" card return zero rows and the {@code mustNot} cards exclude nothing.
     */
    private void applyKpiFilter(ComplaintQueryBuilder qb, String kpiCards, OfficerPrincipal officer) {
        if (kpiCards == null || kpiCards.isBlank()) return;

        switch (kpiCards.toLowerCase().trim()) {
            case "total pending complaints" -> qb.mustNotTerms("status.keyword", CLOSED_STATUSES);

            case "pending with me" -> {
                qb.mustNotTerms("status.keyword", CLOSED_STATUSES);
                if (hasValue(officer.getUserName())) {
                    qb.termFilter("assignedOfficer.keyword", officer.getUserName());
                }
            }

            case "pending with re" -> {
                qb.mustNotTerms("status.keyword", CLOSED_STATUSES);
                qb.termFilter("assignedRole.keyword", "RE");
            }

            case "pending at meeting scheduled" ->
                    qb.termFilter("status.keyword", MEETING_SCHEDULED.name());

            case "sla breached" -> {
                qb.mustNotTerms("status.keyword", CLOSED_STATUSES);
                qb.dateRange("slaDeadline", null, LocalDate.now().minusDays(1));
            }

            default -> log.warn("Unknown kpi_cards value: {}", kpiCards);
        }
    }

    private void applyTabsFilter(ComplaintQueryBuilder qb, String tabs, OfficerPrincipal officer) {
        if (tabs == null || tabs.isBlank()) return;

        switch (tabs.strip().toLowerCase()) {
            case "all" -> {
            }

            case "draft" -> qb.termFilter("status.keyword", DRAFT.name());

            case "meeting scheduled" -> qb.termFilter("status.keyword", MEETING_SCHEDULED.name());

            case "sent back to me" -> {
                qb.termsFilter("status.keyword", SENT_BACK_STATUSES);
                Optional.ofNullable(officer.getUserName())
                        .filter(org.springframework.util.StringUtils::hasText)
                        .ifPresent(user -> qb.termFilter("assignedOfficer.keyword", user));
            }

            case "sent to re" -> qb.termFilter("status.keyword", INFORMATION_REQUIRED.name());

            case "response from re" -> qb.termFilter("status.keyword", SENT_TO_RBI.name());

            case "withdrawn complaints" -> qb.termFilter("status.keyword", COMPLAINT_WITHDRAWN.name());

            default -> log.warn("Unknown tabs value encountered, skipping status filter assignment: {}", tabs);
        }
    }

    private void applyStatusCode(ComplaintQueryBuilder qb, String statusCode, OfficerPrincipal officer) {
        if (statusCode == null || statusCode.isBlank()) return;

        switch (statusCode.strip()) {
            // No status restriction; the department filter added centrally in search() is what bounds
            // it. matchAll is required so the request still reads as an explicit scope rather than as
            // one that supplied nothing.
            case "All Complaints" -> qb.matchAll();

            case "Complaint Assigned To Me" -> Optional.ofNullable(officer.getUserName()).filter(StringUtils::hasText)
                    .ifPresent(user -> qb.termFilter("assignedOfficer.keyword", user));

            case "Complaint Created By Me" -> Optional.ofNullable(officer.getUserName()).filter(StringUtils::hasText)
                    .ifPresent(user -> qb.termFilter("createdBy.keyword", user));

            case "Complaints Rejected/Withdrawn/Settled" -> {
                qb.termsFilter("status.keyword", List.of(
                        COMPLAINT_CLOSED.name(),
                        COMPLAINT_SETTLED.name(),
                        COMPLAINT_WITHDRAWN.name()
                ));
            }

            default -> {
                String targetStatus = switch (statusCode.strip()) {
                    case "New Complaints" -> NEW_COMPLAINT.name();
                    case "Complaint Closed" -> COMPLAINT_CLOSED.name();
                    case "Complaint Re Open" -> COMPLAINT_REOPEN.name();
                    case "Award Passed" -> AWARD_PASSED.name();
                    case "Deputy Ombudsman Decision" -> DEPUTY_OMBUDSMAN_DECISION.name();
                    case "Ombudsman Decision" -> OMBUDSMAN_DECISION.name();
                    case "Meeting Scheduled" -> MEETING_SCHEDULED.name();
                    case "Sent To Reviewer" -> SENT_TO_REVIEWER.name();
                    case "Sent To Deputy Ombudsman" -> SENT_TO_DEPUTY_OMBUDSMAN.name();
                    case "Sent To Ombudsman" -> SENT_TO_OMBUDSMAN.name();
                    case "Sent To Other Regulatory Bodies",
                         "Sent To Other Regulated Bodies" -> SENT_TO_OTHER_REGULATED_BODIES.name();
                    case "Sent To Other Office" -> SENT_TO_OTHER_OFFICE.name();
                    case "Sent To Other Departments" -> SENT_TO_OTHER_DEPARTMENTS.name();
                    case "Sent To Back To Dealing Official" -> SENT_BACK_TO_DO.name();
                    case "Sent Back To Reviewer" -> SENT_BACK_TO_REVIEWER.name();
                    case "Sent Back To Deputy Ombudsman" -> SENT_BACK_TO_DEPUTY_OMBUDSMAN.name();
                    case "Advisory Complied" -> ADVISORY_COMPLIED.name();
                    default -> null;
                };

                if (targetStatus != null) qb.termFilter("status.keyword", targetStatus);
            }
        }
    }

    private void applyAdvancedSearch(ComplaintQueryBuilder qb, AdvancedSearchRequest adv) {
        if (adv == null) return;

        // 1. Text Fields (Wildcard Match)
        applyIfTextPresent(adv.complaintNumber(), val -> qb.wildcardMatch("complaintNumber.keyword", val));
        applyIfTextPresent(adv.complainantName(), val -> qb.wildcardMatch("complainantName.keyword", val));
        applyIfTextPresent(adv.subject(), val -> qb.wildcardMatch("subject.keyword", val));

        // 2. Text Fields (Exact Term Match)
        applyIfTextPresent(adv.statusCode(), val -> qb.termFilter("status.keyword", normalizeStatus(val)));
        applyIfTextPresent(adv.complainantPhone(), val -> qb.termFilter("complainantPhone.keyword", val));
        applyIfTextPresent(adv.complainantEmail(), val -> qb.termFilter("complainantEmail.keyword", val));
        applyIfTextPresent(adv.filingType(), val -> qb.termFilter("filingType.keyword", val));
        applyIfTextPresent(adv.entityName(), val -> qb.termFilter("entityName.keyword", val));
        applyIfTextPresent(adv.nodalOfficerName(), val -> applyNodalOfficerFilter(qb, val));
        applyIfTextPresent(adv.fromEmailId(), val -> qb.termFilter("fromEmailId.keyword", val)); // Added missing filter

        // 3. Numeric & Date Fields
        Optional.ofNullable(adv.id()).ifPresent(id -> qb.termFilterLong("id", id));
        Optional.ofNullable(adv.categoryId()).ifPresent(catId -> qb.termFilterLong("categoryId", catId));
        Optional.ofNullable(adv.filedAt()).ifPresent(date -> qb.termFilter("filedDate", date.toString()));
    }

    private void applyIfTextPresent(String value, Consumer<String> action) {
        if (StringUtils.hasText(value)) {
            action.accept(value.strip());
        }
    }


    private void applyNodalOfficerFilter(ComplaintQueryBuilder qb, String nodalOfficerName) {
        if (!hasValue(nodalOfficerName)) return;

        try {
            String pattern = "*" + nodalOfficerName.toLowerCase() + "*";
            SearchRequest nodalRequest = SearchRequest.of(b -> b
                    .index(ComplaintSearchService.NODAL_OFFICER_INDEX)
                    .query(Query.of(q -> q
                            .wildcard(w -> w
                                    .field("nodalOfficerName.keyword")
                                    .value(pattern)
                                    .caseInsensitive(true))))
                    .size(1000)
                    .source(s -> s.filter(f -> f.includes("complaintNumber.keyword"))));

            SearchResponse<Map> response = openSearchClient.search(nodalRequest, Map.class);

            List<String> complaintNumbers = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> source.get("complaintNumber"))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();

            if (!complaintNumbers.isEmpty()) {
                qb.termsFilter("complaintNumber.keyword", complaintNumbers);
            } else {
                qb.termFilter("complaintNumber.keyword", "__NO_MATCH__");
            }
        } catch (IOException e) {
            log.warn("Failed to search nodal officer index for name '{}': {}", nodalOfficerName, e.getMessage());
        }
    }

    private void applyFilters(ComplaintQueryBuilder qb, FilterSearchRequest filters) {
        if (filters == null) return;

        applyIfPopulated(filters.states(), values -> qb.termsFilter("complainantState.keyword", values));
        applyIfPopulated(filters.districts(), values -> qb.termsFilter("complainantDistrict.keyword", values));

        applyFinancialDateFilters(qb, filters.years(), filters.quarters());

        // TODO: Need to change the logic for these fields
        // applyIfPopulated(filters.meetingTypes(), values -> qb.termsFilter("meetingType", values));
        // applyIfPopulated(filters.documentTypes(), values -> qb.termsFilter("documentType", values));
    }


    private void applyInlineSearch(ComplaintQueryBuilder qb, SearchFieldsRequest search) {
        if (search == null) return;

        // 1. Safe Numeric ID Conversion (Handles spaces/empty strings gracefully)
        applyIfTextPresent(search.complaintId(), val -> {
            try {
                qb.termFilterLong("id", Long.valueOf(val));
            } catch (NumberFormatException e) {
                log.warn("Search field complaint id format is invalid - {}", search.complaintId());
            }
        });

        // 2. Text Fields (Wildcard Match)
        applyIfTextPresent(search.complaintNumber(), val -> qb.wildcardMatch("complaintNumber.keyword", val));
        // Either spelling of the officer: the grid shows the display name, but officers also search by
        // the username they know from elsewhere. Matching only one would be a silent zero-result trap.
        applyIfTextPresent(search.assignedTo(), val -> qb.wildcardMatchAny(val,
                ComplaintDocumentNormalizer.FIELD_ASSIGNED_OFFICER_NAME + ".keyword",
                ComplaintDocumentNormalizer.FIELD_ASSIGNED_OFFICER + ".keyword"));
        applyIfTextPresent(search.slaBreachIn(), val -> qb.wildcardMatch("slaBreachIn", val));
        applyIfTextPresent(search.complainantName(), val -> qb.wildcardMatch("complainantName.keyword", val));
        applyIfTextPresent(search.entityName(), val -> qb.wildcardMatch("entityName.keyword", val));
        applyIfTextPresent(search.complaintCategory(), val -> qb.wildcardMatch("categoryName.keyword", val));
        applyIfTextPresent(search.subject(), val -> qb.wildcardMatch("subject.keyword", val));

        // 3. Text Fields (Exact Term Match)
        applyIfTextPresent(search.mode(), val -> qb.termFilter("filingType.keyword", val));
        applyIfTextPresent(search.status(), val -> qb.termFilter("status.keyword", normalizeStatus(val)));
        applyIfTextPresent(search.priority(), val -> qb.termFilter("priority.keyword", val));

        // 4. Date Expressions. These target the date-only companions the normalizer derives, not the
        // underlying createdAt/updatedAt timestamps: a term on a timestamp compares instants, so a
        // date-only value parses to midnight and matches nothing. Sorting is the reverse — it uses the
        // timestamps, which is why SortFieldRegistry maps these same two keys differently.
        // LocalDate.toString() is already ISO-8601, matching what deriveDate writes.
        Optional.ofNullable(search.createdDate())
                .ifPresent(date -> qb.termFilter("createdDate", date.toString()));

        Optional.ofNullable(search.lastUpdatedDate())
                .ifPresent(date -> qb.termFilter("updatedDate", date.toString()));
    }

    private ComplaintSearchResponse executeSearchWithNodalJoin(
            Query openSearchQuery, Map<String, Aggregation> aggregations, Pageable requestedPageable,
            String callerUserName) {

        Pageable pageable = resolvePageable(requestedPageable);
        int from = (int) pageable.getOffset();
        int size = pageable.getPageSize();

        if (openSearchQuery.isBool() &&
                openSearchQuery.bool().must().isEmpty() &&
                openSearchQuery.bool().filter().isEmpty()) {

            log.warn("Blocking execution of a completely unfiltered global index query.");
            return buildEmptyErrorFallbackResponse(pageable, size);
        }

        try {
            SearchRequest complaintsRequest = SearchRequest.of(builder -> {
                builder.index(ComplaintSearchService.COMPLAINTS_INDEX)
                        .query(openSearchQuery)
                        .from(from)
                        .size(size)
                        .trackTotalHits(th -> th.enabled(true))
                        .aggregations(aggregations);

                boolean hasValidSort = false;

                for (Sort.Order order : pageable.getSort()) {
                    String rawFrontendProperty = order.getProperty();
                    SortOrder sortOrder = order.isAscending() ? SortOrder.Asc : SortOrder.Desc;

                    // 1. Handle Dynamic Field 'slaBreachIn' natively via the raw property key
                    if (SortFieldRegistry.isScriptSort(rawFrontendProperty)) {
                        builder.sort(s -> s.script(sc -> sc
                                        .type(ScriptSortType.Number)
                                        .script(script -> script.inline(inline -> inline
                                                .source("if (doc['slaDeadline'].size() == 0) { return 0; } " +
                                                        "return doc['slaDeadline'].value.toInstant().toEpochMilli() - params.now;")
                                                .params("now", JsonData.of(System.currentTimeMillis()))
                                        ))
                                .order(sortOrder)
                        ));
                        hasValidSort = true;
                        continue;
                    }

                    // 2. Map standard properties safely to their OpenSearch equivalents. An unknown
                    // key raises a 400 rather than being dropped: silently ignoring it returns a
                    // correctly paginated page in the wrong order, which no caller can detect.
                    String openSearchField = SortFieldRegistry.require(rawFrontendProperty);
                    builder.sort(s -> s.field(f -> f.field(openSearchField).order(sortOrder)));
                    hasValidSort = true;
                }

                // 3. Fallback Sort Clause if no sort parameters are active
                if (!hasValidSort) {
                    builder.sort(s -> s.field(f -> f.field(SortFieldRegistry.DEFAULT_SORT_FIELD).order(SortOrder.Desc)));
                }

                return builder;
            });

            SearchResponse<Map> complaintsResponse = openSearchClient.search(complaintsRequest, Map.class);

            long totalElements = Optional.ofNullable(complaintsResponse.hits().total())
                    .map(TotalHits::value)
                    .orElse(0L);

            int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
            List<Hit<Map>> hits = complaintsResponse.hits().hits();

            log.info("Total OpenSearch matches returned: {}", totalElements);

            List<String> complaintNumbers = hits.stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> toStr(source.get("complaintNumber")))
                    .filter(Objects::nonNull)
                    .toList();

            Map<String, Map<String, Object>> nodalOfficerMap = fetchNodalOfficerRecords(complaintNumbers);

            List<ComplaintResponse> content = hits.stream()
                    .map(hit -> mapHitWithNodalData(hit, nodalOfficerMap, callerUserName))
                    .toList();

            PagedResponse<ComplaintResponse> pagedResponse = PagedResponse.<ComplaintResponse>builder()
                    .content(content)
                    .page(pageable.getPageNumber())
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .last((pageable.getPageNumber() + 1) >= totalPages)
                    .build();

            Map<String, Aggregate> aggs = complaintsResponse.aggregations();
            KpiCountsResponse kpiCounts = aggregationService.parseKpiCounts(aggs);
            TabCountsResponse tabCounts = aggregationService.parseTabCounts(aggs);

            return ComplaintSearchResponse.builder()
                    .complaints(pagedResponse)
                    .kpiCounts(kpiCounts)
                    .tabCounts(tabCounts)
                    .build();

        } catch (IOException e) {
            // Deliberately not an empty page: a cluster outage is indistinguishable from "no
            // complaints match" to the caller, and an officer reading zero rows on a screen that
            // should show their queue will act on it.
            log.error("OpenSearch network layer failure occurred: {}", e.getMessage(), e);
            throw new CmsException("The search service is temporarily unavailable. Please retry.",
                    HttpStatus.BAD_GATEWAY);
        }
    }

    /**
     * Bounds the requested window before it reaches OpenSearch.
     *
     * <p>An oversized page is clamped rather than rejected, since the caller still gets usable data.
     * A window past {@code index.max_result_window} cannot be clamped meaningfully — silently
     * returning a different page than asked for would be worse — and OpenSearch would otherwise fail
     * it as an {@code IOException}, so it is rejected here with an explanation instead.
     */
    private Pageable resolvePageable(Pageable requested) {
        if (requested == null || requested.isUnpaged()) {
            return PageRequest.of(0, defaultPageSize);
        }

        int size = Math.min(requested.getPageSize(), maxPageSize);
        long lastRecord = (long) requested.getPageNumber() * size + size;

        if (lastRecord > maxResultWindow) {
            throw new CmsException(
                    "Requested page is beyond the maximum searchable window of " + maxResultWindow
                            + " records. Narrow the search instead of paging further.",
                    HttpStatus.BAD_REQUEST);
        }

        return size == requested.getPageSize()
                ? requested
                : PageRequest.of(requested.getPageNumber(), size, requested.getSort());
    }

    private ComplaintSearchResponse buildEmptyErrorFallbackResponse(Pageable pageable, int size) {
        PagedResponse<ComplaintResponse> emptyPage = PagedResponse.<ComplaintResponse>builder()
                .content(List.of())
                .page(pageable.getPageNumber())
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .last(true)
                .build();

        return ComplaintSearchResponse.builder()
                .complaints(emptyPage)
                .kpiCounts(KpiCountsResponse.builder().build())
                .tabCounts(TabCountsResponse.builder().build())
                .build();
    }


    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> fetchNodalOfficerRecords(List<String> complaintNumbers) {
        if (complaintNumbers == null || complaintNumbers.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            List<FieldValue> fieldValues = complaintNumbers.stream()
                    .map(FieldValue::of)
                    .toList();

            SearchRequest nodalRequest = SearchRequest.of(b -> b
                    .index(ComplaintSearchService.NODAL_OFFICER_INDEX)
                    .query(Query.of(q -> q
                            .terms(t -> t
                                    .field("complaintNumber.keyword")
                                    .terms(tv -> tv.value(fieldValues)))))
                    .size(complaintNumbers.size()));

            SearchResponse<Map> nodalResponse = openSearchClient.search(nodalRequest, Map.class);

            return nodalResponse.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toMap(
                            source -> toStr(source.get("complaintNumber")),
                            source -> (Map<String, Object>) source,
                            (existing, replacement) -> replacement
                    ));

        } catch (IOException e) {
            log.warn("Failed to fetch nodal officer records: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    private ComplaintResponse mapHitWithNodalData(Hit<Map> hit, Map<String, Map<String, Object>> nodalOfficerMap,
                                                  String callerUserName) {
        Map<String, Object> source = hit.source();
        if (source == null) {
            return ComplaintResponse.builder().build();
        }

        String complaintNumber = toStr(source.get("complaintNumber"));

        Map<String, Object> nodalData = complaintNumber != null
                ? nodalOfficerMap.getOrDefault(complaintNumber, Collections.emptyMap())
                : Collections.emptyMap();

        return ComplaintResponse.builder()
                .complaintId(toLong(source.get("id")))
                .complaintNumber(complaintNumber)
                // Display name when the indexer resolved one, otherwise the username. Never blank: a
                // username is actionable, an empty cell is not.
                .assignedTo(firstNonBlank(
                        source.get(ComplaintDocumentNormalizer.FIELD_ASSIGNED_OFFICER_NAME),
                        source.get(ComplaintDocumentNormalizer.FIELD_ASSIGNED_OFFICER)))
                .slaBreachIn(calculateSlaBreachIn(
                        toStr(source.get("status")),
                        toStr(source.get("slaDeadline"))
                ))
                .mode(toStr(source.get("filingType")))
                .complainantName(toStr(source.get("complainantName")))
                .status(toStr(source.get("status")))
                .entityName(toStr(source.get("entityName")))
                .complaintCategory(toStr(source.get("categoryName")))
                .createdDate(toLocalDate(source.get("createdAt")))
                .lastUpdatedDate(toLocalDate(source.get("updatedAt")))
                .priority(toStr(source.get("priority")))
                .subject(toStr(source.get("subject")))
                .nodalOfficer(toStr(nodalData.get("nodalOfficerName")))
                .principalNodalOfficer(toStr(nodalData.get("pnoName")))
                .complaintColor(
                        setComplaintColor(
                                toStr(source.get("status")),
                                source.get("createdAt"),
                                source.get("stageAssignedAt")
                        )
                )
                // Whether this caller has read it, not whether anyone has: the grid greys a row out for
                // the officer who opened it while it stays bold for everyone else.
                .isRead(hasRead(source.get(ComplaintDocumentNormalizer.FIELD_READ_BY), callerUserName))
                .slaColor(determineSlaColor(toStr(source.get("slaDeadline"))))
                .statusColor(setStatusColor(toStr(source.get("status"))))
                .build();
    }

    private boolean hasRead(Object readBy, String callerUserName) {
        if (!(readBy instanceof Collection<?> readers) || callerUserName == null) {
            return false;
        }
        return readers.stream().anyMatch(reader -> callerUserName.equals(toStr(reader)));
    }

    private String setComplaintColor(String status, Object createdAtObj, Object assignedAtObj) {
        if (status == null) return "";

        return switch (status.strip()) {
            case "SENT_BACK_TO_DO" -> "yellow";
            case "COMPLAINT_WITHDRAWN" -> "pink";
            case "INFORMATION_REQUIRED" -> "red";
            case "SENT_TO_RBI" -> "green";

            case "NEW_COMPLAINT" -> {
                LocalDateTime createdAt = toLocalDateTime(createdAtObj);
                LocalDateTime assignedAt = toLocalDateTime(assignedAtObj);

                if (createdAt != null && assignedAt != null) {
                    long daysBetween = ChronoUnit.DAYS.between(createdAt, assignedAt);
                    if (daysBetween > 3) {
                        yield "blue";
                    }
                }
                yield "";
            }

            default -> "";
        };
    }

    private String setStatusColor(String status) {
        if (status == null) return "";

        return switch (status.strip()) {
            case "SENT_BACK_TO_DO" -> "yellow";
            case "COMPLAINT_WITHDRAWN" -> "pink";
            case "INFORMATION_REQUIRED",
                 "OMBUDSMAN_DECISION" -> "red";
            case "SENT_TO_OTHER_OFFICE" -> "blue";
            case "NEW_COMPLAINT" -> "green";
            default -> "";
        };
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(value.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private void applyFinancialDateFilters(ComplaintQueryBuilder qb, List<String> financialYears, List<String> quarters) {
        List<String> validYears = sanitizeList(financialYears);
        List<String> validQuarters = sanitizeList(quarters);

        if (validYears.isEmpty()) return;

        List<Query> combinations = new ArrayList<>();

        for (String fy : validYears) {
            String[] parts = fy.split("-");
            if (parts.length != 2) {
                log.warn("Skipping invalid financial year format payload: {}", fy);
                continue;
            }

            String startYear = parts[0].strip();
            String endYear = parts[1].strip();

            List<String> activeQuarters = validQuarters.isEmpty()
                    ? List.of("Q1", "Q2", "Q3", "Q4")
                    : validQuarters;

            for (String q : activeQuarters) {
                Query rangeQuery = switch (q.toUpperCase().strip()) {
                    case "Q1" ->
                            buildRangeQuery("createdDate", startYear + "-04-01", startYear + "-06-30"); // Apr - Jun
                    case "Q2" ->
                            buildRangeQuery("createdDate", startYear + "-07-01", startYear + "-09-30"); // Jul - Sep
                    case "Q3" ->
                            buildRangeQuery("createdDate", startYear + "-10-01", startYear + "-12-31"); // Oct - Dec
                    case "Q4" -> buildRangeQuery("createdDate", endYear + "-01-01", endYear + "-03-31");   // Jan - Mar
                    default -> null;
                };
                if (rangeQuery != null) combinations.add(rangeQuery);
            }
        }

        if (!combinations.isEmpty()) {
            qb.shouldFilters(combinations);
        }
    }


    private String calculateSlaBreachIn(String status, String slaDeadlineStr) {
        if (status != null && CLOSED_STATUSES.contains(status.toUpperCase())) {
            return "Closed";
        }

        if (slaDeadlineStr == null || slaDeadlineStr.isBlank()) return null;

        try {
            LocalDate slaDate = LocalDate.parse(slaDeadlineStr.substring(0, 10));
            long days = ChronoUnit.DAYS.between(LocalDate.now(), slaDate);

            String suffix = Math.abs(days) == 1 ? " Day" : " Days";
            return days + suffix;

        } catch (Exception e) {
            return null;
        }
    }


    private String determineSlaColor(String slaDeadlineStr) {
        if (slaDeadlineStr == null || slaDeadlineStr.isBlank()) return null;
        try {
            LocalDate slaDate = LocalDate.parse(slaDeadlineStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), slaDate);
            if (days < 0) return "red";
            if (days <= 3) return "orange";
            return "green";
        } catch (Exception e) {
            return null;
        }
    }

    private Query buildRangeQuery(String field, String gte, String lte) {
        return Query.of(q -> q.range(r -> r.field(field)
                .gte(JsonData.of(gte))
                .lte(JsonData.of(lte))));
    }

    private List<String> sanitizeList(List<String> input) {
        if (input == null) return List.of();
        return input.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .toList();
    }

    private void applyIfPopulated(Collection<String> list, java.util.function.Consumer<List<String>> action) {
        Optional.ofNullable(list)
                .map(c -> c.stream().filter(org.springframework.util.StringUtils::hasText).map(String::strip).toList())
                .filter(processedList -> !processedList.isEmpty())
                .ifPresent(action);
    }

    /**
     * Delegates so that the spelling a filter asks for is by construction the spelling the indexer
     * wrote. Keeping a second copy of the rule here is what made {@code pending} unsearchable: the
     * index held one form and the query built another.
     */
    private String normalizeStatus(String status) {
        return ComplaintDocumentNormalizer.canonicalStatus(status);
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private String toStr(Object value) {
        return value != null ? value.toString() : null;
    }

    /** First value that is present and not blank, or null when neither is. */
    private String firstNonBlank(Object preferred, Object fallback) {
        String value = toStr(preferred);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return toStr(fallback);
    }

    private Long toLong(Object value) {
        if (value == null) return null;
        if (value instanceof Number num) return num.longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        try {
            String str = value.toString();
            if (str.length() >= 10) {
                return LocalDate.parse(str.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
