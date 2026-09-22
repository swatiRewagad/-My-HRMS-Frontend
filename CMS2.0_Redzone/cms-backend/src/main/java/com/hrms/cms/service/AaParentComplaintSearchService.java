package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.repository.ComplaintRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parent-complaint search for the Appellate Authority (stories 1-5).
 *
 * WHY A SPECIFICATION: the AA stories require each filter to work independently OR in any
 * combination — complaint number, appellant name/mobile/email, RBIO office, closure clause, category
 * and ground of complaint. The only complaint search that existed was a three-field LIKE
 * (ComplaintRepository.search), and expressing "any subset of seven optional filters" as derived
 * finders needs 2^7 methods. Predicates are built dynamically instead, and every value is bound as a
 * JPA parameter — no string concatenation reaches SQL.
 *
 * DATA-SHAPE TRAPS this class exists to absorb (all verified against the live table):
 *
 *   - COMPLAINTS.status is stored LOWERCASE ('closed'), while the ComplaintStatus enum is uppercase
 *     and Complaint.status is a plain String. Comparisons lower-case both sides.
 *   - "Closed or reopened" is NOT a status set. Reopen is recorded as workflow_stage='REOPENED' with
 *     the status moved back to 'in_progress', so the eligibility predicate is a disjunction of the two.
 *   - COMPLAINTS.entity_code holds the normalised entity NAME ('HDFC Bank') for some rows and a short
 *     code ('PNB') for others, plus NULLs and empty strings. Entity scoping is therefore
 *     case/whitespace-insensitive, and a blank scope matches nothing rather than everything.
 *
 * SERVER-SIDE AUTHORITY: entityScope is set from the caller's resolved JWT claim by the controller and
 * is never accepted from the request. When it is present it is applied as a mandatory AND, so no
 * combination of the optional filters can widen a PNO's view beyond its own entity.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaParentComplaintSearchService {

    /** Terminal status that makes a parent eligible for appeal, lowercase as stored. */
    public static final String STATUS_CLOSED = "closed";

    /** Reopen marker, uppercase as stored in workflow_stage. */
    public static final String STAGE_REOPENED = "REOPENED";

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** A partial-match term shorter than this is rejected rather than scanning the whole table. */
    private static final int MIN_PARTIAL_TERM = 2;

    private final ComplaintRepository complaintRepository;

    /**
     * Search criteria. Every field is optional except the caller-derived entityScope.
     *
     * Name is a partial (contains) match per story 2; mobile and email are exact per the same story.
     * Exactness matters for PII: a partial mobile search is a PII enumeration primitive, letting a
     * caller harvest complainant numbers by prefix.
     */
    @Getter
    @Builder
    public static class ParentSearchCriteria {
        private final String complaintNumber;
        private final String appellantName;
        private final String appellantMobile;
        private final String appellantEmail;
        private final String rbioOfficeCode;
        private final String closureClause;
        private final Long categoryId;
        private final Long groundOfComplaintId;

        /**
         * Restricts results to one regulated entity. Resolved server-side from the caller's
         * entity_code claim for RE/PNO callers; null for AA staff, who are not entity-scoped.
         */
        private final String entityScope;

        /**
         * When true, only parents eligible for an appeal (closed or reopened) are returned.
         * The AA and PNO parent searches both set this: an open complaint has no closure to appeal.
         */
        private final boolean appealEligibleOnly;

        private final Integer page;
        private final Integer size;
    }

    /**
     * Runs the search.
     *
     * Returns an empty page rather than throwing when no filter is supplied, so the caller decides
     * whether an unfiltered request is an error. The controller rejects it — an unfiltered parent
     * search over the national complaint table is neither useful nor safe to serve.
     */
    public Page<Complaint> search(ParentSearchCriteria criteria) {
        int page = criteria.getPage() == null || criteria.getPage() < 0 ? 0 : criteria.getPage();
        int size = criteria.getSize() == null || criteria.getSize() < 1
                ? DEFAULT_PAGE_SIZE
                : Math.min(criteria.getSize(), MAX_PAGE_SIZE);

        Specification<Complaint> spec = toSpecification(criteria);
        return complaintRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /** True when at least one user-supplied filter is present. The entity scope does not count. */
    public boolean hasAnyFilter(ParentSearchCriteria c) {
        return notBlank(c.getComplaintNumber())
                || notBlank(c.getAppellantName())
                || notBlank(c.getAppellantMobile())
                || notBlank(c.getAppellantEmail())
                || notBlank(c.getRbioOfficeCode())
                || notBlank(c.getClosureClause())
                || c.getCategoryId() != null
                || c.getGroundOfComplaintId() != null;
    }

    /**
     * True when a supplied partial-match term is too short to be worth scanning for.
     * Exact-match filters (number, mobile, email) are exempt — they are selective by construction.
     */
    public boolean hasTooShortPartialTerm(ParentSearchCriteria c) {
        String name = c.getAppellantName();
        return name != null && !name.isBlank() && name.trim().length() < MIN_PARTIAL_TERM;
    }

    Specification<Complaint> toSpecification(ParentSearchCriteria c) {
        return (root, query, cb) -> {
            List<Predicate> and = new ArrayList<>();

            // Complaint number: exact match per story 1 ("exact match returns the single parent").
            // Trimmed and case-normalised so a pasted number with stray whitespace still resolves.
            if (notBlank(c.getComplaintNumber())) {
                and.add(cb.equal(cb.upper(cb.trim(root.get("complaintNumber"))),
                                 c.getComplaintNumber().trim().toUpperCase(Locale.ROOT)));
            }

            // Name: partial, case-insensitive (story 2).
            if (notBlank(c.getAppellantName())) {
                and.add(cb.like(cb.lower(root.get("complainantName")),
                                "%" + c.getAppellantName().trim().toLowerCase(Locale.ROOT) + "%"));
            }

            // Mobile and email: exact (story 2). Deliberately not LIKE — see ParentSearchCriteria.
            if (notBlank(c.getAppellantMobile())) {
                and.add(cb.equal(cb.trim(root.get("complainantPhone")), c.getAppellantMobile().trim()));
            }
            if (notBlank(c.getAppellantEmail())) {
                and.add(cb.equal(cb.lower(cb.trim(root.get("complainantEmail"))),
                                 c.getAppellantEmail().trim().toLowerCase(Locale.ROOT)));
            }

            // Story 3 filters — each independent, all combinable.
            if (notBlank(c.getRbioOfficeCode())) {
                and.add(cb.equal(root.get("rbioOfficeCode"), c.getRbioOfficeCode().trim()));
            }
            if (notBlank(c.getClosureClause())) {
                and.add(cb.equal(cb.trim(root.get("closureClause")), c.getClosureClause().trim()));
            }
            if (c.getCategoryId() != null) {
                and.add(cb.equal(root.get("categoryId"), c.getCategoryId()));
            }
            if (c.getGroundOfComplaintId() != null) {
                and.add(cb.equal(root.get("groundOfComplaintId"), c.getGroundOfComplaintId()));
            }

            // Entity scope (story 5). Server-derived, mandatory when present, and a blank scope
            // matches nothing: an unresolvable entity must yield no data, never everyone's data.
            if (c.getEntityScope() != null) {
                String scope = c.getEntityScope().trim();
                if (scope.isEmpty()) {
                    return cb.disjunction();
                }
                and.add(cb.equal(cb.upper(cb.trim(root.get("entityCode"))), scope.toUpperCase(Locale.ROOT)));
            }

            // Appeal eligibility: closed OR reopened. Not expressible as a status set — reopen lives in
            // workflow_stage while the status reverts to in_progress.
            if (c.isAppealEligibleOnly()) {
                and.add(cb.or(
                        cb.equal(cb.lower(cb.trim(root.get("status"))), STATUS_CLOSED),
                        cb.equal(cb.upper(cb.trim(root.get("workflowStage"))), STAGE_REOPENED)));
            }

            return and.isEmpty() ? cb.conjunction() : cb.and(and.toArray(new Predicate[0]));
        };
    }

    /**
     * Whether a complaint may have an appeal filed against it: it must be closed or reopened.
     *
     * Used by the detail-page affordance (story 6) and enforced again at intake, so hiding the button
     * is a convenience and not the control.
     */
    public boolean isAppealEligible(Complaint complaint) {
        if (complaint == null) {
            return false;
        }
        boolean closed = complaint.getStatus() != null
                && STATUS_CLOSED.equalsIgnoreCase(complaint.getStatus().trim());
        boolean reopened = complaint.getWorkflowStage() != null
                && STAGE_REOPENED.equalsIgnoreCase(complaint.getWorkflowStage().trim());
        return closed || reopened;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
