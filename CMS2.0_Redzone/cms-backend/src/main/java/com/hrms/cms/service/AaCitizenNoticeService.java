package com.hrms.cms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hrms.cms.entity.AaCitizenNotice;
import com.hrms.cms.entity.Appeal;
import com.hrms.cms.repository.AaCitizenNoticeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Records notices OWED to parties to an appeal.
 *
 * <p>Every method here writes an obligation, never a delivery. There is no email or SMS gateway in
 * cms-backend, so a row is created PENDING and stays PENDING; no code path in Phase 1 sets SENT. A
 * caller must therefore never report "notice sent" on the strength of a call to this service --
 * "notice recorded" or "notice queued" is the only truthful phrasing.
 *
 * <p>{@code REQUIRES_NEW} on a PUBLIC method of this SEPARATE bean is load-bearing twice over:
 * <ul>
 *   <li>Per-recipient isolation. A try/catch around a loop inside ONE transaction does not isolate
 *       anything -- a constraint violation on recipient 2 marks the whole transaction rollback-only,
 *       so the caller sees success while nothing committed. One recipient with a bad address must not
 *       cost the other parties their notice.
 *   <li>The obligation must survive even if the workflow action that raised it later fails. A notice
 *       we recorded but did not owe is a harmless surplus; an owed notice we never recorded is not.
 * </ul>
 * Self-invocation would silently discard the propagation, so the loop lives in the CALLER
 * ({@link AaWorkflowNotificationService}) and each recipient arrives here as a separate call.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AaCitizenNoticeService {

    private final AaCitizenNoticeRepository noticeRepository;
    private final ObjectMapper objectMapper;

    /**
     * Records one notice obligation, or returns the existing one if this event was already recorded.
     *
     * @param dedupeKey stable per logical event -- the hearing row id, the order revision, etc. A
     *                  retried POST must not double-notify a citizen.
     * @return the persisted obligation, or null when it could not be recorded (never throws into the
     *         caller's transaction: see class javadoc)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AaCitizenNotice record(String appealNumber, String eventCode, String recipientRole,
                                  String recipientName, String email, String phone,
                                  String messageKey, Map<String, String> params,
                                  String dedupeKey, String createdBy) {
        try {
            if (noticeRepository.existsByAppealNumberAndEventCodeAndRecipientRoleAndDedupeKey(
                    appealNumber, eventCode, recipientRole, dedupeKey)) {
                log.debug("AA notice already recorded: {} {} {}", appealNumber, eventCode, recipientRole);
                return null;
            }

            AaCitizenNotice notice = AaCitizenNotice.builder()
                    .appealNumber(appealNumber)
                    .eventCode(eventCode)
                    .recipientRole(recipientRole)
                    .recipientName(trimToNull(recipientName))
                    .recipientEmail(trimToNull(email))
                    .recipientPhone(trimToNull(phone))
                    .channel(preferredChannel(email, phone))
                    .messageKey(messageKey)
                    .messageParams(toJson(params))
                    .status(AaCitizenNotice.STATUS_PENDING)
                    .dedupeKey(dedupeKey)
                    .attemptCount(0)
                    .createdBy(createdBy == null ? "system" : createdBy)
                    .build();

            return noticeRepository.save(notice);
        } catch (RuntimeException e) {
            // Swallowed by design. This bean runs in its own transaction, so only THIS notice is lost;
            // failing the caller would abandon a hearing that was correctly scheduled.
            log.warn("AA notice: could not record {} for {} on {}: {}",
                    eventCode, recipientRole, appealNumber, e.getMessage());
            return null;
        }
    }

    /**
     * Cancels notices for an event that have not gone out yet.
     *
     * <p>Used when a hearing is moved before its notice was ever dispatched: the parties should
     * receive the new date, not both. Scoped to PENDING, so a notice a gateway has already sent can
     * never be retro-cancelled -- that would be rewriting history.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int cancelPendingForEvent(String appealNumber, String eventCode, String reason) {
        try {
            List<AaCitizenNotice> pending = noticeRepository.findPendingForEvent(appealNumber, eventCode);
            for (AaCitizenNotice notice : pending) {
                notice.setStatus(AaCitizenNotice.STATUS_CANCELLED);
                notice.setLastError(reason);
                noticeRepository.save(notice);
            }
            return pending.size();
        } catch (RuntimeException e) {
            log.warn("AA notice: could not cancel pending {} notices on {}: {}",
                    eventCode, appealNumber, e.getMessage());
            return 0;
        }
    }

    @Transactional(readOnly = true)
    public List<AaCitizenNotice> forAppeal(String appealNumber) {
        return noticeRepository.findByAppealNumberOrderByCreatedAtDescIdDesc(appealNumber);
    }

    @Transactional(readOnly = true)
    public List<AaCitizenNotice> dispatchable() {
        return noticeRepository.findDispatchable(LocalDateTime.now());
    }

    /**
     * The appellant's contact details, falling back to the parent complaint's complainant.
     *
     * <p>An appeal filed by staff on a citizen's behalf may carry no contact detail of its own, and a
     * notice owed to an unreachable citizen still has to be recorded -- with channel NONE, so the gap
     * is visible rather than silent.
     */
    public static Map<String, String> appellantContact(Appeal appeal) {
        Map<String, String> contact = new LinkedHashMap<>();
        contact.put("name", appeal.getAppellantName());
        contact.put("email", appeal.getAppellantEmail());
        contact.put("phone", appeal.getAppellantPhone());
        return contact;
    }

    static String preferredChannel(String email, String phone) {
        if (trimToNull(email) != null) return AaCitizenNotice.CHANNEL_EMAIL;
        if (trimToNull(phone) != null) return AaCitizenNotice.CHANNEL_SMS;
        return AaCitizenNotice.CHANNEL_NONE;
    }

    private String toJson(Map<String, String> params) {
        if (params == null || params.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            log.warn("AA notice: could not serialise notice params: {}", e.getMessage());
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
