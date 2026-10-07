package com.hrms.cms.service;

import com.hrms.cms.dto.AssistanceQueueResponse;
import com.hrms.cms.repository.AssistanceQueueRepository;
import com.hrms.cms.repository.projection.AssistanceQueueProjections.QueueDeadlineCounts;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Deadline triage (Brief 21 §5.3 item 4): "3 of your 14 cases breach within 48h".
 *
 * <h2>MEASURED STATE OF THE DATA — and why the brief's column was abandoned</h2>
 * Against the live dev register on 2026-10-07, {@code COMPLAINTS} holds 4403 rows. The brief names
 * {@code re_response_deadline} as the field to key on; it is populated on THIRTEEN of those rows, and
 * twelve of the thirteen are {@code status='closed'}, so exactly ONE OPEN complaint in the whole
 * register carries one. {@code re_response_overdue} is {@code true} on ZERO rows and NULL on 4402.
 * Keyed on the brief's column alone this feature would be silent on 99.7% of the register and on 99.9%
 * of open work — unbuilt-on-arrival rather than merely quiet.
 *
 * <p>So the count keys on {@code sla_deadline} (3870 of 4403 rows; 831 of the 1154 open assigned ones)
 * and folds the RE deadline in where it is present, at no extra cost in the same single pass. The two
 * mean DIFFERENT things and that is stated deliberately rather than smoothed over: {@code sla_deadline}
 * is the deadline for the OFFICE's own handling of the complaint, written by {@code CepcSlaService} from
 * creation plus a priority allowance and by {@code RbioSlaService} from the current stage's allowance;
 * {@code re_response_deadline} is the deadline the REGULATED ENTITY has to answer a 13(1) notice, set by
 * an officer through {@code ReResponseDeadlineService}. Both are deadlines the assigned officer must act
 * before, which is what makes the union the right denominator for a TRIAGE signal — but an officer
 * reading "breaches within 48h" is being told about their own handling window first and foremost.
 *
 * <p>With both columns the signal fires for three officers on real data ({@code cepc_do1} 10 of 126,
 * {@code rbio.officer} 4 of 26, {@code rbio_officer_001} 1 of 142); with the brief's column alone it
 * fires for nobody. Full reasoning and the V86 index finding are on
 * {@code AssistanceQueueRepository}.
 *
 * <h2>The queue is the CALLER'S, derived here and never declared</h2>
 * Both the officer id and the role set arrive from {@code RequestIdentityResolver} via the controller.
 * Nothing in this class reads a request parameter or a body. The defect this avoids is live in the same
 * codebase: {@code EmailSyndicationApiController:451} returned every row in the system when the owner
 * was omitted, because the owner was an input. Here an unresolved caller yields an EMPTY response —
 * there is no "all officers" fallback to fall into.
 *
 * <h2>Two queues, chosen rather than summed</h2>
 * A complaint can be reachable both by the caller's NAME and by their ROLE, so adding the two counts
 * would double-count it and could report "4 of 3". The rule is: if the officer has anything assigned to
 * them by name, that IS their queue; the role pool is consulted only when they hold nothing. Stated
 * here, in Java, where a reader can see it and a test can pin it — the same reason
 * {@code AssistanceRailService} writes its cohort preference as a comparator rather than an
 * {@code ORDER BY}.
 *
 * <h2>Fails silent</h2>
 * Every path returns {@link AssistanceQueueResponse#empty()} rather than throwing. §5.1 requires an
 * ambient affordance to be off the critical path, and a deadline warning that 500s beside a complaint
 * an officer is trying to work is worse than no warning. A MISSING signal costs a convenience; a
 * FABRICATED one would be read as fact, so the degradation is always toward silence and never toward a
 * number.
 */
@Slf4j
@Service
public class AssistanceQueueService {

    /**
     * The one kind this service emits. A STABLE MACHINE KEY — the client maps it to an icon and to the
     * i18n key {@code assistance.queue.signal.queue-deadline-triage}, so renaming it is a breaking
     * change even though nothing in Java reads it. Hyphens are load-bearing, exactly as in the rail's
     * kinds: the seeder spells the key with them.
     */
    public static final String KIND_DEADLINE_TRIAGE = "queue-deadline-triage";

    static final String PARAM_COUNT = "count";
    static final String PARAM_TOTAL = "total";
    static final String PARAM_HOURS = "hours";
    static final String PARAM_OVERDUE = "overdue";

    /**
     * The minimum queue size that makes a RATIO meaningful (§5.1, "glow only when actionable").
     *
     * <p>Three, not one. "1 of 1 case breaches" is not triage — it is the officer's only case, which
     * they are already looking at, and the sentence implies a prioritisation decision that does not
     * exist. The figure's value is in telling an officer WHICH of several cases to pick up first, so
     * below three there is nothing to choose between. This is the denominator floor; the numerator has
     * its own, below.
     *
     * <p>Named a floor rather than a tuning knob deliberately: it is NOT configurable, because a
     * deployment that set it to 1 would be turning the signal into noise and the brief's rule is that
     * the rail must earn attention every time it asks for it.
     */
    static final long MIN_QUEUE_SIZE = 3L;

    /**
     * The minimum breaching count worth interrupting for.
     *
     * <p>ONE — any genuine impending breach is actionable, so unlike the ratio floor this does not need
     * a sample. The constant exists to make the ZERO case explicit: a queue with no breaching case
     * produces NO SIGNAL, never a signal reading "0 of 14". The brief is explicit that the rail says why
     * and how much; "zero" is an answer to a question nobody asked, and an ambient panel that reports
     * the absence of a problem is the thing officers learn to ignore.
     */
    static final long MIN_BREACHING = 1L;

    /** Hard bound on the role {@code IN} list, so a pathological token cannot widen the query. */
    static final int MAX_ROLES = 32;

    /**
     * Role-prefix to department, for the tenancy predicate.
     *
     * <h3>Why a prefix map and not a lookup table</h3>
     * There is no role-to-department table in this schema; {@code RBIO_STAFF_PROFILE} carries a
     * department but only for RBIO staff, and {@code ReportAccessService} reaches for it precisely
     * because reports need a territory this signal does not. Measured against the register, the role
     * names ALREADY encode the department — all 1752 {@code CEPC_DO} rows are in {@code CEPC}, all 761
     * {@code RBIO_OFFICER} rows in {@code RBIO}, and so on for twelve of the fourteen roles present.
     *
     * <p>The two that do not follow the prefix are mapped explicitly: {@code DEO} sits in {@code CRPC}
     * (43 rows) and bare {@code DO} in {@code CEPC} (10 rows). Both are measured, not guessed.
     *
     * <p>The one genuine violation of the rule is recorded rather than smoothed over: 3 rows carry
     * {@code assigned_role='RBIO_REVIEWER'} with {@code department='CEPC'}, against 181 in
     * {@code RBIO}. Those three are data errors — a CEPC complaint cannot be an RBIO reviewer's work —
     * and this map excludes them from an RBIO reviewer's count. That is the CORRECT outcome: the
     * tenancy filter's job is to keep a department's cases out of another department's totals, and a
     * mis-stamped row is exactly what it should catch.
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

    private final AssistanceQueueRepository queueRepository;
    private final RbioStatusVocabulary statusVocabulary;
    private final Clock clock;

    /**
     * The breach window in HOURS, default 48 to match the brief's own example sentence.
     *
     * <p>Configurable because "soon" is an operational judgement, not a statutory one: an office with a
     * weekly cadence wants a wider window than one working daily. Independent of
     * {@code cms.assistance.enabled}, which decides whether the feature exists at all.
     *
     * <p>Field injection rather than a constructor parameter follows
     * {@code AssistanceRailController}'s reasoning — see the kill-switch field there — and keeps the
     * value overridable by {@code ReflectionTestUtils} in a plain Mockito test, since this module has no
     * H2 on the classpath and therefore no context to override a property in.
     */
    @Value("${cms.assistance.deadline-window-hours:48}")
    private int windowHours = 48;

    // @Autowired names the injectable constructor explicitly. Two constructors exist and neither is
    // no-arg, so without this Spring cannot choose and reports the misleading "No default constructor
    // found" rather than an ambiguity — a compile-clean class that fails only at context refresh.
    @Autowired
    public AssistanceQueueService(AssistanceQueueRepository queueRepository,
                                  RbioStatusVocabulary statusVocabulary) {
        this(queueRepository, statusVocabulary, Clock.systemDefaultZone());
    }

    /** Test seam: lets a test state "today" instead of depending on the day it runs. */
    AssistanceQueueService(AssistanceQueueRepository queueRepository,
                           RbioStatusVocabulary statusVocabulary,
                           Clock clock) {
        this.queueRepository = queueRepository;
        this.statusVocabulary = statusVocabulary;
        this.clock = clock;
    }

    /**
     * What the queue has to say to this officer, or nothing.
     *
     * <p>Returns {@link AssistanceQueueResponse#empty()} — not an exception, not a partial payload —
     * for an unresolved caller, a caller whose roles map to no department, a queue below the floors, a
     * queue with no breaching case, and any repository failure.
     *
     * @param userId the CALLER's resolved id; null when identity could not be established
     * @param roles  the CALLER's whole role set; null or empty when identity could not be established
     */
    public AssistanceQueueResponse triage(String userId, Set<String> roles) {
        try {
            Set<String> scopedRoles = normaliseRoles(roles);
            Set<String> departments = departmentsFor(scopedRoles);
            if (departments.isEmpty()) {
                // No department means no tenancy scope, and an unscoped count is the
                // EmailSyndicationApiController defect. Silence is the only safe answer.
                return AssistanceQueueResponse.empty();
            }

            // The SLA column is a LocalDateTime, so the configured window is honoured to the HOUR
            // rather than rounded to a day. The RE column is a LocalDate and takes the two
            // date-typed bounds below; neither parameter is cast, so no indexed column is wrapped.
            LocalDateTime now = LocalDateTime.now(clock);
            LocalDateTime horizon = now.plusHours(windowHours());
            List<String> closedStatuses = closedStatuses();

            QueueDeadlineCounts counts = readQueue(userId, scopedRoles, departments, closedStatuses,
                    now, horizon);

            return signalFor(counts);
        } catch (Exception e) {
            log.warn("Assistance queue triage failed for {}: {}", userId, e.toString());
            return AssistanceQueueResponse.empty();
        }
    }

    /**
     * The officer's own queue if they have one, else the pool their roles are responsible for.
     *
     * <p>CHOSEN, not summed — a complaint bearing both the caller's name and their role would otherwise
     * be counted twice and could produce a numerator above the denominator. The officer-assigned set is
     * preferred because it is the work that is unambiguously theirs; the pool is a fallback so a
     * supervisor or a newly-assigned officer with nothing in their name still gets a reading rather
     * than silence.
     */
    private QueueDeadlineCounts readQueue(String userId,
                                          Set<String> roles,
                                          Set<String> departments,
                                          List<String> closedStatuses,
                                          LocalDateTime now,
                                          LocalDateTime horizon) {
        // The DATE bounds for the LocalDate RE column, derived once here rather than in JPQL: a
        // cast inside the query would wrap the column and defeat the composite's range scan.
        LocalDate today = now.toLocalDate();
        LocalDate horizonDate = horizon.toLocalDate();

        if (userId != null && !userId.isBlank()) {
            QueueDeadlineCounts own = queueRepository
                    .countOfficerQueue(userId, departments, closedStatuses, now, horizon,
                            today, horizonDate)
                    .orElseGet(QueueDeadlineCounts::none);
            if (!own.isEmpty()) {
                return own;
            }
        }
        if (roles.isEmpty()) {
            return QueueDeadlineCounts.none();
        }
        return queueRepository
                .countRolePoolQueue(departments, roles, closedStatuses, now, horizon,
                        today, horizonDate)
                .orElseGet(QueueDeadlineCounts::none);
    }

    /**
     * Applies the two floors and builds the sentence, or returns nothing.
     *
     * <p>Both floors must clear. The denominator floor stops "1 of 1"; the numerator floor stops
     * "0 of 14". §5.1's rule is that the affordance glows only when there is something to act on, and
     * {@link AssistanceQueueResponse#of} then derives {@code glow} from the signal's presence, so a
     * floor failure cannot glow — the two cannot drift apart.
     */
    private AssistanceQueueResponse signalFor(QueueDeadlineCounts counts) {
        long total = counts.total();
        long breaching = counts.breaching();
        long overdue = counts.overdue();

        if (total < MIN_QUEUE_SIZE || breaching < MIN_BREACHING) {
            return AssistanceQueueResponse.empty();
        }
        // Defensive, not expected: a single-pass aggregate cannot produce this. Reported as silence
        // rather than clamped, because a numerator above its denominator means the scope was wrong and
        // a clamped number would hide that.
        if (breaching > total) {
            log.warn("Assistance queue triage: breaching {} exceeds total {} — suppressing",
                    breaching, total);
            return AssistanceQueueResponse.empty();
        }

        Map<String, String> params = new LinkedHashMap<>();
        // Sent EXPLICITLY rather than relying on the client folding `count` in, so the numerator in the
        // localised sentence and the numerator in the field cannot disagree. See the DTO's trap note.
        params.put(PARAM_COUNT, Long.toString(breaching));
        params.put(PARAM_TOTAL, Long.toString(total));
        params.put(PARAM_HOURS, Integer.toString(windowHours()));
        params.put(PARAM_OVERDUE, Long.toString(overdue));

        AssistanceQueueResponse.Signal signal = new AssistanceQueueResponse.Signal(
                KIND_DEADLINE_TRIAGE,
                title(breaching, total),
                detail(overdue),
                breaching,
                // A RELATIVE route, never absolute. Points at the officer's own list filtered to the
                // breaching window rather than at a complaint, because the signal names no complaint.
                "/complaints?deadlineWithinHours=" + windowHours(),
                params);

        return AssistanceQueueResponse.of(List.of(signal));
    }

    /** The English sentence the brief specifies, resolved server-side like the rail's titles. */
    private String title(long breaching, long total) {
        return String.format(Locale.ROOT, "%d of your %d cases breach within %dh",
                breaching, total, windowHours());
    }

    /**
     * The overdue sub-count as prose, or null when none are overdue.
     *
     * <p>Null rather than "0 already overdue" for the same reason the signal is suppressed at zero: a
     * clause reporting the absence of a problem adds length without adding information. The NUMBER is
     * still in {@code params['overdue']} so a client can render it however it likes.
     */
    private String detail(long overdue) {
        if (overdue <= 0) {
            return null;
        }
        return overdue == 1
                ? "1 of these is already overdue"
                : overdue + " of these are already overdue";
    }

    /**
     * The configured window, floored at 1 hour.
     *
     * <p>A zero or negative setting would make the horizon equal to or earlier than {@code now}, and the
     * signal would report only already-overdue cases while CLAIMING a forward-looking window — a sentence
     * that is wrong rather than merely narrow. Clamped rather than rejected because the feature must not
     * take a screen down over a typo'd ConfigMap.
     */
    int windowHours() {
        return Math.max(1, windowHours);
    }

    /**
     * The terminal vocabulary, guaranteed non-empty.
     *
     * <p>An empty {@code NOT IN} list is invalid JPQL, so a status master that returned nothing would
     * throw rather than degrade. {@code RbioStatusVocabulary} already falls back to its legacy six, and
     * this is the second belt: it is cheaper to repeat the guard than to have the queue endpoint be the
     * one caller that discovers the vocabulary was empty.
     */
    private List<String> closedStatuses() {
        List<String> closed = statusVocabulary.closedStatuses();
        if (closed == null || closed.isEmpty()) {
            return RbioStatusVocabulary.legacyClosedStatuses();
        }
        return closed;
    }

    /**
     * The caller's roles, trimmed, upper-cased, de-duplicated and capped.
     *
     * <p>The WHOLE set, never {@code RequestIdentity.getPrimaryRole()}. That field is
     * {@code roles.iterator().next()} over a {@code HashSet}, so for a multi-role officer it names an
     * arbitrary one and can name a DIFFERENT one across JVMs — the signal would count a queue the
     * officer merely has access to rather than the one they work, and would appear to change its mind
     * between page loads for no reason.
     *
     * <p>Capped at {@link #MAX_ROLES} so the {@code IN} list cannot be widened without bound by an
     * unusual token. Keycloak's own {@code offline_access} and {@code default-roles-cms} are left in
     * rather than filtered: they match no {@code assigned_role} and no department prefix, so they cost
     * nothing, and a filter here would mean this service encoding the realm's role vocabulary.
     */
    private Set<String> normaliseRoles(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (String role : roles) {
            if (role == null || role.isBlank()) {
                continue;
            }
            out.add(role.trim().toUpperCase(Locale.ROOT));
            if (out.size() >= MAX_ROLES) {
                break;
            }
        }
        return out;
    }

    /**
     * The departments these roles may see — the TENANCY scope, and the reason a cross-department count
     * cannot happen.
     *
     * <p>Measured as load-bearing, not theoretical: SEVEN officers in this register hold complaints in
     * more than one department ({@code rbio_officer_002} has 22 rows across {@code CEPC} and
     * {@code RBIO}, {@code officer.crpc.5} spans three). Without this filter, "every row bearing my
     * name" would include cases the officer cannot open, and the denominator they are asked to act on
     * would be wrong. {@code RbioComplaintListService} makes the same point about its own predicate:
     * omitting it is "a cross-module data leak, not merely a wrong list".
     *
     * <p>An unmapped role contributes NOTHING rather than a wildcard. A role this method does not
     * recognise therefore narrows the scope to silence instead of widening it to every department,
     * which is the direction a scoping bug should fail in.
     */
    Set<String> departmentsFor(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Set.of();
        }
        Set<String> departments = new LinkedHashSet<>();
        for (String role : roles) {
            String override = ROLE_DEPARTMENT_OVERRIDES.get(role);
            if (override != null) {
                departments.add(override);
                continue;
            }
            int underscore = role.indexOf('_');
            if (underscore <= 0) {
                continue;
            }
            String department = ROLE_PREFIX_DEPARTMENT.get(role.substring(0, underscore));
            if (department != null) {
                departments.add(department);
            }
        }
        return departments;
    }
}
