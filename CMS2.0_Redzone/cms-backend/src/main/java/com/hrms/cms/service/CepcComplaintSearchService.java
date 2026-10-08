package com.hrms.cms.service;

import com.hrms.cms.dto.cepc.CepcComplaintRow;
import com.hrms.cms.dto.cepc.CepcDashboardResponse;
import com.hrms.cms.dto.cepc.CepcSearchRequest;
import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.entity.CepcDashboardFilter;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.ComplaintCategory;
import com.hrms.cms.entity.ComplaintReadState;
import com.hrms.cms.entity.NodalOfficerRecord;
import com.hrms.cms.entity.ReActivityStatus;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.CepcDashboardFilterRepository;
import com.hrms.cms.repository.ComplaintRepository;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side paging, sorting, filtering and scoping for the CEPC complaint dashboard.
 *
 * <p>Backs {@code POST /api/v1/search/complaints/search}. The dashboard used to post this to a separate
 * OpenSearch-backed service on port 8091 that does not exist in this tree, so every tab, KPI card and grid
 * load 404'd. The query now runs against MySQL through JPA Criteria, in this process.
 *
 * <p><b>This is not a port of {@code CMS2.0/cms-search-service}.</b> That implementation is broken in ways
 * that all fail silently — a snake_case naming strategy that discarded the entire request body, a default
 * status label that matched no switch case, an unwhitelisted {@code sort} parameter, and an
 * {@code IOException} swallowed into a 200-with-empty-page. It is modelled instead on
 * {@link RbioComplaintListService}, which solves the same problem correctly for the RBIO list.
 *
 * <h2>Three filter dimensions, ANDed</h2>
 * A request can carry a status selection, a tab and a KPI card at once, and all three narrow the result.
 * They are not alternatives: clicking "SLA Breached" while the "Sent to RE" tab is active means
 * "breached AND with the RE", which is what the officer sees on screen and therefore what the count must be.
 *
 * <h2>An unrecognised filter code matches nothing</h2>
 * Every code is looked up in {@code CEPC_DASHBOARD_FILTER}; a miss adds {@code cb.disjunction()}. Returning
 * the unfiltered list instead would show an officer complaints outside their remit and look like a working
 * screen — a visibly empty grid gets reported, a silently widened one does not.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CepcComplaintSearchService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    /**
     * Shortest term accepted for a substring search.
     *
     * <p>A one-character {@code LIKE '%a%'} scans the table and returns most of it, which is useless to the
     * officer and an easy way to pull the whole complaint set a page at a time. Exact-match fields are exempt
     * because they are selective by construction.
     */
    private static final int MIN_PARTIAL_TERM = 2;

    /**
     * Statuses that end a complaint's life: closed, withdrawn and rejected.
     *
     * <p>Taken from {@link CepcStatus#terminalSpellings()} rather than assembled here, so this and the
     * {@code :terminal} argument {@code ComplaintRepository.cepcDashboardCounts} takes cannot disagree about
     * what "pending" excludes — a disagreement reads as a KPI badge that does not match its own grid.
     */
    static final Set<String> TERMINAL_STATUSES = CepcStatus.terminalSpellings();

    /**
     * Sortable columns, mapped from the grid's column key to the entity property.
     *
     * <p>Whitelisted rather than passed through: the sort field arrives as a string and is interpolated into
     * a JPA {@code Sort}, so an unvalidated value lets a caller order by any mapped property and probe for
     * its existence. It also stops a typo becoming a 500.
     *
     * <p>Both {@code createdDate} (what the grid sends, and its default) and {@code createdAt} (the column)
     * are accepted, because the two names for one thing are baked into opposite ends of this call.
     */
    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("createdDate", "createdAt"),
            Map.entry("createdAt", "createdAt"),
            Map.entry("lastUpdatedDate", "updatedAt"),
            Map.entry("updatedAt", "updatedAt"),
            Map.entry("complaintId", "id"),
            Map.entry("complaintNumber", "complaintNumber"),
            Map.entry("complainantName", "complainantName"),
            Map.entry("subject", "subject"),
            Map.entry("status", "status"),
            Map.entry("priority", "priority"),
            Map.entry("assignedTo", "assignedOfficer"),
            Map.entry("mode", "filingType"),
            Map.entry("entityName", "entityCode"),
            Map.entry("slaBreachIn", "slaDeadline"));

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    /** The grid renders dates as dd/MM/yyyy, so an officer typing what they see must match. */
    private static final DateTimeFormatter DMY_SLASH = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ComplaintRepository complaintRepository;
    private final CepcDashboardFilterRepository filterRepo;
    private final AaOfficerPoolRepository officerPoolRepository;
    private final CepcComplaintRowEnricher enricher;
    private final CepcDashboardCountService countService;

    /**
     * Thrown when the request asks for something the schema cannot answer.
     *
     * <p>Refused rather than quietly dropped. An ignored criterion produces a result set that looks like an
     * answer to a question nobody asked, and the officer has no way to tell that their filter did nothing.
     */
    public static class UnsupportedFilterException extends RuntimeException {
        private final String messageKey;

        public UnsupportedFilterException(String messageKey) {
            super(messageKey);
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    /** Who is asking, and in what capacity. */
    public record Caller(String userId, String role) {
    }

    public CepcDashboardResponse search(CepcSearchRequest request, Integer pageParam, Integer sizeParam,
                                        String sortParam, Caller caller) {
        CepcSearchRequest req = request == null
                ? new CepcSearchRequest(null, null, null, null, null, null, null, null)
                : request;

        int page = (pageParam == null || pageParam < 0) ? 0 : pageParam;
        int size = (sizeParam == null || sizeParam < 1) ? DEFAULT_PAGE_SIZE : Math.min(sizeParam, MAX_PAGE_SIZE);

        rejectUnsupported(req);

        String office = officeOf(caller);
        CepcDashboardCountService.Counts counts = countService.counts(caller, office);
        CepcDashboardResponse.Page pageBody = complaintPage(req, caller, office, page, size, sortParam);

        return new CepcDashboardResponse(pageBody, counts.kpi(), counts.tab());
    }

    /**
     * The caller's own office, which scopes every CEPC listing alongside the department.
     *
     * <p>Read from {@code WF_OFFICER_POOL} rather than from the request, so an officer cannot widen their own
     * scope, and rather than from the token, which carries no office claim. {@code COMPLAINTS.RBIO_OFFICE_CODE}
     * is no use either: it is the RBIO intake office parsed out of the complaint number and is NULL on every
     * CEPC complaint.
     *
     * <p>An officer with no pool row is unscoped rather than shown nothing. The pool is a workload roster, not
     * an authorisation table — it is empty on a fresh database and does not contain every reader — so treating
     * a missing row as "no office" would empty the dashboard for anyone who has never been an assignment
     * candidate.
     */
    private String officeOf(Caller caller) {
        if (!hasText(caller.userId())) {
            return null;
        }
        return officerPoolRepository.findByUserId(caller.userId())
                .map(AaOfficerPool::getRegionalOffice)
                .filter(CepcComplaintSearchService::hasText)
                .orElse(null);
    }

    /**
     * Refuses, up front, anything the schema cannot answer.
     *
     * <p>Checked before any query runs rather than while the specification is being built. The counts are
     * gathered first, so a refusal raised from inside the specification would already have cost two round
     * trips — and would arrive after the caller's own count numbers had been computed against a request that
     * was never valid.
     */
    private void rejectUnsupported(CepcSearchRequest req) {
        CepcSearchRequest.AdvancedSearch a = req.advancedSearch();
        if (a != null) {
            requireLongEnough("complainantName", a.complainantName());
            requireLongEnough("entityName", a.entityName());
            requireLongEnough("subject", a.subject());
            requireLongEnough("nodalOfficerName", a.nodalOfficerName());
        }
        if (req.search() != null && hasText(req.search().slaBreachIn())) {
            // slaBreachIn is a phrase this service composes for display ("Overdue by 2 days"), not a stored
            // value. Refused rather than ignored, because an ignored filter here looks like one that matched
            // everything.
            throw new UnsupportedFilterException("cepc.search.error.sla_breach_in_not_filterable");
        }
        if (req.filters() != null && notEmpty(req.filters().meetingTypes())) {
            // Meeting type is a property of a conciliation meeting, and CEPC_CONCILIATION_MEETINGS does not
            // exist yet. Refused so the officer sees the filter did nothing.
            throw new UnsupportedFilterException("cepc.search.error.meeting_type_not_available");
        }
    }

    // ─────────────────────────────── the complaint grid ───────────────────────────────

    private CepcDashboardResponse.Page complaintPage(CepcSearchRequest req, Caller caller, String office,
                                                     int page, int size, String sortParam) {
        Page<Complaint> found = complaintRepository.findAll(
                toSpecification(req, caller, office), PageRequest.of(page, size, parseSort(sortParam)));

        List<CepcComplaintRow> rows = enricher.enrich(found.getContent(), caller.userId());
        return CepcDashboardResponse.Page.of(rows, page, size, found.getTotalElements());
    }

    /**
     * Parses the {@code sort} query parameter.
     *
     * <p>Done by hand rather than with {@code @PageableDefault} so that an unknown field falls back to
     * {@code createdAt DESC} instead of becoming a 500 — and so the sort property is whitelisted at all.
     *
     * <p>The direction arrives in two dialects: {@code asc}/{@code desc} from a hand-written call, and
     * PrimeNG's {@code 1}/{@code -1} from the grid header, which is what
     * {@code cepc-dashboard.component.ts} actually sends ({@code sortOrder.toString()}). Both are accepted;
     * anything else sorts newest-first, which is the only sensible default for a worklist.
     */
    private Sort parseSort(String sortParam) {
        String field = "createdAt";
        boolean ascending = false;

        if (sortParam != null && !sortParam.isBlank()) {
            String[] parts = sortParam.split(",", 2);
            String requested = parts[0].trim();
            String resolved = SORTABLE.get(requested);
            if (resolved == null) {
                log.debug("Ignoring unsortable CEPC field '{}'", requested);
            } else {
                field = resolved;
            }
            if (parts.length > 1) {
                String dir = parts[1].trim();
                ascending = "asc".equalsIgnoreCase(dir) || "1".equals(dir);
            }
        }
        return Sort.by(ascending ? Sort.Direction.ASC : Sort.Direction.DESC, field);
    }

    Specification<Complaint> toSpecification(CepcSearchRequest req, Caller caller, String office) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();

            // CEPC only. Without this the endpoint serves RBIO and AA complaints to a CEPC officer — a
            // cross-module data leak, not merely a wrong list.
            and.add(cb.equal(root.get("department"), "CEPC"));

            // Office scope, kept next to the department so the pair is applied together and neither can be
            // forgotten. Null means the caller has no office on record — see officeOf() on why that is
            // unscoped rather than empty.
            if (hasText(office)) {
                and.add(cb.equal(root.get("regionalOffice"), office));
            }

            applyFilterCode(CepcDashboardFilter.DIMENSION_STATUS,
                    blankToDefault(req.statusCode(), null), caller, root, query, cb, and);
            applyFilterCode(CepcDashboardFilter.DIMENSION_TAB,
                    blankToDefault(req.tabs(), "All"), caller, root, query, cb, and);
            applyFilterCode(CepcDashboardFilter.DIMENSION_KPI,
                    blankToDefault(req.kpiCards(), null), caller, root, query, cb, and);

            if (Boolean.TRUE.equals(req.unread())) {
                and.add(cb.not(cb.exists(readStateSubquery(root, query, cb, caller.userId()))));
            }
            if (Boolean.TRUE.equals(req.withoutAttachments())) {
                and.add(cb.not(cb.exists(attachmentSubquery(root, query, cb, null))));
            }

            applyAdvancedSearch(req.advancedSearch(), root, query, cb, and);
            applyColumnFilters(req.search(), root, query, cb, and);
            applyPanelFilters(req.filters(), root, query, cb, and);

            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    // ─────────────────────────────── filter vocabulary ───────────────────────────────

    private Optional<CepcDashboardFilter> filter(String dimension, String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return filterRepo.findByDimensionAndFilterCode(dimension, code.trim());
    }

    /**
     * Resolves one filter code against {@code CEPC_DASHBOARD_FILTER} and ANDs its predicate on.
     *
     * <p>A blank code means the dimension is not in play and adds nothing. A non-blank code that is not in
     * the table adds {@code cb.disjunction()} — see the class javadoc on why an unknown code must not widen.
     */
    private void applyFilterCode(String dimension, String code, Caller caller, Root<Complaint> root,
                                 CriteriaQuery<?> query, CriteriaBuilder cb, List<Predicate> and) {
        if (code == null || code.isBlank()) {
            return;
        }
        Optional<CepcDashboardFilter> opt = filter(dimension, code);
        if (opt.isEmpty()) {
            log.warn("Unknown CEPC {} filter '{}' — returning no rows", dimension, code);
            and.add(cb.disjunction());
            return;
        }
        CepcDashboardFilter f = opt.get();
        if (!f.isActiveFilter()) {
            log.warn("CEPC {} filter '{}' is inactive — returning no rows", dimension, code);
            and.add(cb.disjunction());
            return;
        }

        Predicate p = buildPredicate(f, caller, root, query, cb);
        if (p != null) {
            and.add(p);
        }
        if (f.isPendingOnly()) {
            and.add(notTerminal(root, cb));
        }
    }

    /** Null when the filter constrains nothing beyond the base scope. */
        private Predicate buildPredicate(CepcDashboardFilter f, Caller caller, Root<Complaint> root,
                                     CriteriaQuery<?> query, CriteriaBuilder cb) {
        List<String> values = f.predicateValueList();

        switch (f.getPredicateKind()) {
            case CepcDashboardFilter.PREDICATE_NONE:
                return null;

            case CepcDashboardFilter.PREDICATE_ASSIGNED_TO_ME:
                // "Assigned to me" with no identity cannot be answered. Returning the unfiltered list would
                // show every officer's work to a caller who asked only for their own.
                return hasText(caller.userId())
                        ? cb.equal(root.get("assignedOfficer"), caller.userId())
                        : cb.disjunction();

            case CepcDashboardFilter.PREDICATE_STATUS_IN:
                return values.isEmpty() ? cb.disjunction()
                        : cb.lower(root.get("status")).in(lowercase(values));

            case CepcDashboardFilter.PREDICATE_STAGE_IN:
                return values.isEmpty() ? cb.disjunction() : root.get("workflowStage").in(values);

            case CepcDashboardFilter.PREDICATE_ASSIGNED_ROLE_IN:
                return values.isEmpty() ? cb.disjunction() : root.get("assignedRole").in(values);

            case CepcDashboardFilter.PREDICATE_SENT_BACK_TO_ME:
                return sentBackToMe(caller, root, cb);

            case CepcDashboardFilter.PREDICATE_RE_PENDING:
                return rePending(values, root, cb);

            case CepcDashboardFilter.PREDICATE_RE_ACTIVITY_IN:
                return values.isEmpty() ? cb.disjunction()
                        : root.get("reActivityStatus").in(reActivityValues(values));

            case CepcDashboardFilter.PREDICATE_SLA_BREACHED:
                return cb.and(cb.isNotNull(root.get("slaDeadline")),
                        cb.lessThan(root.get("slaDeadline"), cb.literal(LocalDateTime.now())));

            case CepcDashboardFilter.PREDICATE_SLA_WINDOW:
                return slaWindow(f, root, cb);

            case CepcDashboardFilter.PREDICATE_STAFF_DRAFT:
                // No CEPC filter is seeded with this kind any more: the Draft tab lists complaints in status
                // DRAFT. Left as an explicit arm because a row from before that change can still carry it, and
                // a staff draft has no complaint row for any predicate over COMPLAINTS to reach.
                log.warn("Filter '{}' still uses STAFF_DRAFT — returning no rows", f.getFilterCode());
                return cb.disjunction();

            default:
                log.warn("Unhandled CEPC predicate kind '{}' on filter '{}' — returning no rows",
                        f.getPredicateKind(), f.getFilterCode());
                return cb.disjunction();
        }
    }

    /**
     * Rework sitting with the caller.
     *
     * <p>The stage arm is not redundant with the status arm. Rows written before {@code SEND_BACK_REVIEWER}
     * and {@code SEND_BACK_INCHARGE} were corrected carry a review status ({@code reviewer_review},
     * {@code incharge_review}) together with a {@code SENT_BACK_*} stage, so a status-only predicate would
     * silently omit every complaint sent back before that fix.
     */
    /**
     * {@code sent_back} is the literal the live workflow still writes for every send-back regardless of
     * destination — that write path is untouched here — so it stays checked alongside the three destination
     * statuses {@code CepcDevSeeder} now seeds ({@code SENT_BACK_TO_DO/REVIEWER/INCHARGE}). Dropping it would
     * make this predicate blind to every complaint the real workflow sends back.
     */
    private static final Set<String> SENT_BACK_STATUSES = Set.of(
            "sent_back",
            CepcStatus.SENT_BACK_TO_DO.toLowerCase(Locale.ROOT),
            CepcStatus.SENT_BACK_TO_REVIEWER.toLowerCase(Locale.ROOT),
            CepcStatus.SENT_BACK_TO_INCHARGE.toLowerCase(Locale.ROOT));

    private Predicate sentBackToMe(Caller caller, Root<Complaint> root, CriteriaBuilder cb) {
        Predicate isSentBack = cb.or(
                cb.lower(root.get("status")).in(SENT_BACK_STATUSES),
                cb.like(root.get("workflowStage"), "SENT_BACK%"));
        if (!hasText(caller.userId())) {
            return cb.disjunction();
        }
        return cb.and(isSentBack, cb.equal(root.get("assignedOfficer"), caller.userId()));
    }

    /**
     * With the regulated entity and not yet answered.
     *
     * <p>{@code values} lists the activity states that mean "answered" and are therefore EXCLUDED. A null
     * {@code reActivityStatus} counts as still waiting: it means the forward happened before the activity
     * ladder was wired up, and such a complaint is with the RE whether or not a badge says so.
     */
    private Predicate rePending(List<String> values, Root<Complaint> root, CriteriaBuilder cb) {
        Predicate withRe = cb.equal(root.get("assignedRole"), "RE");
        List<ReActivityStatus> answered = reActivityValues(values);
        if (answered.isEmpty()) {
            return withRe;
        }
        return cb.and(withRe, cb.or(
                cb.isNull(root.get("reActivityStatus")),
                cb.not(root.get("reActivityStatus").in(answered))));
    }

    /** Falls due inside the configured forward-looking day window. */
    private Predicate slaWindow(CepcDashboardFilter f, Root<Complaint> root, CriteriaBuilder cb) {
        Integer from = f.getWindowFromDays();
        Integer to = f.getWindowToDays();
        if (from == null || to == null) {
            log.warn("SLA window filter '{}' has no day bounds — returning no rows", f.getFilterCode());
            return cb.disjunction();
        }
        LocalDateTime lower = LocalDate.now().plusDays(from).atStartOfDay();
        // Exclusive upper bound at the start of the day after `to`, so a deadline at 16:20 on the closing
        // day is inside the window. Comparing against the date itself drops everything after midnight.
        LocalDateTime upper = LocalDate.now().plusDays(to + 1L).atStartOfDay();
        return cb.and(
                cb.isNotNull(root.get("slaDeadline")),
                cb.greaterThanOrEqualTo(root.get("slaDeadline"), cb.literal(lower)),
                cb.lessThan(root.get("slaDeadline"), cb.literal(upper)));
    }

    private Predicate notTerminal(Root<Complaint> root, CriteriaBuilder cb) {
        return cb.or(
                cb.isNull(root.get("status")),
                cb.not(cb.lower(root.get("status")).in(TERMINAL_STATUSES)));
    }

    /** Unparseable names are dropped with a warning rather than failing the request. */
    private static List<ReActivityStatus> reActivityValues(List<String> raw) {
        List<ReActivityStatus> out = new ArrayList<>();
        for (String v : raw) {
            try {
                out.add(ReActivityStatus.valueOf(v.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                log.warn("Filter names unknown RE activity status '{}'", v);
            }
        }
        return out;
    }

    // ─────────────────────────────── subqueries ───────────────────────────────

    /**
     * {@code COMPLAINT_READ_STATE} rows for this caller and complaint.
     *
     * <p>Correlated rather than joined because absence of a row is the meaningful case: unread is
     * {@code NOT EXISTS}, and an outer join would need a null check on a column that is never null.
     */
    private Subquery<Long> readStateSubquery(Root<Complaint> root, CriteriaQuery<?> query,
                                             CriteriaBuilder cb, String userId) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<ComplaintReadState> rs = sub.from(ComplaintReadState.class);
        sub.select(rs.get("id"));
        sub.where(cb.equal(rs.get("complaintId"), root.get("id")),
                cb.equal(rs.get("userId"), userId == null ? "" : userId));
        return sub;
    }

    /**
     * Attachments on this complaint, optionally narrowed to given document types.
     *
     * <p>{@code COMPLAINTS} has no {@code hasAttachment} flag, so the presence of an attachment can only be
     * answered by looking. A denormalised flag would be the faster read and the one that goes stale.
     */
    private Subquery<Long> attachmentSubquery(Root<Complaint> root, CriteriaQuery<?> query,
                                              CriteriaBuilder cb, List<String> documentTypes) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<ComplaintAttachment> att = sub.from(ComplaintAttachment.class);
        sub.select(att.get("id"));
        List<Predicate> where = new ArrayList<>();
        where.add(cb.equal(att.get("complaintId"), root.get("id")));
        if (documentTypes != null && !documentTypes.isEmpty()) {
            where.add(att.get("documentType").in(documentTypes));
        }
        sub.where(cb.and(where.toArray(new Predicate[0])));
        return sub;
    }

    // ─────────────────────────────── advance search ───────────────────────────────

    /**
     * The advance-search panel. Every criterion is ANDed, so adding a field narrows the result.
     *
     * <p>Phone and email are exact matches — see {@link CepcSearchRequest.AdvancedSearch} on why a partial
     * match there is a PII enumeration primitive.
     */
    private void applyAdvancedSearch(CepcSearchRequest.AdvancedSearch a, Root<Complaint> root,
                                     CriteriaQuery<?> query, CriteriaBuilder cb, List<Predicate> and) {
        if (a == null) {
            return;
        }
        if (hasText(a.complaintNumber())) {
            and.add(cb.equal(cb.upper(cb.trim(root.get("complaintNumber"))),
                    a.complaintNumber().trim().toUpperCase(Locale.ROOT)));
        }
        if (hasText(a.complainantName())) {
            and.add(like(cb, root.get("complainantName"), a.complainantName()));
        }
        if (a.id() != null) {
            and.add(cb.equal(root.get("id"), a.id()));
        }
        if (hasText(a.complainantPhone())) {
            and.add(cb.equal(cb.trim(root.get("complainantPhone")), a.complainantPhone().trim()));
        }
        if (hasText(a.complainantEmail())) {
            and.add(cb.equal(cb.lower(cb.trim(root.get("complainantEmail"))),
                    a.complainantEmail().trim().toLowerCase(Locale.ROOT)));
        }
        if (hasText(a.filingType())) {
            and.add(cb.equal(cb.lower(cb.trim(root.get("filingType"))),
                    a.filingType().trim().toLowerCase(Locale.ROOT)));
        }
        if (hasText(a.entityName())) {
            and.add(like(cb, root.get("entityCode"), a.entityName()));
        }
        if (hasText(a.subject())) {
            and.add(like(cb, root.get("subject"), a.subject()));
        }
        if (a.categoryId() != null) {
            and.add(cb.equal(root.get("categoryId"), a.categoryId()));
        }
        if (hasText(a.filedAt())) {
            // An inclusive single day. Unparseable means no rows, not a 500: the officer typed something
            // into the date box and deserves an empty result rather than an error page.
            parseFlexibleDate(a.filedAt()).ifPresentOrElse(
                    day -> and.add(cb.and(
                            cb.greaterThanOrEqualTo(root.get("filedAt"), day.atStartOfDay()),
                            cb.lessThan(root.get("filedAt"), day.plusDays(1).atStartOfDay()))),
                    () -> and.add(cb.disjunction()));
        }
        if (hasText(a.fromEmailId())) {
            // There is no "arrived from" column on COMPLAINTS; the address a complaint came in on is the
            // complainant's email. Matched against that rather than left inert, and exact for the same
            // PII reason as the email field above.
            and.add(cb.equal(cb.lower(cb.trim(root.get("complainantEmail"))),
                    a.fromEmailId().trim().toLowerCase(Locale.ROOT)));
        }
        if (hasText(a.nodalOfficerName())) {
            and.add(nodalOfficerNamePredicate(root, query, cb, a.nodalOfficerName()));
        }
    }

    /**
     * Nodal officer name, matched through the complaint's nodal officer record.
     *
     * <p>{@code NodalOfficerRecord} has no FK to {@code Complaint} — it carries the complaint NUMBER as a
     * loose string — so this correlates on that rather than joining on an id that does not exist.
     */
    private Predicate nodalOfficerNamePredicate(Root<Complaint> root, CriteriaQuery<?> query,
                                                CriteriaBuilder cb, String name) {
        String term = "%" + name.trim().toLowerCase(Locale.ROOT) + "%";
        Subquery<Long> sub = query.subquery(Long.class);
        Root<NodalOfficerRecord> nor = sub.from(NodalOfficerRecord.class);
        sub.select(nor.get("id"));
        sub.where(cb.equal(nor.get("complaintNumber"), root.get("complaintNumber")),
                cb.or(cb.like(cb.lower(nor.<String>get("nodalOfficerName")), term),
                        cb.like(cb.lower(nor.<String>get("pnoName")), term)));
        return cb.exists(sub);
    }

    // ─────────────────────────────── column filters ───────────────────────────────

    /** The grid's per-column filter row, which narrows within whatever the tab and status already selected. */
    private void applyColumnFilters(CepcSearchRequest.ColumnFilters s, Root<Complaint> root,
                                    CriteriaQuery<?> query, CriteriaBuilder cb, List<Predicate> and) {
        if (s == null) {
            return;
        }
        if (hasText(s.complaintId())) {
            try {
                and.add(cb.equal(root.get("id"), Long.parseLong(s.complaintId().trim())));
            } catch (NumberFormatException e) {
                log.debug("Column filter complaintId '{}' is not numeric", s.complaintId());
                and.add(cb.disjunction());
            }
        }
        if (hasText(s.complaintNumber())) {
            and.add(like(cb, root.get("complaintNumber"), s.complaintNumber()));
        }
        if (hasText(s.assignedTo())) {
            and.add(like(cb, root.get("assignedOfficer"), s.assignedTo()));
        }
        if (hasText(s.mode())) {
            and.add(like(cb, root.get("filingType"), s.mode()));
        }
        if (hasText(s.complainantName())) {
            and.add(like(cb, root.get("complainantName"), s.complainantName()));
        }
        if (hasText(s.status())) {
            and.add(statusLike(cb, root, s.status()));
        }
        if (hasText(s.entityName())) {
            and.add(like(cb, root.get("entityCode"), s.entityName()));
        }
        if (hasText(s.priority())) {
            and.add(like(cb, root.get("priority"), s.priority()));
        }
        if (hasText(s.subject())) {
            and.add(like(cb, root.get("subject"), s.subject()));
        }
        if (hasText(s.complaintCategory())) {
            and.add(categoryNamePredicate(root, query, cb, s.complaintCategory()));
        }
        applyDayFilter(s.createdDate(), root.get("createdAt"), cb, and);
        applyDayFilter(s.lastUpdatedDate(), root.get("updatedAt"), cb, and);
    }

    /** Category matched by NAME, because the grid column shows a name and the officer types one. */
    private Predicate categoryNamePredicate(Root<Complaint> root, CriteriaQuery<?> query,
                                            CriteriaBuilder cb, String name) {
        Subquery<Long> sub = query.subquery(Long.class);
        Root<ComplaintCategory> cat = sub.from(ComplaintCategory.class);
        sub.select(cat.get("id"));
        sub.where(cb.equal(cat.get("id"), root.get("categoryId")),
                cb.like(cb.lower(cat.<String>get("name")),
                        "%" + name.trim().toLowerCase(Locale.ROOT) + "%"));
        return cb.exists(sub);
    }

    private void applyDayFilter(String raw, jakarta.persistence.criteria.Path<LocalDateTime> path,
                                CriteriaBuilder cb, List<Predicate> and) {
        if (!hasText(raw)) {
            return;
        }
        parseFlexibleDate(raw).ifPresentOrElse(
                day -> and.add(cb.and(
                        cb.greaterThanOrEqualTo(path, day.atStartOfDay()),
                        cb.lessThan(path, day.plusDays(1).atStartOfDay()))),
                () -> and.add(cb.disjunction()));
    }

    // ─────────────────────────────── panel filters ───────────────────────────────

    private void applyPanelFilters(CepcSearchRequest.Filters f, Root<Complaint> root,
                                   CriteriaQuery<?> query, CriteriaBuilder cb, List<Predicate> and) {
        if (f == null) {
            return;
        }
        if (notEmpty(f.states())) {
            and.add(root.get("complainantState").in(f.states()));
        }
        if (notEmpty(f.districts())) {
            and.add(root.get("complainantDistrict").in(f.districts()));
        }
        if (notEmpty(f.years())) {
            and.add(cb.function("year", Integer.class, root.get("createdAt")).in(f.years()));
        }
        if (notEmpty(f.quarters())) {
            and.add(quarterPredicate(f.quarters(), root, cb));
        }
        if (notEmpty(f.documentTypes())) {
            and.add(cb.exists(attachmentSubquery(root, query, cb, f.documentTypes())));
        }
    }

    /**
     * Filing quarter, expressed as month ranges.
     *
     * <p>Via {@code month()} rather than a {@code quarter()} call because the former is a core HQL function
     * every dialect implements, while the latter is not — and this schema is deployed on both MySQL and
     * Oracle.
     */
    private Predicate quarterPredicate(List<String> quarters, Root<Complaint> root, CriteriaBuilder cb) {
        var month = cb.function("month", Integer.class, root.get("createdAt"));
        List<Predicate> or = new ArrayList<>();
        for (String raw : quarters) {
            int q = parseQuarter(raw);
            if (q < 1) {
                log.warn("Ignoring unrecognised quarter '{}'", raw);
                continue;
            }
            or.add(cb.between(month, (q - 1) * 3 + 1, q * 3));
        }
        return or.isEmpty() ? cb.disjunction() : cb.or(or.toArray(new Predicate[0]));
    }

    /** Accepts {@code Q1}, {@code 1} and {@code Quarter 1}; returns -1 when it cannot tell. */
    private static int parseQuarter(String raw) {
        if (raw == null) {
            return -1;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() != 1) {
            return -1;
        }
        int q = digits.charAt(0) - '0';
        return (q >= 1 && q <= 4) ? q : -1;
    }

    // ─────────────────────────────── helpers ───────────────────────────────

    private void requireLongEnough(String field, String term) {
        if (hasText(term) && term.trim().length() < MIN_PARTIAL_TERM) {
            throw new UnsupportedFilterException("cepc.search.error.term_too_short." + field);
        }
    }

    private static Predicate like(CriteriaBuilder cb, jakarta.persistence.criteria.Path<String> path,
                                  String term) {
        return cb.like(cb.lower(path), "%" + term.trim().toLowerCase(Locale.ROOT) + "%");
    }

    /**
     * The status column filter, matching either the stored token or the label the grid displays.
     *
     * <p>Without the label arm, an officer typing what the chip says gets an empty grid — "Pending Office
     * Head Approval" shares no substring with the {@code incharge_review} actually stored. Same hazard the
     * date columns have, where the grid shows {@code dd/MM/yyyy} and the filter has to parse it back.
     */
    private static Predicate statusLike(CriteriaBuilder cb, Root<Complaint> root, String term) {
        Predicate onToken = like(cb, root.get("status"), term);
        Set<String> byLabel = CepcStatus.matchingLabel(term);
        if (byLabel.isEmpty()) {
            return onToken;
        }
        return cb.or(onToken, cb.lower(root.get("status")).in(byLabel));
    }

    private static List<String> lowercase(List<String> values) {
        return values.stream().map(v -> v.toLowerCase(Locale.ROOT)).toList();
    }

    /**
     * Accepts the ISO form the API documents, the {@code dd-MM-yyyy} the UI date pickers emit, and the
     * {@code dd/MM/yyyy} the dashboard grid displays.
     */
    static Optional<LocalDate> parseFlexibleDate(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        // An ISO datetime arrives from the grid when the officer picks a day; keep the date part.
        if (trimmed.length() > 10 && trimmed.charAt(10) == 'T') {
            trimmed = trimmed.substring(0, 10);
        }
        for (DateTimeFormatter fmt : List.of(ISO, DMY, DMY_SLASH)) {
            try {
                return Optional.of(LocalDate.parse(trimmed, fmt));
            } catch (Exception ignored) {
                // Try the next pattern.
            }
        }
        log.debug("CEPC search date '{}' matched no accepted pattern", raw);
        return Optional.empty();
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }

    private static String blankToDefault(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value.trim();
    }

    /** Sort fields a client may legitimately request, for the contract and for error messages. */
    public static Set<String> sortableFields() {
        return SORTABLE.keySet();
    }
}
