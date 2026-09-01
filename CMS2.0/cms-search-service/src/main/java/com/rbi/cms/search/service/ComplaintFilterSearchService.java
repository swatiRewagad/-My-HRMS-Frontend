package com.rbi.cms.search.service;

import com.rbi.cms.common.dto.PagedResponse;
import com.rbi.cms.search.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.SortOrder;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.opensearch.core.SearchRequest;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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
    private final SearchAggregationService aggregationService;

    private static final List<String> CLOSED_STATUSES = List.of(
            "COMPLAINT_CLOSED", "COMPLAINT_SETTLED", "COMPLAINT_WITHDRAWN", "COMPLAINT_REJECTED"
    );

    private static final List<String> SENT_BACK_STATUSES = List.of(
            "SENT_BACK_TO_DO", "SENT_BACK_TO_REVIEWER", "SENT_BACK_TO_DEPUTY_OMBUDSMAN"
    );

    public ComplaintSearchResponse search(ComplaintSearchRequestDTO request, OfficerContext officer, Pageable pageable) {
        ComplaintQueryBuilder qb = new ComplaintQueryBuilder();

        applyKpiFilter(qb, request.getKpiCards(), officer);
        applyTabsFilter(qb, request.getTabs(), officer);
        applyStatusCode(qb, request.getStatusCode(), officer);
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
        Map<String, Aggregation> aggregations = aggregationService.buildAggregations(officer);

        return executeSearchWithNodalJoin(boolQuery, aggregations, officer, pageable);
    }

    private void applyKpiFilter(ComplaintQueryBuilder qb, String kpiCards, OfficerContext officer) {
        if (kpiCards == null || kpiCards.isBlank()) return;

        switch (kpiCards.toLowerCase().trim()) {
            case "total pending complaints" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                applyRegionalScope(qb, officer);
            }

            case "pending with me" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                if (hasValue(officer.getUserId())) {
                    qb.termFilter("assignedOfficer", officer.getUserId());
                }
            }

            case "pending with re" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                qb.termFilter("assignedRole", "RE");
            }

            case "pending at meeting scheduled" -> {
                qb.termFilter("status", "MEETING_SCHEDULED");
                applyRegionalScope(qb, officer);
            }

            case "sla breached" -> {
                qb.mustNotTerms("status", CLOSED_STATUSES);
                qb.dateRange("slaDeadline", null, LocalDate.now().minusDays(1));
                applyRegionalScope(qb, officer);
            }

            default -> log.warn("Unknown kpi_cards value: {}", kpiCards);
        }
    }

    private void applyTabsFilter(ComplaintQueryBuilder qb, String tabs, OfficerContext officer) {
        if (tabs == null || tabs.isBlank()) return;

        switch (tabs.toLowerCase().trim()) {
            case "all" -> { /* no additional filter */ }
            case "draft" -> qb.termFilter("status", "DRAFT");
            case "meeting scheduled", "meeting_scheduled" ->
                    qb.termFilter("status", "MEETING_SCHEDULED");
            case "sent back to me", "sent_back_to_me" -> {
                qb.termsFilter("status", SENT_BACK_STATUSES);
                if (hasValue(officer.getUserId())) {
                    qb.termFilter("assignedOfficer", officer.getUserId());
                }
            }
            default -> log.warn("Unknown tabs value: {}", tabs);
        }
    }

    private void applyStatusCode(ComplaintQueryBuilder qb, String statusCode, OfficerContext officer) {
        if (statusCode == null || statusCode.isBlank()) return;

        String normalized = statusCode.trim().toLowerCase();
        switch (normalized) {
            case "all complaints", "all_complaints" ->
                    applyRegionalScope(qb, officer);

            case "complaints assigned to me", "assigned_to_me" -> {
                if (hasValue(officer.getUserId())) {
                    qb.termFilter("assignedOfficer", officer.getUserId());
                }
            }

            case "complaints created by me", "created_by_me" -> {
                if (hasValue(officer.getUserId())) {
                    qb.termFilter("createdBy", officer.getUserId());
                }
            }

            default -> {
                String enumStatus = statusCode.toUpperCase().replace(" ", "_");
                qb.termFilter("status", enumStatus);
            }
        }
    }

    private void applyAdvancedSearch(ComplaintQueryBuilder qb, AdvancedSearchDTO adv) {
        if (adv == null) return;

        qb.wildcardMatch("complaintNumber", adv.getComplaintNumber());
        qb.termFilterLong("id", adv.getId());
        qb.termFilter("status", normalizeStatus(adv.getStatusCode()));
        qb.wildcardMatch("complainantName", adv.getComplainantName());
        qb.termFilter("complainantPhone", adv.getComplainantPhone());
        qb.termFilter("complainantEmail", adv.getComplainantEmail());
        qb.termFilter("filingType", adv.getFilingType());
        qb.termFilter("entityCode", adv.getEntityCode());
        qb.wildcardMatch("subject", adv.getSubject());
        qb.termFilterLong("categoryId", adv.getCategoryId());
        qb.dateRange("filedAt", adv.getFiledAtStart(), adv.getFiledAtEnd());
        qb.termFilter("fromEmailId", adv.getFromEmailId());

        applyNodalOfficerFilter(qb, adv.getNodalOfficerName());
    }

    private void applyNodalOfficerFilter(ComplaintQueryBuilder qb, String nodalOfficerName) {
        if (!hasValue(nodalOfficerName)) return;

        try {
            String pattern = "*" + nodalOfficerName.toLowerCase() + "*";
            SearchRequest nodalRequest = SearchRequest.of(b -> b
                    .index(ComplaintSearchService.NODAL_OFFICER_INDEX)
                    .query(Query.of(q -> q
                            .wildcard(w -> w
                                    .field("nodalOfficerName")
                                    .value(pattern)
                                    .caseInsensitive(true))))
                    .size(1000)
                    .source(s -> s.filter(f -> f.includes("complaintNumber"))));

            SearchResponse<Map> response = openSearchClient.search(nodalRequest, Map.class);

            List<String> complaintNumbers = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> source.get("complaintNumber"))
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .toList();

            if (!complaintNumbers.isEmpty()) {
                qb.termsFilter("complaintNumber", complaintNumbers);
            } else {
                qb.termFilter("complaintNumber", "__NO_MATCH__");
            }
        } catch (IOException e) {
            log.warn("Failed to search nodal officer index for name '{}': {}", nodalOfficerName, e.getMessage());
        }
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

    private ComplaintSearchResponse executeSearchWithNodalJoin(
            BoolQuery boolQuery, Map<String, Aggregation> aggregations,
            OfficerContext officer, Pageable pageable) {

        int from = pageable.getPageNumber() * pageable.getPageSize();
        int size = pageable.getPageSize();

        try {
            SearchRequest complaintsRequest = SearchRequest.of(b -> {
                b.index(ComplaintSearchService.COMPLAINTS_INDEX)
                        .query(Query.of(q -> q.bool(boolQuery)))
                        .from(from)
                        .size(size)
                        .trackTotalHits(th -> th.enabled(true))
                        .aggregations(aggregations);

                for (Sort.Order order : pageable.getSort()) {
                    SortOrder sortOrder = order.isAscending() ? SortOrder.Asc : SortOrder.Desc;
                    b.sort(s -> s.field(f -> f.field(order.getProperty()).order(sortOrder)));
                }

                return b;
            });

            SearchResponse<Map> complaintsResponse = openSearchClient.search(complaintsRequest, Map.class);

            long totalElements = complaintsResponse.hits().total() != null
                    ? complaintsResponse.hits().total().value() : 0;
            int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

            List<Hit<Map>> hits = complaintsResponse.hits().hits();

            List<String> complaintNumbers = hits.stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(source -> toStr(source.get("complaintNumber")))
                    .filter(Objects::nonNull)
                    .toList();

            Map<String, Map<String, Object>> nodalOfficerMap = fetchNodalOfficerRecords(complaintNumbers);

            List<ComplaintRowDTO> content = hits.stream()
                    .map(hit -> mapHitWithNodalData(hit, nodalOfficerMap))
                    .toList();

            PagedResponse<ComplaintRowDTO> pagedResponse = PagedResponse.<ComplaintRowDTO>builder()
                    .content(content)
                    .page(pageable.getPageNumber())
                    .size(size)
                    .totalElements(totalElements)
                    .totalPages(totalPages)
                    .last((pageable.getPageNumber() + 1) >= totalPages)
                    .build();

            Map<String, Aggregate> aggs = complaintsResponse.aggregations();
            KpiCountsDTO kpiCounts = aggregationService.parseKpiCounts(aggs);
            TabCountsDTO tabCounts = aggregationService.parseTabCounts(aggs);

            return ComplaintSearchResponse.builder()
                    .complaints(pagedResponse)
                    .kpiCounts(kpiCounts)
                    .tabCounts(tabCounts)
                    .build();

        } catch (IOException e) {
            log.error("OpenSearch query failed: {}", e.getMessage(), e);
            PagedResponse<ComplaintRowDTO> emptyPage = PagedResponse.<ComplaintRowDTO>builder()
                    .content(List.of())
                    .page(pageable.getPageNumber())
                    .size(size)
                    .totalElements(0)
                    .totalPages(0)
                    .last(true)
                    .build();

            return ComplaintSearchResponse.builder()
                    .complaints(emptyPage)
                    .kpiCounts(KpiCountsDTO.builder().build())
                    .tabCounts(TabCountsDTO.builder().build())
                    .build();
        }
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
    private ComplaintRowDTO mapHitWithNodalData(Hit<Map> hit, Map<String, Map<String, Object>> nodalOfficerMap) {
        Map<String, Object> source = hit.source();
        if (source == null) {
            return ComplaintRowDTO.builder().build();
        }

        String complaintNumber = toStr(source.get("complaintNumber"));

        Map<String, Object> nodalData = complaintNumber != null
                ? nodalOfficerMap.getOrDefault(complaintNumber, Collections.emptyMap())
                : Collections.emptyMap();

        return ComplaintRowDTO.builder()
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
                .nodalOfficer(toStr(nodalData.get("nodalOfficerName")))
                .principalNodalOfficer(toStr(nodalData.get("pnoName")))
                .complaintColor(null)
                .isRead(toBool(source.get("isRead")))
                .slaColor(determineSlaColor(toStr(source.get("slaDeadline"))))
                .build();
    }

    private void applyRegionalScope(ComplaintQueryBuilder qb, OfficerContext officer) {
        if (hasValue(officer.getRegionalOffice())) {
            qb.termFilter("regionalOffice", officer.getRegionalOffice());
        }
    }

    private String calculateSlaBreachIn(String slaDeadlineStr) {
        if (slaDeadlineStr == null || slaDeadlineStr.isBlank()) return null;
        try {
            LocalDate slaDate = LocalDate.parse(slaDeadlineStr.substring(0, 10), DateTimeFormatter.ISO_LOCAL_DATE);
            long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), slaDate);
            if (days < 0) return Math.abs(days) + " Days Breached";
            if (days == 0) return "Today";
            if (days <= 1) return days + " Day";
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

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
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

    private Boolean toBool(Object value) {
        if (value == null) return null;
        if (value instanceof Boolean bool) return bool;
        return Boolean.parseBoolean(value.toString());
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
