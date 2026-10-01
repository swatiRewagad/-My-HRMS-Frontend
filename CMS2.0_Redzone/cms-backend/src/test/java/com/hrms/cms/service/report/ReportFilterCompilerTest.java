package com.hrms.cms.service.report;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.service.SystemConfigService;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The operator tests that did not exist.
 *
 * <h2>Why this file matters more than its size suggests</h2>
 * {@code QueryCompilerSecurityTest} had thirteen tests and none of them could have caught the defect
 * these cover: every one called {@code validate()} only, and every one used {@code operator("=")} — the
 * single operator that happened to work. BETWEEN, IN, LIKE, GREATER_THAN and LESS_THAN all collapsed
 * into {@code cb.equal} and no test exercised predicate construction at all.
 *
 * <p>The assertions are therefore deliberately about WHICH CriteriaBuilder call is made, not about the
 * returned {@code Predicate} object (which is an opaque mock). Verifying {@code cb.between} was invoked
 * for a BETWEEN filter is exactly the thing that was silently false before.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ReportFilterCompiler — operators are real, not cosmetic")
class ReportFilterCompilerTest {

    @Mock private SystemConfigService systemConfig;
    @Mock private CriteriaBuilder cb;
    @Mock private Root<Complaint> root;
    @Mock private Predicate predicate;

    private ReportFilterCompiler compiler;

    @BeforeEach
    void setUp() {
        compiler = new ReportFilterCompiler(systemConfig);
        // Defaults mirror the seeded SYSTEM_CONFIG rows.
        when(systemConfig.getInt(eq("cms.reports.max_date_range_days"), anyInt())).thenReturn(365);
        when(systemConfig.getInt(eq("cms.reports.min_date_range_days"), anyInt())).thenReturn(1);
        when(systemConfig.getInt(eq("cms.reports.max_in_values"), anyInt())).thenReturn(100);
    }

    /** A date column, as {@code createdAt} / {@code closedAt} really are. */
    @SuppressWarnings("unchecked")
    private void givenDateField(String jpaField) {
        Path<LocalDateTime> path = mock(Path.class);
        when(path.getJavaType()).thenReturn((Class) LocalDateTime.class);
        when(root.get(jpaField)).thenReturn((Path) path);
        when(cb.between(any(), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.greaterThan(any(), any(LocalDateTime.class))).thenReturn(predicate);
        when(cb.lessThan(any(), any(LocalDateTime.class))).thenReturn(predicate);
    }

    /** A string column, as {@code status} / {@code department} really are. */
    @SuppressWarnings("unchecked")
    private void givenStringField(String jpaField) {
        Path<String> path = mock(Path.class);
        when(path.getJavaType()).thenReturn((Class) String.class);
        when(root.get(jpaField)).thenReturn((Path) path);
        jakarta.persistence.criteria.Expression<String> lowered = mock(jakarta.persistence.criteria.Expression.class);
        when(cb.lower(any())).thenReturn(lowered);
        when(cb.equal(any(), anyString())).thenReturn(predicate);
        when(cb.like(any(), anyString(), eq('\\'))).thenReturn(predicate);
        when(cb.between(any(), anyString(), anyString())).thenReturn(predicate);
        when(cb.greaterThan(any(), anyString())).thenReturn(predicate);
        when(cb.lessThan(any(), anyString())).thenReturn(predicate);
        when(lowered.in(anyCollection())).thenReturn(predicate);
    }

    @Nested
    @DisplayName("BETWEEN — could never match a row before")
    class BetweenTests {

        @Test
        @DisplayName("parses the pipe-joined wire value into a real between predicate")
        void parsesPipeIntoBetween() {
            givenDateField("createdAt");

            ReportFilterCompiler.CompiledFilter result = compiler.compile(
                    cb, root, "filedDate", "createdAt", "BETWEEN", "2026-01-01|2026-03-01");

            assertThat(result.predicate()).isSameAs(predicate);
            assertThat(result.notice()).isNull();
            // The whole point: a real BETWEEN, with both bounds parsed as timestamps. Previously the
            // literal string "2026-01-01|2026-03-01" was handed to cb.equal on a LocalDateTime column.
            verify(cb).between(any(), eq(LocalDateTime.parse("2026-01-01T00:00")),
                    eq(LocalDateTime.parse("2026-03-02T00:00").minusNanos(1)));
            verify(cb, never()).equal(any(), anyString());
        }

        @Test
        @DisplayName("rejects a reversed range instead of silently swapping it")
        void rejectsReversedRange() {
            givenDateField("createdAt");
            // The client used Math.abs, so to < from passed validation there.
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "filedDate", "createdAt", "BETWEEN", "2026-03-01|2026-01-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("after its To date");
        }

        @Test
        @DisplayName("rejects a sub-one-day range (UST617 minimum, server-side)")
        void rejectsSubOneDayRange() {
            givenDateField("createdAt");
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "filedDate", "createdAt", "BETWEEN", "2026-01-01|2026-01-01"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("minimum is 1");
        }

        @Test
        @DisplayName("auto-caps beyond one year FROM the From Date, and says so")
        void autoCapsOverOneYear() {
            givenDateField("createdAt");

            ReportFilterCompiler.CompiledFilter result = compiler.compile(
                    cb, root, "filedDate", "createdAt", "BETWEEN", "2026-01-01|2028-01-01");

            // Capped, not refused — matching the client's stated behaviour. The notice exists so a
            // capped range is not mistaken for a complete answer.
            assertThat(result.notice()).contains("capped to 2027-01-01");
            verify(cb).between(any(), eq(LocalDateTime.parse("2026-01-01T00:00")),
                    eq(LocalDateTime.parse("2027-01-02T00:00").minusNanos(1)));
        }

        @Test
        @DisplayName("rejects a single-parameter BETWEEN")
        void rejectsMissingSecondBound() {
            givenDateField("createdAt");
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "filedDate", "createdAt", "BETWEEN", "2026-01-01|"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("both a From and a To");
        }
    }

    @Nested
    @DisplayName("IN — could never match a row before")
    class InTests {

        @Test
        @DisplayName("splits the comma list into a real in predicate")
        void splitsCommaListIntoIn() {
            givenStringField("status");

            ReportFilterCompiler.CompiledFilter result =
                    compiler.compile(cb, root, "status", "status", "IN", "closed, resolved , pending");

            assertThat(result.predicate()).isSameAs(predicate);
            // Previously the whole string "closed, resolved , pending" went to cb.equal.
            verify(cb).lower(any());
            verify(cb, never()).equal(any(), anyString());
        }

        @Test
        @DisplayName("refuses more than the configured maximum rather than truncating")
        void refusesOverLongInList() {
            givenStringField("status");
            when(systemConfig.getInt(eq("cms.reports.max_in_values"), anyInt())).thenReturn(3);

            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "status", "status", "IN", "a,b,c,d"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("the maximum is 3");
        }

        @Test
        @DisplayName("refuses an IN list that is only separators")
        void refusesEmptyInList() {
            givenStringField("status");
            assertThatThrownBy(() -> compiler.compile(cb, root, "status", "status", "IN", " , , "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("lists no values");
        }
    }

    @Nested
    @DisplayName("LIKE — was equality with no wildcard")
    class LikeTests {

        @Test
        @DisplayName("wraps the single parameter in wildcards")
        void wrapsInWildcards() {
            givenStringField("entityCode");

            compiler.compile(cb, root, "reName", "entityCode", "LIKE", "HDFC");

            verify(cb).like(any(), eq("%hdfc%"), eq('\\'));
        }

        @Test
        @DisplayName("escapes LIKE metacharacters so a literal % is not a wildcard")
        void escapesMetacharacters() {
            givenStringField("entityCode");

            compiler.compile(cb, root, "reName", "entityCode", "LIKE", "50%_x");

            verify(cb).like(any(), eq("%50\\%\\_x%"), eq('\\'));
        }

        @Test
        @DisplayName("rejects a multi-parameter LIKE (UST618 exactly one)")
        void rejectsMultiParameterLike() {
            givenStringField("entityCode");
            // Using the first value silently would tell the user both had been applied.
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "reName", "entityCode", "LIKE", "HDFC,ICICI"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("exactly one value");
        }

        @Test
        @DisplayName("refuses LIKE on a date field rather than producing nonsense")
        void refusesLikeOnDate() {
            givenDateField("createdAt");
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "filedDate", "createdAt", "LIKE", "2026"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("cannot be applied to the date field");
        }
    }

    @Nested
    @DisplayName("GREATER_THAN / LESS_THAN — silently became equality")
    class ComparisonTests {

        @Test
        @DisplayName("GREATER_THAN on a date builds a real greaterThan")
        void greaterThanOnDate() {
            givenDateField("createdAt");

            compiler.compile(cb, root, "filedDate", "createdAt", "GREATER_THAN", "2026-06-01");

            // This is the dangerous case the old code hid: equality DOES return rows, just the wrong
            // ones, so nothing looked broken.
            verify(cb).greaterThan(any(), eq(LocalDateTime.parse("2026-06-01T00:00")));
            verify(cb, never()).equal(any(), anyString());
        }

        @Test
        @DisplayName("LESS_THAN on a date builds a real lessThan")
        void lessThanOnDate() {
            givenDateField("createdAt");

            compiler.compile(cb, root, "filedDate", "createdAt", "LESS_THAN", "2026-06-01");

            verify(cb).lessThan(any(), eq(LocalDateTime.parse("2026-06-01T00:00")));
            verify(cb, never()).equal(any(), anyString());
        }
    }

    @Nested
    @DisplayName("EQUAL on a date means the whole day")
    class EqualTests {

        @Test
        @DisplayName("expands to a day-long range rather than an instant comparison")
        void expandsToWholeDay() {
            givenDateField("createdAt");

            compiler.compile(cb, root, "filedDate", "createdAt", "EQUAL", "2026-06-01");

            // An instant comparison would only match a complaint filed at exactly midnight, which reads
            // as "no data" rather than "this filter cannot work".
            verify(cb).between(any(), eq(LocalDateTime.parse("2026-06-01T00:00")),
                    eq(LocalDateTime.parse("2026-06-02T00:00").minusNanos(1)));
        }

        @Test
        @DisplayName("accepts '=' because every saved widget in the database holds it")
        void acceptsEqualsAlias() {
            givenStringField("status");

            ReportFilterCompiler.CompiledFilter result =
                    compiler.compile(cb, root, "status", "status", "=", "closed");

            assertThat(result.predicate()).isSameAs(predicate);
        }
    }

    @Nested
    @DisplayName("Refusals replace silent defaults")
    class RefusalTests {

        @Test
        @DisplayName("an unknown operator is refused, not treated as equality")
        void refusesUnknownOperator() {
            givenStringField("status");
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "status", "status", "NOT_A_REAL_OPERATOR", "closed"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported filter operator");
        }

        @Test
        @DisplayName("a null operator is refused")
        void refusesNullOperator() {
            givenStringField("status");
            assertThatThrownBy(() -> compiler.compile(cb, root, "status", "status", null, "closed"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unsupported filter operator");
        }

        @Test
        @DisplayName("an empty value is refused, because dropping it would WIDEN the report")
        void refusesEmptyValue() {
            givenStringField("status");
            // The old code logged and dropped an unusable filter, which removes a predicate and returns
            // MORE rows than asked for — the worst outcome for a PII-bearing export.
            assertThatThrownBy(() -> compiler.compile(cb, root, "status", "status", "EQUAL", "  "))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("has no value");
        }

        @Test
        @DisplayName("an unparseable date is refused with a message naming the field")
        void refusesBadDate() {
            givenDateField("createdAt");
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "filedDate", "createdAt", "EQUAL", "not-a-date"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("is not a valid date");
        }

        @Test
        @DisplayName("an unknown named range is refused rather than dropped")
        void refusesUnknownNamedRange() {
            givenDateField("createdAt");
            // resolveTimeRange returned null for an unknown token and the filter vanished.
            assertThatThrownBy(() -> compiler.compile(
                    cb, root, "filedDate", "createdAt", "RANGE", "LAST_FORTNIGHT"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown named date range");
        }
    }

    @Nested
    @DisplayName("Named ranges still work, because saved widgets depend on them")
    class NamedRangeTests {

        @Test
        @DisplayName("LAST_30D resolves to a real window")
        void resolvesLast30d() {
            givenDateField("createdAt");

            ReportFilterCompiler.CompiledFilter result =
                    compiler.compile(cb, root, "filedDate", "createdAt", "RANGE", "LAST_30D");

            assertThat(result.predicate()).isSameAs(predicate);
            verify(cb).between(any(), any(LocalDateTime.class), any(LocalDateTime.class));
        }
    }
}
