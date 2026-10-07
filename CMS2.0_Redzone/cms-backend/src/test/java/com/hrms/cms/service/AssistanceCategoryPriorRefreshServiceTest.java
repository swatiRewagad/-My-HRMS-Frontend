package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceCategoryPrior;
import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.repository.AssistanceCategoryPriorRepository;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.CategoryPriorSourceRepository;
import com.hrms.cms.repository.projection.CategoryPriorProjections.LabelledText;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The category-prior refresh: the two kill switches, the lease, the three floors, and the invariants the
 * denominator depends on.
 *
 * <p>Everything asserted here is a REFUSAL or an INVARIANT. A job that ran without the lease, counted a
 * repeated word twice, swept after a failed pass, or wrote a base rate from a different pass than its
 * counts would still pass a test that only asked "did it write a row" — and every one of those failures
 * is invisible in production, because a wrong rollup produces a confident suggestion rather than an
 * error.
 *
 * <h2>MUTATION-CHECKED</h2>
 * Each nested class names the mutation it catches. Deleting the corpus floor, the sample floor, the
 * document-fraction ceiling, the lease check, or the "sweep only after a complete pass" ordering each
 * fails at least one test here. A test that passes with the feature absent is worthless, so the happy
 * path is asserted exactly once and the rest of this file is about what the job REFUSES to do.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AssistanceCategoryPriorRefreshService")
class AssistanceCategoryPriorRefreshServiceTest {

    private static final long ATM_CATEGORY = 1L;
    private static final long LOAN_CATEGORY = 5L;

    @Mock private CategoryPriorSourceRepository sourceRepository;
    @Mock private AssistanceCategoryPriorRepository rollupRepository;
    @Mock private AssistanceJobLockRepository lockRepository;

    private AssistanceCategoryPriorRefreshService service;

    @BeforeEach
    void setUp() {
        service = new AssistanceCategoryPriorRefreshService(sourceRepository, rollupRepository,
                lockRepository);
        // All three are @Value FIELD-injected (the class is @RequiredArgsConstructor and Lombok does
        // not copy @Value onto generated constructor parameters), so a unit test has to set them here.
        ReflectionTestUtils.setField(service, "assistanceEnabled", true);
        ReflectionTestUtils.setField(service, "categorySuggestionEnabled", true);
        ReflectionTestUtils.setField(service, "podIdentity", "pod-under-test");

        // Default: the lease is free, the register is empty, nothing exists in the rollup yet. Each
        // test adds only the one fact it is about.
        when(lockRepository.acquire(anyString(), any(), any(), anyString())).thenReturn(1);
        when(sourceRepository.findLabelledForRollup(anyLong(), any())).thenReturn(List.of());
        when(rollupRepository.findByTokenAndCategory(anyString(), anyLong()))
                .thenReturn(Optional.empty());
        when(rollupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ─── fixtures ─────────────────────────────────────────────────────────────────────────────────

    private static final class Corpus {
        private final List<LabelledText> rows = new ArrayList<>();
        private long nextId = 1L;

        /** {@code n} complaints in {@code category} whose subject is {@code subject}. */
        Corpus add(int n, long category, String subject) {
            for (int i = 0; i < n; i++) {
                rows.add(new LabelledText(nextId++, category, subject, null));
            }
            return this;
        }

        /**
         * {@code n} complaints whose text is unique to each, so they lift the CORPUS size past
         * {@link AssistanceCategoryPriorRefreshService#MIN_LABELLED_CORPUS} without contributing any
         * token that can clear the sample floor. Each filler word appears once, so none is ever stored.
         */
        Corpus filler(int n, long category) {
            for (int i = 0; i < n; i++) {
                rows.add(new LabelledText(nextId++, category, "fillerword" + letters(i), null));
            }
            return this;
        }

        List<LabelledText> rows() {
            return rows;
        }

        /** Digit-free distinct suffixes: the tokenizer rejects anything numeric. */
        private static String letters(int n) {
            StringBuilder sb = new StringBuilder();
            int value = n + 1;
            while (value > 0) {
                sb.append((char) ('a' + (value % 26)));
                value /= 26;
            }
            return sb.toString();
        }
    }

    private void register(Corpus corpus) {
        // One page, then empty — the service stops when a page comes back shorter than PAGE_SIZE.
        when(sourceRepository.findLabelledForRollup(eq(0L), any(Pageable.class)))
                .thenReturn(corpus.rows());
    }

    private List<AssistanceCategoryPrior> savedRows() {
        ArgumentCaptor<AssistanceCategoryPrior> captor =
                ArgumentCaptor.forClass(AssistanceCategoryPrior.class);
        verify(rollupRepository, atLeast(0)).save(captor.capture());
        return captor.getAllValues();
    }

    private static AssistanceCategoryPrior row(List<AssistanceCategoryPrior> rows,
                                               String token, long category) {
        return rows.stream()
                .filter(r -> token.equals(r.getToken()) && category == r.getCategoryKey())
                .findFirst().orElse(null);
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The §6.2 kill switches")
    class KillSwitches {

        /**
         * Off means off at the FIRST statement. A disabled feature that still took a lease and still
         * tokenised every labelled complaint would cost exactly what the switch exists to stop — and
         * for THIS job that cost is reading complainant prose, which is the specific thing an operator
         * might be switching off.
         *
         * <p>MUTATION CHECK: moving the switch check below {@code tryAcquireLease} fails this test.
         */
        @Test
        @DisplayName("the global switch off means no lease and no text is read")
        void globalSwitchOff() {
            ReflectionTestUtils.setField(service, "assistanceEnabled", false);

            assertThat(service.refresh()).isZero();
            verifyNoInteractions(lockRepository, sourceRepository, rollupRepository);
        }

        /**
         * The independent switch must stop the JOB and not only the read. §6.2's requirement is that the
         * switch "stops the queries and not merely the display"; a job that carried on mining text to
         * maintain a table nothing read would be the privacy failure this switch exists to prevent.
         */
        @Test
        @DisplayName("this feature's own switch off means no lease and no text is read")
        void featureSwitchOff() {
            ReflectionTestUtils.setField(service, "categorySuggestionEnabled", false);

            assertThat(service.refresh()).isZero();
            verifyNoInteractions(lockRepository, sourceRepository, rollupRepository);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The lease")
    class Lease {

        /**
         * Losing the lease must stop the pass before it reads anything. Two pods refreshing concurrently
         * would interleave upserts against the same (token, category) keys.
         *
         * <p>MUTATION CHECK: making {@code tryAcquireLease} return true unconditionally fails this.
         */
        @Test
        @DisplayName("another pod holding it means nothing is read and nothing is written")
        void leaseHeldElsewhere() {
            when(lockRepository.acquire(anyString(), any(), any(), anyString())).thenReturn(0);

            assertThat(service.refresh()).isZero();
            verifyNoInteractions(sourceRepository);
            verify(rollupRepository, never()).save(any());
            verify(rollupRepository, never()).deleteStale(any());
        }

        /**
         * A lease row that does not exist — the unapplied-migration state — must read as "I did not get
         * the lock" and not as permission to proceed. Treating an error as permission would mean the one
         * environment where the lock is unavailable is the one where every pod refreshes at once.
         */
        @Test
        @DisplayName("a failure taking it is treated as NOT acquired, never as permission")
        void leaseFailureIsNotPermission() {
            when(lockRepository.acquire(anyString(), any(), any(), anyString()))
                    .thenThrow(new RuntimeException("Table 'ASSISTANCE_JOB_LOCK' doesn't exist"));

            assertThat(service.refresh()).isZero();
            verifyNoInteractions(sourceRepository);
        }

        /** Its OWN lease name. Sharing a row with a sibling job would make one of them never run. */
        @Test
        @DisplayName("uses its own lease name, never a sibling's")
        void ownLeaseName() {
            register(new Corpus().filler(60, ATM_CATEGORY));
            service.refresh();

            verify(lockRepository).acquire(
                    eq(AssistanceJobLock.LOCK_NAME_CATEGORY_PRIOR_REFRESH), any(), any(), anyString());
            assertThat(AssistanceJobLock.LOCK_NAME_CATEGORY_PRIOR_REFRESH)
                    .isEqualTo("assistance-category-prior-refresh")
                    .isNotEqualTo(AssistanceJobLock.LOCK_NAME_NEXT_ACTION_REFRESH)
                    .isNotEqualTo(AssistanceJobLock.LOCK_NAME_CLAUSE_AFFINITY_REFRESH)
                    .isNotEqualTo(AssistanceJobLock.LOCK_NAME_ENTITY_PATTERN_REFRESH);
        }

        /** Released even when the pass fails, so a failure costs no extra idle cycle. */
        @Test
        @DisplayName("is released after a failed pass as well as a successful one")
        void releasedOnFailure() {
            register(new Corpus().add(10, ATM_CATEGORY, "ATM withdrawal failed")
                    .filler(60, LOAN_CATEGORY));
            when(rollupRepository.save(any())).thenThrow(new RuntimeException("deadlock"));

            assertThat(service.refresh()).isZero();
            verify(lockRepository).release(
                    eq(AssistanceJobLock.LOCK_NAME_CATEGORY_PRIOR_REFRESH), any());
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Floor 1: MIN_LABELLED_CORPUS — is there a register to learn from at all")
    class CorpusFloor {

        /**
         * Below the floor the job writes NOTHING, and the specific thing this test pins is that it also
         * SWEEPS nothing. A fresh or half-seeded environment must leave the last good rollup in place
         * rather than deleting it, because the promised degraded state is "the previous counts" and not
         * "no counts".
         *
         * <p>MUTATION CHECK: moving the {@code deleteStale} call above the corpus-floor return, or
         * deleting the floor, fails this test.
         */
        @Test
        @DisplayName("a corpus below the floor writes nothing AND sweeps nothing")
        void belowCorpusFloorLeavesTheRollupAlone() {
            int justUnder = (int) AssistanceCategoryPriorRefreshService.MIN_LABELLED_CORPUS - 1;
            register(new Corpus().add(justUnder, ATM_CATEGORY, "ATM withdrawal failed"));

            assertThat(service.refresh()).isZero();
            verify(rollupRepository, never()).save(any());
            verify(rollupRepository, never()).deleteStale(any());
        }

        @Test
        @DisplayName("a corpus at the floor is enough to write")
        void atTheFloorItWrites() {
            // 20 bearing the token and 30 filler: exactly MIN_LABELLED_CORPUS documents, with the
            // token at 40% so it clears MAX_DOC_FRACTION. Padding with filler rather than making every
            // document identical is necessary throughout this file — a token present in 100% of the
            // corpus is rejected by the ceiling, so "all 50 complaints say the same thing" is a fixture
            // that tests the ceiling instead of the floor.
            register(new Corpus().add(20, ATM_CATEGORY, "withdrawal dispute").filler(30, LOAN_CATEGORY));

            assertThat(service.refresh()).isPositive();
        }

        /**
         * A complaint whose text is entirely stopwords or digits contributes no evidence and must not
         * count towards the corpus. Counting it would inflate LABELLED_TOTAL, depress every base rate
         * and therefore inflate every lift — the opposite of the conservative direction a floor errs in.
         */
        @Test
        @DisplayName("a complaint yielding no tokens counts towards neither the corpus nor a category")
        void textWithNoTokensIsNotCounted() {
            Corpus corpus = new Corpus()
                    .add(25, ATM_CATEGORY, "ATM withdrawal")
                    .filler(35, LOAN_CATEGORY)
                    .add(40, LOAN_CATEGORY, "the and not for was");   // all stopwords
            register(corpus);

            service.refresh();
            AssistanceCategoryPrior atm = row(savedRows(), "atm", ATM_CATEGORY);

            assertThat(atm).isNotNull();
            // 60, not 100: the forty stopword-only complaints contributed nothing.
            assertThat(atm.getLabelledTotal()).isEqualTo(60L);
            assertThat(atm.getCategoryTotal()).isEqualTo(25L);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Floor 2: MIN_TOKEN_SAMPLE — the k-anonymity and statistical floor")
    class TokenSampleFloor {

        /**
         * k=3. A token appearing in two complaints is both statistically meaningless and, as PII, a
         * near-identifier: it is the floor that makes a word unique to one complainant unstorable.
         *
         * <p>MUTATION CHECK: lowering MIN_TOKEN_SAMPLE to 1 or deleting the check fails this test, and
         * the failure is a PII regression rather than a precision one.
         */
        @Test
        @DisplayName("a token in fewer than three complaints is not stored at all")
        void belowSampleFloorIsNotStored() {
            Corpus corpus = new Corpus()
                    .add(2, ATM_CATEGORY, "cardcloning incident")       // 2 documents — rejected
                    .add(3, LOAN_CATEGORY, "foreclosure penalty")       // 3 documents — accepted
                    .filler(60, ATM_CATEGORY);
            register(corpus);

            service.refresh();
            List<AssistanceCategoryPrior> rows = savedRows();

            assertThat(rows).extracting(AssistanceCategoryPrior::getToken)
                    .doesNotContain("cardcloning")
                    .contains("foreclosure");
        }

        /** The constant itself, pinned: it is quoted in the migration header as a PII guarantee. */
        @Test
        @DisplayName("MIN_TOKEN_SAMPLE is 3, the value the migration header documents as k")
        void sampleFloorIsThree() {
            assertThat(AssistanceCategoryPriorRefreshService.MIN_TOKEN_SAMPLE).isEqualTo(3L);
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("Floor 3: MAX_DOC_FRACTION — a word in most of the corpus carries no signal")
    class DocumentFractionCeiling {

        /**
         * A token in more than half the register describes the register rather than distinguishing
         * between categories. This is the generic mechanism that catches boilerplate the stopword list
         * missed.
         *
         * <p>MUTATION CHECK: deleting the ceiling means {@code ubiquitousword} is stored with a
         * near-100% share and becomes the thickest row in the table, outranking every real token.
         */
        @Test
        @DisplayName("a token above the ceiling is rejected while a token below it is kept")
        void aboveTheCeilingIsRejected() {
            // 60 of 100 complaints carry 'ubiquitousword' (60%, above 0.50); 40 carry 'rarerword'.
            Corpus corpus = new Corpus()
                    .add(60, ATM_CATEGORY, "ubiquitousword here")
                    .add(40, LOAN_CATEGORY, "ubiquitousword rarerword");
            register(corpus);

            service.refresh();
            List<AssistanceCategoryPrior> rows = savedRows();

            assertThat(rows).extracting(AssistanceCategoryPrior::getToken)
                    .doesNotContain("ubiquitousword")
                    .contains("rarerword");
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("The counts, and the invariants the denominator depends on")
    class Counts {

        /**
         * THE happy path, asserted exactly once, and the four numbers that make a row a self-contained
         * statement. "Of the 30 labelled complaints containing 'withdrawal', 20 were category 1; that
         * category holds 20 of the 80 labelled complaints."
         */
        @Test
        @DisplayName("writes numerator, token denominator, category total and corpus total")
        void writesAllFourFigures() {
            Corpus corpus = new Corpus()
                    .add(20, ATM_CATEGORY, "withdrawal dispute")
                    .add(10, LOAN_CATEGORY, "withdrawal foreclosure")
                    .filler(50, LOAN_CATEGORY);
            register(corpus);

            service.refresh();
            AssistanceCategoryPrior atm = row(savedRows(), "withdrawal", ATM_CATEGORY);

            assertThat(atm).isNotNull();
            assertThat(atm.getOccurrences()).isEqualTo(20L);     // numerator
            assertThat(atm.getTokenTotal()).isEqualTo(30L);      // 20 + 10 complaints have the token
            assertThat(atm.getCategoryTotal()).isEqualTo(20L);   // category 1's own size
            assertThat(atm.getLabelledTotal()).isEqualTo(80L);   // 20 + 10 + 50
            assertThat(atm.share()).isEqualTo(20d / 30d);
            assertThat(atm.baseRate()).isEqualTo(20d / 80d);
        }

        /**
         * DOCUMENT frequency, not term frequency. One complaint contributes at most one however many
         * times the word appears in it; the published denominator is "complaints CONTAINING this token".
         *
         * <p>MUTATION CHECK: making the tokenizer return a List instead of a Set makes TOKEN_TOTAL
         * exceed the corpus size here, which is a denominator with no interpretation — and the share
         * would still look plausible, which is why this is asserted and not assumed.
         */
        @Test
        @DisplayName("a repeated word counts ONCE per complaint, so the denominator stays interpretable")
        void documentFrequencyNotTermFrequency() {
            register(new Corpus()
                    .add(25, ATM_CATEGORY, "withdrawal withdrawal withdrawal WITHDRAWAL")
                    .filler(35, LOAN_CATEGORY));

            service.refresh();
            AssistanceCategoryPrior atm = row(savedRows(), "withdrawal", ATM_CATEGORY);

            assertThat(atm).isNotNull();
            // 25, not 100: four mentions in one complaint are one complaint.
            assertThat(atm.getTokenTotal()).isEqualTo(25L);
            assertThat(atm.getTokenTotal()).isLessThanOrEqualTo(atm.getLabelledTotal());
        }

        /**
         * A token's rows must sum to its denominator, across every category. The read divides a sum of
         * shares by the matched-token count, so a distribution that does not sum to 1 would make its
         * arithmetic silently optimistic.
         *
         * <p>MUTATION CHECK: adding a per-category share floor to the refresh (the shape
         * {@code AssistanceClauseAffinityRefreshService} uses, which would look like consistency) breaks
         * this invariant and this test.
         */
        @Test
        @DisplayName("a token's category rows sum to its denominator — no per-category share floor")
        void distributionIsComplete() {
            Corpus corpus = new Corpus()
                    .add(57, ATM_CATEGORY, "withdrawal here")       // 95% of the token
                    .add(3, LOAN_CATEGORY, "withdrawal there")      // 5% — must still be stored
                    .filler(70, LOAN_CATEGORY);                     // keeps the token under the ceiling
            register(corpus);

            service.refresh();
            List<AssistanceCategoryPrior> withdrawal = savedRows().stream()
                    .filter(r -> "withdrawal".equals(r.getToken())).toList();

            assertThat(withdrawal).hasSize(2);
            assertThat(withdrawal.stream().mapToLong(AssistanceCategoryPrior::getOccurrences).sum())
                    .isEqualTo(withdrawal.get(0).getTokenTotal());
        }

        /**
         * ONE stamp for the whole pass, because the sweep's predicate is "older than this run". A per-row
         * {@code now()} would make rows written late in the pass look newer than rows written early, and
         * the sweep could not distinguish them from genuinely stale ones — it would delete part of the
         * rollup it had just written.
         */
        @Test
        @DisplayName("every row of one pass carries the SAME stamp, and the sweep uses it")
        void oneStampPerPass() {
            register(new Corpus().add(25, ATM_CATEGORY, "withdrawal dispute")
                    .filler(35, LOAN_CATEGORY));

            LocalDateTime before = LocalDateTime.now();
            service.refresh();
            List<AssistanceCategoryPrior> rows = savedRows();

            assertThat(rows).isNotEmpty();
            LocalDateTime stamp = rows.get(0).getRefreshedAt();
            assertThat(rows).allSatisfy(r -> assertThat(r.getRefreshedAt()).isEqualTo(stamp));
            assertThat(stamp).isAfterOrEqualTo(before);
            verify(rollupRepository).deleteStale(stamp);
        }

        /**
         * The sweep runs ONLY after a complete pass. A pass that aborted halfway has stamped only the
         * tokens it reached, so sweeping on its stamp would delete the rest and the feature would go
         * silent until the next successful run. There is no transaction to roll the upserts back, so
         * this ordering is enforced by control flow and nothing else.
         *
         * <p>MUTATION CHECK: moving {@code deleteStale} into a {@code finally} block — which looks like
         * robustness — fails this test.
         */
        @Test
        @DisplayName("a failed pass does NOT sweep")
        void failedPassDoesNotSweep() {
            register(new Corpus().add(25, ATM_CATEGORY, "withdrawal dispute")
                    .filler(35, LOAN_CATEGORY));
            when(rollupRepository.save(any())).thenThrow(new RuntimeException("deadlock"));

            assertThat(service.refresh()).isZero();
            verify(rollupRepository, never()).deleteStale(any());
        }

        /** Never throws, whatever happens. A scheduled job has no caller to report to. */
        @Test
        @DisplayName("never throws, even when the source read fails")
        void neverThrows() {
            when(sourceRepository.findLabelledForRollup(anyLong(), any()))
                    .thenThrow(new RuntimeException("Table 'ASSISTANCE_CATEGORY_PRIOR' doesn't exist"));

            assertThat(service.refresh()).isZero();
        }

        /**
         * UPSERT, not insert. A truncate-and-reload would leave the feature silent for the duration of
         * every refresh, and a blind insert would violate the unique key on the second pass.
         */
        @Test
        @DisplayName("overwrites an existing (token, category) row rather than inserting a duplicate")
        void upsertsRatherThanDuplicating() {
            register(new Corpus().add(25, ATM_CATEGORY, "withdrawal dispute")
                    .filler(35, LOAN_CATEGORY));
            // A row left by an EARLIER pass, carrying that pass's counts.
            AssistanceCategoryPrior existing = AssistanceCategoryPrior.builder()
                    .id(99L).token("withdrawal").categoryKey(ATM_CATEGORY)
                    .occurrences(1L).tokenTotal(1L).categoryTotal(1L).labelledTotal(1L)
                    .refreshedAt(LocalDateTime.now().minusDays(1)).build();
            when(rollupRepository.findByTokenAndCategory("withdrawal", ATM_CATEGORY))
                    .thenReturn(Optional.of(existing));

            service.refresh();
            AssistanceCategoryPrior saved = row(savedRows(), "withdrawal", ATM_CATEGORY);

            assertThat(saved).isNotNull();
            assertThat(saved.getId()).isEqualTo(99L);          // the SAME row, not a new one
            assertThat(saved.getOccurrences()).isEqualTo(25L); // with this pass's counts
        }
    }
}
