package com.hrms.cms.service;

import com.hrms.cms.entity.Appeal;
import com.hrms.cms.entity.AppealOrder;
import com.hrms.cms.repository.AppealOrderRepository;
import com.hrms.cms.repository.AppealRepository;
import com.hrms.cms.repository.ClosureClauseMasterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Issues and corrects appeal orders.
 *
 * <p>An order is a legal instrument, so this service NEVER updates one. {@link #issue} writes revision
 * 1 and refuses a second; {@link #correct} appends revision N+1 with a mandatory reason and stamps the
 * prior revision as superseded. Both the entity ({@code updatable=false} on every substantive column)
 * and this service enforce that -- belt and braces, because the previous behaviour let an
 * AA_SECRETARIAT caller silently replace an issued order by re-POSTing PASS_ORDER.
 *
 * <p>Phase 1 is TEXT ONLY. The order is persisted completely and exposed for rendering; no PDF is
 * generated. That is a deliberate scope call: the one existing precedent fabricates its reference
 * number in the browser and persists nothing, so it is not a foundation to build on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaAppealOrderService {

    static final String CFG_REQUIRE_ED_APPROVAL = "cms.aa.order.require_ed_approval";
    static final String CFG_SCHEME_VERSION = "cms.aa.order.scheme_version";
    /**
     * Statutory award ceiling in rupees, applied to the order total. 0 or absent = unenforced.
     * Now ARMED at the sum of the two component ceilings. See {@link #validateAward}.
     */
    static final String CFG_MAX_AWARD_AMOUNT = "cms.aa.order.max_award_amount";
    /** Ceiling on financial/consequential-loss compensation in an AA order. Mirrors the RBIO cap. */
    static final String CFG_MAX_FINANCIAL_COMPENSATION = "cms.aa.order.max_financial_compensation";
    /** Ceiling on mental-harassment compensation in an AA order. Mirrors the RBIO cap. */
    static final String CFG_MAX_HARASSMENT_COMPENSATION = "cms.aa.order.max_harassment_compensation";
    /** Ceiling on the two components combined. Must equal financial + harassment. */
    static final String CFG_MAX_COMBINED = "cms.aa.order.max_combined";

    /**
     * Documented defaults, armed. The AA ceiling mirrors RBIO exactly by ruling: 30,00,000 financial
     * plus 3,00,000 mental harassment, 33,00,000 combined.
     *
     * <p>These are no longer 0/unenforced. An absent config row previously meant "no ceiling at all",
     * so an AA order could record an award of any size — the statutory limit existed only in prose.
     * The values remain configurable; only the default changed.
     */
    static final long DEFAULT_MAX_FINANCIAL_COMPENSATION = 3000000L;
    static final long DEFAULT_MAX_HARASSMENT_COMPENSATION = 300000L;
    static final long DEFAULT_MAX_COMBINED =
            DEFAULT_MAX_FINANCIAL_COMPENSATION + DEFAULT_MAX_HARASSMENT_COMPENSATION;

    /** Award component an amount is being validated against. */
    public static final String COMPENSATION_FINANCIAL = "FINANCIAL";
    public static final String COMPENSATION_HARASSMENT = "HARASSMENT";
    public static final String COMPENSATION_COMBINED = "COMBINED";
    /** Refuse an order on a declared sub-judice matter without a stated ground. Default false. */
    static final String CFG_BLOCK_SUB_JUDICE = "cms.aa.order.block_sub_judice";

    private static final String DEFAULT_SCHEME_VERSION = "RBIOS_2021";

    private static final Set<String> VALID_OUTCOMES = Set.of(
            AppealOrder.OUTCOME_UPHELD,
            AppealOrder.OUTCOME_MODIFIED,
            AppealOrder.OUTCOME_SET_ASIDE,
            AppealOrder.OUTCOME_REMANDED,
            AppealOrder.OUTCOME_DISMISSED);

    /** Outcomes for which a monetary award is meaningful. */
    private static final Set<String> AWARD_BEARING_OUTCOMES = Set.of(
            AppealOrder.OUTCOME_MODIFIED, AppealOrder.OUTCOME_UPHELD);

    private final AppealOrderRepository orderRepository;
    private final AppealRepository appealRepository;
    private final ClosureClauseMasterRepository clauseRepository;
    private final SystemConfigService systemConfigService;

    /** Raised when an order already exists. Prevents silent replacement of an issued decision. */
    public static class OrderAlreadyIssuedException extends RuntimeException {
        public OrderAlreadyIssuedException(String message) {
            super(message);
        }
    }

    /** Raised when ED approval is required by configuration but has not been recorded. */
    public static class EdApprovalRequiredException extends RuntimeException {
        public EdApprovalRequiredException(String message) {
            super(message);
        }
    }

    /**
     * Raised when an award exceeds the configured statutory ceiling.
     *
     * <p>A distinct type rather than a bare IllegalArgumentException so the ceiling can be reported to
     * the operator as a refusal on legal grounds, and so a test can prove the cap specifically fired.
     */
    public static class AwardCapExceededException extends RuntimeException {
        public AwardCapExceededException(String message) {
            super(message);
        }
    }

    /** Raised when an order would issue on a matter declared to be before a court. */
    public static class SubJudiceException extends RuntimeException {
        public SubJudiceException(String message) {
            super(message);
        }
    }

    /**
     * Issues revision 1.
     *
     * <p>ED approval is enforced only when {@code cms.aa.order.require_ed_approval} is true. It
     * defaults to FALSE: making it a gate is net-new behaviour rather than a bug fix (the columns have
     * never had a consumer), and defaulting it on would block every order the moment this ships. The
     * approval state is COPIED onto the order either way, so the columns stop being write-once dead
     * data and the order records what approved it.
     */
    @Transactional
    public AppealOrder issue(String appealNumber, String outcome, String orderSummary,
                             BigDecimal awardAmount, String clauseCode, String ground,
                             String actor, String actorRole) {
        Appeal appeal = requireAppeal(appealNumber);

        if (orderRepository.existsByAppealNumber(appealNumber)) {
            throw new OrderAlreadyIssuedException(
                    "An order has already been issued on " + appealNumber
                            + ". Record a correction instead of replacing it.");
        }

        String normalisedOutcome = requireValidOutcome(outcome);
        requireSummary(orderSummary);
        String validatedClause = validateClause(clauseCode);
        BigDecimal award = validateAward(normalisedOutcome, awardAmount);

        if (systemConfigService.getBoolean(CFG_REQUIRE_ED_APPROVAL, false)
                && !Boolean.TRUE.equals(appeal.getEdApprovalGiven())) {
            throw new EdApprovalRequiredException(
                    "ED approval has not been recorded on " + appealNumber + ", so an order cannot issue");
        }

        requireNotSubJudice(appeal, ground);

        AppealOrder order = orderRepository.save(AppealOrder.builder()
                .appealNumber(appealNumber)
                .revisionNo(1)
                .outcome(normalisedOutcome)
                .orderSummary(orderSummary.trim())
                .awardAmount(award)
                .clauseCode(validatedClause)
                .ground(trimToNull(ground))
                .issuingAuthority(actor == null ? "system" : actor)
                .issuingAuthorityRole(actorRole)
                .orderDate(LocalDateTime.now())
                .edApprovalGiven(appeal.getEdApprovalGiven())
                .edApprovalBy(Boolean.TRUE.equals(appeal.getEdApprovalGiven())
                        ? appeal.getCreatedBy() : null)
                .edApprovalAt(appeal.getEdApprovalDate())
                .performedBy(actor == null ? "system" : actor)
                .performedByRole(actorRole)
                .build());

        mirrorOntoAppeal(appeal, order);
        return order;
    }

    /**
     * Appends a correction as a new revision, linked to the one it replaces.
     *
     * <p>The prior revision keeps every value it issued with; only its supersession stamps change. A
     * reader can therefore always reconstruct exactly what the parties were originally told, which an
     * in-place edit would destroy.
     */
    @Transactional
    public AppealOrder correct(String appealNumber, String outcome, String orderSummary,
                               BigDecimal awardAmount, String clauseCode, String ground,
                               String correctionReason, String actor, String actorRole) {
        Appeal appeal = requireAppeal(appealNumber);

        if (correctionReason == null || correctionReason.isBlank()) {
            throw new IllegalArgumentException(
                    "A reason is required to correct an issued order");
        }

        AppealOrder current = orderRepository.findOperativeOne(appealNumber)
                .orElseThrow(() -> new IllegalStateException(
                        "There is no issued order on " + appealNumber + " to correct"));

        String normalisedOutcome = requireValidOutcome(outcome);
        requireSummary(orderSummary);
        String validatedClause = validateClause(clauseCode);
        BigDecimal award = validateAward(normalisedOutcome, awardAmount);

        AppealOrder correction = orderRepository.save(AppealOrder.builder()
                .appealNumber(appealNumber)
                .revisionNo(orderRepository.maxRevisionNo(appealNumber) + 1)
                .supersedesOrderId(current.getId())
                .correctionReason(correctionReason.trim())
                .outcome(normalisedOutcome)
                .orderSummary(orderSummary.trim())
                .awardAmount(award)
                .clauseCode(validatedClause)
                .ground(trimToNull(ground))
                .issuingAuthority(actor == null ? "system" : actor)
                .issuingAuthorityRole(actorRole)
                .orderDate(LocalDateTime.now())
                .edApprovalGiven(appeal.getEdApprovalGiven())
                .edApprovalAt(appeal.getEdApprovalDate())
                .performedBy(actor == null ? "system" : actor)
                .performedByRole(actorRole)
                .build());

        current.setSupersededAt(LocalDateTime.now());
        current.setSupersededById(correction.getId());
        orderRepository.save(current);

        mirrorOntoAppeal(appeal, correction);
        return correction;
    }

    @Transactional(readOnly = true)
    public List<AppealOrder> revisions(String appealNumber) {
        return orderRepository.findByAppealNumberOrderByRevisionNoAscIdAsc(appealNumber);
    }

    @Transactional(readOnly = true)
    public Optional<AppealOrder> operative(String appealNumber) {
        return orderRepository.findOperativeOne(appealNumber);
    }

    /** Notice interpolation values for an order. */
    public Map<String, String> noticeParams(AppealOrder order) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("outcome", order.getOutcome());
        params.put("orderDate", order.getOrderDate().toString());
        if (order.getAwardAmount() != null) params.put("awardAmount", order.getAwardAmount().toPlainString());
        if (order.getClauseCode() != null) params.put("clauseCode", order.getClauseCode());
        params.put("dedupe", "O" + order.getId());
        return params;
    }

    /**
     * Keeps the appeal's denormalised order columns in step with the operative revision.
     *
     * <p>Those columns are read by list screens and the public status endpoint. They are a MIRROR: the
     * APPEAL_ORDER table is the record of truth, and a correction updates the mirror precisely because
     * the mirror should always show what is currently in force.
     */
    private void mirrorOntoAppeal(Appeal appeal, AppealOrder order) {
        appeal.setOrderOutcome(order.getOutcome());
        appeal.setOrderSummary(order.getOrderSummary());
        appeal.setOrderDate(order.getOrderDate());
        if (order.getAwardAmount() != null) {
            appeal.setAwardModifiedAmount(order.getAwardAmount());
        }
        if (order.getClauseCode() != null) {
            appeal.setClosureClause(order.getClauseCode());
        }
        appealRepository.save(appeal);
    }

    /**
     * Validates the clause against CLOSURE_CLAUSE_MASTER rather than accepting free text.
     *
     * <p>A clause cited on an order must be a real clause of the Scheme in force. Note the master
     * currently holds COMPLAINT-CLOSURE clauses; whether an appeal order's ground should be drawn from
     * this same vocabulary is flagged for legal sign-off. Unknown codes are rejected rather than
     * stored, so no order can cite a clause that does not exist.
     */
    private String validateClause(String clauseCode) {
        String code = trimToNull(clauseCode);
        if (code == null) {
            return null;
        }
        String scheme = systemConfigService.getString(CFG_SCHEME_VERSION, DEFAULT_SCHEME_VERSION);
        return clauseRepository.findInForce(scheme, code, LocalDate.now())
                .map(clause -> code)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Clause '" + code + "' is not an in-force clause of " + scheme));
    }

    private String requireValidOutcome(String outcome) {
        String normalised = outcome == null ? "" : outcome.trim().toUpperCase();
        if (!VALID_OUTCOMES.contains(normalised)) {
            throw new IllegalArgumentException(
                    "Outcome must be one of " + VALID_OUTCOMES + " but was '" + outcome + "'");
        }
        return normalised;
    }

    private void requireSummary(String orderSummary) {
        if (orderSummary == null || orderSummary.isBlank()) {
            throw new IllegalArgumentException("An order summary is required -- an order with no reasoning is not an order");
        }
    }

    /**
     * A negative award is always a mistake; an award on a non-award outcome is dropped; and an award
     * above the configured statutory ceiling is REFUSED.
     *
     * <p>Dropping rather than rejecting a non-award outcome because a UI that leaves a stale amount in
     * a hidden field should not block a dismissal, but the amount must not be recorded against an
     * outcome that awards nothing.
     *
     * <p><b>The ceiling is configuration, and it is now ARMED.</b> It previously defaulted to 0, which
     * meant no ceiling was enforced at all and an AA order could exceed the statutory limit. The
     * default is the combined ceiling of 33,00,000 (30,00,000 financial + 3,00,000 harassment),
     * mirroring RBIO. Setting {@code cms.aa.order.max_award_amount} to 0 still disables the check, so
     * an operator retains an escape hatch, but shipping unenforced is no longer the default.
     *
     * <p>{@code AppealOrder} records one award TOTAL, so the total is what can be checked here; the
     * per-component ceilings are enforced by {@link #validateAwardComponent} for callers that know
     * which component an amount represents.
     */
    private BigDecimal validateAward(String outcome, BigDecimal awardAmount) {
        if (awardAmount == null) return null;
        if (awardAmount.signum() < 0) {
            throw new IllegalArgumentException("An award amount cannot be negative");
        }
        if (!AWARD_BEARING_OUTCOMES.contains(outcome)) {
            log.debug("AA order: award amount ignored for outcome {}", outcome);
            return null;
        }

        long ceiling = systemConfigService.getLong(CFG_MAX_AWARD_AMOUNT, DEFAULT_MAX_COMBINED);
        if (ceiling > 0 && awardAmount.compareTo(BigDecimal.valueOf(ceiling)) > 0) {
            throw new AwardCapExceededException(String.format(
                    "Award amount Rs %s exceeds the maximum permitted cap of Rs %s. "
                            + "Per the RBI Integrated Ombudsman Scheme, this award cannot be issued.",
                    awardAmount.toPlainString(), BigDecimal.valueOf(ceiling).toPlainString()));
        }
        return awardAmount;
    }

    /**
     * Returns the configured ceiling for one award component, so the two tiers cannot drift apart.
     *
     * @param component FINANCIAL, HARASSMENT or COMBINED
     */
    public BigDecimal maxCompensation(String component) {
        if (component == null) {
            throw new IllegalArgumentException("Compensation component must not be null");
        }
        return switch (component.toUpperCase()) {
            case COMPENSATION_FINANCIAL -> BigDecimal.valueOf(systemConfigService.getLong(
                    CFG_MAX_FINANCIAL_COMPENSATION, DEFAULT_MAX_FINANCIAL_COMPENSATION));
            case COMPENSATION_HARASSMENT -> BigDecimal.valueOf(systemConfigService.getLong(
                    CFG_MAX_HARASSMENT_COMPENSATION, DEFAULT_MAX_HARASSMENT_COMPENSATION));
            case COMPENSATION_COMBINED -> BigDecimal.valueOf(systemConfigService.getLong(
                    CFG_MAX_COMBINED, DEFAULT_MAX_COMBINED));
            default -> throw new IllegalArgumentException(
                    "Unknown compensation component: " + component
                            + ". Valid components are: FINANCIAL, HARASSMENT, COMBINED");
        };
    }

    /**
     * Refuses an amount that exceeds the ceiling for its component. A ceiling of 0 disables the check,
     * matching {@link #validateAward}.
     */
    public void validateAwardComponent(BigDecimal amount, String component) {
        if (amount == null) return;
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("An award amount cannot be negative");
        }
        BigDecimal ceiling = maxCompensation(component);
        if (ceiling.signum() > 0 && amount.compareTo(ceiling) > 0) {
            throw new AwardCapExceededException(String.format(
                    "Award amount Rs %s exceeds the maximum permitted %s compensation of Rs %s. "
                            + "Per the RBI Integrated Ombudsman Scheme, this award cannot be issued.",
                    amount.toPlainString(), component.toUpperCase(), ceiling.toPlainString()));
        }
    }

    /**
     * Refuses an order on a matter declared to be before a court, unless an override reason is stated.
     *
     * <p>The appellant declares a related court trial at registration and it is stored on APPEALS, but
     * nothing ever read it: an order could be passed on a sub-judice matter with no record that anyone
     * considered the point. There is no Legal Cases module in this product, so the declared flag is the
     * only signal available — this deliberately does NOT pretend to track case numbers or hearings.
     *
     * <p>The override is the {@code ground} text, which is persisted on the order, so a deliberate
     * decision to proceed is auditable rather than silent. <b>Disabled by default</b>
     * ({@code cms.aa.order.block_sub_judice}) because whether the AA may proceed on a sub-judice matter,
     * and who may authorise it, is a question of Scheme law — arming it on a guess could block lawful
     * orders. The mechanism and its tests exist so that switching it on is a config change, not a build.
     */
    private void requireNotSubJudice(Appeal appeal, String ground) {
        if (!systemConfigService.getBoolean(CFG_BLOCK_SUB_JUDICE, false)) {
            return;
        }
        if (!Boolean.TRUE.equals(appeal.getHasRelatedCourtTrial())) {
            return;
        }
        if (ground != null && !ground.isBlank()) {
            log.warn("AA order: proceeding on sub-judice appeal {} with a recorded override: {}",
                    appeal.getAppealNumber(), ground);
            return;
        }
        throw new SubJudiceException(
                "Appeal " + appeal.getAppealNumber() + " is recorded as having a related court trial. "
                        + "State the ground on which the Appellate Authority proceeds, or do not issue "
                        + "an order while the matter is sub-judice.");
    }

    private Appeal requireAppeal(String appealNumber) {
        return appealRepository.findByAppealNumber(appealNumber)
                .orElseThrow(() -> new IllegalArgumentException("Appeal not found: " + appealNumber));
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
