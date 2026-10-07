package com.hrms.cms.service;

import com.hrms.cms.entity.AssistanceCategoryPrior;
import com.hrms.cms.entity.AssistanceJobLock;
import com.hrms.cms.repository.AssistanceCategoryPriorRepository;
import com.hrms.cms.repository.AssistanceJobLockRepository;
import com.hrms.cms.repository.CategoryPriorSourceRepository;
import com.hrms.cms.repository.projection.CategoryPriorProjections.LabelledText;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mines token-to-category co-occurrence from the labelled register, on a schedule, under a lease.
 *
 * <h2>Why a job and not a query</h2>
 * §6.2's governing rule is that "rollups are computed on a schedule, never on request". The aggregate
 * this produces is "tokenise every labelled complaint and group by word" — a text pass over the whole
 * labelled slice. Running it behind a filing form would put that work on every debounced keystroke. So
 * it runs here, every six hours, and the request path reads a bounded set of index ranges.
 *
 * <h2>This is a COUNT, not a prediction</h2>
 * No inference, no model, no weights, nothing trained. The output is "of the N labelled complaints
 * containing this word, M were category X", and the read reports it with the denominator attached. That
 * is what keeps this inside the same Tier 1 rule as the sibling rollups and is why the whole thing is
 * arithmetic over a word list.
 *
 * <h2>Why this rollup is the highest-leverage of the four</h2>
 * {@code COMPLAINTS.category_id} is populated on 273 of 4,403 rows (6.2%). That single gap is why
 * {@code AssistanceNextAction}, {@code AssistanceClauseAffinity} and the entity-pattern rollup all carry
 * a {@code CATEGORY_KEY} holding the wildcard sentinel on essentially every row they can write. When
 * officers start accepting the suggestions this job's output produces, those three sharpen with NO code
 * change to any of them.
 *
 * <h2>DOCUMENT frequency, not term frequency, and the denominator depends on it</h2>
 * {@code AssistanceTextTokenizer.tokenize} returns a SET, so one complaint contributes at most one to
 * any token's count however many times the word appears in it. The published denominator is "labelled
 * complaints CONTAINING this token"; counting occurrences instead would make {@code TOKEN_TOTAL} exceed
 * the corpus size and turn the share into a number with no interpretation — which is precisely the
 * figure §5.1 says an officer must be able to trust.
 *
 * <h2>What this rollup cannot say today, measured on {@code cms_db} 2026-10-07</h2>
 * Reproduce with {@code python -I CMS2.0_Redzone/scripts/measure-category-prior.py}.
 * <ul>
 *   <li>273 of 4,403 complaints carry a category. 249 of those yield at least one token; 24 are pure
 *       stopwords or digits.
 *   <li>284 distinct raw tokens; <b>85</b> clear {@link #MIN_TOKEN_SAMPLE} and
 *       {@link #MAX_DOC_FRACTION}; the table holds <b>141</b> rows. 141 rows is the honest size of this
 *       rollup against today's register.
 *   <li>COVERAGE of the 4,130 unlabelled complaints: <b>628 (15.2%)</b> would receive a suggestion.
 *       3,469 are silent for carrying fewer than {@code MIN_MATCHED_TOKENS} known tokens, and 33 fall
 *       below the confidence or lift floor. Of the 628, <b>599 point at category 1</b>.
 *   <li><b>THE LABELS ARE PARTLY WRONG AT SOURCE AND THIS JOB FAITHFULLY LEARNS THE ERROR.</b>
 *       {@code config/DemoDataSeeder.java:72} pairs its {@code categoryIds} array positionally against a
 *       {@code subjects} array ordered for a DIFFERENT taxonomy, so nine of its ten subjects carry the
 *       wrong category: "ATM card blocked" is labelled Loan/Advances, "Loan EMI overcharged" is labelled
 *       Mobile Banking/UPI, "Net banking fraud" is labelled ATM/Debit Card. 60 of the 273 labelled rows
 *       come from it, and the consequence is visible in the rollup — the token {@code atm} resolves to
 *       category 5 (Loan / Advances) on 11 of its 21 documents. THIS MUST NOT BE PATCHED HERE:
 *       correcting it in the counting would mean hard-coding a judgement about which labels are right,
 *       which is the one thing a count may not do. The fix is one line in {@code DemoDataSeeder}.
 *   <li>164 of the 273 labelled complaints carry a FIXTURE subject, so {@code retlc}, {@code ftwin} and
 *       {@code cepc} are among the thickest tokens stored. Deliberately not stopworded — see
 *       {@link AssistanceTextTokenizer#STOPWORDS}.
 *   <li>The majority class is 74% of the corpus. {@code CategorySuggestionService.MIN_LIFT} exists for
 *       exactly that and does not fix it.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistanceCategoryPriorRefreshService {

    /**
     * Labelled complaints read per page.
     *
     * <p>Keyset-paged rather than read whole, so the job's heap does not scale with the register. 2,000
     * projections of two ids and two text fields is a few megabytes at worst; the whole labelled slice
     * at once is 273 rows today and unbounded later — and "it fit in dev" is how a job comes to fail
     * only in production. Matches {@code AssistanceClauseAffinityRefreshService.PAGE_SIZE}
     * deliberately: two jobs with the same shape and different page sizes invite the reader to look for
     * a reason there is not.
     *
     * <p>The page size matters more here than for the siblings, because this is the one projection that
     * carries {@code description} — a {@code TEXT} column. A larger page would hold more complainant
     * prose in heap at once, for no benefit.
     */
    static final int PAGE_SIZE = 2_000;

    /**
     * Minimum labelled complaints before the rollup writes ANYTHING.
     *
     * <p>50. A floor on the CORPUS, distinct from the per-token floor below, and the two answer
     * different questions: {@link #MIN_TOKEN_SAMPLE} asks "is this word evidenced", this asks "is there
     * a register to learn from at all". Below 50 labelled complaints every base rate is noise, so
     * {@code MIN_LIFT} cannot discriminate, and the feature would confidently suggest whatever the first
     * few dozen complaints happened to be about.
     *
     * <p>MEASURED: the corpus is 273, so this clears comfortably today. It exists for a FRESH
     * environment — a newly provisioned tenant with twelve complaints filed would otherwise get a
     * feature that speaks with total confidence from nothing, which is the worst available failure mode
     * for a suggestion an officer is meant to trust.
     */
    static final long MIN_LABELLED_CORPUS = 50;

    /**
     * Minimum labelled complaints a token must appear in before it is stored.
     *
     * <p>THREE, and it is doing two jobs at once.
     *
     * <p>As a STATISTICAL floor it is the lowest defensible one: a word seen in two complaints that
     * happened to share a category tells you about those two complaints. Three is lower than the
     * sibling rollups' {@code MIN_COHORT_SAMPLE} of 5, and the difference is measured rather than
     * casual — at 5 the surviving vocabulary drops from 85 tokens to 40 and the table from 141 rows to
     * 78, which on a 273-complaint corpus is the difference between a feature that speaks and one that
     * does not. The floors that protect the OUTPUT are on the read side
     * ({@code MIN_MATCHED_TOKENS}, {@code MIN_CONFIDENCE}, {@code MIN_LIFT}), where they apply to the
     * suggestion an officer actually sees; this one only decides what is worth storing.
     *
     * <p>As a PII floor it is k-anonymity with k=3: no token unique to one or two complainants can be
     * persisted. That is the half of the reason it cannot be lowered to 2 to buy coverage, and it is
     * why the number is stated in both places. See {@link AssistanceTextTokenizer} for the rest of the
     * PII position, including the name-derived blocklist that was measured and rejected.
     */
    static final long MIN_TOKEN_SAMPLE = 3;

    /**
     * Maximum share of the labelled corpus a token may appear in and still be stored.
     *
     * <p>0.50. A word in more than half the complaints does not distinguish between categories — it
     * describes the register. This is the generic, data-independent mechanism for a token that carries
     * no signal, and it is the reason {@link AssistanceTextTokenizer#STOPWORDS} does not need to
     * enumerate this particular register's boilerplate exhaustively: anything it missed that is genuinely
     * universal is caught here.
     *
     * <p>MEASURED on today's corpus it removes 3 tokens, which is small — the thickest surviving token
     * ({@code withdrawal}) appears in 87 of 273, well under half. Stated plainly rather than reported as
     * a working filter: on THIS data the ceiling is nearly inert, and the stopword list is doing the
     * work. The ceiling earns its place on a corpus large enough for a genuinely universal word to
     * emerge that nobody thought to list.
     */
    static final double MAX_DOC_FRACTION = 0.50d;

    /**
     * How long a taken lease lasts.
     *
     * <p>Generous relative to the measured runtime (a pass over 273 complaints is sub-second) because
     * the failure modes are asymmetric: a lease that expires while its holder is still working readmits
     * the concurrency it exists to prevent, whereas one held too long after a crash costs at most one
     * skipped cycle. Must stay comfortably BELOW the refresh interval, or a lapsed lease would still be
     * held when the next cycle came round.
     */
    static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    private final CategoryPriorSourceRepository sourceRepository;
    private final AssistanceCategoryPriorRepository rollupRepository;
    private final AssistanceJobLockRepository lockRepository;

    /**
     * The §6.2 GLOBAL kill switch, shared with the rail and with the three sibling refreshes.
     *
     * <p>Defaults to {@code false} so an environment that never set the key does not quietly acquire a
     * scheduled text pass over every labelled complaint.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * This feature's OWN §6.2 kill switch, {@code cms.assistance.category-suggestion.enabled}.
     *
     * <h3>Why a second switch exists at all</h3>
     * This is the one assistance job that READS COMPLAINANT PROSE. Every other one reads status codes,
     * clause codes and timestamps. An operator who needs to stop a job from tokenising citizens' free
     * text — after a privacy question, a DPDP query, or a discovery that the text column contains
     * something it should not — must be able to do that without also blinding the rail, the closure
     * picker and the entity alert. That is a different and stronger reason for an independent switch
     * than the clause recommendation's, and it is why this one exists even though the global switch
     * would also stop the job.
     *
     * <h3>Why it is ANDed with {@link #assistanceEnabled} and does not replace it</h3>
     * An operator reaching for the big lever after an incident gets this feature too. A standalone flag
     * would mean the switch that was missed is the one that keeps running, which is the failure a kill
     * switch exists to prevent.
     *
     * <h3>Why the JOB honours it and not only the read</h3>
     * {@code CategorySuggestionService} checks the same pair, so turning the switch off makes the
     * suggestion disappear immediately. If only the read honoured it, this job would carry on
     * tokenising the labelled register every six hours to maintain a table nothing read — §6.2's
     * requirement is that the switch "stops the queries and not merely the display".
     *
     * <p>Note the asymmetry that remains, stated rather than hidden: turning the switch back ON makes
     * the read live before this job has run, so suggestions come from whatever the last pass left. The
     * rows carry their own {@code REFRESHED_AT} and a count from six hours ago is still a true count —
     * but it does mean the switch is NOT a way to clear the rollup. For the privacy case above, that is
     * the thing to know: flipping the switch stops the reading and the suggesting, and the already-mined
     * tokens stay in the table until the sweep or a manual {@code DELETE} removes them.
     *
     * <p>The key is {@code cms.assistance.category-suggestion.enabled}, the SAME spelling as the read
     * path, the route {@code /category-suggestion} and this job's own interval keys. Two spellings for
     * one feature is how an operator comes to turn off a display while a scheduled text pass carries on.
     */
    @Value("${cms.assistance.category-suggestion.enabled:false}")
    private boolean categorySuggestionEnabled;

    /** Diagnostics only. Identifies the pod in {@code LOCKED_BY} so a lease can be traced to a holder. */
    @Value("${HOSTNAME:unknown-host}")
    private String podIdentity;

    /**
     * One refresh cycle: take the lease, recompute, release.
     *
     * <h3>Never throws</h3>
     * A scheduled task that throws is logged by Spring and then, in some configurations, silently never
     * runs again; more importantly this job has no caller to report to. So every failure is caught,
     * logged at WARN, and the lease released. The suggestion degrades to absent when the rollup is
     * stale, empty or missing — which is the behaviour an unapplied migration relies on.
     *
     * @return the number of (token, category) rows written, or 0 if the cycle did not run
     */
    public int refresh() {
        if (!assistanceEnabled || !categorySuggestionEnabled) {
            // Not a warning. The switch being off is a choice, and a job that complained about it every
            // cycle would train operators to filter the log line that matters. BOTH keys are named in
            // the one line so an operator who turned one on and not the other can see which from the
            // log rather than by reading this class.
            log.debug("Assistance category-prior refresh skipped: cms.assistance.enabled={}, "
                    + "cms.assistance.category-suggestion.enabled={}", assistanceEnabled,
                    categorySuggestionEnabled);
            return 0;
        }

        LocalDateTime now = LocalDateTime.now();
        if (!tryAcquireLease(now)) {
            return 0;
        }

        try {
            int written = recompute(now);
            log.info("Assistance category prior refreshed: {} (token, category) rows written", written);
            return written;
        } catch (Exception e) {
            // Includes the case where the migration has not been applied — the rollup table does not
            // exist, every statement fails, and the correct outcome is a warning and no suggestion
            // rather than a job that brings attention to itself by failing loudly every cycle.
            log.warn("Assistance category-prior refresh failed; the rollup is left as it was: {}",
                    e.toString());
            return 0;
        } finally {
            releaseLease();
        }
    }

    /**
     * Wins or loses the lease, in one statement, and never throws.
     *
     * <p>A failure here is treated as "did not get the lock", which is the safe reading: the most likely
     * cause is the lease row not existing because the migration has not been applied, and in that state
     * the job must do NOTHING. Treating an error as permission to proceed would mean the one environment
     * where the lock is unavailable is the one where every pod refreshes at once.
     */
    private boolean tryAcquireLease(LocalDateTime now) {
        try {
            int taken = lockRepository.acquire(
                    AssistanceJobLock.LOCK_NAME_CATEGORY_PRIOR_REFRESH,
                    now,
                    now.plus(LEASE_DURATION),
                    podIdentity);
            if (taken == 0) {
                log.debug("Assistance category-prior refresh skipped: another pod holds the lease");
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Assistance category-prior refresh skipped: could not take the lease ({}). "
                    + "If V120 / oracle V118 has not been applied this is expected.", e.toString());
            return false;
        }
    }

    private void releaseLease() {
        try {
            // Expired one second ago rather than exactly now, so a clock that has not advanced between
            // the release and the next acquire still satisfies the acquire's `lockedUntil <= :now`.
            lockRepository.release(AssistanceJobLock.LOCK_NAME_CATEGORY_PRIOR_REFRESH,
                    LocalDateTime.now().minusSeconds(1));
        } catch (Exception e) {
            // Survivable by design: the lease expires on its own, so a failed release costs one idle
            // cycle rather than a stuck rollup. This is the reason it is a lease and not a flag.
            log.warn("Assistance category-prior lease release failed; it will expire on its own: {}",
                    e.toString());
        }
    }

    /**
     * Tokenises the labelled register, tallies word-to-category co-occurrence, upserts what qualifies.
     *
     * <h3>UPSERT, not truncate-and-reload</h3>
     * A truncate would leave the suggestion silent for the duration of every refresh — a self-inflicted
     * degradation on a feature whose only failure mode is silence. The unique key is the idempotency, so
     * each (token, category) row is found and overwritten.
     *
     * <h3>NO transaction spanning the pass, and the reason is specific</h3>
     * There is deliberately no {@code @Transactional} here, and it is not an oversight. It could not
     * work: this method is called from {@link #refresh} on {@code this}, and a self-invocation does not
     * pass through the Spring proxy, so the annotation would be INERT while reading as a guarantee — the
     * trap that is only discovered when someone relies on the rollback.
     *
     * <p>Annotating {@link #refresh} instead is worse: it catches its own exceptions, so a failed pass
     * would mark the transaction rollback-only and the commit would then throw out of a method whose
     * whole contract is never to throw. So the one modifying query that needs a boundary —
     * {@code deleteStale} — declares {@code @Transactional} on the REPOSITORY method, where the proxy IS
     * the bean, and each upsert commits on its own via {@code SimpleJpaRepository.save}.
     *
     * <p>What that costs is cross-token atomicity: a reader during a refresh can see token A updated and
     * token B not yet. What it does NOT cost is the consistency that matters — numerator, denominator and
     * both base-rate figures are four fields of ONE row written by ONE save, so no officer can be shown a
     * numerator from this pass beside a denominator from the last. Across tokens the worst a reader sees
     * is a suggestion computed from fewer tokens than the text offered: a weaker suggestion, not a wrong
     * one, and every number in it still true.
     *
     * @param stamp the single instant every row written by this pass carries. One value for the whole
     *              pass, because the stale sweep's predicate is "older than this run" — a per-row
     *              {@code now()} would make rows written late in the pass look newer than rows written
     *              early, and the sweep could not distinguish them
     */
    int recompute(LocalDateTime stamp) {
        Corpus corpus = scanLabelled();

        if (corpus.documents() < MIN_LABELLED_CORPUS) {
            // Writes NOTHING and sweeps NOTHING. Returning early before the sweep is deliberate: a
            // register that has shrunk below the floor (or a pass that read a half-seeded database)
            // must leave the last good rollup in place rather than deleting it, because the degraded
            // state this feature promises is "the previous counts" and not "no counts".
            log.info("Assistance category prior not refreshed: only {} labelled complaints carry text, "
                    + "below the {} floor. The existing rollup is left untouched.",
                    corpus.documents(), MIN_LABELLED_CORPUS);
            return 0;
        }

        int written = 0;
        for (Map.Entry<String, Tally> entry : corpus.tokens().entrySet()) {
            written += upsertQualifying(entry.getKey(), entry.getValue(), corpus, stamp);
        }

        // Only after a COMPLETE pass. A failure above propagates out of this method, so the sweep is
        // never reached with a partial tally — which would delete every token the pass had not got to
        // yet. That ordering is the whole guarantee: there is no transaction to roll the upserts back,
        // so "do not sweep on a failed pass" is enforced by control flow and nothing else.
        int swept = rollupRepository.deleteStale(stamp);
        if (swept > 0) {
            log.info("Assistance category prior: {} rows no longer clear the floors and were removed",
                    swept);
        }
        return written;
    }

    /**
     * Walks every labelled complaint, keyset-paged, tokenising each into the corpus tally.
     *
     * <p>A {@link LinkedHashMap} and not a {@code HashMap}. Iteration order has no bearing on
     * correctness — the ranking is applied on the read side and has a stated tiebreak — but it makes a
     * pass's write order reproducible, which is the difference between a diagnosable job and one whose
     * logs come out shuffled between runs over identical data.
     *
     * <p>A complaint whose text yields NO tokens is counted into neither the corpus total nor any
     * token's. The repository already excludes rows with no text at all; this additionally excludes a
     * row whose text is entirely stopwords or digits (MEASURED: 24 of 273). Counting them into
     * {@code LABELLED_TOTAL} would inflate the corpus denominator with complaints that contributed no
     * evidence, depressing every base rate and therefore every lift — the opposite of the conservative
     * direction a floor is meant to err in.
     */
    private Corpus scanLabelled() {
        Map<String, Tally> tokens = new LinkedHashMap<>();
        Map<Long, Long> categoryTotals = new LinkedHashMap<>();
        long documents = 0;
        long afterId = 0L;

        while (true) {
            List<LabelledText> page = sourceRepository.findLabelledForRollup(
                    afterId, PageRequest.of(0, PAGE_SIZE));
            if (page.isEmpty()) {
                break;
            }

            for (LabelledText row : page) {
                // The repository filters category_id IS NOT NULL, so this is non-null. Guarded anyway:
                // a null here would key a tally under null and then violate the column's NOT NULL at
                // save time, turning a data oddity into a failed pass.
                if (row.categoryId() == null) {
                    continue;
                }
                Set<String> docTokens = AssistanceTextTokenizer.tokenize(
                        row.subject(), row.description());
                if (docTokens.isEmpty()) {
                    continue;
                }
                documents++;
                categoryTotals.merge(row.categoryId(), 1L, Long::sum);
                for (String token : docTokens) {
                    tokens.computeIfAbsent(token, t -> new Tally()).count(row.categoryId());
                }
            }

            // .get(size - 1) and not getLast(): cms-backend compiles at release 17, where the Java 21
            // sequenced-collection methods do not exist.
            afterId = page.get(page.size() - 1).complaintId();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return new Corpus(documents, tokens, categoryTotals);
    }

    /**
     * Writes one token's category rows, if the token clears both floors.
     *
     * <p>A token below {@link #MIN_TOKEN_SAMPLE} or above {@link #MAX_DOC_FRACTION} writes NOTHING —
     * not even its dominant category — because both floors are statements about the DENOMINATOR, and a
     * denominator of two cannot support any share while a denominator of "most of the corpus" supports
     * one that means nothing.
     *
     * <p>There is deliberately no per-CATEGORY share floor, which is where this differs from
     * {@code AssistanceClauseAffinityRefreshService.MIN_CLAUSE_SHARE}. That rollup stores an ORDERING
     * for a 15-option picker and must drop a long tail that would otherwise be promoted above the
     * unranked options. This one stores a DISTRIBUTION the read sums across several tokens, so a
     * category holding 1 of a token's 20 documents is real evidence AGAINST that category when another
     * token points at it — dropping it would make the read's arithmetic silently optimistic, because the
     * shares it summed would no longer add to 1. The floors that protect the OUTPUT are on the read
     * side, where they apply to the suggestion an officer sees.
     *
     * <p>Rows that fail a floor are left to the stale sweep rather than deleted here: deleting would
     * mean a per-row delete for the common case of a row that never qualified, and the sweep handles the
     * one case that matters — a row that used to qualify and no longer does.
     *
     * @return how many rows were written
     */
    private int upsertQualifying(String token, Tally tally, Corpus corpus, LocalDateTime stamp) {
        long tokenTotal = tally.total();
        if (tokenTotal < MIN_TOKEN_SAMPLE) {
            return 0;
        }
        if ((double) tokenTotal / (double) corpus.documents() > MAX_DOC_FRACTION) {
            log.debug("Assistance category prior: token '{}' rejected — in {} of {} labelled "
                    + "complaints, above the {} ceiling", token, tokenTotal, corpus.documents(),
                    MAX_DOC_FRACTION);
            return 0;
        }

        int written = 0;
        for (Map.Entry<Long, Long> entry : tally.counts().entrySet()) {
            Long categoryKey = entry.getKey();
            // The category's own size in the SAME pass. Never null — a category cannot have a tally
            // without having been counted into categoryTotals on the same row — but defaulted rather
            // than unboxed, because a null here would be an NPE inside a scheduled job that is
            // contractually silent, which is the hardest failure in this class to diagnose.
            long categoryTotal = corpus.categoryTotals().getOrDefault(categoryKey, 0L);

            AssistanceCategoryPrior row = rollupRepository
                    .findByTokenAndCategory(token, categoryKey)
                    .orElseGet(() -> AssistanceCategoryPrior.builder()
                            .token(token)
                            .categoryKey(categoryKey)
                            .build());

            row.setOccurrences(entry.getValue());
            row.setTokenTotal(tokenTotal);
            row.setCategoryTotal(categoryTotal);
            row.setLabelledTotal(corpus.documents());
            row.setRefreshedAt(stamp);
            rollupRepository.save(row);
            written++;
        }
        return written;
    }

    /**
     * One pass's whole tally: the corpus size, every token, and every category's size.
     *
     * <p>The three travel together because they are three parts of ONE statement. A token's row carries
     * its own count, its token total, its category's total and the corpus total, and all four must come
     * from the same pass — a numerator from this pass beside a base rate from the last would make the
     * lift the read computes meaningless while every individual number stayed true.
     */
    record Corpus(long documents,
                  Map<String, Tally> tokens,
                  Map<Long, Long> categoryTotals) {
    }

    /** Category counts for one token, and its denominator. */
    static final class Tally {

        /**
         * A {@link LinkedHashMap} so the write order of a token's rows is reproducible across runs over
         * identical data. Correctness does not depend on it — the RANKING happens on the read side and
         * has a stated tiebreak — but a job whose log lines come out shuffled between identical runs is
         * harder to diagnose than one whose do not.
         */
        private final Map<Long, Long> counts = new LinkedHashMap<>();
        private long total;

        void count(Long categoryKey) {
            counts.merge(categoryKey, 1L, Long::sum);
            total++;
        }

        long total() {
            return total;
        }

        Map<Long, Long> counts() {
            return counts;
        }
    }
}
