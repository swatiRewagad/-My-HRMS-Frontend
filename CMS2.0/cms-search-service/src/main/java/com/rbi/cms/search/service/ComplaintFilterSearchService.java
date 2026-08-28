package com.rbi.cms.search.service;

import com.rbi.cms.common.dto.PagedResponse;
import com.rbi.cms.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cms.opensearch.enabled", havingValue = "true", matchIfMissing = true)
public class ComplaintFilterSearchService {

    private final OpenSearchClient openSearchClient;

    private static final List<String> CLOSED_STATUSES = List.of(
            "COMPLAINT_CLOSED", "COMPLAINT_SETTLED", "COMPLAINT_WITHDRAWN", "COMPLAINT_REJECTED"
    );

    private static final List<String> SENT_BACK_STATUSES = List.of(
            "SENT_BACK_TO_DO", "SENT_BACK_TO_REVIEWER", "SENT_BACK_TO_DEPUTY_OMBUDSMAN"
    );

    public PagedResponse<ComplaintListResponse> search(ComplaintSearchRequestDTO request, String currentOfficer) {
        ComplaintQueryBuilder qb = new ComplaintQueryBuilder();

        applyKpiFilter(qb, request.getKpiCards(), currentOfficer);
        applyTabsFilter(qb, request.getTabs());
        applyStatusCode(qb, request.getStatusCode());
        applyAdvancedSearch(qb, request.getAdvancedSearch());
        applyFilters(qb, request.getFilters());
        applyInlineSearch(qb, request.getSearch());

        if (Boolean.TRUE.equals(request.getUnread())) {
            qb.boolFilter("isRead", false);
        }
        if (Boolean.TRUE.equals(request.getWithoutAttachments())) {
            qb.boolFilter("hasAttachments", false);
        }

        BoolQuery boolQuery = qb.build();
        return executeSearchWithNodalJoin(boolQuery, request);
    }

    private void applyKpiFilter(ComplaintQueryBuilder qb, String kpiCards, String currentOfficer) {
        if (kpiCards == null || kpiCards.isBlank()) return;

        switch (kpiCards.toLowerCase().trim()) {
            case "total pending complaints" -> qb.mustNotTerms("status", CLOSED_STATUSES);

            case "pending with me" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                if (currentOfficer != null && !currentOfficer.isBlank()) {
                    qb.termFilter("assignedOfficer", currentOfficer);
                }
            }

            case "pending with re" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                // TODO: Add RE-specific filter logic
            }

            case "pending at meeting scheduled" -> {
                // TODO: Add meeting scheduled filter logic
            }

            case "sla breached" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                qb.dateRange("slaDeadline", null, LocalDate.now().minusDays(1));
            }

            default -> log.warn("Unknown kpi_cards value: {}", kpiCards);
        }
    }

    private void applyTabsFilter(ComplaintQueryBuilder qb, String tabs) {
        if (tabs == null || tabs.isBlank()) return;

        switch (tabs.toLowerCase().trim()) {
            case "all" -> { /* no additional filter */ }
            case "draft" -> qb.termFilter("status", "DRAFT");
            case "meeting scheduled", "meeting_scheduled" -> {
                // TODO: Custom logic for meeting scheduled tab
            }
            case "sent back to me", "sent_back_to_me" -> qb.termsFilter("status", SENT_BACK_STATUSES);
            default -> log.warn("Unknown tabs value: {}", tabs);
        }
    }

    private void applyStatusCode(ComplaintQueryBuilder qb, String statusCode) {
        if (statusCode == null || statusCode.isBlank()) return;
        String normalizedStatus = statusCode.toUpperCase().replace(" ", "_");
        qb.termFilter("status", normalizedStatus);
    }

    private void applyAdvancedSearch(ComplaintQueryBuilder qb, AdvancedSearchDTO adv) {
        if (adv == null) return;

        qb.wildcardMatch("complainantName", adv.getComplainantName());
        qb.termFilter("status", normalizeStatus(adv.getStatus()));
        qb.wildcardMatch("entityName", adv.getEntityName());
        qb.wildcardMatch("categoryName", adv.getComplaintCategory());
        qb.dateRange("createdAt", adv.getCreatedDateStart(), adv.getCreatedDateEnd());
        qb.dateRange("resolvedAt", adv.getResolvedDateStart(), adv.getResolvedDateEnd());
        qb.termFilter("priority", adv.getPriority());
        qb.termFilter("slaPriority", adv.getSeverity());
        qb.wildcardMatch("department", adv.getRegion());
        qb.termFilter("entityCode", adv.getBranchCode());
        qb.termFilter("workflowStage", adv.getEscalationLevel());
    }

    private void applyFilters(ComplaintQueryBuilder qb, FiltersDTO filters) {
        if (filters == null) return;

        qb.termsFilter("complainantState", filters.getStates());
        qb.termsFilter("complainantDistrict", filters.getDistricts());
        qb.termsFilter("year", filters.getYears());
        qb.termsFilter("quarter", filters.getQuarters());
        qb.termsFilter("meetingType", filters.getMeetingTypes());
        qb.termsFilter("documentType", filters.getDocumentTypes());
    }

    private void applyInlineSearch(ComplaintQueryBuilder qb, SearchFieldsDTO search) {
        if (search == null) return;

        qb.wildcardMatch("complaintNumber", search.getComplaintId());
        qb.wildcardMatch("complaintNumber", search.getComplaintNumber());
        qb.wildcardMatch("assignedOfficer", search.getAssignedTo());
        qb.wildcardMatch("slaBreachIn", search.getSlaBreachIn());
        qb.termFilter("filingType", search.getMode());
        qb.wildcardMatch("complainantName", search.getComplainantName());
        qb.termFilter("status", normalizeStatus(search.getStatus()));
        qb.wildcardMatch("entityName", search.getEntityName());
        qb.wildcardMatch("categoryName", search.getComplaintCategory());
        qb.termFilter("priority", search.getPriority());
        qb.wildcardMatch("subject", search.getSubject());

        if (search.getCreatedDate() != null && !search.getCreatedDate().isBlank()) {
            LocalDate date = LocalDate.parse(search.getCreatedDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            qb.dateRange("createdAt", date, date);
        }
        if (search.getLastUpdatedDate() != null && !search.getLastUpdatedDate().isBlank()) {
            LocalDate date = LocalDate.parse(search.getLastUpdatedDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            qb.dateRange("updatedAt", date, date);
        }
    }

    /**
     * Executes the complaint search and enriches results with nodal officer data
     * by querying the nodal officer index using complaint numbers.
     */
    private PagedResponse<ComplaintListResponse> executeSearchWithNodalJoin(
            BoolQuery boolQuery, ComplaintSearchRequestDTO request) {

        int from = request.getPage() * request.getSize();
        int size = request.getSize();
        String sortField = request.getSortField() != null ? request.getSortField() : "createdAt";
        SortOrder sortOrder = "asc".equalsIgnoreCase(request.getSortOrder()) ? SortOrder.Asc : SortOrder.Desc;

        try {
            // Step 1: Search complaints index
            SearchRequest complaintsRequest = SearchRequest.of(b -> b
                    .index(ComplaintSearchService.COMPLAINTS_INDEX)
                    .query(Query.of(q -> q.bool(boolQuery)))
                    .from(from)
                    .size(size)
                    .sort(s -> s.field(f -> f.field(sortField).order(sortOrder)))
                    .trackTotalHits(th -> th.enabled(true)));

            SearchResponse<Map> complaintsResponse = openSearchClient.search(complaintsRequest, Map.class);

            long totalElements = complaintsResponse.hits().total() != null
                    ? complaintsResponse.hits().total().value() : 0;
            int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

            List<Hit<Map>> hits = complaintsResponse.hits().hits();

            // Step 2: Collect complaint numbers from results
            List<String> complaintNumbers = hits.stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> toStr(source.get("complaintNumber")))
                    .filter(Objects::nonNull)
                    .toList();

            // Step 3: Fetch matching nodal officer records by complaint numbers
            Map<String, Map<String, Object>> nodalOfficerMap = fetchNodalOfficerRecords(complaintNumbers);

            // Step 4: Map and join results
            List<ComplaintListResponse> content = hits.stream()
                    .map(hit -> mapHitWithNodalData(hit, nodalOfficerMap))
                    .toList();

            return PagedResponse.<ComplaintListResponse>builder()
                    .content(content)
                    .page(request.getPage())
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .last((request.getPage() + 1) >= totalPages)
                    .build();

        } catch (IOException e) {
            log.error("OpenSearch query failed: {}", e.getMessage(), e);
            return PagedResponse.<ComplaintListResponse>builder()
                    .content(List.of())
                    .page(request.getPage())
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();
        }
    }

    /**
     * Queries the nodal officer index for records matching the given complaint numbers.
     * Returns a map of complaintNumber -> nodal officer document.
     */
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
                                    .field("complaintNumber")
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
    private ComplaintListResponse mapHitWithNodalData(Hit<Map> hit, Map<String, Map<String, Object>> nodalOfficerMap) {
        Map<String, Object> source = hit.source();
        if (source == null) {
            return ComplaintListResponse.builder().build();
        }

        String complaintNumber = toStr(source.get("complaintNumber"));

        // Get nodal officer data for this complaint
        Map<String, Object> nodalData = complaintNumber != null
                ? nodalOfficerMap.getOrDefault(complaintNumber, Collections.emptyMap())
                : Collections.emptyMap();

        return ComplaintListResponse.builder()
                .complaintId(toLong(source.get("id")))
                .complaintNumber(complaintNumber)
                .assignedTo(toStr(source.get("assignedOfficer")))
                .slaBreachIn(calculateSlaBreachIn(toStr(source.get("slaDeadline"))))
                .mode(toStr(source.get("filingType")))
                .complainantName(toStr(source.get("complainantName")))
                .status(toStr(source.get("status")))
                .entityName(toStr(source.get("entityName")))
                .complaintCategory(toStr(source.get("categoryName")))
                .createdDate(toLocalDate(source.get("createdAt")))
                .lastUpdatedDate(toLocalDate(source.get("updatedAt")))
                .priority(toStr(source.get("priority")))
                .subject(toStr(source.get("subject")))
                // Nodal officer fields from joined index
                .nodalOfficer(toStr(nodalData.get("nodalOfficerName")))
                .principalNodalOfficer(toStr(nodalData.get("pnoName")))
                // Computed fields (placeholder logic)
                .complaintColor(null)
                .isRead(null)
                .slaColor(determineSlaColor(toStr(source.get("slaDeadline"))))
                .build();
    }

    private String calculateSlaBreachIn(String slaDeadlineStr) {
        if (slaDeadlineStr == null || slaDeadlineStr.isBlank()) return null;
        try {
            LocalDate slaDate = LocalDate.parse(slaDeadlineStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), slaDate);
            if (days < 0) return Math.abs(days) + " Days Breached";
            if (days == 0) return "Today";
            if (days == 1) return "1 Day";
            return days + " Days";
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

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        return status.toUpperCase().replace(" ", "_");
    }

    private String toStr(Object value) {
        return value != null ? value.toString() : null;
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
