package com.rbi.cms.search.service;

import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.Query;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
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

    /**
     * Matches the value against any one of several fields, as a single AND-ed clause.
     *
     * <p>For columns denormalized into more than one field — an officer's display name alongside their
     * username — where the caller may reasonably type either. Chaining {@link #wildcardMatch} per field
     * would instead require the value to match them all, which no document can satisfy.
     */
    public ComplaintQueryBuilder wildcardMatchAny(String value, String... fields) {
        if (!hasValue(value) || fields == null || fields.length == 0) return this;
        String pattern = "*" + value.toLowerCase() + "*";
        List<Query> alternatives = List.of(fields).stream()
                .map(field -> Query.of(q -> q
                        .wildcard(w -> w.field(field).value(pattern).caseInsensitive(true))))
                .toList();
        mustQueries.add(Query.of(q -> q.bool(b -> b.should(alternatives).minimumShouldMatch("1"))));
        return this;
    }

    public ComplaintQueryBuilder matchPhrasePrefix(String field, String value) {
        if (!hasValue(value)) return this;
        mustQueries.add(Query.of(q -> q
                .matchPhrasePrefix(m -> m.field(field).query(value))));
        return this;
    }

    public ComplaintQueryBuilder termsFilter(String field, List<String> values) {
        List<FieldValue> cleanValues = sanitizeStringList(values);
                
        if(cleanValues.isEmpty()) return this;
        
        filterQueries.add(Query.of(q -> q
                .terms(t -> t.field(field).terms(tv -> tv.value(cleanValues)))));
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

    public ComplaintQueryBuilder mustNotTerm(String field, String value) {
        if (!hasValue(value)) return this;
        mustNotQueries.add(Query.of(q -> q
                .term(t -> t.field(field).value(FieldValue.of(value)))));
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

    public ComplaintQueryBuilder termFilterLong(String field, Long value) {
        if (value == null) return this;
        filterQueries.add(Query.of(q -> q
                .term(t -> t.field(field).value(FieldValue.of(value)))));
        return this;
    }

    public ComplaintQueryBuilder shouldFilters(List<Query> queries) {
        if (queries == null || queries.isEmpty()) return this;
        filterQueries.add(Query.of(q -> q.bool(b -> b.should(queries))));
        return this;
    }

    public ComplaintQueryBuilder existsFilter(String field) {
        if (!hasValue(field)) return this;
        filterQueries.add(Query.of(q -> q
                .exists(e -> e.field(field))));
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

    /**
     * Records an explicit "no restriction" intent, for a request that legitimately selects every
     * complaint the caller may see. Needed because {@link #isEmpty()} is used upstream to reject
     * requests that supplied no search scope at all, and such a request would otherwise be
     * indistinguishable from one that asked for everything.
     */
    public ComplaintQueryBuilder matchAll() {
        mustQueries.add(Query.of(q -> q.matchAll(m -> m)));
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

        // if (mustQueries.isEmpty() && filterQueries.isEmpty() && mustNotQueries.isEmpty()) {
        //     builder.must(List.of(Query.of(q -> q.matchAll(m -> m))));
        // }

        return builder.build();
    }

public boolean isEmpty() { return mustQueries.isEmpty() && filterQueries.isEmpty() && mustNotQueries.isEmpty();}

    private boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    private List<FieldValue> sanitizeStringList(Collection<String> input) {
    if (input == null) return List.of();
    return input.stream()
            .filter(StringUtils::hasText)
            .map(val -> FieldValue.of(val.strip()))
            .toList();
}
}
