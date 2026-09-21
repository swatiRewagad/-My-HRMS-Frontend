package com.hrms.cms.service.report;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ReportColumnDefinition;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Compiles a report query into JPA Criteria and runs it.
 *
 * <h2>What changed and why (UST616-619, UST670, UST671-672)</h2>
 * Two defects were fixed here together because they compound each other.
 *
 * <p><b>Operators were cosmetic.</b> {@code buildPredicates} matched exactly one operator string,
 * {@code "RANGE"}, and sent everything else into an {@code else} that became {@code cb.equal}. BETWEEN
 * and IN could therefore never match a row, and GREATER_THAN/LESS_THAN silently returned equality
 * matches — wrong rows rather than no rows, which is why it went unnoticed. Operator handling now lives
 * in {@link ReportFilterCompiler}, which refuses what it cannot honour instead of defaulting.
 *
 * <p><b>The authorisation scope failed open.</b> {@code buildAuthScope} took a role and a department as
 * strings supplied by request headers whose {@code defaultValue}s were {@code "SENIOR"} and {@code ""}
 * — and it granted unrestricted access for either. A caller sending no headers received every complaint
 * in the database. It now takes a {@link ReportScope} resolved from the SSO token by
 * {@link ReportAccessService}, and an unidentified caller matches nothing.
 *
 * <p>The row cap and timeout are unchanged. They are the reason a compromised query is bounded, so they
 * are applied to every execution path including the aggregates.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueryCompiler {

    private static final int MAX_ROWS = 5000;
    private static final int QUERY_TIMEOUT_SECONDS = 30;

    private final EntityManager entityManager;
    private final SemanticModelRegistry registry;
    private final ReportFilterCompiler filterCompiler;
    private final ReportColumnRegistry columnRegistry;

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

    /**
     * Validates the query shape before any SQL is built.
     *
     * <p>Previously this checked field allow-listing only — never the operator, never the value. That
     * gap is why an unimplemented operator could reach {@code cb.equal} and produce plausible output.
     * The operator is now resolved here so an unknown one is a 400 at the door rather than a silent
     * equality filter deeper in.
     */
    public void validate(ReportQuery query) {
        if (query.getSubjectId() == null || !registry.isValidSubject(query.getSubjectId())) {
            throw new IllegalArgumentException("Invalid subject: " + query.getSubjectId());
        }
        if (query.getFilters() != null) {
            for (ReportQuery.QueryFilter f : query.getFilters()) {
                if (!registry.getAllowedFields().contains(f.getField())) {
                    throw new SecurityException("Field not in semantic model: " + f.getField());
                }
                ReportFilterOperator.from(f.getOperator()).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Unsupported filter operator '" + f.getOperator() + "' on field '"
                                        + f.getField() + "'. Supported: "
                                        + String.join(", ", ReportFilterOperator.names())));
            }
        }
        if (query.getGroupByField() != null && !registry.isValidGroupBy(query.getGroupByField())) {
            throw new IllegalArgumentException("Invalid group-by field: " + query.getGroupByField());
        }
    }

    /**
     * Runs the query within the caller's scope.
     *
     * <p>Notices raised while compiling filters (currently the UST617 range auto-cap) are collected into
     * {@code notices} so the caller can surface them. A silently capped range would otherwise look like
     * a complete answer to the question the user asked, which it is not.
     */
    public List<Map<String, Object>> execute(ReportQuery query, ReportScope scope, List<String> notices) {
        validate(query);

        if (scope == null || !scope.canView()) {
            // Fail closed. The previous signature took two forgeable strings and treated the absence of
            // information as permission; this refuses instead.
            throw new ReportAccessDeniedException(
                    "You do not have access to reports. Ask an administrator to grant your role report "
                            + "access.");
        }

        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        SemanticModelRegistry.Subject subject = registry.findSubject(query.getSubjectId())
                .orElseThrow(() -> new IllegalArgumentException("Subject not found"));

        if (subject.isAggregate() && query.getGroupByField() != null) {
            return executeGroupedAggregate(cb, query, subject, scope, notices);
        } else if (!subject.isAggregate() && query.getGroupByField() != null) {
            SemanticModelRegistry.Subject countSubject = registry.findSubject("count")
                    .orElseThrow(() -> new IllegalArgumentException("count subject not found"));
            return executeGroupedAggregate(cb, query, countSubject, scope, notices);
        } else if (subject.isAggregate()) {
            return executeSingleAggregate(cb, query, subject, scope, notices);
        } else {
            return executeList(cb, query, scope, notices);
        }
    }

    /** Convenience overload for callers that do not surface notices. */
    public List<Map<String, Object>> execute(ReportQuery query, ReportScope scope) {
        return execute(query, scope, new ArrayList<>());
    }

    private List<Map<String, Object>> executeList(CriteriaBuilder cb, ReportQuery query,
                                                   ReportScope scope, List<String> notices) {
        CriteriaQuery<Complaint> cq = cb.createQuery(Complaint.class);
        Root<Complaint> root = cq.from(Complaint.class);

        List<Predicate> predicates = buildPredicates(cb, root, query.getFilters(), notices);
        predicates.add(buildAuthScope(cb, root, scope));

        cq.where(predicates.toArray(new Predicate[0]));
        cq.orderBy(cb.desc(root.get("createdAt")));

        TypedQuery<Complaint> tq = entityManager.createQuery(cq);
        tq.setMaxResults(MAX_ROWS);
        tq.setHint("jakarta.persistence.query.timeout", QUERY_TIMEOUT_SECONDS * 1000);

        List<Complaint> results = tq.getResultList();

        List<ReportColumnDefinition> columns = columnRegistry.defaultColumns();
        if (columns.isEmpty()) {
            // The registry is empty or unreadable. Emitting no columns would render as "no data", so the
            // ten built-in columns remain as the floor. UST613's extra columns are additive on top.
            return results.stream().map(this::projectBuiltIn).toList();
        }

        return results.stream().map(c -> projectRegistered(c, columns)).toList();
    }

    /**
     * Projects a row using the DB-driven column registry (UST613).
     *
     * <p>{@code LinkedHashMap} is deliberate: the Angular table derives its headers from
     * {@code Object.keys(results[0])}, so insertion order is the on-screen column order, and the
     * registry's {@code DISPLAY_ORDER} is therefore what controls it.
     */
    private Map<String, Object> projectRegistered(Complaint c, List<ReportColumnDefinition> columns) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (ReportColumnDefinition column : columns) {
            Object value = readPath(c, column.getJpaPath());
            // Dates are stringified rather than left as LocalDateTime so the JSON shape matches what the
            // client has always received. Changing it would be a silent wire-format change.
            row.put(column.getColumnKey(),
                    value == null ? null : (column.isDateTime() ? value.toString() : value));
        }
        return row;
    }

    /** The original ten-column projection, retained as the fallback when the registry is unavailable. */
    private Map<String, Object> projectBuiltIn(Complaint c) {
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
    }

    /**
     * Reads one registry-named property off the entity.
     *
     * <p>Reflective rather than a switch because the point of the registry is that adding a column needs
     * no Java change. The path has already been validated against the JPA metamodel by
     * {@link ReportColumnRegistry}, so an unresolvable name cannot reach here; a read failure is
     * nonetheless logged and rendered as null rather than failing the whole report for one cell.
     */
    private Object readPath(Complaint c, String path) {
        try {
            String getter = "get" + Character.toUpperCase(path.charAt(0)) + path.substring(1);
            return Complaint.class.getMethod(getter).invoke(c);
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("Report column path '{}' could not be read from Complaint: {}", path, e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> executeGroupedAggregate(CriteriaBuilder cb, ReportQuery query,
                                                               SemanticModelRegistry.Subject subject,
                                                               ReportScope scope, List<String> notices) {
        CriteriaQuery<Object[]> cq = cb.createQuery(Object[].class);
        Root<Complaint> root = cq.from(Complaint.class);

        String jpaGroupField = resolveJpaField(query.getGroupByField());
        Path<Object> groupPath = root.get(jpaGroupField);

        Expression<?> measure = buildMeasure(cb, root, subject);
        cq.multiselect(groupPath, measure);

        List<Predicate> predicates = buildPredicates(cb, root, query.getFilters(), notices);
        predicates.add(buildAuthScope(cb, root, scope));
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
                                                              ReportScope scope, List<String> notices) {
        CriteriaQuery<Object> cq = cb.createQuery(Object.class);
        Root<Complaint> root = cq.from(Complaint.class);

        Expression<?> measure = buildMeasure(cb, root, subject);
        cq.select((Selection<Object>) measure);

        List<Predicate> predicates = buildPredicates(cb, root, query.getFilters(), notices);
        predicates.add(buildAuthScope(cb, root, scope));
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

    /**
     * Builds one predicate per filter, delegating operator semantics to {@link ReportFilterCompiler}.
     *
     * <p>A filter that cannot be honoured now propagates as {@link IllegalArgumentException} (a 400)
     * instead of being logged and dropped. Dropping was the more dangerous behaviour: removing a
     * predicate WIDENS the result set, so a user who asked for one department's complaints and mistyped
     * the value silently received everybody's.
     *
     * <p>UST672: a date filter also asserts the column is non-null, so a Complaint-Closed-On report
     * cannot include complaints that were never closed.
     */
    private List<Predicate> buildPredicates(CriteriaBuilder cb, Root<Complaint> root,
                                             List<ReportQuery.QueryFilter> filters,
                                             List<String> notices) {
        List<Predicate> predicates = new ArrayList<>();

        if (filters == null) return predicates;

        for (ReportQuery.QueryFilter f : filters) {
            String jpaField = resolveJpaField(f.getField());

            ReportFilterCompiler.CompiledFilter compiled =
                    filterCompiler.compile(cb, root, f.getField(), jpaField,
                            f.getOperator(), f.getValue());

            predicates.add(compiled.predicate());

            if (isClosureDateField(f.getField())) {
                predicates.add(filterCompiler.notNull(cb, root, jpaField));
            }

            if (compiled.notice() != null && notices != null) {
                notices.add(compiled.notice());
            }
        }

        return predicates;
    }

    /**
     * UST672's "only non-null closed dates". Matched on the SEMANTIC field name, because
     * {@code closedDate} is the name the client sends and {@code closedAt} is the column.
     */
    private boolean isClosureDateField(String semanticField) {
        return "closedDate".equals(semanticField) || "complaint_closed_on".equals(semanticField);
    }

    /**
     * Confines the query to what this caller may see.
     *
     * <p>The three outcomes are deliberate and none of them is "everything by default":
     * <ul>
     *   <li>{@code isUnrestricted()} — the caller holds an unrestricted role, so no extra predicate.</li>
     *   <li>{@code matchesNothing()} — the caller could not be identified, or has no office posting on
     *       file. {@code cb.disjunction()} is always-false, so the report returns zero rows.</li>
     *   <li>otherwise — confined to their own department.</li>
     * </ul>
     *
     * <p>The replaced version returned {@code cb.conjunction()} (always TRUE) for the unidentified case,
     * which is how a header-less request came to read the whole database.
     */
    private Predicate buildAuthScope(CriteriaBuilder cb, Root<Complaint> root, ReportScope scope) {
        if (scope == null || scope.matchesNothing()) {
            log.warn("Report scope matches nothing; returning an always-false predicate");
            return cb.disjunction();
        }
        if (scope.isUnrestricted()) {
            return cb.conjunction();
        }
        return cb.equal(root.get("department"), scope.departmentScope());
    }

    private String resolveJpaField(String semanticField) {
        String mapped = FIELD_MAPPING.get(semanticField);
        if (mapped != null) return mapped;
        if (Set.of("status", "priority", "department").contains(semanticField)) return semanticField;
        throw new SecurityException("Unmapped field in semantic model: " + semanticField);
    }

}
