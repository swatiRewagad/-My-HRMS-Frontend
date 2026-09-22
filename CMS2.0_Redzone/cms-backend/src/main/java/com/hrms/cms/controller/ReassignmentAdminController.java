package com.hrms.cms.controller;

import com.hrms.cms.entity.NotificationDeliveryLog;
import com.hrms.cms.entity.ReassignmentHistory;
import com.hrms.cms.repository.NotificationDeliveryLogRepository;
import com.hrms.cms.security.RequestIdentity;
import com.hrms.cms.security.RequestIdentityResolver;
import com.hrms.cms.service.ReassignmentReportService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * Reassignment history report (UST844) and the notification delivery log (UST845).
 *
 * <p>Scoping is identical to the write paths — {@link ReassignmentReportService} delegates to
 * {@code ReassignmentService.resolveScope}, so an RE caller reads only their own entity's history
 * and an ADMIN must name an entity explicitly. A report is as much an information-disclosure surface
 * as a detail page, so it does not get a laxer rule.
 */
@RestController
@RequestMapping("/api/v1/re-portal/reassignment-report")
@RequiredArgsConstructor
@Slf4j
public class ReassignmentAdminController {

    private final ReassignmentReportService reportService;
    private final NotificationDeliveryLogRepository deliveryLogRepository;
    private final RequestIdentityResolver identityResolver;

    @GetMapping
    public ResponseEntity<Map<String, Object>> history(
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String fromUserId,
            @RequestParam(required = false) String toUserId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "0") int size,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            Page<ReassignmentHistory> results = reportService.search(
                    identity, entityCode, fromUserId, toUserId,
                    parseDateTime(from), parseDateTime(to), page, size);

            List<Map<String, Object>> items = new ArrayList<>();
            for (ReassignmentHistory h : results.getContent()) {
                items.add(render(h));
            }
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("history", items);
            response.put("totalElements", results.getTotalElements());
            response.put("totalPages", results.getTotalPages());
            response.put("page", results.getNumber());
            response.put("size", results.getSize());
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary(
            @RequestParam(required = false) String entityCode,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        try {
            Map<String, Object> summary = reportService.summary(
                    identity, entityCode, parseDateTime(from), parseDateTime(to));
            Map<String, Object> response = new LinkedHashMap<>(summary);
            response.put("success", true);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            return forbidden(e);
        } catch (IllegalArgumentException e) {
            return badRequest(e);
        }
    }

    /**
     * Notification delivery attempts (UST845). ADMIN only: the log spans recipients across entities
     * and is a diagnostic surface, not entity-facing data.
     */
    @GetMapping("/delivery-log")
    public ResponseEntity<Map<String, Object>> deliveryLog(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String complaintNumber,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpServletRequest request) {

        RequestIdentity identity = identityResolver.resolve(request);
        if (identity == null) {
            return unauthenticated();
        }
        if (!identity.hasAnyRole("ADMIN", "RE_ADMIN")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("success", false, "messageKey", "re.reassign.error.not_permitted"));
        }

        List<Map<String, Object>> items = new ArrayList<>();
        long total;
        if (complaintNumber != null && !complaintNumber.isBlank()) {
            List<NotificationDeliveryLog> rows = deliveryLogRepository
                    .findByRelatedComplaintNumberOrderByAttemptedAtDesc(complaintNumber.trim());
            rows.forEach(r -> items.add(render(r)));
            total = rows.size();
        } else if (status != null && !status.isBlank()) {
            Page<NotificationDeliveryLog> rows = deliveryLogRepository.findByStatusOrderByAttemptedAtDesc(
                    status.trim().toUpperCase(), PageRequest.of(Math.max(page, 0), clamp(size)));
            rows.getContent().forEach(r -> items.add(render(r)));
            total = rows.getTotalElements();
        } else {
            Page<NotificationDeliveryLog> rows = deliveryLogRepository.findAll(
                    PageRequest.of(Math.max(page, 0), clamp(size)));
            rows.getContent().forEach(r -> items.add(render(r)));
            total = rows.getTotalElements();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("deliveries", items);
        response.put("totalElements", total);
        response.put("sentCount", deliveryLogRepository.countByStatus(NotificationDeliveryLog.STATUS_SENT));
        response.put("failedCount", deliveryLogRepository.countByStatus(NotificationDeliveryLog.STATUS_FAILED));
        return ResponseEntity.ok(response);
    }

    private int clamp(int size) {
        return size <= 0 ? 20 : Math.min(size, 200);
    }

    private Map<String, Object> render(ReassignmentHistory h) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", h.getId());
        item.put("recordId", h.getNodalOfficerRecordId());
        item.put("complaintNumber", h.getComplaintNumber());
        item.put("entityCode", h.getEntityCode());
        item.put("fromUserId", h.getFromUserId());
        item.put("fromUserName", h.getFromUserName());
        item.put("toUserId", h.getToUserId());
        item.put("toUserName", h.getToUserName());
        item.put("triggerType", h.getTriggerType());
        item.put("requestId", h.getReassignmentRequestId());
        item.put("reason", h.getReason());
        item.put("performedBy", h.getPerformedBy());
        item.put("performedByName", h.getPerformedByName());
        item.put("reassignedAt", h.getReassignedAt() == null ? null : h.getReassignedAt().toString());
        return item;
    }

    private Map<String, Object> render(NotificationDeliveryLog d) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", d.getId());
        item.put("notificationId", d.getNotificationId());
        item.put("recipientUserId", d.getRecipientUserId());
        item.put("notificationType", d.getNotificationType());
        item.put("channel", d.getChannel());
        item.put("status", d.getStatus());
        item.put("errorMessage", d.getErrorMessage());
        item.put("relatedComplaintNumber", d.getRelatedComplaintNumber());
        item.put("attemptedAt", d.getAttemptedAt() == null ? null : d.getAttemptedAt().toString());
        return item;
    }

    /** Accepts a date or a full timestamp; a date alone is taken as the start of that day. */
    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        try {
            if (trimmed.length() == 10) {
                return java.time.LocalDate.parse(trimmed).atStartOfDay();
            }
            return LocalDateTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("re.reassign.error.invalid_date");
        }
    }

    private ResponseEntity<Map<String, Object>> unauthenticated() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("success", false, "messageKey", "re.reassign.error.unauthenticated"));
    }

    private ResponseEntity<Map<String, Object>> forbidden(Exception e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "messageKey", keyOf(e, "re.reassign.error.not_permitted")));
    }

    private ResponseEntity<Map<String, Object>> badRequest(Exception e) {
        return ResponseEntity.badRequest()
                .body(Map.of("success", false, "messageKey", keyOf(e, "re.reassign.error.invalid")));
    }

    private String keyOf(Exception e, String fallback) {
        String message = e.getMessage();
        if (message != null && message.startsWith("re.reassign.")) {
            return message;
        }
        return fallback;
    }
}
