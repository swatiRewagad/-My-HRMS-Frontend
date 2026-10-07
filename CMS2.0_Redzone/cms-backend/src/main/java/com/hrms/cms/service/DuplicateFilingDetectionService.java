package com.hrms.cms.service;

import com.hrms.cms.dto.DuplicateFilingResponse;
import com.hrms.cms.dto.DuplicateFilingResponse.Match;
import com.hrms.cms.dto.DuplicateFilingResponse.RepeatComplainant;
import com.hrms.cms.entity.AssistanceComplainantHistory;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.AssistanceComplainantHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Duplicate / repeat-filing detection, and the repeat-complainant signal. ONE feature, two faces.
 *
 * <h2>Why they are one service and not two</h2>
 * Both answers come from the SAME keyed lookup over the same complainant's filing history — the duplicate
 * signal is that history narrowed to one entity and one window, and the repeat signal is the same history
 * counted. Splitting them would mean two services issuing the same two index seeks for one screen, and
 * worse, two places where the SCOPE filter had to be applied correctly. One read, one scope filter, two
 * projections of the result.
 *
 * <h2>What each face says</h2>
 * <ul>
 *   <li>DUPLICATE DETECTION: "3 earlier complaints by this complainant against HDFC Bank in the last 30
 *       days", with the complaint numbers so the officer can verify it. Supports the real operational
 *       problem — double-handling of one grievance across CEPC and RBIO.
 *   <li>REPEAT COMPLAINANT: lifetime filing count and how many closed as non-maintainable. This is the
 *       evidence an officer currently assembles BY HAND to support a statutory {@code 16(2)(b)}
 *       "frivolous or vexatious" closure determination.
 * </ul>
 * COUNTS, never a verdict. There is no field in {@link DuplicateFilingResponse} that could express
 * "vexatious", and that is enforced by the TYPE rather than by discipline here — see
 * {@link RepeatComplainant}. The determination is a statutory discretion and may not be delegated to a
 * threshold constant.
 *
 * <h2>THE MATCH RULE: EMAIL OR PHONE, both normalised on write, placeholders suppressed</h2>
 * The two columns DISAGREE about identity in this register (16 phones carry more than one email, 1 email
 * carries more than one phone), so matching on email alone — which is what the existing
 * {@code complainant-history} rail signal does, and it says so — loses real repeat complainants.
 *
 * <p>But matching on phone naively is catastrophic here: {@code 9876543210} appears on 3,527 complaints
 * across 3,427 DISTINCT EMAILS. Unguarded, "email OR phone" flags 3,849 of 4,403 complaints (87%) as
 * duplicate filings; with the fan-out guard, 259 (5.9%). The guard runs in
 * {@code AssistanceComplainantHistoryRefreshService} on the schedule, and this service simply never sees
 * a suppressed key — it reads {@code EMAIL_KEY} and {@code PHONE_KEY} as stored and refuses the sentinel.
 *
 * <h2>THE SCOPE FILTER IS NOT OPTIONAL, AND THERE IS ONE PATH THROUGH IT</h2>
 * An officer must not learn of complaints outside their own department through this read. Every candidate
 * row passes {@link #inScope} and there is no method here that returns a row without it. The departments
 * come from the CALLER's RESOLVED role set via {@link #departmentsFor}, reusing the role-prefix map
 * {@code AssistanceQueueService} measured and already ships.
 *
 * <p>It FAILS CLOSED. A caller whose roles map to no department — an unresolved identity, a blank role, a
 * citizen, an RE user, an unrecognised role name — receives {@link DuplicateFilingResponse#empty}, never
 * an unscoped answer. That is the specific defect this guard exists to avoid repeating:
 * {@code EmailSyndicationApiController:451} accepted a client-supplied owner and returned every row in
 * the system. Failing OPEN on a PII-bearing list is how a cross-department leak is introduced.
 *
 * <p>MEASURED, and it changes how this must be TESTED: ZERO complainant emails in this register span more
 * than one department. So the scope filter removes NOTHING on today's data, and a test that merely
 * asserted "the duplicate appears" would pass identically with {@link #inScope} deleted. The unit tests
 * therefore build cross-department fixtures explicitly and assert, per role, that the out-of-scope
 * complaint is ABSENT.
 *
 * <h2>Why the request path reads rows rather than a precomputed count</h2>
 * {@code §6.2} says rollups are computed on a schedule, never on request, and this read does NOT violate
 * it — but the reasoning is worth stating because the shape differs from its three siblings.
 *
 * <p>The duplicate signal must NAME the earlier complaints: "3 earlier complaints" an officer cannot open
 * is a claim they cannot verify, which is worse than silence. A list cannot live in a cohort row, and a
 * rollup keyed on (complainant, entity, window) would need one row per possible as-of date. So the read is
 * TWO INDEX SEEKS — one per contact key, leading equality with {@code FILED_AT} as the trailing sort
 * column — unioned and counted in Java over at most {@link AssistanceComplainantHistory#MAX_HISTORY_ROWS}
 * rows. No {@code GROUP BY}, no {@code COUNT(*)}, no {@code DISTINCT}, no window function, no scan. It is
 * the same arithmetic {@code ClosureClauseRecommendationService} performs over the 64 rows of a cohort.
 *
 * <p>The bound is MEASURED and tight: the largest history behind one email in this register is 10
 * complaints, and behind one phone surviving the fan-out guard, 19. The genuine aggregate this feature
 * needs — the fan-out guard, a {@code COUNT(DISTINCT)} over the whole register — is precomputed, which is
 * where {@code §6.2} actually bites.
 *
 * <h2>Degrades to silence, never to an error</h2>
 * Every failure — an absent table (the live state, the migration ships unapplied), a timeout, an
 * unresolvable complaint, a blank role, either switch off — yields {@link DuplicateFilingResponse#empty}
 * at HTTP 200. There is no code path here that returns a 500 or lets an exception reach an officer, and
 * none that returns a 4xx: {@code GlobalExceptionHandler} maps a bare {@code RuntimeException} to 400, so
 * a leaked failure would read as a client error on a valid request.
 *
 * <h2>Suggests and highlights only</h2>
 * Nothing here blocks a filing, auto-merges two complaints, or auto-selects a closure clause. The
 * response carries no action and no selection; a client that wanted to block would have to invent the
 * decision itself. That is deliberate for a feature feeding a statutory determination — and it is why the
 * word used throughout is "earlier complaints" and never "duplicate": the server has established that the
 * same contact filed before against the same entity, which is not the same thing as establishing that the
 * grievance is the same.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DuplicateFilingDetectionService {

    /**
     * The duplicate window, in days. 30, the figure the brief names.
     *
     * <p>Configurable because the right window is an operational judgement this code cannot make: a
     * CEPC dealing official working a 30-day SLA and an Ombudsman on a statutory clock want different
     * horizons. The value is RETURNED in the response ({@code windowDays}) so a client's sentence cannot
     * go on claiming 30 days after an operator set it to 14.
     */
    static final int DEFAULT_WINDOW_DAYS = 30;

    /** Narrowest and widest windows {@link #windowDays()} will honour. */
    static final int MIN_WINDOW_DAYS = 1;
    static final int MAX_WINDOW_DAYS = 365;

    /**
     * Matches returned to the client, at most.
     *
     * <p>20. A {@code §6.2} declared cap on the RESPONSE, distinct from
     * {@link AssistanceComplainantHistory#MAX_HISTORY_ROWS} which caps the QUERY. Measured need: the
     * largest same-entity 30-day cluster in this register is 18 earlier complaints, so 20 covers the worst
     * real case; and a panel listing more than twenty cases is not a signal an officer reads, it is a
     * second complaint grid. {@code duplicateCount} reports the TRUE total even when the list is
     * truncated, so the denominator stays honest — the cap costs rows, never the count.
     */
    static final int MAX_MATCHES_RETURNED = 20;

    /**
     * Role-prefix to department, for the tenancy predicate.
     *
     * <p>COPIED DELIBERATELY from {@code AssistanceQueueService}, which measured it: all 1,752
     * {@code CEPC_DO} rows are in {@code CEPC}, all 761 {@code RBIO_OFFICER} rows in {@code RBIO}, and so
     * on for twelve of the fourteen roles present in the register. There is no role-to-department table in
     * this schema — {@code RBIO_STAFF_PROFILE} carries a department but only for RBIO staff — so the role
     * NAME is the authority, and it already encodes the department.
     *
     * <p>Copied rather than extracted into a shared helper, and that is a judgement worth stating. The
     * two uses have different consequences: in the queue service a wrong department costs a miscounted
     * triage number, and here it costs a cross-department PII leak. Sharing the map would make a future
     * widening of it — adding a prefix to fix a triage count — silently widen a privacy boundary too. The
     * duplication is the point; if the two ever need to disagree, they can.
     */
    private static final Map<String, String> ROLE_PREFIX_DEPARTMENT = Map.of(
            "CEPC", "CEPC",
            "CEPD", "CEPC",
            "CRPC", "CRPC",
            "RBIO", "RBIO",
            "ORBIO", "RBIO");

    /** Roles whose department the prefix cannot give, measured from the register. */
    private static final Map<String, String> ROLE_DEPARTMENT_OVERRIDES = Map.of(
            "DEO", "CRPC",
            "DO", "CEPC");

    /** Hard bound on the role set considered, so a pathological token list cannot widen the scope. */
    static final int MAX_ROLES = 32;

    /** ISO-8601, so the client decides presentation and no locale is baked into the wire. */
    private static final DateTimeFormatter WIRE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final AssistanceComplainantHistoryRepository historyRepository;

    /**
     * The assistance-wide kill switch.
     *
     * <p>Field injection because the class is {@code @RequiredArgsConstructor} and Lombok does not copy
     * {@code @Value} onto generated constructor parameters — a {@code final} field would be null.
     */
    @Value("${cms.assistance.enabled:false}")
    private boolean assistanceEnabled;

    /**
     * This feature's OWN {@code §6.2} kill switch, defaulting to the safe state.
     *
     * <p>{@code cms.assistance.duplicate-detection.enabled}, the SAME spelling the refresh job, the route
     * and the scheduler's interval keys use. One spelling for one feature, so an operator grepping
     * configuration for the switch finds all of it — two spellings is how a display gets turned off while
     * a scheduled table scan carries on.
     *
     * <p>Defaults to {@code false}, and for this feature that default carries more weight than for its
     * siblings: this is the one assistance surface that shows an officer another person's complaints and
     * feeds a statutory determination. It is turned on per environment by someone who has looked at the
     * scope filter and the fan-out guard, not by a default.
     */
    @Value("${cms.assistance.duplicate-detection.enabled:false}")
    private boolean duplicateDetectionEnabled;

    @Value("${cms.assistance.duplicate-detection.window-days:30}")
    private int configuredWindowDays = DEFAULT_WINDOW_DAYS;

    /**
     * The duplicate and repeat-filing signals for one complaint, to one officer.
     *
     * <p>Never throws and never returns null.
     *
     * <h3>The WHOLE role set, never a single "primary" role</h3>
     * {@code RequestIdentity.primaryRole} is {@code roles.iterator().next()} over a {@code HashSet}
     * ({@code RequestIdentityResolver:69}), so it names an ARBITRARY one of the caller's roles and can
     * name a different one across JVMs — the same officer would see a complainant's history scoped to
     * CEPC on one pod and to RBIO on another, which for a PII boundary is not a cosmetic inconsistency.
     * So the caller passes the full set and {@link #departmentsFor} unions the departments, which is the
     * only reading of "what may this caller see" that is both stable and correct: a caller holding
     * CEPC_DO and RBIO_OFFICER is responsible for work in both.
     *
     * @param complaint the complaint being worked, or null. Null is not an error — it degrades to the
     *                  empty shape, because without a complaint there is no complainant to look up.
     * @param roles     the caller's RESOLVED role set, from {@code RequestIdentity.getRoles()} and NEVER
     *                  from a request parameter or body. A null or empty set yields the empty shape: the
     *                  scope filter fails closed, because an unscoped read of one person's complaint
     *                  history is the worst outcome this feature could produce.
     */
    public DuplicateFilingResponse detect(Complaint complaint, Set<String> roles) {
        String complaintNumber = complaint == null ? null : complaint.getComplaintNumber();

        if (!assistanceEnabled || !duplicateDetectionEnabled) {
            return DuplicateFilingResponse.empty(complaintNumber);
        }
        if (complaint == null) {
            return DuplicateFilingResponse.empty(null);
        }

        try {
            Set<String> departments = departmentsFor(roles);
            if (departments.isEmpty()) {
                // FAILS CLOSED. No department means no tenancy scope, and an unscoped read of one
                // person's filing history is this feature's worst outcome. Silence is the only safe
                // answer — and it is returned without touching the repository, so a caller with no scope
                // cannot even cause the rows to be read.
                log.debug("Duplicate detection suppressed for {}: caller roles map to no department",
                        complaintNumber);
                return DuplicateFilingResponse.empty(complaintNumber);
            }

            return detectInScope(complaint, departments);
        } catch (Exception e) {
            // The live state as shipped: V121 is unapplied, so the table does not exist and this is the
            // path every call takes. WARN and not ERROR — a complaint screen with no duplicate panel is
            // fully functional, so this is a missing convenience and not an incident.
            log.warn("Duplicate detection unavailable for {}; no signal is shown. If V121 / oracle V119 "
                    + "has not been applied this is expected: {}", complaintNumber, e.toString());
            return DuplicateFilingResponse.empty(complaintNumber);
        }
    }

    /**
     * The read proper, with a non-empty department scope already established.
     *
     * <p>Split out so that the scope check cannot be bypassed by a future caller: this method is private
     * and {@link #detect} is the only way in.
     */
    private DuplicateFilingResponse detectInScope(Complaint complaint, Set<String> departments) {
        String complaintNumber = complaint.getComplaintNumber();
        String emailKey = AssistanceComplainantHistoryRefreshService
                .normaliseEmail(complaint.getComplainantEmail());
        String phoneKey = AssistanceComplainantHistoryRefreshService
                .normalisePhone(complaint.getComplainantPhone());

        if (emailKey == null && phoneKey == null) {
            // 51 of 4,403 complaints carry neither contact. There is no complainant identity to look up,
            // so there is nothing to say — not even a lifetime count of 1, because a count of 1 implies
            // the absence of others and this read established nothing of the kind.
            return DuplicateFilingResponse.empty(complaintNumber);
        }

        // THE SAME NORMALISER ON BOTH SIDES. The refresh wrote ENTITY_KEY through
        // AssistanceEntityAliasNormaliser and the read resolves the subject complaint's own dirty
        // entity_code through it too. If only one side did, the table would hold PNB's history under one
        // key and be asked for it under another — a WRONG count rather than a missing one, because what
        // came back would be one alias's partial tally presented as the entity's total.
        String entityKey = AssistanceEntityAliasNormaliser.normalise(complaint.getEntityCode());

        List<AssistanceComplainantHistory> history = readHistory(emailKey, phoneKey);
        if (history.isEmpty()) {
            return DuplicateFilingResponse.empty(complaintNumber);
        }

        // SCOPE FIRST, before any counting. Every number this method goes on to report is derived from
        // the scoped list, so an out-of-scope complaint can affect neither a count nor a denominator nor
        // the ordering — not merely be filtered out of the rendered rows.
        List<AssistanceComplainantHistory> scoped = new ArrayList<>(history.size());
        boolean scopeLimited = false;
        for (AssistanceComplainantHistory row : history) {
            if (inScope(row, departments)) {
                scoped.add(row);
            } else {
                scopeLimited = true;
            }
        }
        if (scoped.isEmpty()) {
            return DuplicateFilingResponse.empty(complaintNumber);
        }

        int windowDays = windowDays();
        LocalDateTime subjectFiledAt = subjectFiledAt(complaint, scoped);
        LocalDateTime windowStart = subjectFiledAt.minusDays(windowDays);

        // The matching ROWS, not the wire shape. Sorted as entities so the ordering is on a
        // LocalDateTime rather than on a formatted string — ISO_LOCAL_DATE_TIME omits a zero seconds
        // field, so "10:00" and "10:00:30" are different lengths and a lexicographic sort over the
        // rendered text only happens to be chronological. Sorting the typed value removes the need for
        // anyone to verify that it does.
        List<AssistanceComplainantHistory> matching = new ArrayList<>();
        int windowFilings = 0;
        int nonMaintainable = 0;

        for (AssistanceComplainantHistory row : scoped) {
            if (row.isNonMaintainableClosure()) {
                nonMaintainable++;
            }

            // EARLIER than the complaint on screen, and never the complaint itself. Keyed on the
            // complaint NUMBER and not on the timestamp: two complaints filed in the same millisecond
            // would otherwise either both be "earlier" than each other or neither, and the subject row
            // would list itself as its own duplicate.
            boolean isSubject = row.getComplaintNumber() != null
                    && row.getComplaintNumber().equals(complaintNumber);
            boolean earlier = !isSubject && !row.getFiledAt().isAfter(subjectFiledAt);
            boolean inWindow = earlier && !row.getFiledAt().isBefore(windowStart);

            if (inWindow) {
                windowFilings++;
                // SAME ENTITY ONLY for the duplicate signal, and the sentinel never matches itself: a
                // complaint naming no entity is not "the same entity" as another naming no entity, and
                // treating two unknowns as equal would group a complainant's unrelated filings into a
                // false duplicate cluster. 290 complaints carry no entity_code, so this guard is load
                // bearing rather than theoretical.
                if (entityKey != null && row.hasEntity() && entityKey.equals(row.getEntityKey())) {
                    matching.add(row);
                }
            }
        }

        // Newest first, with the complaint number as a STABLE final tiebreak. Without a total order two
        // requests over identical data could return the same cases in a different sequence, and a panel
        // that appears to rearrange itself for no reason is the §5.1 defect the clause picker documents.
        matching.sort(Comparator
                .comparing(AssistanceComplainantHistory::getFiledAt, Comparator.reverseOrder())
                .thenComparing(AssistanceComplainantHistory::getComplaintNumber));

        int duplicateCount = matching.size();
        List<AssistanceComplainantHistory> capped = duplicateCount <= MAX_MATCHES_RETURNED
                ? matching
                : matching.subList(0, MAX_MATCHES_RETURNED);

        List<Match> returned = new ArrayList<>(capped.size());
        for (AssistanceComplainantHistory row : capped) {
            returned.add(toMatch(row));
        }
        returned = List.copyOf(returned);

        RepeatComplainant repeat = new RepeatComplainant(
                scoped.size(), nonMaintainable, windowFilings, scopeLimited);

        if (duplicateCount == 0) {
            // A real negative finding, distinct from "not asked". See DuplicateFilingResponse#none.
            return DuplicateFilingResponse.none(complaintNumber, windowDays, repeat);
        }
        // duplicateCount is the TRUE total even when `returned` is truncated, so the denominator stays
        // honest and the cap costs rows rather than the count.
        return new DuplicateFilingResponse(
                complaintNumber, true, windowDays, duplicateCount, returned, repeat);
    }

    /**
     * The two seeks, unioned and de-duplicated by complaint number.
     *
     * <p>TWO queries and not one {@code OR}, and this is measured rather than stylistic: an OR across two
     * columns cannot be index-sought as a single predicate on either engine, which is the recorded reason
     * {@code ComplaintRepository.countOtherComplaintsByComplainantEmail} declined to match phone at all
     * (1 row examined with email alone against 2,509 with the OR). Two seeks recover that recall without
     * the scan — measured, phone matching adds 32 complaints of genuine duplicate signal over email alone
     * (259 against 227).
     *
     * <p>DE-DUPLICATED BY COMPLAINT NUMBER, which is load-bearing and not tidiness. A complaint carrying
     * BOTH contacts is returned by both seeks, and counting it twice would inflate the lifetime total and
     * the duplicate count — the denominator would be a lie, which is precisely the number {@code §5.1}
     * says an officer must be able to trust, and here it is a number that could support a
     * {@code 16(2)(b)} determination. A {@link LinkedHashMap} keyed on the number gives the
     * de-duplication and preserves the newest-first order the index returned.
     *
     * <p>THE SENTINEL IS REFUSED on both keys. {@code '*'} is shared by every contactless complaint and —
     * for the phone — by all 3,699 rows the fan-out guard suppressed, so seeking it would return most of
     * the register as one person's filing history. The repository javadoc states the precondition; this is
     * where it is enforced.
     */
    private List<AssistanceComplainantHistory> readHistory(String emailKey, String phoneKey) {
        Map<String, AssistanceComplainantHistory> byNumber = new LinkedHashMap<>();

        if (emailKey != null && !AssistanceComplainantHistory.KEY_ABSENT.equals(emailKey)) {
            collect(byNumber, historyRepository.findByEmailKey(emailKey,
                    PageRequest.of(0, AssistanceComplainantHistory.MAX_HISTORY_ROWS)), "email");
        }
        if (phoneKey != null && !AssistanceComplainantHistory.KEY_ABSENT.equals(phoneKey)) {
            collect(byNumber, historyRepository.findByPhoneKey(phoneKey,
                    PageRequest.of(0, AssistanceComplainantHistory.MAX_HISTORY_ROWS)), "phone");
        }
        return new ArrayList<>(byNumber.values());
    }

    /**
     * Folds one seek's rows into the union, warning if the read came back at its cap.
     *
     * <p>The WARN matters: a truncated page would make the lifetime count SHORT rather than merely
     * incomplete, and "8 lifetime filings" computed from a truncated read is a false denominator rather
     * than a conservative one. A read at the cap means either the fan-out guard has a hole or a genuinely
     * extraordinary complainant exists, and both are things an operator should see rather than infer from
     * an odd-looking total months later. The KEY is not logged — it is contact data, and a log line is a
     * place PII escapes.
     */
    private void collect(Map<String, AssistanceComplainantHistory> byNumber,
                         List<AssistanceComplainantHistory> rows,
                         String keyKind) {
        if (rows.size() >= AssistanceComplainantHistory.MAX_HISTORY_ROWS) {
            log.warn("Duplicate detection: the {} history read hit its {}-row cap, so the lifetime count "
                            + "reported is a FLOOR rather than a total. Check the contact fan-out guard.",
                    keyKind, AssistanceComplainantHistory.MAX_HISTORY_ROWS);
        }
        for (AssistanceComplainantHistory row : rows) {
            if (row.getComplaintNumber() == null || row.getFiledAt() == null) {
                // FILED_AT is NOT NULL in the schema, so this is defensive against a hand-edited row
                // rather than expected. Dropped rather than defaulted: a row with no date cannot be
                // placed inside or outside the window, and guessing would decide a duplicate finding.
                continue;
            }
            byNumber.putIfAbsent(row.getComplaintNumber(), row);
        }
    }

    /**
     * THE SCOPE PREDICATE. Whether this row is inside the caller's department scope.
     *
     * <p>Every candidate row passes through here and there is no path around it. The rule:
     * <ul>
     *   <li>The row's department must be one the caller's roles are responsible for.
     *   <li>The {@code '*'} SENTINEL IS NEVER IN SCOPE, for anyone. 23 of 4,403 complaints carry no
     *       department, and a complaint whose department is unknown cannot be PROVEN to be in the
     *       caller's. The safe reading of an unprovable scope on a PII-bearing read is EXCLUSION — the
     *       alternative, showing it to everyone, is how a cross-department leak is introduced by a NULL.
     *       Note that this is strictly safer than the complaint grid's own behaviour, and deliberately so:
     *       a grid shows an officer rows they were given, whereas this read volunteers a stranger's case.
     * </ul>
     * Compared against already-upper-cased values: the refresh upper-cases {@code DEPARTMENT} on write
     * and {@link #departmentsFor} upper-cases the role-derived departments, so neither side needs a fold
     * and MySQL's case-folding collation cannot make this pass in dev and fail in production.
     */
    private boolean inScope(AssistanceComplainantHistory row, Set<String> departments) {
        String rowDepartment = row.getDepartment();
        if (rowDepartment == null
                || AssistanceComplainantHistory.KEY_ABSENT.equals(rowDepartment)) {
            return false;
        }
        return departments.contains(rowDepartment.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * The departments the caller's roles are responsible for. EMPTY means no scope, which means no signal.
     *
     * <p>Derived from the role NAME, because there is no role-to-department table in this schema and the
     * names already encode it — measured across the register, twelve of the fourteen roles present follow
     * their prefix and the two that do not are mapped explicitly.
     *
     * <p>UNION across the caller's roles, not intersection. A caller holding {@code CEPC_DO} and
     * {@code RBIO_OFFICER} is responsible for work in both departments, and that is what holding two roles
     * means. Intersecting would deny an officer the scope of a role they actually hold, which is a
     * privilege reduction invented here rather than configured anywhere — and it would make the signal
     * quietly silent for exactly the multi-role supervisors most likely to be spotting a repeat filer.
     *
     * <p>Capped at {@link #MAX_ROLES} so a pathological token list cannot widen the scope or the work.
     * The cap is applied to the SORTED role set, so which roles survive a truncation is deterministic
     * rather than dependent on {@code HashSet} iteration order — a caller at the cap must not get a
     * different scope on a different pod.
     *
     * <p>Returns an empty set for null, empty, blank-only and unrecognised role sets. A role name this map
     * does not know contributes NOTHING rather than defaulting to a department: an unknown role is an
     * unknown scope, and guessing one is how a role added next year silently acquires read access to
     * another department's complainants.
     */
    Set<String> departmentsFor(Set<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }

        List<String> ordered = new ArrayList<>(roles.size());
        for (String role : roles) {
            if (role != null && !role.isBlank()) {
                ordered.add(role.trim().toUpperCase(Locale.ROOT));
            }
        }
        // Sorted before the cap so truncation is deterministic across JVMs. See the javadoc.
        ordered.sort(Comparator.naturalOrder());
        if (ordered.size() > MAX_ROLES) {
            ordered = ordered.subList(0, MAX_ROLES);
        }

        Set<String> departments = new HashSet<>();
        for (String role : ordered) {
            String override = ROLE_DEPARTMENT_OVERRIDES.get(role);
            if (override != null) {
                departments.add(override);
                continue;
            }
            for (Map.Entry<String, String> entry : ROLE_PREFIX_DEPARTMENT.entrySet()) {
                if (role.startsWith(entry.getKey())) {
                    departments.add(entry.getValue());
                    break;
                }
            }
        }
        return departments;
    }

    /**
     * The instant the subject complaint was filed, which anchors the window.
     *
     * <p>Taken from the complaint itself where possible. Falls back to the complaint's OWN projection row
     * if the entity carries neither date, so the window anchor and the rows being compared against it
     * come from the same normalisation — and finally to {@code now()}, which can only be reached for a
     * complaint with no {@code filedAt}, no {@code createdAt} and no projection row, and which anchors the
     * window at the present moment rather than failing the read.
     *
     * <p>Not simply {@code now()} in all cases, and the difference is substantive: an officer reviewing a
     * complaint filed six months ago must see the duplicates that existed AROUND IT, not the empty set
     * that "the last 30 days from today" would produce. Anchoring on the subject's own filing date is what
     * makes the signal meaningful for anything other than a brand-new filing.
     */
    private LocalDateTime subjectFiledAt(Complaint complaint,
                                         List<AssistanceComplainantHistory> scoped) {
        if (complaint.getFiledAt() != null) {
            return complaint.getFiledAt();
        }
        if (complaint.getCreatedAt() != null) {
            return complaint.getCreatedAt();
        }
        for (AssistanceComplainantHistory row : scoped) {
            if (row.getComplaintNumber() != null
                    && row.getComplaintNumber().equals(complaint.getComplaintNumber())) {
                return row.getFiledAt();
            }
        }
        return LocalDateTime.now();
    }

    /**
     * The configured window, clamped.
     *
     * <p>Clamped rather than trusted, because both extremes produce a FALSE sentence rather than merely
     * an unhelpful one: a zero or negative window would report only same-instant filings while the
     * response still said "in the last N days", and a window of decades would report a complainant's
     * entire history as a 30-day duplicate cluster. The clamped value is what travels on the wire, so the
     * officer is told the window actually applied.
     */
    int windowDays() {
        if (configuredWindowDays < MIN_WINDOW_DAYS) {
            return MIN_WINDOW_DAYS;
        }
        if (configuredWindowDays > MAX_WINDOW_DAYS) {
            return MAX_WINDOW_DAYS;
        }
        return configuredWindowDays;
    }

    /**
     * Maps one projection row onto the wire shape.
     *
     * <p>FIVE FIELDS, and the privacy argument for each is in {@link Match}'s javadoc. This method is the
     * single place a stored row becomes a response, which is what makes that argument auditable: there is
     * no other construction of {@link Match} in the codebase, so the set of fields an officer can receive
     * is exactly the set named here.
     *
     * <p>Note what is NOT read off {@code row} even though the row holds it: {@code EMAIL_KEY} and
     * {@code PHONE_KEY}. They are join keys and they stay on the server. The only contact details an
     * officer sees are the ones already on the complaint in front of them, which this feature did not
     * give them.
     */
    private Match toMatch(AssistanceComplainantHistory row) {
        return new Match(
                row.getComplaintNumber(),
                row.getFiledAt().format(WIRE_FORMAT),
                row.getEntityKey(),
                row.getStatus(),
                row.getDepartment());
    }
}
