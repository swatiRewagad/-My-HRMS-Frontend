package com.rbi.cms.search.service;

import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class ComplaintQueryBuilder {

    private final List<Query> mustQueries = new ArrayList<>();
    private final List<Query> filterQueries = new ArrayList<>();
    private final List<Query> mustNotQueries = new ArrayList<>();

    public ComplaintQueryBuilder termFilter(String field, String value) {
        if (!hasValue(value)) return this;
        filterQueries.add(Query.of(q -> q
                .term(t -> t.field(field).value(FieldValue.of(value)))));
        return this;
    }

    public ComplaintQueryBuilder wildcardMatch(String field, String value) {
        if (!hasValue(value)) return this;
        String pattern = "*" + value.toLowerCase() + "*";
        mustQueries.add(Query.of(q -> q
                .wildcard(w -> w.field(field).value(pattern).caseInsensitive(true))));
        return this;
    }

    public ComplaintQueryBuilder matchPhrasePrefix(String field, String value) {
        if (!hasValue(value)) return this;
        mustQueries.add(Query.of(q -> q
                .matchPhrasePrefix(m -> m.field(field).query(value))));
        return this;
    }

    public ComplaintQueryBuilder termsFilter(String field, List<String> values) {
        if (values == null || values.isEmpty()) return this;
        List<FieldValue> fieldValues = values.stream()
                .map(FieldValue::of)
                .toList();
        filterQueries.add(Query.of(q -> q
                .terms(t -> t.field(field).terms(tv -> tv.value(fieldValues)))));
        return this;
    }

    public ComplaintQueryBuilder dateRange(String field, LocalDate start, LocalDate end) {
        if (start == null && end == null) return this;
        filterQueries.add(Query.of(q -> q
                .range(r -> {
                    var rangeBuilder = r.field(field);
                    if (start != null) {
                        rangeBuilder.gte(org.opensearch.client.json.JsonData.of(
                                start.format(DateTimeFormatter.ISO_LOCAL_DATE)));
                    }
                    if (end != null) {
                        rangeBuilder.lte(org.opensearch.client.json.JsonData.of(
                                end.format(DateTimeFormatter.ISO_LOCAL_DATE)));
                    }
                    return rangeBuilder;
                })));
        return this;
    }

    public ComplaintQueryBuilder mustNotTerms(String field, List<String> values) {
        if (values == null || values.isEmpty()) return this;
        List<FieldValue> fieldValues = values.stream()
                .map(FieldValue::of)
                .toList();
        mustNotQueries.add(Query.of(q -> q
                .terms(t -> t.field(field).terms(tv -> tv.value(fieldValues)))));
        return this;
    }

    public ComplaintQueryBuilder boolFilter(String field, Boolean value) {
        if (value == null) return this;
        filterQueries.add(Query.of(q -> q
                .term(t -> t.field(field).value(FieldValue.of(value)))));
        return this;
    }

    public ComplaintQueryBuilder multiMatch(String value, String... fields) {
        if (!hasValue(value)) return this;
        mustQueries.add(Query.of(q -> q
                .multiMatch(mm -> mm
                        .query(value)
                        .fields(List.of(fields))
                        .fuzziness("AUTO"))));
        return this;
    }

    public BoolQuery build() {
        BoolQuery.Builder builder = new BoolQuery.Builder();

        if (!mustQueries.isEmpty()) {
            builder.must(mustQueries);
        }
        if (!filterQueries.isEmpty()) {
            builder.filter(filterQueries);
        }
        if (!mustNotQueries.isEmpty()) {
            builder.mustNot(mustNotQueries);
        }

        if (mustQueries.isEmpty() && filterQueries.isEmpty() && mustNotQueries.isEmpty()) {
            builder.must(List.of(Query.of(q -> q.matchAll(m -> m))));
        }

        return builder.build();
    }

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }
}
