package com.hrms.cms.service.report;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.service.SystemConfigService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;

/**
 * Turns a report filter into a real JPA predicate, and refuses the ones that cannot be honoured.
 *
 * <h2>Why this is a separate class from QueryCompiler</h2>
 * The operator handling is the part that was wrong and the part with all the rules, so it is isolated
 * where it can be unit-tested directly. {@code QueryCompiler}'s own tests could never have caught the
 * original defect: all thirteen of them called {@code validate()} only, and every one used
 * {@code operator("=")} — the single operator that happened to work.
 *
 * <h2>Every client-side rule is mirrored here, because a client cap is not a control</h2>
 * The Angular component enforces a 1-day minimum, a 365-day maximum with auto-cap, and an IN limit of
 * 100. All three were advisory: the range check was wired to one {@code (blur)} handler whose return
 * value was discarded and was never consulted by {@code executeReport()}, and the IN truncation ran
 * only in a keystroke handler that switching the operator bypassed. Anyone posting to
 * {@code /api/v1/reports/execute} directly was bounded by nothing at all. The bounds live in
 * SYSTEM_CONFIG so they can be tuned without a release.
 *
 * <h2>Refusals are explicit</h2>
 * An unparseable date, an unknown operator, a wrong parameter count or an over-long IN list throws
 * {@link IllegalArgumentException}, which the global handler renders as a 400 with a message. The
 * previous code logged and dropped the filter, which silently WIDENED the result set — a report that
 * quietly returns more than was asked for is the worst outcome for a PII-bearing export.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportFilterCompiler {

    private static final String CFG_MAX_RANGE_DAYS = "cms.reports.max_date_range_days";
    private static final String CFG_MIN_RANGE_DAYS = "cms.reports.min_date_range_days";
    private static final String CFG_MAX_IN_VALUES = "cms.reports.max_in_values";

    private static final int DEFAULT_MAX_RANGE_DAYS = 365;
    private static final int DEFAULT_MIN_RANGE_DAYS = 1;
    private static final int DEFAULT_MAX_IN_VALUES = 100;

    private final SystemConfigService systemConfig;

    /** The outcome of compiling one filter: a predicate, plus any notice the user should see. */
    public record CompiledFilter(Predicate predicate, String notice) {
        public static CompiledFilter of(Predicate predicate) {
            return new CompiledFilter(predicate, null);
        }
    }

    /**
     * Compiles one filter into a predicate.
     *
     * @param jpaField the already-resolved and allow-listed {@code Complaint} attribute name
     * @throws IllegalArgumentException when the filter cannot be honoured as written
     */
    public CompiledFilter compile(CriteriaBuilder cb, Root<Complaint> root,
                                  String semanticField, String jpaField,
                                  String rawOperator, String rawValue) {

        ReportFilterOperator operator = ReportFilterOperator.from(rawOperator)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported filter operator '" + rawOperator + "' on field '" + semanticField
                                + "'. Supported: " + String.join(", ", ReportFilterOperator.names())));

        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Filter on '" + semanticField + "' has no value. An empty filter would silently "
                            + "widen the report rather than narrow it.");
        }

        Path<?> path = root.get(jpaField);
        boolean temporal = isTemporal(path);

        return switch (operator) {
            case RANGE -> compileNamedRange(cb, path, semanticField, rawValue, temporal);
            case BETWEEN -> compileBetween(cb, path, semanticField, rawValue, temporal);
            case GREATER_THAN -> CompiledFilter.of(
                    temporal
                            ? cb.greaterThan(asTemporal(path), parseDateTime(semanticField, rawValue, false))
                            : cb.greaterThan(cb.lower(asString(cb, path)), rawValue.trim().toLowerCase()));
            case LESS_THAN -> CompiledFilter.of(
                    temporal
                            ? cb.lessThan(asTemporal(path), parseDateTime(semanticField, rawValue, false))
                            : cb.lessThan(cb.lower(asString(cb, path)), rawValue.trim().toLowerCase()));
            case LIKE -> compileLike(cb, path, semanticField, rawValue, temporal);
            case IN -> compileIn(cb, path, semanticField, rawValue, temporal);
            case EQUAL -> compileEqual(cb, path, semanticField, rawValue, temporal);
        };
    }

    /**
     * {@code EQUAL} on a date means "on that DAY", not "at that instant".
     *
     * <p>The wire value is an HTML {@code type="date"} string, so it carries no time. Comparing it to a
     * {@code LocalDateTime} column with {@code equal} would only match a complaint filed at exactly
     * midnight — i.e. it would almost always return nothing, and look like "there is no data" rather
     * than "this filter cannot work". A whole-day range is what the user means.
     */
    private CompiledFilter compileEqual(CriteriaBuilder cb, Path<?> path, String field,
                                        String rawValue, boolean temporal) {
        if (temporal) {
            LocalDate day = parseDate(field, rawValue);
            return CompiledFilter.of(cb.between(asTemporal(path),
                    day.atStartOfDay(), day.plusDays(1).atStartOfDay().minusNanos(1)));
        }
        if (isNumeric(path)) {
            return CompiledFilter.of(cb.equal(path, parseLong(field, rawValue)));
        }
        // Case-insensitive so a picker value cased differently from the stored value still matches.
        // UST619 additionally expects partial-prefix behaviour under Equal for identifier-like fields;
        // that is honoured by LIKE, which the UI offers for those field types.
        return CompiledFilter.of(cb.equal(cb.lower(asString(cb, path)), rawValue.trim().toLowerCase()));
    }

    /** UST618: LIKE takes exactly one parameter, and the server supplies the wildcards. */
    private CompiledFilter compileLike(CriteriaBuilder cb, Path<?> path, String field,
                                       String rawValue, boolean temporal) {
        if (temporal) {
            throw new IllegalArgumentException(
                    "The LIKE operator cannot be applied to the date field '" + field
                            + "'. Use Equal, Between, Greater than or Less than.");
        }
        if (rawValue.contains(",") || rawValue.contains("|")) {
            throw new IllegalArgumentException(
                    "The LIKE operator on '" + field + "' takes exactly one value, but received "
                            + "several. Use IN for a list.");
        }
        String escaped = escapeLike(rawValue.trim().toLowerCase());
        return CompiledFilter.of(cb.like(cb.lower(asString(cb, path)), "%" + escaped + "%", '\\'));
    }

    /** UST618: IN is capped server-side. */
    private CompiledFilter compileIn(CriteriaBuilder cb, Path<?> path, String field,
                                     String rawValue, boolean temporal) {
        if (temporal) {
            throw new IllegalArgumentException(
                    "The IN operator cannot be applied to the date field '" + field + "'.");
        }
        List<String> values = Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .distinct()
                .toList();

        if (values.isEmpty()) {
            throw new IllegalArgumentException("The IN filter on '" + field + "' lists no values.");
        }

        int maxIn = systemConfig.getInt(CFG_MAX_IN_VALUES, DEFAULT_MAX_IN_VALUES);
        if (values.size() > maxIn) {
            // Refused rather than truncated. The client truncates and warns, which is reasonable there
            // because the user can see what remains. Truncating server-side would return a confidently
            // incomplete report with no indication that values were dropped.
            throw new IllegalArgumentException(
                    "The IN filter on '" + field + "' lists " + values.size()
                            + " values; the maximum is " + maxIn + ".");
        }

        if (isNumeric(path)) {
            List<Long> numeric = values.stream().map(v -> parseLong(field, v)).toList();
            return CompiledFilter.of(path.in(numeric));
        }

        List<String> lowered = values.stream().map(String::toLowerCase).toList();
        return CompiledFilter.of(cb.lower(asString(cb, path)).in(lowered));
    }

    /**
     * UST617/671: {@code "from|to"}, with the 1-day minimum and the 1-year auto-cap enforced HERE.
     *
     * <p>The auto-cap moves the TO date to one year after the FROM date, matching the client's stated
     * behaviour ("auto-capped to 1 year from the From Date") rather than rejecting. A reversed range is
     * refused outright: the client's check used {@code Math.abs}, so {@code to < from} passed there, and
     * silently swapping the bounds would return a year of data the user did not ask for.
     */
    private CompiledFilter compileBetween(CriteriaBuilder cb, Path<?> path, String field,
                                          String rawValue, boolean temporal) {
        String[] parts = rawValue.split("\\|", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "The Between filter on '" + field + "' needs both a From and a To value.");
        }

        if (!temporal) {
            String from = parts[0].trim().toLowerCase();
            String to = parts[1].trim().toLowerCase();
            if (from.compareTo(to) > 0) {
                throw new IllegalArgumentException(
                        "The Between filter on '" + field + "' has its From value after its To value.");
            }
            return CompiledFilter.of(cb.between(cb.lower(asString(cb, path)), from, to));
        }

        LocalDate from = parseDate(field, parts[0]);
        LocalDate to = parseDate(field, parts[1]);

        if (to.isBefore(from)) {
            throw new IllegalArgumentException(
                    "The Between filter on '" + field + "' has its From date (" + from
                            + ") after its To date (" + to + ").");
        }

        int minDays = systemConfig.getInt(CFG_MIN_RANGE_DAYS, DEFAULT_MIN_RANGE_DAYS);
        int maxDays = systemConfig.getInt(CFG_MAX_RANGE_DAYS, DEFAULT_MAX_RANGE_DAYS);

        long spanDays = java.time.temporal.ChronoUnit.DAYS.between(from, to);
        if (spanDays < minDays) {
            throw new IllegalArgumentException(
                    "The date range on '" + field + "' spans " + spanDays + " day(s); the minimum is "
                            + minDays + " day(s).");
        }

        String notice = null;
        if (spanDays > maxDays) {
            LocalDate capped = from.plusDays(maxDays);
            notice = "The date range on '" + field + "' exceeded " + maxDays
                    + " days and was capped to " + capped + ".";
            log.info("Report date range auto-capped on '{}': {}..{} -> {}..{}", field, from, to, from, capped);
            to = capped;
        }

        return new CompiledFilter(
                cb.between(asTemporal(path), from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay().minusNanos(1)),
                notice);
    }

    /**
     * The named relative windows ({@code LAST_30D} and friends) that {@code SemanticModelRegistry}
     * pins on its time filters.
     */
    private CompiledFilter compileNamedRange(CriteriaBuilder cb, Path<?> path, String field,
                                             String rawValue, boolean temporal) {
        if (!temporal) {
            throw new IllegalArgumentException(
                    "A named date range was applied to the non-date field '" + field + "'.");
        }
        LocalDateTime[] window = resolveNamedWindow(rawValue.trim().toUpperCase());
        if (window == null) {
            throw new IllegalArgumentException(
                    "Unknown named date range '" + rawValue + "' on field '" + field + "'.");
        }
        return CompiledFilter.of(cb.between(asTemporal(path), window[0], window[1]));
    }

    /**
     * UST672: the Complaint-Closed-On filter must return only rows that actually have a closure date.
     *
     * <p>A range predicate on a nullable column already excludes NULLs in SQL, but this is asserted
     * explicitly because it is a stated acceptance criterion and because it documents the intent for
     * the next person: an open complaint has no closure date and must never appear in a closed-on
     * report.
     */
    public Predicate notNull(CriteriaBuilder cb, Root<Complaint> root, String jpaField) {
        return cb.isNotNull(root.get(jpaField));
    }

    private LocalDateTime[] resolveNamedWindow(String token) {
        LocalDate today = LocalDate.now();
        return switch (token) {
            case "THIS_WEEK" -> new LocalDateTime[]{
                    today.with(DayOfWeek.MONDAY).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "THIS_MONTH" -> new LocalDateTime[]{
                    today.withDayOfMonth(1).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "LAST_MONTH" -> new LocalDateTime[]{
                    today.minusMonths(1).withDayOfMonth(1).atStartOfDay(),
                    today.withDayOfMonth(1).atStartOfDay()};
            case "LAST_30D" -> new LocalDateTime[]{
                    today.minusDays(30).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "LAST_90D" -> new LocalDateTime[]{
                    today.minusDays(90).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "THIS_YEAR" -> new LocalDateTime[]{
                    today.withDayOfYear(1).atStartOfDay(), today.plusDays(1).atStartOfDay()};
            case "LAST_YEAR" -> new LocalDateTime[]{
                    today.minusYears(1).withDayOfYear(1).atStartOfDay(),
                    today.withDayOfYear(1).atStartOfDay()};
            default -> null;
        };
    }

    private boolean isTemporal(Path<?> path) {
        Class<?> type = path.getJavaType();
        return LocalDateTime.class.isAssignableFrom(type) || LocalDate.class.isAssignableFrom(type);
    }

    private boolean isNumeric(Path<?> path) {
        Class<?> type = path.getJavaType();
        return Long.class.equals(type) || long.class.equals(type)
                || Integer.class.equals(type) || int.class.equals(type);
    }

    @SuppressWarnings("unchecked")
    private Path<LocalDateTime> asTemporal(Path<?> path) {
        return (Path<LocalDateTime>) path;
    }

    /**
     * Renders a path as a string expression for case-insensitive comparison.
     *
     * <p>{@code cb.lower} needs a {@code String} expression. A non-string, non-numeric column (an enum
     * mapped as ordinal, say) would fail the cast, so those are refused with a clear message rather
     * than producing a {@code ClassCastException} 500.
     */
    @SuppressWarnings("unchecked")
    private jakarta.persistence.criteria.Expression<String> asString(CriteriaBuilder cb, Path<?> path) {
        if (!String.class.isAssignableFrom(path.getJavaType())) {
            throw new IllegalArgumentException(
                    "Field of type " + path.getJavaType().getSimpleName()
                            + " cannot be compared as text.");
        }
        return (jakarta.persistence.criteria.Expression<String>) path;
    }

    private LocalDate parseDate(String field, String raw) {
        String trimmed = raw.trim();
        try {
            // HTML date inputs send yyyy-MM-dd; a full ISO timestamp is tolerated because saved widgets
            // may hold one.
            if (trimmed.length() > 10) {
                return LocalDateTime.parse(trimmed.replace(' ', 'T')).toLocalDate();
            }
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "The value '" + raw + "' on date field '" + field
                            + "' is not a valid date (expected yyyy-MM-dd).");
        }
    }

    private LocalDateTime parseDateTime(String field, String raw, boolean endOfDay) {
        LocalDate date = parseDate(field, raw);
        return endOfDay ? date.plusDays(1).atStartOfDay().minusNanos(1) : date.atStartOfDay();
    }

    private Long parseLong(String field, String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            // The old code responded to this by filtering LOWER(subject) LIKE %value% instead — a
            // predicate on an entirely different column than the user selected. Refusing is the only
            // honest answer.
            throw new IllegalArgumentException(
                    "The value '" + raw + "' on numeric field '" + field + "' is not a number.");
        }
    }

    /**
     * Escapes the LIKE metacharacters so a user searching for a literal {@code %} or {@code _} gets
     * what they asked for rather than a wildcard match.
     */
    private String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    /** Exposed so callers can report the effective bounds without duplicating the config keys. */
    public int maxDateRangeDays() {
        return systemConfig.getInt(CFG_MAX_RANGE_DAYS, DEFAULT_MAX_RANGE_DAYS);
    }

    public int minDateRangeDays() {
        return systemConfig.getInt(CFG_MIN_RANGE_DAYS, DEFAULT_MIN_RANGE_DAYS);
    }

    public int maxInValues() {
        return systemConfig.getInt(CFG_MAX_IN_VALUES, DEFAULT_MAX_IN_VALUES);
    }
}
