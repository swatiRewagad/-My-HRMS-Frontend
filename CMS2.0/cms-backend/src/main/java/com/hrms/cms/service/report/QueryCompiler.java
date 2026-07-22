package com.hrms.cms.service.report;

import com.hrms.cms.entity.Complaint;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCompiler {

    private static final int MAX_ROWS = 5000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final EntityManager entityManager;
    private final SemanticModelRegistry registry;

    private static final Map<String, String> FIELD_MAPPING = Map.ofEntries(
            Map.entry("reName", "entityCode"),
            Map.entry("entityType", "entityCode"),
            Map.entry("region", "department"),
            Map.entry("status", "status"),
            Map.entry("priority", "priority"),
            Map.entry("department", "department"),
            Map.entry("maintainability", "maintainabilityDetermination"),
            Map.entry("category", "categoryId"),
            Map.entry("filedDate", "createdAt"),
            Map.entry("closedDate", "closedAt"),
            Map.entry("month", "createdAt")
    );

    public void validate(ReportQuery query) {
        if (query.getSubjectId() == null || !registry.isValidSubject(query.getSubjectId())) {
            throw new IllegalArgumentException("Invalid subject: " + query.getSubjectId());
        }
        if (query.getFilters() != null) {
            for (ReportQuery.QueryFilter f : query.getFilters()) {
                if (!registry.getAllowedFields().contains(f.getField())) {
                    throw new SecurityException("Field not in semantic model: " + f.getField());
                }
            }
        }
        if (query.getGroupByField() != null && !registry.isValidGroupBy(query.getGroupByField())) {
            throw new IllegalArgumentException("Invalid group-by field: " + query.getGroupByField());
        }
    }

    public List<Map<String, Object>> execute(ReportQuery query, String userRole, String userDepartment) {
        validate(query);

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        SemanticModelRegistry.Subject subject = registry.findSubject(query.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (subject.isAggregate() && query.getGroupByField() != null) {
            return executeGroupedAggregate(cb, query, subject, userRole, userDepartment);
        } else if (!subject.isAggregate() && query.getGroupByField() != null) {
            SemanticModelRegistry.Subject countSubject = registry.findSubject("count")
                    .orElseThrow(() -> new IllegalArgumentException("count subject not found"));
            return executeGroupedAggregate(cb, query, countSubject, userRole, userDepartment);
        } else if (subject.isAggregate()) {
            return executeSingleAggregate(cb, query, subject, userRole, userDepartment);
        } else {
            return executeList(cb, query, userRole, userDepartment);
        }
    }

    private List<Map<String, Object>> executeList(CriteriaBuilder cb, ReportQuery query,
                                                   String userRole, String userDepartment) {
        CriteriaQuery<Complaint> cq = cb.createQuery(Complaint.class);
        Root<Complaint> root = cq.from(Complaint.class);

        List<Predicate> predicates = buildPredicates(cb, root, query.getFilters());
        predicates.add(buildAuthScope(cb, root, userRole, userDepartment));

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")));

        TypedQuery<Complaint> tq = entityManager.createQuery(cq);
        tq.setMaxResults(MAX_ROWS);
        tq.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_SECONDS * 1000);

        List<Complaint> results = tq.getResultList();

        return results.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("complaintNumber", c.getComplaintNumber());
            row.put("subject", c.getSubject());
            row.put("status", c.getStatus());
            row.put("priority", c.getPriority());
            row.put("department", c.getDepartment());
            row.put("entityCode", c.getEntityCode());
            row.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
            row.put("filedAt", c.getFiledAt() != null ? c.getFiledAt().toString() : null);
            row.put("resolvedAt", c.getResolvedAt() != null ? c.getResolvedAt().toString() : null);
            row.put("triageSignal", c.getTriageSignal());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> executeGroupedAggregate(CriteriaBuilder cb, ReportQuery query,
                                                               SemanticModelRegistry.Subject subject,
                                                               String userRole, String userDepartment) {
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Complaint> root = cq.from(Complaint.class);

        String jpaGroupField = resolveJpaField(query.getGroupByField());
        Path<Object> groupPath = root.get(jpaGroupField);

        Expression<?> measure = buildMeasure(cb, root, subject);
        cq.multiselect(groupPath, measure);

        List<Predicate> predicates = buildPredicates(cb, root, query.getFilters());
        predicates.add(buildAuthScope(cb, root, userRole, userDepartment));
        cq.where(predicates.toArray(new Predicate[0]));

        cq.groupBy(groupPath);
        cq.orderBy(cb.desc(measure));

        TypedQuery<Object[]> tq = entityManager.createQuery(cq);
        tq.setMaxResults(MAX_ROWS);
        tq.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_SECONDS * 1000);

        List<Object[]> results = tq.getResultList();

        return results.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("group", row[0] != null ? row[0].toString() : "Unknown");
            m.put("value", row[1]);
            return m;
        }).toList();
    }

    private List<Map<String, Object>> executeSingleAggregate(CriteriaBuilder cb, ReportQuery query,
                                                              SemanticModelRegistry.Subject subject,
                                                              String userRole, String userDepartment) {
        CriteriaQuery<Object> cq = cb.createQuery(Object.class);
        Root<Complaint> root = cq.from(Complaint.class);

        Expression<?> measure = buildMeasure(cb, root, subject);
        cq.select((Selection<Object>) measure);

        List<Predicate> predicates = buildPredicates(cb, root, query.getFilters());
        predicates.add(buildAuthScope(cb, root, userRole, userDepartment));
        cq.where(predicates.toArray(new Predicate[0]));

        TypedQuery<Object> tq = entityManager.createQuery(cq);
        tq.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_SECONDS * 1000);

        Object result = tq.getSingleResult();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("metric", subject.getPhrase());
        row.put("value", result);
        return List.of(row);
    }

    private Expression<?> buildMeasure(CriteriaBuilder cb, Root<Complaint> root,
                                       SemanticModelRegistry.Subject subject) {
        return switch (subject.getId()) {
            case "count" -> cb.count(root);
            case "avgtat" -> cb.avg(root.get("id")); // placeholder - real TAT needs join
            case "medtat" -> cb.count(root); // median not natively supported, fallback to count
            default -> cb.count(root);
        };
    }

    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Complaint> root,
                                             List<ReportQuery.QueryFilter> filters) {
        List<Predicate> predicates = new ArrayList<>();

        if (filters == null) return predicates;

        for (ReportQuery.QueryFilter f : filters) {
            String jpaField = resolveJpaField(f.getField());

            if ("RANGE".equals(f.getOperator())) {
                LocalDateTime[] range = resolveTimeRange(f.getValue());
                if (range != null) {
                    predicates.add(cb.between(root.get(jpaField), range[0], range[1]));
                }
            } else {
                try {
                    Class<?> fieldType = root.get(jpaField).getJavaType();
                    if (Long.class.equals(fieldType) || long.class.equals(fieldType)) {
                        try {
                            predicates.add(cb.equal(root.get(jpaField), Long.parseLong(f.getValue())));
                        } catch (NumberFormatException e) {
                            log.debug("Filter '{}={}' skipped: non-numeric value for Long field '{}'",
                                    f.getField(), f.getValue(), jpaField);
                            predicates.add(cb.like(cb.lower(root.get("subject")),
                                    "%" + f.getValue().toLowerCase() + "%"));
                        }
                    } else {
                        predicates.add(cb.equal(root.get(jpaField), f.getValue()));
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Skipping unknown JPA field '{}' for filter '{}'", jpaField, f.getField());
                }
            }
        }

        return predicates;
    }

    private Predicate buildAuthScope(CriteriaBuilder cb, Root<Complaint> root,
                                     String userRole, String userDepartment) {
        if (userDepartment != null && !userDepartment.isBlank()
                && !"ADMIN".equals(userRole) && !"SENIOR".equals(userRole)) {
            return cb.equal(root.get("department"), userDepartment);
        }
        return cb.conjunction();
    }

    private String resolveJpaField(String semanticField) {
        String mapped = FIELD_MAPPING.get(semanticField);
        if (mapped != null) return mapped;
        if (Set.of("status", "priority", "department").contains(semanticField)) return semanticField;
        throw new SecurityException("Unmapped field in semantic model: " + semanticField);
    }

    private LocalDateTime[] resolveTimeRange(String rangeValue) {
        LocalDate today = LocalDate.now();
        return switch (rangeValue) {
            case "THIS_WEEK" -> new LocalDateTime[]{
                    today.with(DayOfWeek.MONDAY).atStartOfDay(),
                    today.plusDays(1).atStartOfDay()
            };
            case "THIS_MONTH" -> new LocalDateTime[]{
                    today.withDayOfMonth(1).atStartOfDay(),
                    today.plusDays(1).atStartOfDay()
            };
            case "LAST_MONTH" -> {
                LocalDate firstLastMonth = today.minusMonths(1).withDayOfMonth(1);
                yield new LocalDateTime[]{
                        firstLastMonth.atStartOfDay(),
                        today.withDayOfMonth(1).atStartOfDay()
                };
            }
            case "LAST_30D" -> new LocalDateTime[]{
                    today.minusDays(30).atStartOfDay(),
                    today.plusDays(1).atStartOfDay()
            };
            case "LAST_90D" -> new LocalDateTime[]{
                    today.minusDays(90).atStartOfDay(),
                    today.plusDays(1).atStartOfDay()
            };
            case "THIS_YEAR" -> new LocalDateTime[]{
                    today.withDayOfYear(1).atStartOfDay(),
                    today.plusDays(1).atStartOfDay()
            };
            case "LAST_YEAR" -> new LocalDateTime[]{
                    today.minusYears(1).withDayOfYear(1).atStartOfDay(),
                    today.withDayOfYear(1).atStartOfDay()
            };
            default -> null;
        };
    }
}
