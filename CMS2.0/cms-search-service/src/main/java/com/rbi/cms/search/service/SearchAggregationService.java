package com.rbi.cms.search.service;

import com.rbi.cms.search.dto.KpiCountsResponse;
import com.rbi.cms.search.dto.OfficerPrincipal;
import com.rbi.cms.search.dto.TabCountsResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.json.JsonData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.rbi.cms.common.enums.ComplaintStatus.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchAggregationService {

    private final OfficerScopePolicy scopePolicy;

    private static final List<String> CLOSED_STATUSES = List.of(
            COMPLAINT_CLOSED.name(), COMPLAINT_SETTLED.name(), COMPLAINT_WITHDRAWN.name(), COMPLAINT_REJECTED.name()
    );

    private static final List<String> SENT_BACK_STATUSES = List.of(
            SENT_BACK_TO_DO.name(), SENT_BACK_TO_REVIEWER.name(), SENT_BACK_TO_DEPUTY_OMBUDSMAN.name()
    );

    public Map<String, Aggregation> buildAggregations(OfficerPrincipal officer, Query aggregationBaseQuery) {
    Map<String, Aggregation> aggregations = new HashMap<>();

    Query pendingBase = Query.of(q -> q.bool(b -> b
            .must(aggregationBaseQuery)
            .must(buildPendingBaseQuery(officer))
    ));

    // KPI: totalPendingComplaints
    aggregations.put("totalPendingComplaints", Aggregation.of(a -> a
            .filter(pendingBase)));

    // KPI: pendingWithMe
    if (hasValue(officer.getUserName())) {
        aggregations.put("pendingWithMe", Aggregation.of(a -> a
                .filter(Query.of(q -> q.bool(b -> b
                        .must(pendingBase)
                        .filter(termQuery("assignedOfficer.keyword", officer.getUserName())))))));
    }

    // KPI: pendingWithRe
    aggregations.put("pendingWithRe", Aggregation.of(a -> a
            .filter(Query.of(q -> q.bool(b -> b
                    .must(pendingBase)
                    .filter(termQuery("status.keyword", INFORMATION_REQUIRED.name())))))));

        // KPI: pendingAtMeetingSchedule
    aggregations.put("pendingAtMeetingScheduled", Aggregation.of(a -> a
            .filter(scopePolicy.scoped(officer, termQuery("status.keyword", MEETING_SCHEDULED.name())))));

    // KPI: slaBreached (Remains the same - looks at the past)
    String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
    aggregations.put("slaBreached", Aggregation.of(a -> a
            .filter(Query.of(q -> q.bool(b -> b
                    .must(pendingBase)
                    .filter(Query.of(rq -> rq.range(r -> r
                            .field("slaDeadline")
                            .lt(JsonData.of(today))))))))));

    // KPI: sla0To15Days (FIXED: Looks at next 1 to 15 days)
    String tomorrow = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String fifteenDaysAhead = LocalDate.now().plusDays(15).format(DateTimeFormatter.ISO_LOCAL_DATE);
    aggregations.put("sla0To15Days", Aggregation.of(a -> a
            .filter(Query.of(q -> q.bool(b -> b
                    .must(pendingBase)
                    .filter(Query.of(rq -> rq.range(r -> r
                            .field("slaDeadline")
                            .gte(JsonData.of(tomorrow))
                            .lte(JsonData.of(fifteenDaysAhead))))))))));

    // KPI: sla16To30Days (FIXED: Looks at 16 to 30 days ahead)
    String sixteenDaysAhead = LocalDate.now().plusDays(16).format(DateTimeFormatter.ISO_LOCAL_DATE);
    String thirtyDaysAhead = LocalDate.now().plusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
        aggregations.put("sla16To30Days", Aggregation.of(a -> a
            .filter(Query.of(q -> q.bool(b -> b
                    .must(pendingBase)
                    .filter(Query.of(rq -> rq.range(r -> r
                            .field("slaDeadline")
                            .gte(JsonData.of(sixteenDaysAhead))
                            .lte(JsonData.of(thirtyDaysAhead))))))))));

    // Tab: all — every complaint in the caller's department, with no status restriction.
    aggregations.put("tabAll", Aggregation.of(a -> a
            .filter(scopePolicy.scopeQuery(officer))));

    // Tab: draft
    aggregations.put("tabDraft", Aggregation.of(a -> a
            .filter(scopePolicy.scoped(officer, termQuery("status.keyword", DRAFT.name())))));

    // Tab: meetingScheduled
    aggregations.put("tabMeetingScheduled", Aggregation.of(a -> a
            .filter(scopePolicy.scoped(officer, termQuery("status.keyword", MEETING_SCHEDULED.name())))));

    // Tab: sentBackToMe
    if (hasValue(officer.getUserName())) {
        List<FieldValue> sentBackValues = SENT_BACK_STATUSES.stream()
                .map(FieldValue::of).toList();
        aggregations.put("tabSentBackToMe", Aggregation.of(a -> a
                .filter(Query.of(q -> q.bool(b -> {
                    b.filter(termsQuery("status.keyword", sentBackValues));
                    b.filter(termQuery("assignedOfficer.keyword", officer.getUserName()));
                    b.filter(scopePolicy.scopeQuery(officer));
                    return b;
                })))));
        }

            // Tab: sentToRe
    aggregations.put("tabSentToRe", Aggregation.of(a -> a
            .filter(scopePolicy.scoped(officer, termQuery("status.keyword", INFORMATION_REQUIRED.name())))));

    // Tab: responseFromRe
    aggregations.put("tabResponseFromRe", Aggregation.of(a -> a
            .filter(scopePolicy.scoped(officer, termQuery("status.keyword", SENT_TO_RBI.name())))));

    // Tab: withdrawnComplaints
    aggregations.put("tabWithdrawnComplaints", Aggregation.of(a -> a
            .filter(scopePolicy.scoped(officer, termQuery("status.keyword", COMPLAINT_CLOSED.name())))));

    return aggregations;
}

public KpiCountsResponse parseKpiCounts(Map<String, Aggregate> aggregations) {
    return KpiCountsResponse.builder()
            .totalPendingComplaints(getFilterCount(aggregations, "totalPendingComplaints"))
            .pendingWithMe(getFilterCount(aggregations, "pendingWithMe"))
            .pendingWithRe(getFilterCount(aggregations, "pendingWithRe"))
            .pendingAtMeetingScheduled(getFilterCount(aggregations, "pendingAtMeetingScheduled"))
            .slaBreached(getFilterCount(aggregations, "slaBreached"))
            .sla0To15Days(getFilterCount(aggregations, "sla0To15Days"))
            .sla16To30Days(getFilterCount(aggregations, "sla16To30Days"))
            .build();
}

    public TabCountsResponse parseTabCounts(Map<String, Aggregate> aggregations) {
        return TabCountsResponse.builder()
                .all(getFilterCount(aggregations, "tabAll"))
                .draft(getFilterCount(aggregations, "tabDraft"))
                .meetingScheduled(getFilterCount(aggregations, "tabMeetingScheduled"))
                .sentBackToMe(getFilterCount(aggregations, "tabSentBackToMe"))
                .sentToRe(getFilterCount(aggregations, "tabSentToRe"))
                .responseFromRe(getFilterCount(aggregations, "tabResponseFromRe"))
                .withdrawnComplaints(getFilterCount(aggregations, "tabWithdrawnComplaints"))
                .build();
    }

    private long getFilterCount(Map<String, Aggregate> aggregations, String name) {
        if (aggregations == null || !aggregations.containsKey(name)) {
            return 0;
        }
        try {
            return aggregations.get(name).filter().docCount();
        } catch (Exception e) {
            log.warn("Failed to parse aggregation '{}': {}", name, e.getMessage());
            return 0;
        }
    }

    private Query buildPendingBaseQuery(OfficerPrincipal officer) {
        List<FieldValue> closedValues = CLOSED_STATUSES.stream()
                .map(FieldValue::of).toList();
        Query scope = scopePolicy.scopeQuery(officer);
        return Query.of(q -> q.bool(b -> {
            b.mustNot(Query.of(mq -> mq.terms(t -> t
                    .field("status.keyword")
                    .terms(tv -> tv.value(closedValues)))));
            b.filter(scope);
            return b;
        }));
    }

    private Query termQuery(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(FieldValue.of(value))));
    }

    private Query termsQuery(String field, List<FieldValue> values) {
        return Query.of(q -> q.terms(t -> t.field(field).terms(tv -> tv.value(values))));
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
