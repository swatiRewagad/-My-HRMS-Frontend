package com.hrms.cms.repository;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.projection.AssistanceQueueProjections;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.QueryHint;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;

/**
 * The deadline-triage counts (Brief 21 §5.3 item 4): "3 of your 14 cases breach within 48h".
 *
 * <h2>Why this is its own interface, like {@code ClauseAffinitySourceRepository}</h2>
 * {@code ComplaintRepository} is a chokepoint several sessions edit, and these two queries serve one
 * endpoint. Extending {@link Repository} rather than {@code JpaRepository} is the load-bearing part:
 * {@code JpaRepository} would inherit {@code findAll()} and {@code count()} over the 105-column
 * {@code COMPLAINTS} table, unbounded finders §6.2 forbids, one autocomplete away from a caller. A
 * marker-interface repository exposes ONLY the two aggregates below, so no caller can reach an
 * unbounded read through this type.
 *
 * <h2>There is no row cap because nothing here returns rows</h2>
 * §6.2's cap applies to result sets, and both finders are scalar {@code COUNT}/{@code SUM} aggregates
 * returning exactly one tuple regardless of how large the queue is. That is STRONGER than a cap, not an
 * exemption from one: a capped list of 200 would silently under-report an officer with 597 open cases,
 * which is precisely the shape {@code cepc_do_001} has in this register. The aggregate cannot
 * under-report and cannot grow. The {@code IN}-list parameters are bounded by the caller's own role set
 * and department list, which the service caps — see {@code AssistanceQueueService}.
 *
 * <h2>Two finders, never one with an OR</h2>
 * An officer's queue is reachable two ways — rows assigned to them by NAME, and rows sitting with their
 * ROLE in their department — and these are two different indexes. Measured on the live register
 * (4403 rows), {@code (assigned_officer = ? OR (department = ? AND assigned_role = ?))} degrades to
 * {@code type=range key=idx_complaint_status key_len=122 rows=1208 filtered=12.25%} — the optimizer
 * abandons both composites (they stay in {@code possible_keys} and are not chosen) and falls back to
 * the single-column status index: an OR across two columns cannot be index-sought even when both are
 * indexed. The two statements below are sought on the composite that actually matches each predicate,
 * at {@code rows=129} and {@code rows=229} with {@code filtered=100%}. Two cheap seeks beat one scan.
 *
 * <h2>══ THE BRIEF NAMES THE WRONG COLUMN, AND THE NUMBERS SAY SO ══</h2>
 * §5.3.4 says "{@code re_response_deadline} and {@code re_response_overdue} are indexed (V86). '3 of
 * your 14 cases breach within 48h' is a count over an existing index." Measured against the live
 * register on 2026-10-07, every clause of that sentence is wrong:
 * <ul>
 *   <li>{@code re_response_deadline} is populated on THIRTEEN of 4403 rows. Twelve of the thirteen are
 *       {@code status='closed'}, so exactly ONE OPEN complaint in the whole register carries one. A
 *       feature keyed on it alone is silent on 99.7% of the register and on 99.9% of open work — not
 *       degraded, SILENT, and indistinguishable from a feature that does not work.</li>
 *   <li>{@code re_response_overdue} is {@code true} on ZERO rows and NULL on 4402. It is a
 *       sweep-maintained denormalisation of a comparison, so reading it would report "nothing is
 *       overdue" for a register that simply has not been swept. The deadline is the FACT; the boolean
 *       is a cache of it, and this query performs the comparison directly instead.</li>
 *   <li>Neither column is INDEXED, and the reason is that <b>V86 was never applied to this database at
 *       all</b>. VERIFIED: {@code information_schema.STATISTICS} holds no index on any deadline column,
 *       and none of V86's three — {@code idx_complaint_re_deadline}, {@code idx_complaint_re_overdue},
 *       {@code idx_no_re_deadline} — exists in any letter case.
 *       <p>The columns V86 declares DO exist, which is what makes this look like a partially-applied
 *       file, so the discriminating evidence is worth recording. V86 also seeds two {@code SYSTEM_CONFIG}
 *       rows ({@code re.deadline.sweep_interval_minutes}, {@code re.deadline.overdue_highlight_colour})
 *       stamped {@code updated_by='V86_migration'}; NEITHER ROW IS PRESENT. A file that had run would
 *       have left them, because they are plain {@code INSERT ... SELECT ... WHERE NOT EXISTS} with no
 *       guard that could skip them on a first run. The columns are therefore the work of
 *       {@code ddl-auto: update}, which creates columns from the entity mapping and <b>never creates
 *       indexes</b> — and every V86 column is mapped on an entity
 *       ({@code Complaint.reResponseOverdue}, {@code NodalOfficerRecord.reResponseDeadline} and
 *       {@code .deadlineCommunication}).
 *       <p>An earlier draft of this javadoc blamed a case-sensitivity bug in V86's
 *       {@code information_schema} guard under {@code lower_case_table_names=1}. That explanation is
 *       WRONG, and it is corrected here rather than quietly deleted because it is the conclusion a
 *       reader will reach independently: {@code information_schema.STATISTICS.TABLE_NAME} collates
 *       {@code utf8mb3_tolower_ci}, so the guard matches {@code 'COMPLAINTS'} and {@code 'complaints'}
 *       identically — measured both ways, three rows each — and V111's indexes, written with the same
 *       uppercase literal, did land. The guard works; the file never ran.
 *       <p>This matters beyond this feature. §6.1 warns that {@code ddl-auto} never applied the
 *       Oracle-only indexes; the sharper rule is that <b>a column existing is not evidence that its
 *       migration ran</b>, so an index named in any hand-applied file must be verified in
 *       {@code information_schema} before a query is designed to rely on it.</li>
 * </ul>
 *
 * <h2>So the count keys on {@code sla_deadline}, with the RE deadline folded in</h2>
 * {@code sla_deadline} is populated on 3870 of 4403 rows and on 831 of the 1154 open assigned ones —
 * three orders of magnitude more reach. Folding {@code re_response_deadline} in where present costs
 * nothing (it is the same single pass) and keeps the brief's intent honest for the rows that do carry
 * one. With both columns the signal fires for THREE officers on real data
 * ({@code cepc_do1} 10 of 126, {@code rbio.officer} 4 of 26, {@code rbio_officer_001} 1 of 142); with
 * {@code re_response_deadline} alone it fires for nobody.
 *
 * <h2>Neither column is wrapped in a function, and the two types are kept apart</h2>
 * {@code sla_deadline} is a {@code LocalDateTime} and {@code re_response_deadline} is a
 * {@code LocalDate}, so they are compared against SEPARATE parameters of their own types rather than
 * cast to meet. No {@code CURRENT_DATE}, no {@code DATE(...)}, no {@code TRUNC}, no {@code CAST} —
 * §6.2 forbids wrapping an indexed column and a cast here would defeat the range scan on the composite
 * that serves the predicate. Computing both bounds in the service also makes the window testable
 * without freezing a clock in the database.
 */
public interface AssistanceQueueRepository extends Repository<Complaint, Long> {

    /** Shared with {@code AssistanceNextActionRepository}; spelled out because interfaces cannot inherit it. */
    String QUERY_TIMEOUT_HINT = "jakarta.persistence.query.timeout";

    /**
     * The rail read budget, matching {@code AssistanceNextActionRepository.RAIL_TIMEOUT_MS}.
     *
     * <p>A CEILING past which the caller would rather have no answer, not an expectation of how long
     * the query takes — measured p50 is 1.9ms. A tighter bound would make the signal flap under
     * incidental contention, and a flapping deadline warning is worse than none.
     */
    String RAIL_TIMEOUT_MS = "5000";

    /**
     * Counts for the complaints assigned to this officer BY NAME.
     *
     * <p>Measured plan (MySQL 8.4, live register, 2026-10-07): {@code type=range}, key
     * {@code idx_complaints_dept_officer_status}, {@code key_len=1008}, {@code rows=130},
     * {@code filtered=100%}, {@code Extra='Using index condition'}. That index is
     * {@code (department, assigned_officer, status)} from {@code V111__complaints_dashboard_queue_indexes.sql},
     * and all THREE of this query's equality/{@code IN} predicates are sought on it — the department
     * filter below is not a post-filter, it is the index's leading column.
     *
     * <h3>The SEEK is already right; what V119 adds is the COVER</h3>
     * V111's composite stops at {@code status}, so the two deadline {@code CASE} expressions are
     * evaluated from the 105-column row and the aggregate is NOT covering as the schema stands. The
     * paired migration {@code database/V119__assistance_deadline_triage_index.sql} and
     * {@code database/oracle/V117__*.sql} extend both V111 composites with
     * {@code (sla_deadline, re_response_deadline)} as trailing covering columns; proved on a
     * row-for-row temp copy of {@code COMPLAINTS} to give {@code type=range rows=130 filtered=100.00
     * Extra='Using where; Using index'}. The migrations are SHIPPED UNAPPLIED, so the plan quoted above
     * is what runs today and it is adequate: 2.3ms warm for the 597-row widest queue in the register.
     *
     * <h3>An index two siblings name does NOT exist</h3>
     * {@code ComplaintRepository} and {@code AssistanceRailService} both cite a key named
     * {@code idx_complaints_officer_status_deadline} "added by V117 / oracle V115". VERIFIED against
     * {@code information_schema.STATISTICS}: no migration in {@code database/} under those numbers
     * creates it, and V119's own first draft, which did, was revised away — it led with
     * {@code assigned_officer} and was measurably NEVER CHOSEN by the optimiser, which preferred V111's
     * department-leading composite and left it unused in {@code possible_keys}. Nothing in Java names an
     * index for exactly this reason: the two engines carry different index NAMES for the same shape and
     * the optimiser, not a javadoc, picks.
     *
     * <h3>The department filter is tenancy and is NOT redundant</h3>
     * Measured: SEVEN officers in this register hold complaints across more than one department —
     * {@code rbio_officer_002} has 22 rows spanning {@code CEPC} and {@code RBIO},
     * {@code officer.crpc.5} spans three. So "every row bearing my name" is a DIFFERENT set from "every
     * row bearing my name in a department I work in", and reporting the former would count a case the
     * officer cannot open against the deadline total they are being asked to act on. {@code ComplaintRepository}
     * has no such guard on its deadline finders, which is why they are not reused here.
     *
     * <h3>Closed statuses are EXCLUDED, not open statuses included</h3>
     * {@code status NOT IN :closedStatuses} rather than {@code status IN :openStatuses}, and the
     * direction is measured rather than stylistic. This register holds 22 DISTINCT status values —
     * {@code closed}, {@code assigned}, {@code pending}, {@code withdrawn}, {@code forwarded},
     * {@code in_progress}, {@code reviewer_review}, {@code awaiting_closure}, {@code sent_back} and
     * thirteen more — of which only six are terminal. An enumerated OPEN list would have to name
     * sixteen values and would silently stop counting a complaint the day someone adds a
     * seventeenth status, which is exactly the kind of thing that gets added: the officer's deadline
     * total would quietly shrink and nothing would look broken. Excluding the six terminal values
     * means an unrecognised status counts as OPEN, which is the safe direction for a warning.
     * {@code RbioStatusVocabulary} already owns that six-value vocabulary and reads it from the
     * operator-editable status master, so this query does not restate it.
     *
     * @param officerUserId   the CALLER's resolved id, never a request parameter
     * @param departments     the departments the caller's roles grant, never empty
     * @param closedStatuses  the terminal vocabulary from {@code RbioStatusVocabulary}, never empty —
     *                        an empty {@code NOT IN} list is invalid JPQL, so the caller substitutes
     *                        the legacy fallback rather than passing nothing
     * @param now             the instant "overdue" is measured against — an {@code sla_deadline}
     *                        strictly before it has passed
     * @param horizon         the far edge of the breach window as an instant, inclusive
     * @param today           {@code now}'s DATE, for the {@code LocalDate} RE column
     * @param horizonDate     {@code horizon}'s DATE, likewise. Passed rather than derived in JPQL
     *                        because deriving it would mean wrapping the column or the parameter in a
     *                        cast, and the service already knows both.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceQueueProjections$QueueDeadlineCounts("
            + "COUNT(c), "
            + "SUM(CASE WHEN (c.slaDeadline IS NOT NULL AND c.slaDeadline <= :horizon) "
            + "            OR (c.reResponseDeadline IS NOT NULL "
            + "                AND c.reResponseDeadline <= :horizonDate) THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (c.slaDeadline IS NOT NULL AND c.slaDeadline < :now) "
            + "            OR (c.reResponseDeadline IS NOT NULL "
            + "                AND c.reResponseDeadline < :today) THEN 1 ELSE 0 END)) "
            + "FROM Complaint c "
            + "WHERE c.assignedOfficer = :officerUserId "
            + "AND c.status NOT IN :closedStatuses "
            + "AND c.department IN :departments")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = RAIL_TIMEOUT_MS))
    Optional<AssistanceQueueProjections.QueueDeadlineCounts> countOfficerQueue(
            @Param("officerUserId") String officerUserId,
            @Param("departments") Collection<String> departments,
            @Param("closedStatuses") Collection<String> closedStatuses,
            @Param("now") LocalDateTime now,
            @Param("horizon") LocalDateTime horizon,
            @Param("today") LocalDate today,
            @Param("horizonDate") LocalDate horizonDate);

    /**
     * Counts for the complaints sitting with the caller's ROLES in their departments — the unassigned
     * pool an officer is expected to pick from.
     *
     * <h3>══ THIS IS THE ONE QUERY WITH A BAD PLAN, AND THE NUMBER IS THE REASON FOR V119 ══</h3>
     * The DENOMINATOR alone is served correctly — {@code type=range} on
     * {@code idx_complaints_dept_role_status} {@code (department, assigned_role, status)} from V111,
     * {@code key_len=408}, {@code filtered=100%}, {@code Extra='Using where; Using index'}, fully
     * covering. Add the deadline {@code CASE} expressions and the plan COLLAPSES. Measured on the live
     * register, 2026-10-07, with the aggregate exactly as written below:
     * <pre>
     * type=ref  key=idx_complaints_dept_officer_status  key_len=83  ref=const
     * rows=2231  filtered=5.40  Extra='Using index condition; Using where'
     * </pre>
     * The optimiser abandons the role composite entirely and degrades to a {@code ref} on the OFFICER
     * composite's {@code department} prefix alone, reading 2231 rows to discard 94.6% of them — half the
     * table scanned to answer one aggregate. §6.2 rejects that regardless of how fast it runs on 4403
     * rows, and it is the defect the paired migrations exist to fix: extending this composite with
     * {@code (sla_deadline, re_response_deadline)} restores {@code type=range key_len=408 rows=856
     * filtered=100.00 Extra='Using where; Using index'}, proved on a row-for-row temp copy.
     *
     * <p>The migrations are shipped UNAPPLIED, so this is the plan that runs today. It is recorded here
     * rather than hidden in a migration header because this javadoc is where the next reader will look
     * before reusing the query: measured 3.7ms warm for the 846-row CEPC pool, which is survivable and
     * is NOT the same claim as "the plan is correct".
     *
     * <p>ONE statement over an {@code IN} list rather than a seek per role: a staff token carries a SET
     * of roles plus Keycloak's own {@code offline_access} and {@code default-roles-cms}, and the rail
     * does not know which one the officer is acting as. Looping would issue up to sixteen statements on
     * a screen load to answer one question.
     *
     * <h3>Why this is a SECOND number and not merged into the officer count</h3>
     * They overlap: a row can carry both the caller's name and their role. The service therefore does
     * not add them — it reports the officer-assigned queue when there is one and falls back to the role
     * pool only when the officer holds nothing, so no complaint is ever counted twice. See
     * {@code AssistanceQueueService}.
     */
    @Query("SELECT new com.hrms.cms.repository.projection.AssistanceQueueProjections$QueueDeadlineCounts("
            + "COUNT(c), "
            + "SUM(CASE WHEN (c.slaDeadline IS NOT NULL AND c.slaDeadline <= :horizon) "
            + "            OR (c.reResponseDeadline IS NOT NULL "
            + "                AND c.reResponseDeadline <= :horizonDate) THEN 1 ELSE 0 END), "
            + "SUM(CASE WHEN (c.slaDeadline IS NOT NULL AND c.slaDeadline < :now) "
            + "            OR (c.reResponseDeadline IS NOT NULL "
            + "                AND c.reResponseDeadline < :today) THEN 1 ELSE 0 END)) "
            + "FROM Complaint c "
            + "WHERE c.department IN :departments "
            + "AND c.assignedRole IN :roles "
            + "AND c.status NOT IN :closedStatuses")
    @QueryHints(@QueryHint(name = QUERY_TIMEOUT_HINT, value = RAIL_TIMEOUT_MS))
    Optional<AssistanceQueueProjections.QueueDeadlineCounts> countRolePoolQueue(
            @Param("departments") Collection<String> departments,
            @Param("roles") Collection<String> roles,
            @Param("closedStatuses") Collection<String> closedStatuses,
            @Param("now") LocalDateTime now,
            @Param("horizon") LocalDateTime horizon,
            @Param("today") LocalDate today,
            @Param("horizonDate") LocalDate horizonDate);
}
