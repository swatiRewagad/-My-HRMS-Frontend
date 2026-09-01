package com.rbi.cms.mailintake.admin;

import com.rbi.cms.mailintake.entity.AdminAction;
import com.rbi.cms.mailintake.entity.AdminActionStatus;
import com.rbi.cms.mailintake.entity.AdminActionType;
import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailAttachment;
import com.rbi.cms.mailintake.entity.InboundEmailEvent;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import com.rbi.cms.mailintake.repository.AdminActionRepository;
import com.rbi.cms.mailintake.repository.InboundEmailAttachmentRepository;
import com.rbi.cms.mailintake.repository.InboundEmailEventRepository;
import com.rbi.cms.mailintake.repository.InboundEmailRepository;
import com.rbi.cms.mailintake.smtp.RawMessageStore;
import com.rbi.cms.mailintake.state.InboundEmailStateMachine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Everything behind {@code /admin/mail-intake/**}. Replay and force-link are never applied
 * directly — they're always a {@link AdminAction} request (maker) that a *different* operator
 * must {@link #decide} on (checker) before anything actually changes on the {@link InboundEmail}.
 * See the class Javadoc on {@link InboundEmailStateMachine} for why every resulting mutation still
 * goes through that class rather than this service touching status/audit rows directly.
 */
@Service
@RequiredArgsConstructor
public class AdminMailIntakeService {

    private final InboundEmailRepository emailRepository;
    private final InboundEmailEventRepository eventRepository;
    private final InboundEmailAttachmentRepository attachmentRepository;
    private final AdminActionRepository actionRepository;
    private final RawMessageStore rawMessageStore;
    private final InboundEmailStateMachine stateMachine;

    public Page<InboundEmail> listQuarantined(Pageable pageable) {
        return emailRepository.findByStatusOrderByReceivedAtDesc(InboundEmailStatus.QUARANTINED, pageable);
    }

    public InboundEmail getEmailOrThrow(Long emailId) {
        return emailRepository.findById(emailId)
                .orElseThrow(() -> new NoSuchElementException("inbound_email " + emailId + " not found"));
    }

    public List<InboundEmailAttachment> getAttachments(Long emailId) {
        return attachmentRepository.findByEmailId(emailId);
    }

    public List<InboundEmailEvent> getTimeline(Long emailId) {
        return eventRepository.findByEmailIdOrderByEventAtAsc(emailId);
    }

    /** Throws if the raw bytes have already been purged by the retention job — callers must
     *  translate that into a 410 Gone, not a 404 or a silent empty body. */
    @Transactional
    public byte[] downloadRaw(Long emailId, String actor) {
        InboundEmail email = getEmailOrThrow(emailId);
        if (email.getRawPurgedAt() != null) {
            throw new RawBytesPurgedException(emailId, email.getRawPurgedAt());
        }
        byte[] bytes;
        try {
            bytes = rawMessageStore.read(email.getRawStoreUri());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read raw bytes for inbound_email " + emailId, e);
        }
        stateMachine.recordAuditEvent(email, actor, "Raw .eml downloaded by admin");
        return bytes;
    }

    public List<AdminAction> listPendingActions(Pageable pageable) {
        return actionRepository.findByStatusOrderByRequestedAtAsc(AdminActionStatus.PENDING, pageable).getContent();
    }

    public List<AdminAction> getActionHistory(Long emailId) {
        return actionRepository.findByEmailIdOrderByRequestedAtDesc(emailId);
    }

    @Transactional
    public AdminAction requestReplay(Long emailId, String requestedBy, String reason) {
        InboundEmail email = getEmailOrThrow(emailId);
        AdminAction action = actionRepository.save(AdminAction.builder()
                .emailId(emailId)
                .actionType(AdminActionType.REPLAY)
                .requestedBy(requestedBy)
                .requestReason(reason)
                .build());
        stateMachine.recordAuditEvent(email, requestedBy,
                "REPLAY requested (admin action #" + action.getId() + ", pending approval): " + reason);
        return action;
    }

    @Transactional
    public AdminAction requestForceLink(Long emailId, String requestedBy, String reason, String targetComplaintId) {
        InboundEmail email = getEmailOrThrow(emailId);
        AdminAction action = actionRepository.save(AdminAction.builder()
                .emailId(emailId)
                .actionType(AdminActionType.FORCE_LINK)
                .targetComplaintId(targetComplaintId)
                .requestedBy(requestedBy)
                .requestReason(reason)
                .build());
        stateMachine.recordAuditEvent(email, requestedBy,
                "FORCE_LINK to " + targetComplaintId + " requested (admin action #" + action.getId()
                        + ", pending approval): " + reason);
        return action;
    }

    /** Maker-checker: the person deciding must not be the person who requested it. Approve
     *  applies the underlying REPLAY/FORCE_LINK; reject just records the decision. */
    @Transactional
    public AdminAction decide(Long actionId, String decidedBy, boolean approve, String note) {
        AdminAction action = actionRepository.findById(actionId)
                .orElseThrow(() -> new NoSuchElementException("admin action " + actionId + " not found"));
        if (action.getStatus() != AdminActionStatus.PENDING) {
            throw new IllegalStateException("admin action " + actionId + " has already been decided");
        }
        if (decidedBy.equalsIgnoreCase(action.getRequestedBy())) {
            throw new IllegalStateException("maker-checker: " + decidedBy
                    + " requested this action and cannot also decide it");
        }

        InboundEmail email = getEmailOrThrow(action.getEmailId());
        action.setDecidedBy(decidedBy);
        action.setDecidedAt(Instant.now());
        action.setDecisionNote(note);

        if (!approve) {
            action.setStatus(AdminActionStatus.REJECTED);
            actionRepository.save(action);
            stateMachine.recordAuditEvent(email, decidedBy,
                    action.getActionType() + " request #" + actionId + " rejected: " + note);
            return action;
        }

        action.setStatus(AdminActionStatus.APPROVED);
        actionRepository.save(action);

        switch (action.getActionType()) {
            case REPLAY -> stateMachine.replay(email,
                    "admin:" + decidedBy + " (approved request #" + actionId + " from " + action.getRequestedBy() + ")");
            case FORCE_LINK -> {
                email.setLinkedComplaintId(action.getTargetComplaintId());
                email.setComplaintRef(action.getTargetComplaintId());
                emailRepository.save(email);
                stateMachine.recordAuditEvent(email, decidedBy,
                        "Force-linked to complaint " + action.getTargetComplaintId()
                                + " (approved request #" + actionId + " from " + action.getRequestedBy() + ")");
            }
        }
        return action;
    }

    public static class RawBytesPurgedException extends RuntimeException {
        public RawBytesPurgedException(Long emailId, Instant purgedAt) {
            super("Raw bytes for inbound_email " + emailId + " were purged at " + purgedAt
                    + " per retention policy");
        }
    }
}
