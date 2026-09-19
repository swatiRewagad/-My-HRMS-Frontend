package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.RbioStatusMaster;
import com.hrms.cms.repository.ComplaintRepository;
import com.hrms.cms.repository.RbioStatusMasterRepository;
import com.hrms.cms.repository.RbioStatusRoleVisibilityRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Server-side paging, sorting, filtering and role scoping for the RBIO complaint list.
 *
 * <p>Backs {@code GET /api/v1/rbio/complaints}, which did not exist: the RBIO home screen called it,
 * got a 404, and fell through to {@code generateSampleData()} — so the main RBIO list screen showed ten
 * hardcoded fake complaints with plausible names and banks. It looked like a working product in every
 * demo, which is exactly why it survived.
 *
 * <p><b>Sorting is whitelisted, not passed through.</b> A sort field arrives as a string and is
 * interpolated into a JPA {@code Sort}; an unvalidated value lets a caller order by any mapped property
 * and probe for its existence. The whitelist also stops a typo becoming a 500.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RbioComplaintListService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    /** Sortable columns, mapped from API name to entity property. */
    private static final Map<String, String> SORTABLE = Map.ofEntries(
            Map.entry("createdAt", "createdAt"),
            Map.entry("updatedAt", "updatedAt"),
            Map.entry("complaintNumber", "complaintNumber"),
            Map.entry("complainantName", "complainantName"),
            Map.entry("subject", "subject"),
            Map.entry("status", "status"),
            Map.entry("priority", "priority"),
            Map.entry("slaDeadline", "slaDeadline"),
            Map.entry("slaDueDate", "slaDeadline"),
            Map.entry("assignedOfficer", "assignedOfficer"),
            Map.entry("assignedRole", "assignedRole"),
            Map.entry("entityName", "entityCode"),
            Map.entry("milestone", "milestone"));

    private final ComplaintRepository complaintRepository;
    private final RbioStatusMasterRepository statusRepo;
    private final RbioStatusRoleVisibilityRepository visibilityRepo;

    /**
     * Shortest term accepted for a substring search (UST441).
     *
     * <p>A one-character LIKE '%a%' scans the whole table and returns most of it, which is both useless to
     * the officer and an easy way to pull the entire complaint set one page at a time. Exact-match fields
     * are exempt because they are selective by construction. Copied from the AA parent search, which set
     * this precedent for the same reason.
     */
    private static final int MIN_PARTIAL_TERM = 2;

    /** A request for a page of the RBIO list. */
    public record ListQuery(
            String statusCode,
            String search,
            String entityName,
            String priority,
            String milestone,
            String assignedOfficer,
            String assignedRole,
            String officeCode,
            String callerUserId,
            String callerRole,
            Integer page,
            Integer size,
            String sortBy,
            String sortDir,
            AdvancedSearch advanced) {

        /** Backwards-compatible constructor for callers with no advance-search criteria. */
        public ListQuery(String statusCode, String search, String entityName, String priority,
                         String milestone, String assignedOfficer, String assignedRole, String officeCode,
                         String callerUserId, String callerRole, Integer page, Integer size,
                         String sortBy, String sortDir) {
            this(statusCode, search, entityName, priority, milestone, assignedOfficer, assignedRole,
                    officeCode, callerUserId, callerRole, page, size, sortBy, sortDir, null);
        }
    }

    /**
     * The UST439 advance-search criteria, matched SERVER-side.
     *
     * <h2>Which fields are partial and which are exact, and why</h2>
     * Name and entity name are substring matches, because UST441 asks for them and an officer searching
     * for "Sharma" cannot be expected to know the full recorded name. Mobile and email are EXACT. That is
     * not an oversight: a partial search over a phone number is a PII enumeration primitive — it lets a
     * caller harvest complainants' numbers prefix by prefix and confirm whether a particular citizen has
     * complained. The AA parent search made the same call for the same reason.
     *
     * <p>{@code complaintId} is a numeric primary key, so it is parsed and matched exactly. The old client
     * lower-cased it and did a substring match, which is meaningless against a number.
     */
    public record AdvancedSearch(
            String complaintNumber,
            String complainantName,
            String complainantMobile,
            String complainantEmail,
            String statusCode,
            String complaintId,
            String fromEmailId,
            String subject,
            String modeOfReceipt,
            String entityName,
            String nodalOfficerName,
            String categoryId,
            String reportedFrom,
            String reportedTo) {

        /** Whether the officer supplied anything at all. */
        public boolean any() {
            return notBlank(complaintNumber) || notBlank(complainantName) || notBlank(complainantMobile)
                    || notBlank(complainantEmail) || notBlank(statusCode) || notBlank(complaintId)
                    || notBlank(fromEmailId) || notBlank(subject) || notBlank(modeOfReceipt)
                    || notBlank(entityName) || notBlank(nodalOfficerName) || notBlank(categoryId)
                    || notBlank(reportedFrom) || notBlank(reportedTo);
        }

        /** The partial-match fields whose term is too short to run. */
        public List<String> tooShortTerms() {
            List<String> bad = new ArrayList<>();
            if (notBlank(complainantName) && complainantName.trim().length() < MIN_PARTIAL_TERM) {
                bad.add("complainantName");
            }
            if (notBlank(entityName) && entityName.trim().length() < MIN_PARTIAL_TERM) {
                bad.add("entityName");
            }
            if (notBlank(subject) && subject.trim().length() < MIN_PARTIAL_TERM) {
                bad.add("subject");
            }
            if (notBlank(nodalOfficerName) && nodalOfficerName.trim().length() < MIN_PARTIAL_TERM) {
                bad.add("nodalOfficerName");
            }
            return bad;
        }
    }

    public Page<Complaint> search(ListQuery q) {
        int page = (q.page() == null || q.page() < 0) ? 0 : q.page();
        int size = (q.size() == null || q.size() < 1) ? DEFAULT_PAGE_SIZE : Math.min(q.size(), MAX_PAGE_SIZE);

        String property = SORTABLE.getOrDefault(
                q.sortBy() == null ? "" : q.sortBy().trim(), "createdAt");
        Sort.Direction direction = "asc".equalsIgnoreCase(q.sortDir())
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        return complaintRepository.findAll(toSpecification(q),
                PageRequest.of(page, size, Sort.by(direction, property)));
    }

    Specification<Complaint> toSpecification(ListQuery q) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();

            // RBIO only. Without this the endpoint would serve CEPC and AA complaints to an RBIO
            // officer — a cross-module data leak, not merely a wrong list.
            and.add(cb.equal(root.get("department"), "RBIO"));

            applyStatusFilter(q, root, cb, and);

            // Free-text search across the fields the grid displays. Case-insensitive partial match;
            // deliberately excludes email and phone, which are PII the grid does not show and which
            // would let the search box be used to confirm whether a given citizen has complained.
            if (notBlank(q.search())) {
                String term = "%" + q.search().trim().toLowerCase(Locale.ROOT) + "%";
                and.add(cb.or(
                        cb.like(cb.lower(root.get("complaintNumber")), term),
                        cb.like(cb.lower(root.get("complainantName")), term),
                        cb.like(cb.lower(root.get("subject")), term)));
            }

            if (notBlank(q.entityName())) {
                and.add(cb.like(cb.lower(root.get("entityCode")),
                        "%" + q.entityName().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (notBlank(q.priority())) {
                and.add(cb.equal(cb.lower(root.get("priority")), q.priority().trim().toLowerCase(Locale.ROOT)));
            }
            if (notBlank(q.milestone())) {
                and.add(cb.equal(root.get("milestone"), q.milestone().trim().toUpperCase(Locale.ROOT)));
            }
            if (notBlank(q.assignedOfficer())) {
                and.add(cb.equal(root.get("assignedOfficer"), q.assignedOfficer().trim()));
            }
            if (notBlank(q.assignedRole())) {
                and.add(cb.equal(root.get("assignedRole"), q.assignedRole().trim()));
            }
            if (notBlank(q.officeCode())) {
                and.add(cb.equal(root.get("rbioOfficeCode"), q.officeCode().trim()));
            }

            applyAdvancedSearch(q.advanced(), root, cb, and);

            return cb.and(and.toArray(new Predicate[0]));
        };
    }

    /**
     * Applies the UST439-441 advance-search criteria.
     *
     * <p>Every criterion is ANDed, so adding a field narrows the result rather than widening it. A field
     * the schema cannot answer is REFUSED by the controller rather than silently ignored here — an ignored
     * criterion produces a result set that looks like an answer to a question nobody asked, which is worse
     * than an error message.
     */
    private void applyAdvancedSearch(AdvancedSearch a,
                                     jakarta.persistence.criteria.Root<Complaint> root,
                                     jakarta.persistence.criteria.CriteriaBuilder cb,
                                     List<Predicate> and) {
        if (a == null) {
            return;
        }

        // Complaint number: exact, case-folded. A complaint number is quoted in full or not at all.
        if (notBlank(a.complaintNumber())) {
            and.add(cb.equal(cb.upper(cb.trim(root.get("complaintNumber"))),
                    a.complaintNumber().trim().toUpperCase(Locale.ROOT)));
        }

        // UST441: partial match on complainant name.
        if (notBlank(a.complainantName())) {
            and.add(cb.like(cb.lower(root.get("complainantName")),
                    "%" + a.complainantName().trim().toLowerCase(Locale.ROOT) + "%"));
        }

        // EXACT, deliberately — see the AdvancedSearch javadoc on PII enumeration.
        if (notBlank(a.complainantMobile())) {
            and.add(cb.equal(cb.trim(root.get("complainantPhone")), a.complainantMobile().trim()));
        }
        if (notBlank(a.complainantEmail())) {
            and.add(cb.equal(cb.lower(cb.trim(root.get("complainantEmail"))),
                    a.complainantEmail().trim().toLowerCase(Locale.ROOT)));
        }

        // From Email ID has no column of its own on COMPLAINTS; the address a complaint arrived from is
        // the complainant's email. Matched against that rather than left inert, and exact for the same
        // PII reason.
        if (notBlank(a.fromEmailId())) {
            and.add(cb.equal(cb.lower(cb.trim(root.get("complainantEmail"))),
                    a.fromEmailId().trim().toLowerCase(Locale.ROOT)));
        }

        if (notBlank(a.subject())) {
            and.add(cb.like(cb.lower(root.get("subject")),
                    "%" + a.subject().trim().toLowerCase(Locale.ROOT) + "%"));
        }

        // UST441: partial match on entity name. NOTE this matches ENTITY_CODE, the only entity column on
        // COMPLAINTS, whose data is dirty — it holds 'HDFC Bank' on some rows and 'PNB' on others, and is
        // NULL on many. So a name search here under-returns, and that is a data problem this query cannot
        // fix. Reported rather than papered over with a join that would silently drop unmapped entities.
        if (notBlank(a.entityName())) {
            and.add(cb.like(cb.lower(root.get("entityCode")),
                    "%" + a.entityName().trim().toLowerCase(Locale.ROOT) + "%"));
        }

        // Mode of receipt is backed by FILING_TYPE. Exact because it is a controlled value, not prose.
        if (notBlank(a.modeOfReceipt())) {
            and.add(cb.equal(cb.lower(cb.trim(root.get("filingType"))),
                    a.modeOfReceipt().trim().toLowerCase(Locale.ROOT)));
        }

        if (notBlank(a.statusCode())) {
            statusRepo.findById(a.statusCode().trim().toUpperCase(Locale.ROOT)).ifPresentOrElse(
                    status -> {
                        if (notBlank(status.getLegacyValue())) {
                            and.add(cb.equal(cb.lower(root.get("status")),
                                    status.getLegacyValue().toLowerCase(Locale.ROOT)));
                        } else {
                            and.add(cb.disjunction());
                        }
                    },
                    () -> {
                        log.warn("Advance search used unknown status code '{}' — returning no rows",
                                a.statusCode());
                        and.add(cb.disjunction());
                    });
        }

        // Numeric primary key. An unparseable value matches nothing rather than being dropped: the
        // officer typed something into the Complaint Id box and deserves an empty result, not a silently
        // unfiltered list of every complaint.
        if (notBlank(a.complaintId())) {
            try {
                and.add(cb.equal(root.get("id"), Long.parseLong(a.complaintId().trim())));
            } catch (NumberFormatException e) {
                log.debug("Advance search complaintId '{}' is not numeric", a.complaintId());
                and.add(cb.disjunction());
            }
        }

        if (notBlank(a.categoryId())) {
            try {
                and.add(cb.equal(root.get("categoryId"), Long.parseLong(a.categoryId().trim())));
            } catch (NumberFormatException e) {
                log.debug("Advance search categoryId '{}' is not numeric", a.categoryId());
                and.add(cb.disjunction());
            }
        }

        // Reported On, as an inclusive day range. The end bound is the START of the following day so a
        // complaint filed at 16:20 on the closing date is included — comparing against the date itself
        // would silently exclude everything filed after midnight on that day.
        if (notBlank(a.reportedFrom())) {
            parseDate(a.reportedFrom()).ifPresentOrElse(
                    from -> and.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from.atStartOfDay())),
                    () -> and.add(cb.disjunction()));
        }
        if (notBlank(a.reportedTo())) {
            parseDate(a.reportedTo()).ifPresentOrElse(
                    to -> and.add(cb.lessThan(root.get("createdAt"), to.plusDays(1).atStartOfDay())),
                    () -> and.add(cb.disjunction()));
        }
    }

    private static Optional<java.time.LocalDate> parseDate(String raw) {
        try {
            return Optional.of(java.time.LocalDate.parse(raw.trim()));
        } catch (Exception e) {
            log.debug("Advance search date '{}' is not an ISO date", raw);
            return Optional.empty();
        }
    }

    /**
     * Applies the chosen filter, which may be a STATUS, a QUEUE or a SCOPE.
     *
     * <p>The three are not interchangeable and this is where the distinction earns its keep. A SCOPE
     * ("Complaint Assigned to Me") constrains the CALLER, not the complaint — there is no complaint whose
     * status is {@code ASSIGNED_TO_ME}, so treating it as a status yields a permanently empty grid that
     * looks like "you have no work" rather than a bug.
     */
    private void applyStatusFilter(ListQuery q, jakarta.persistence.criteria.Root<Complaint> root,
                                   jakarta.persistence.criteria.CriteriaBuilder cb, List<Predicate> and) {
        String code = q.statusCode();
        if (!notBlank(code)) {
            return;
        }
        code = code.trim().toUpperCase(Locale.ROOT);

        Optional<RbioStatusMaster> opt = statusRepo.findById(code);
        if (opt.isEmpty()) {
            // An unknown filter code must NOT silently widen the result set to everything. Matching
            // nothing is the safe failure: a visibly empty grid gets reported, a silently unfiltered one
            // shows an officer complaints outside their remit.
            log.warn("Unknown RBIO status filter '{}' — returning no rows", code);
            and.add(cb.disjunction());
            return;
        }

        RbioStatusMaster status = opt.get();
        switch (status.getFilterKind()) {
            case "SCOPE" -> applyScope(code, q, root, cb, and);

            case "QUEUE" -> {
                // A queue is "sitting with role X". Status is not constrained: a file can be with the
                // Reviewer while in_progress or escalated, and pinning a status here would hide most of
                // the queue.
                if (notBlank(status.getQueueRole())) {
                    and.add(cb.equal(root.get("assignedRole"), status.getQueueRole()));
                }
            }

            default -> {
                if (notBlank(status.getLegacyValue())) {
                    and.add(cb.equal(cb.lower(root.get("status")),
                            status.getLegacyValue().toLowerCase(Locale.ROOT)));
                } else {
                    // A STATUS row with no legacy value is a backlog status nothing writes yet. Match
                    // nothing rather than everything, for the reason above.
                    log.debug("RBIO status '{}' has no legacy value — no complaint can match it yet", code);
                    and.add(cb.disjunction());
                }
            }
        }
    }

    private void applyScope(String code, ListQuery q, jakarta.persistence.criteria.Root<Complaint> root,
                            jakarta.persistence.criteria.CriteriaBuilder cb, List<Predicate> and) {
        switch (code) {
            case "ALL" -> {
                // No additional predicate. The department predicate still applies.
            }
            case "ASSIGNED_TO_ME" -> {
                if (notBlank(q.callerUserId())) {
                    and.add(cb.equal(root.get("assignedOfficer"), q.callerUserId()));
                } else {
                    // "Assigned to me" with no identity cannot be answered. Returning the unfiltered
                    // list would show every officer's work to a caller who asked only for their own.
                    and.add(cb.disjunction());
                }
            }
            case "CREATED_BY_ME" -> {
                // There is no created_by column on COMPLAINTS: authorship lives in the timeline. Rather
                // than invent a column here — a schema change that belongs to the story that needs it —
                // this scope is not yet answerable and matches nothing, which is visible, instead of
                // falling back to assignedOfficer, which would be silently WRONG data.
                log.debug("CREATED_BY_ME requested but COMPLAINTS has no author column");
                and.add(cb.disjunction());
            }
            default -> log.warn("Unhandled RBIO scope filter '{}'", code);
        }
    }

    /** The filter tabs a role may see, from data. Empty when the role has no configured visibility. */
    public List<Map<String, Object>> filtersFor(String role) {
        if (!notBlank(role)) return List.of();

        List<Map<String, Object>> out = new ArrayList<>();
        for (var vis : visibilityRepo.findByRoleNameOrderByDisplayOrderAsc(role.trim().toUpperCase(Locale.ROOT))) {
            statusRepo.findById(vis.getStatusCode()).ifPresent(status -> {
                if (!"Y".equalsIgnoreCase(status.getIsActive())) return;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("statusCode", status.getStatusCode());
                item.put("label", status.getLabelEn());
                item.put("translationKey", status.getTranslationKey());
                item.put("filterKind", status.getFilterKind());
                item.put("milestone", status.getMilestoneCode());
                item.put("isDefault", vis.isDefaultFilter());
                out.add(item);
            });
        }
        return out;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Field set the grid renders. Excludes description and every PII field the list does not show. */
    public Map<String, Object> toListItem(Complaint c) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("complaintId", c.getId());
        item.put("complaintNumber", c.getComplaintNumber());
        item.put("complainantName", c.getComplainantName());
        item.put("subject", c.getSubject());
        item.put("status", c.getStatus());
        item.put("milestone", c.getMilestone());
        item.put("workflowStage", c.getWorkflowStage());
        item.put("priority", c.getPriority() != null ? c.getPriority().toUpperCase(Locale.ROOT) : "MEDIUM");
        item.put("entityName", c.getEntityCode());
        item.put("modeOfReceipt", c.getFilingType());
        item.put("assignedOfficer", c.getAssignedOfficer());
        item.put("assignedRole", c.getAssignedRole());
        item.put("officeCode", c.getRbioOfficeCode());
        item.put("createdAt", c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);
        item.put("slaDueDate", c.getSlaDeadline() != null ? c.getSlaDeadline().toString() : null);
        // Lets the UI send the version back on a write so a stale save is refused with 409 rather than
        // silently overwriting a colleague's decision (UST675).
        item.put("recordVersion", c.getRecordVersion());
        return item;
    }

    /** Sort fields a client may legitimately request, for the contract and for error messages. */
    public static Set<String> sortableFields() {
        return SORTABLE.keySet();
    }
}
