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
            String sortDir) {
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

            return cb.and(and.toArray(new Predicate[0]));
        };
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
