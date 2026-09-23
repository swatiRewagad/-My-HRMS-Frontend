package com.rbi.cms.search.service;

import com.rbi.cms.search.dto.KpiCountsDTO;
import com.rbi.cms.search.dto.OfficerContext;
import com.rbi.cms.search.dto.TabCountsDTO;
import lombok.extern.slf4j.Slf4j;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.opensearch.client.json.JsonData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SearchAggregationService {

    private static final List<String> CLOSED_STATUSES = List.of(
            "COMPLAINT_CLOSED", "COMPLAINT_SETTLED", "COMPLAINT_WITHDRAWN", "COMPLAINT_REJECTED"
    );

    private static final List<String> SENT_BACK_STATUSES = List.of(
            "SENT_BACK_TO_DO", "SENT_BACK_TO_REVIEWER", "SENT_BACK_TO_DEPUTY_OMBUDSMAN"
    );

    public Map<String, Aggregation> buildAggregations(OfficerContext officer) {
        Map<String, Aggregation> aggregations = new HashMap<>();

        Query pendingBase = buildPendingBaseQuery(officer);

        // KPI: totalPendingComplaints
        aggregations.put("totalPendingComplaints", Aggregation.of(a -> a
                .filter(pendingBase)));

        // KPI: pendingWithMe
        if (hasValue(officer.getUserId())) {
            aggregations.put("pendingWithMe", Aggregation.of(a -> a
                    .filter(Query.of(q -> q.bool(b -> b
                            .must(pendingBase)
                            .filter(termQuery("assignedOfficer", officer.getUserId())))))));
        }

        // KPI: pendingWithRe
        aggregations.put("pendingWithRe", Aggregation.of(a -> a
                .filter(Query.of(q -> q.bool(b -> b
                        .must(pendingBase)
                        .filter(termQuery("assignedRole", "RE")))))));

        // KPI: pendingAtMeetingSchedule
        aggregations.put("pendingAtMeetingSchedule", Aggregation.of(a -> a
                .filter(buildRegionalQuery(officer, termQuery("status", "MEETING_SCHEDULED")))));

        // KPI: slaBreached
        String today = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        aggregations.put("slaBreached", Aggregation.of(a -> a
                .filter(Query.of(q -> q.bool(b -> b
                        .must(pendingBase)
                        .filter(Query.of(rq -> rq.range(r -> r
                                .field("slaDeadline")
                                .lt(JsonData.of(today))))))))));

        // KPI: sla0To15Days
        String fifteenDaysAgo = LocalDate.now().minusDays(15).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String oneDayAgo = LocalDate.now().minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE);
        aggregations.put("sla0To15Days", Aggregation.of(a -> a
                .filter(Query.of(q -> q.bool(b -> b
                        .must(pendingBase)
                        .filter(Query.of(rq -> rq.range(r -> r
                                .field("slaDeadline")
                                .gte(JsonData.of(fifteenDaysAgo))
                                .lte(JsonData.of(oneDayAgo))))))))));

        // KPI: sla16To30Days
        String thirtyDaysAgo = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE);
        String sixteenDaysAgo = LocalDate.now().minusDays(16).format(DateTimeFormatter.ISO_LOCAL_DATE);
        aggregations.put("sla16To30Days", Aggregation.of(a -> a
                .filter(Query.of(q -> q.bool(b -> b
                        .must(pendingBase)
                        .filter(Query.of(rq -> rq.range(r -> r
                                .field("slaDeadline")
                                .gte(JsonData.of(thirtyDaysAgo))
                                .lte(JsonData.of(sixteenDaysAgo))))))))));

        // Tab: all
        aggregations.put("tabAll", Aggregation.of(a -> a
                .filter(buildRegionalBaseQuery(officer))));

        // Tab: draft
        aggregations.put("tabDraft", Aggregation.of(a -> a
                .filter(buildRegionalQuery(officer, termQuery("status", "DRAFT")))));

        // Tab: meetingScheduled
        aggregations.put("tabMeetingScheduled", Aggregation.of(a -> a
                .filter(buildRegionalQuery(officer, termQuery("status", "MEETING_SCHEDULED")))));

        // Tab: sentBackToMe
        if (hasValue(officer.getUserId())) {
            List<FieldValue> sentBackValues = SENT_BACK_STATUSES.stream()
                    .map(FieldValue::of).toList();
            aggregations.put("tabSentBackToMe", Aggregation.of(a -> a
                    .filter(Query.of(q -> q.bool(b -> {
                        b.filter(termsQuery("status", sentBackValues));
                        b.filter(termQuery("assignedOfficer", officer.getUserId()));
                        if (hasValue(officer.getRegionalOffice())) {
                            b.filter(termQuery("regionalOffice", officer.getRegionalOffice()));
                        }
                        return b;
                    })))));
        }

        return aggregations;
    }

    public KpiCountsDTO parseKpiCounts(Map<String, Aggregate> aggregations) {
        return KpiCountsDTO.builder()
                .totalPendingComplaints(getFilterCount(aggregations, "totalPendingComplaints"))
                .pendingWithMe(getFilterCount(aggregations, "pendingWithMe"))
                .pendingWithRe(getFilterCount(aggregations, "pendingWithRe"))
                .pendingAtMeetingSchedule(getFilterCount(aggregations, "pendingAtMeetingSchedule"))
                .slaBreached(getFilterCount(aggregations, "slaBreached"))
                .sla0To15Days(getFilterCount(aggregations, "sla0To15Days"))
                .sla16To30Days(getFilterCount(aggregations, "sla16To30Days"))
                .build();
    }

    public TabCountsDTO parseTabCounts(Map<String, Aggregate> aggregations) {
        return TabCountsDTO.builder()
                .all(getFilterCount(aggregations, "tabAll"))
                .draft(getFilterCount(aggregations, "tabDraft"))
                .meetingScheduled(getFilterCount(aggregations, "tabMeetingScheduled"))
                .sentBackToMe(getFilterCount(aggregations, "tabSentBackToMe"))
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

    private Query buildPendingBaseQuery(OfficerContext officer) {
        List<FieldValue> closedValues = CLOSED_STATUSES.stream()
                .map(FieldValue::of).toList();
        return Query.of(q -> q.bool(b -> {
            b.mustNot(Query.of(mq -> mq.terms(t -> t
                    .field("status")
                    .terms(tv -> tv.value(closedValues)))));
            if (hasValue(officer.getRegionalOffice())) {
                b.filter(termQuery("regionalOffice", officer.getRegionalOffice()));
            }
            return b;
        }));
    }

    private Query buildRegionalBaseQuery(OfficerContext officer) {
        if (hasValue(officer.getRegionalOffice())) {
            return termQuery("regionalOffice", officer.getRegionalOffice());
        }
        return Query.of(q -> q.matchAll(m -> m));
    }

    private Query buildRegionalQuery(OfficerContext officer, Query additionalFilter) {
        return Query.of(q -> q.bool(b -> {
            b.filter(additionalFilter);
            if (hasValue(officer.getRegionalOffice())) {
                b.filter(termQuery("regionalOffice", officer.getRegionalOffice()));
            }
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
