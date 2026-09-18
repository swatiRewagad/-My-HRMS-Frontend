package com.hrms.cms.controller;

import com.hrms.cms.entity.AaCitizenNotice;
import com.hrms.cms.entity.AppealHearing;
import com.hrms.cms.entity.AppealOrder;
import com.hrms.cms.security.AaIdentityResolver;
import com.hrms.cms.security.AaRoleGuard;
import com.hrms.cms.service.AaAppealOrderService;
import com.hrms.cms.service.AaCitizenNoticeService;
import com.hrms.cms.service.AaHearingService;
import com.hrms.cms.service.AaNotifyEvent;
import com.hrms.cms.service.AaWorkflowNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hearing and order endpoints for the AA module.
 *
 * <p>Separate from AppealController because that file belongs to another session; these are additive
 * paths under the same {@code /api/v1/appeals} prefix and collide with nothing there.
 *
 * <p>Every write resolves the actor and role SERVER-SIDE via {@link AaIdentityResolver}. The body is
 * never trusted for identity -- that was the exact defect that let any caller invoke any AA action
 * under any name they typed.
 */
@RestController
@RequestMapping("/api/v1/appeals")
@RequiredArgsConstructor
@Slf4j
public class AaHearingOrderController {

    private final AaHearingService hearingService;
    private final AaAppealOrderService orderService;
    private final AaCitizenNoticeService noticeService;
    private final AaWorkflowNotificationService notifier;
    private final AaIdentityResolver identityResolver;

    // ---------------------------------------------------------------- hearings

    @GetMapping("/{appealNumber}/hearings")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> hearingHistory(@PathVariable String appealNumber) {
        List<AppealHearing> history = hearingService.history(appealNumber);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("appealNumber", appealNumber);
        body.put("hearingHistory", history.stream().map(AaHearingOrderController::toHearingMap).toList());
        body.put("operative", hearingService.operative(appealNumber)
                .map(AaHearingOrderController::toHearingMap).orElse(null));
        return ResponseEntity.ok(body);
    }

    /**
     * Schedules or reschedules a hearing and records the notices owed to the selected parties.
     *
     * <p>Only AA_REVIEWER / AA_SECRETARIAT / AA_ADMIN may list a hearing, matching the existing
     * SCHEDULE_HEARING matrix.
     */
    @PostMapping("/{appealNumber}/hearings")
    @AaRoleGuard(roles = {"AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> scheduleHearing(@PathVariable String appealNumber,
                                                               @RequestBody Map<String, Object> request) {
        String actor = identityResolver.resolveActor();
        String role = identityResolver.resolveAaRole();

        try {
            boolean wasListed = hearingService.operative(appealNumber).isPresent();

            AppealHearing hearing = hearingService.schedule(appealNumber,
                    asString(request.get("hearingDate")),
                    asString(request.get("hearingVenue")),
                    asString(request.get("hearingMode")),
                    asString(request.get("reason")),
                    actor, role);

            AaNotifyEvent event = wasListed
                    ? AaNotifyEvent.HEARING_RESCHEDULED
                    : AaNotifyEvent.HEARING_SCHEDULED;

            // A hearing moved before its first notice ever went out should not leave the parties with
            // two conflicting dates queued.
            if (wasListed) {
                noticeService.cancelPendingForEvent(appealNumber,
                        AaNotifyEvent.HEARING_SCHEDULED.name(), "Superseded by a rescheduled hearing");
            }

            Map<String, String> params = hearingService.noticeParams(hearing);
            params.put("actor", actor == null ? "system" : actor);

            int noticesRecorded = notifier.recordPartyNotices(appealNumber, event,
                    partiesToNotify(request.get("partiesToNotify")), params, actor);

            if (hearing.getPresidingOfficer() != null) {
                notifier.notifyOfficer(hearing.getPresidingOfficer(), appealNumber, event);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("hearing", toHearingMap(hearing));
            body.put("noticesRecorded", noticesRecorded);
            // Deliberately "recorded", never "sent": there is no gateway, so claiming delivery here
            // would be a lie the UI would repeat to the user.
            body.put("noticeStatus", AaCitizenNotice.STATUS_PENDING);
            body.put("messageKey", wasListed
                    ? "aa.hearing.rescheduled_notices_recorded"
                    : "aa.hearing.scheduled_notices_recorded");
            body.put("shortNotice", hearingService.isShortNotice(hearing.getHearingDate()));
            return ResponseEntity.ok(body);

        } catch (AaHearingService.HearingConflictException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", false);
            body.put("messageKey", "aa.hearing.error_officer_double_booked");
            body.put("message", e.getMessage());
            body.put("conflictingAppeal", e.getConflictingAppeal());
            body.put("conflictingAt", String.valueOf(e.getConflictingAt()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("aa.hearing.error_invalid_request", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.hearing.error_invalid_state", e.getMessage()));
        }
    }

    @PostMapping("/{appealNumber}/hearings/adjourn")
    @AaRoleGuard(roles = {"AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> adjourn(@PathVariable String appealNumber,
                                                       @RequestBody Map<String, Object> request) {
        String actor = identityResolver.resolveActor();
        String role = identityResolver.resolveAaRole();
        try {
            AppealHearing hearing = hearingService.adjourn(appealNumber,
                    asString(request.get("reason")), actor, role);

            Map<String, String> params = hearingService.noticeParams(hearing);
            params.put("actor", actor == null ? "system" : actor);
            int noticesRecorded = notifier.recordPartyNotices(appealNumber, AaNotifyEvent.HEARING_ADJOURNED,
                    partiesToNotify(request.get("partiesToNotify")), params, actor);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("hearing", toHearingMap(hearing));
            body.put("noticesRecorded", noticesRecorded);
            body.put("noticeStatus", AaCitizenNotice.STATUS_PENDING);
            body.put("messageKey", "aa.hearing.adjourned");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("aa.hearing.error_invalid_request", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.hearing.error_no_scheduled_hearing", e.getMessage()));
        }
    }

    @PostMapping("/{appealNumber}/hearings/outcome")
    @AaRoleGuard(roles = {"AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> recordOutcome(@PathVariable String appealNumber,
                                                             @RequestBody Map<String, Object> request) {
        String actor = identityResolver.resolveActor();
        String role = identityResolver.resolveAaRole();
        try {
            AppealHearing hearing = hearingService.recordOutcome(appealNumber,
                    asString(request.get("outcome")), asString(request.get("remarks")), actor, role);

            if (hearing.getPresidingOfficer() != null) {
                notifier.notifyOfficer(hearing.getPresidingOfficer(), appealNumber,
                        AaNotifyEvent.HEARING_OUTCOME_RECORDED);
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("hearing", toHearingMap(hearing));
            body.put("messageKey", "aa.hearing.outcome_recorded");
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("aa.hearing.error_invalid_request", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.hearing.error_no_scheduled_hearing", e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ orders

    @GetMapping("/{appealNumber}/order")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> order(@PathVariable String appealNumber) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("appealNumber", appealNumber);
        body.put("order", orderService.operative(appealNumber)
                .map(AaHearingOrderController::toOrderMap).orElse(null));
        body.put("revisions", orderService.revisions(appealNumber).stream()
                .map(AaHearingOrderController::toOrderMap).toList());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/{appealNumber}/order")
    @AaRoleGuard(roles = {"AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> issueOrder(@PathVariable String appealNumber,
                                                          @RequestBody Map<String, Object> request) {
        String actor = identityResolver.resolveActor();
        String role = identityResolver.resolveAaRole();
        try {
            AppealOrder order = orderService.issue(appealNumber,
                    asString(request.get("outcome")),
                    asString(request.get("orderSummary")),
                    asAmount(request.get("awardAmount")),
                    asString(request.get("clauseCode")),
                    asString(request.get("ground")),
                    actor, role);

            Map<String, String> params = orderService.noticeParams(order);
            params.put("actor", actor == null ? "system" : actor);
            notifier.notifyAppellant(appealNumber, AaNotifyEvent.ORDER_PASSED, params);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("order", toOrderMap(order));
            body.put("noticeStatus", AaCitizenNotice.STATUS_PENDING);
            body.put("messageKey", "aa.order.issued");
            return ResponseEntity.ok(body);
        } catch (AaAppealOrderService.OrderAlreadyIssuedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.order.error_already_issued", e.getMessage()));
        } catch (AaAppealOrderService.EdApprovalRequiredException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.order.error_ed_approval_required", e.getMessage()));
        } catch (AaAppealOrderService.AwardCapExceededException e) {
            // 422, not 400: the request is well-formed but the award is not permissible. A distinct
            // status and key let the screen tell the operator the ceiling was the reason.
            return ResponseEntity.unprocessableEntity()
                    .body(error("aa.order.error_award_cap_exceeded", e.getMessage()));
        } catch (AaAppealOrderService.SubJudiceException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.order.error_sub_judice", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("aa.order.error_invalid_request", e.getMessage()));
        }
    }

    @PostMapping("/{appealNumber}/order/corrections")
    @AaRoleGuard(roles = {"AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> correctOrder(@PathVariable String appealNumber,
                                                            @RequestBody Map<String, Object> request) {
        String actor = identityResolver.resolveActor();
        String role = identityResolver.resolveAaRole();
        try {
            AppealOrder correction = orderService.correct(appealNumber,
                    asString(request.get("outcome")),
                    asString(request.get("orderSummary")),
                    asAmount(request.get("awardAmount")),
                    asString(request.get("clauseCode")),
                    asString(request.get("ground")),
                    asString(request.get("correctionReason")),
                    actor, role);

            Map<String, String> params = orderService.noticeParams(correction);
            params.put("actor", actor == null ? "system" : actor);
            notifier.notifyAppellant(appealNumber, AaNotifyEvent.ORDER_CORRECTED, params);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("order", toOrderMap(correction));
            body.put("noticeStatus", AaCitizenNotice.STATUS_PENDING);
            body.put("messageKey", "aa.order.corrected");
            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(error("aa.order.error_no_order_to_correct", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(error("aa.order.error_invalid_request", e.getMessage()));
        }
    }

    // ----------------------------------------------------------------- notices

    /**
     * The notices owed on an appeal.
     *
     * <p>Contact details are MASKED here. The row holds them in full because a dispatcher will need
     * them, but an operator reading an audit screen does not, and appeals carry citizen PII.
     */
    @GetMapping("/{appealNumber}/notices")
    @AaRoleGuard(roles = {"AA_DO", "AA_REVIEWER", "AA_SECRETARIAT", "AA_ADMIN"})
    public ResponseEntity<Map<String, Object>> notices(@PathVariable String appealNumber) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AaCitizenNotice notice : noticeService.forAppeal(appealNumber)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", notice.getId());
            row.put("eventCode", notice.getEventCode());
            row.put("recipientRole", notice.getRecipientRole());
            row.put("recipientName", notice.getRecipientName());
            row.put("recipientEmail", mask(notice.getRecipientEmail()));
            row.put("recipientPhone", mask(notice.getRecipientPhone()));
            row.put("channel", notice.getChannel());
            row.put("messageKey", notice.getMessageKey());
            row.put("status", notice.getStatus());
            row.put("attemptCount", notice.getAttemptCount());
            row.put("createdAt", String.valueOf(notice.getCreatedAt()));
            row.put("dispatchedAt", notice.getDispatchedAt() == null ? null
                    : String.valueOf(notice.getDispatchedAt()));
            rows.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("appealNumber", appealNumber);
        body.put("notices", rows);
        // Stated explicitly so no client can honestly render "sent".
        body.put("gatewayAvailable", false);
        return ResponseEntity.ok(body);
    }

    // ------------------------------------------------------------------ mapping

    private static Map<String, Object> toHearingMap(AppealHearing hearing) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", hearing.getId());
        map.put("sequenceNo", hearing.getSequenceNo());
        map.put("eventType", hearing.getEventType());
        // 'date', 'venue' and 'outcome' are the field names the existing hearing table template binds.
        map.put("date", String.valueOf(hearing.getHearingDate()));
        map.put("venue", hearing.getHearingVenue());
        map.put("mode", hearing.getHearingMode());
        map.put("outcome", hearing.getOutcome());
        map.put("outcomeRemarks", hearing.getOutcomeRemarks());
        map.put("reason", hearing.getReason());
        map.put("presidingOfficer", hearing.getPresidingOfficer());
        map.put("performedBy", hearing.getPerformedBy());
        map.put("performedByRole", hearing.getPerformedByRole());
        map.put("performedAt", String.valueOf(hearing.getPerformedAt()));
        map.put("superseded", hearing.getSupersededAt() != null);
        map.put("supersededById", hearing.getSupersededById());
        return map;
    }

    private static Map<String, Object> toOrderMap(AppealOrder order) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", order.getId());
        map.put("revisionNo", order.getRevisionNo());
        map.put("supersedesOrderId", order.getSupersedesOrderId());
        map.put("correctionReason", order.getCorrectionReason());
        map.put("outcome", order.getOutcome());
        map.put("orderSummary", order.getOrderSummary());
        map.put("awardAmount", order.getAwardAmount());
        map.put("clauseCode", order.getClauseCode());
        map.put("ground", order.getGround());
        map.put("issuingAuthority", order.getIssuingAuthority());
        map.put("issuingAuthorityRole", order.getIssuingAuthorityRole());
        map.put("orderDate", String.valueOf(order.getOrderDate()));
        map.put("edApprovalGiven", order.getEdApprovalGiven());
        map.put("operative", order.getSupersededAt() == null);
        map.put("supersededById", order.getSupersededById());
        // No PDF in Phase 1: stated in the payload so a client never renders a download it cannot get.
        map.put("artefactAvailable", false);
        return map;
    }

    @SuppressWarnings("unchecked")
    private static List<String> partiesToNotify(Object raw) {
        if (raw instanceof List<?> list) {
            List<String> parties = new ArrayList<>();
            for (Object item : list) {
                if (item != null) parties.add(String.valueOf(item));
            }
            return parties;
        }
        if (raw instanceof String single && !single.isBlank()) {
            return List.of(single.split("\\s*,\\s*"));
        }
        // Defaulting to the appellant rather than to nobody: the party with a statutory interest in
        // being told must not be dropped because a client omitted the field.
        return List.of("appellant");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static BigDecimal asAmount(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) return null;
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Award amount '" + text + "' is not a valid number");
        }
    }

    /** Keeps enough to recognise the address, not enough to use it. */
    static String mask(String value) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.contains("@")) {
            int at = trimmed.indexOf('@');
            String local = trimmed.substring(0, at);
            String domain = trimmed.substring(at);
            String head = local.length() <= 2 ? local.substring(0, 1) : local.substring(0, 2);
            return head + "***" + domain;
        }
        return trimmed.length() <= 4 ? "***"
                : "******" + trimmed.substring(trimmed.length() - 4);
    }

    private static Map<String, Object> error(String messageKey, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", false);
        body.put("messageKey", messageKey);
        body.put("message", message);
        return body;
    }
}
