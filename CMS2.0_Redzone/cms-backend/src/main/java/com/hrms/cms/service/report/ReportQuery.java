package com.hrms.cms.service.report;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter @Setter @Builder
public class ReportQuery {

    private String subjectId;
    private List<QueryFilter> filters;
    private String groupByField;
    private String sentence;

    @Getter @Builder
    public static class QueryFilter {
        private final String field;
        private final String operator;
        private final String value;
    }
}
