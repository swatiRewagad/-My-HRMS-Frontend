package com.hrms.cms.service.report;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.*;

@Component
public class SemanticModelRegistry {

    private final List<Subject> subjects = new ArrayList<>();
    private final List<FilterDef> filters = new ArrayList<>();
    private final List<GroupByDef> groupBys = new ArrayList<>();

    @PostConstruct
    void init() {
        subjects.add(Subject.builder().id("list").phrase("all complaints").aggregate(false).column("*").build());
        subjects.add(Subject.builder().id("count").phrase("the number of complaints").aggregate(true).column("COUNT(*)").build());
        subjects.add(Subject.builder().id("avgtat").phrase("the average TAT of complaints").aggregate(true).column("AVG_TAT").build());
        subjects.add(Subject.builder().id("medtat").phrase("the median TAT of complaints").aggregate(true).column("MEDIAN_TAT").build());

        // RE category filters
        addFilter("from", "from banks", "entityType", "=", "BANK");
        addFilter("from", "from NBFCs", "entityType", "=", "NBFC");
        addFilter("from", "from prepaid wallet issuers", "entityType", "=", "PPI");
        addFilter("from", "from credit bureaus", "entityType", "=", "CIC");

        // Specific REs (seeded - extensible from DB)
        addFilter("from", "from State Bank of India", "reName", "=", "SBI");
        addFilter("from", "from HDFC Bank", "reName", "=", "HDFC");
        addFilter("from", "from ICICI Bank", "reName", "=", "ICICI");
        addFilter("from", "from Punjab National Bank", "reName", "=", "PNB");
        addFilter("from", "from Axis Bank", "reName", "=", "AXIS");
        addFilter("from", "from Kotak Mahindra Bank", "reName", "=", "KOTAK");
        addFilter("from", "from Bank of Baroda", "reName", "=", "BOB");
        addFilter("from", "from Union Bank", "reName", "=", "UNION");
        addFilter("from", "from Canara Bank", "reName", "=", "CANARA");
        addFilter("from", "from Indian Bank", "reName", "=", "INDIAN");

        // Region filters
        addFilter("in", "in the Western region", "region", "=", "WEST");
        addFilter("in", "in the Northern region", "region", "=", "NORTH");
        addFilter("in", "in the Southern region", "region", "=", "SOUTH");
        addFilter("in", "in the Eastern region", "region", "=", "EAST");
        addFilter("in", "in the North-Eastern region", "region", "=", "NORTH_EAST");

        // Status filters
        addFilter("status", "that are open", "status", "=", "pending");
        addFilter("status", "that are closed", "status", "=", "closed");
        addFilter("status", "that are in progress", "status", "=", "in_progress");
        addFilter("status", "that are resolved", "status", "=", "resolved");
        addFilter("status", "that are escalated", "status", "=", "escalated");
        addFilter("status", "that are forwarded to the RE", "status", "=", "forwarded");

        // Maintainability
        addFilter("kind", "that are maintainable", "maintainability", "=", "MAINTAINABLE");
        addFilter("kind", "that are non-maintainable", "maintainability", "=", "NON_MAINTAINABLE");

        // Time filters
        addFilter("time", "filed this week", "filedDate", "RANGE", "THIS_WEEK");
        addFilter("time", "filed this month", "filedDate", "RANGE", "THIS_MONTH");
        addFilter("time", "filed last month", "filedDate", "RANGE", "LAST_MONTH");
        addFilter("time", "filed in the last 30 days", "filedDate", "RANGE", "LAST_30D");
        addFilter("time", "filed in the last 90 days", "filedDate", "RANGE", "LAST_90D");
        addFilter("time", "filed this year", "filedDate", "RANGE", "THIS_YEAR");
        addFilter("time", "filed last year", "filedDate", "RANGE", "LAST_YEAR");
        addFilter("time", "closed this month", "closedDate", "RANGE", "THIS_MONTH");
        addFilter("time", "closed last month", "closedDate", "RANGE", "LAST_MONTH");
        addFilter("time", "closed this year", "closedDate", "RANGE", "THIS_YEAR");

        // Category / deficiency type
        addFilter("about", "about failed transactions", "category", "=", "FAILED_TXN");
        addFilter("about", "about wrong charges", "category", "=", "WRONG_CHARGE");
        addFilter("about", "about mis-selling", "category", "=", "MIS_SELLING");
        addFilter("about", "about loan issues", "category", "=", "LOAN");
        addFilter("about", "about ATM or card disputes", "category", "=", "CARD");
        addFilter("about", "about UPI transactions", "category", "=", "UPI");
        addFilter("about", "about NEFT/RTGS", "category", "=", "NEFT_RTGS");
        addFilter("about", "about deposits", "category", "=", "DEPOSIT");
        addFilter("about", "about insurance", "category", "=", "INSURANCE");

        // Priority
        addFilter("priority", "with high priority", "priority", "=", "high");
        addFilter("priority", "with medium priority", "priority", "=", "medium");
        addFilter("priority", "with low priority", "priority", "=", "low");

        // Department
        addFilter("dept", "handled by CRPC", "department", "=", "CRPC");
        addFilter("dept", "handled by RBIO", "department", "=", "RBIO");
        addFilter("dept", "handled by CEPC", "department", "=", "CEPC");

        // Group-bys
        groupBys.add(GroupByDef.builder().phrase("grouped by RE").field("reName").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by region").field("region").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by month").field("month").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by status").field("status").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by category").field("category").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by department").field("department").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by priority").field("priority").build());
        groupBys.add(GroupByDef.builder().phrase("grouped by entity type").field("entityType").build());
    }

    private void addFilter(String cat, String phrase, String field, String op, String value) {
        filters.add(FilterDef.builder().category(cat).phrase(phrase).field(field).operator(op).value(value).build());
    }

    public List<Subject> getSubjects() { return Collections.unmodifiableList(subjects); }
    public List<FilterDef> getFilters() { return Collections.unmodifiableList(filters); }
    public List<GroupByDef> getGroupBys() { return Collections.unmodifiableList(groupBys); }

    public boolean isValidSubject(String id) {
        return subjects.stream().anyMatch(s -> s.getId().equals(id));
    }

    public boolean isValidFilter(String field, String value) {
        return filters.stream().anyMatch(f -> f.getField().equals(field) && f.getValue().equals(value));
    }

    public boolean isValidGroupBy(String field) {
        return groupBys.stream().anyMatch(g -> g.getField().equals(field));
    }

    public Optional<Subject> findSubject(String id) {
        return subjects.stream().filter(s -> s.getId().equals(id)).findFirst();
    }

    public Set<String> getAllowedFields() {
        Set<String> fields = new HashSet<>();
        filters.forEach(f -> fields.add(f.getField()));
        groupBys.forEach(g -> fields.add(g.getField()));
        return fields;
    }

    @Getter @Builder
    public static class Subject {
        private final String id;
        private final String phrase;
        private final boolean aggregate;
        private final String column;
    }

    @Getter @Builder
    public static class FilterDef {
        private final String category;
        private final String phrase;
        private final String field;
        private final String operator;
        private final String value;
    }

    @Getter @Builder
    public static class GroupByDef {
        private final String phrase;
        private final String field;
    }
}
