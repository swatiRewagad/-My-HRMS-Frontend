package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.ComplaintAttachment;
import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.exception.ClosureCommunicationIncompleteException;
import com.hrms.cms.repository.ComplaintAttachmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Refuses a closure until its statutory communication requirements are met (UST507-509, 520, 549, 764).
 *
 * <p>WHY THIS EXISTS. The closure dialog blocked correctly in the browser and the server enforced nothing.
 * {@code dateOfSending} was posted and dropped — it appeared nowhere in cms-backend — and the "signed letter"
 * the officer attached lived in an Angular {@code signal<File>} that was never uploaded. So a complaint could
 * be closed through the API with no letter, no send date and no email to the complainant, and the UI would
 * still show a tidy closure. These are the conditions that decide whether a citizen was actually told their
 * case ended, so they belong on the server.
 *
 * <p>ARMED BY CONFIGURATION, DEFAULTING TO OFF. Making these mandatory changes citizen-facing legal
 * behaviour on a database where 1646 of 1647 closed complaints predate the requirement, so switching it on
 * by default would either break every existing closure flow or silently grandfather the old ones. Following
 * the precedent set by {@code cms.aa.order.max_award_amount} (0 = unenforced) and
 * {@code cms.aa.order.block_sub_judice} (false = unenforced), each requirement arms with a single
 * SYSTEM_CONFIG row after sign-off. The migration is behaviour-neutral and cannot break go-live, and the
 * enforcement is fully built and tested rather than deferred.
 *
 * <p>{@link #isClosure} reads the transition row's own metadata rather than a hardcoded action list, so a new
 * closing action added as a table row is covered automatically — the same approach as
 * {@code UploadLinkGuardService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClosureCommunicationGuardService {

    static final String CFG_REQUIRE_SEND_DATE = "cms.rbio.closure.require_send_date";
    static final String CFG_REQUIRE_SIGNED_LETTER = "cms.rbio.closure.require_signed_letter";
    static final String CFG_REQUIRE_COMPLAINANT_EMAIL = "cms.rbio.closure.require_complainant_email";

    /** The {@code documentType} a closure letter upload must carry to satisfy the gate. */
    public static final String DOCUMENT_TYPE_SIGNED_LETTER = "SIGNED_LETTER";

    private final ComplaintAttachmentRepository attachmentRepository;
    private final SystemConfigService systemConfigService;

    /**
     * Refuses the closure when a required communication precondition is unmet.
     *
     * <p>Called before any mutation, so a refusal leaves the complaint exactly as it was. Order matters: the
     * email check comes first because a missing email address cannot be fixed inside the closure dialog at
     * all — UST764 requires routing back via Facilitation/Rejection or Decision instead — whereas a send date
     * or a letter can be supplied on the spot.
     *
     * @param sendDate the officer-supplied Date of Sending, previously dropped by the server
     */
    @Transactional(readOnly = true)
    public void assertClosureCommunicationComplete(Complaint complaint,
                                                   RbioWorkflowTransition transition,
                                                   String sendDate) {
        if (complaint == null || !isClosure(transition)) {
            return;
        }
        String complaintNumber = complaint.getComplaintNumber();

        if (systemConfigService.getBoolean(CFG_REQUIRE_COMPLAINANT_EMAIL, false)
                && isBlank(complaint.getComplainantEmail())) {
            throw new ClosureCommunicationIncompleteException(
                    "rbio.closure.error_email_missing",
                    "This complaint cannot be closed directly because the complainant has no email address "
                            + "on record, so the closure letter cannot be dispatched. Record the outcome "
                            + "through Facilitation/Rejection or Decision instead.",
                    complaintNumber,
                    ClosureCommunicationIncompleteException.MISSING_EMAIL);
        }

        if (systemConfigService.getBoolean(CFG_REQUIRE_SEND_DATE, false) && isBlank(sendDate)) {
            throw new ClosureCommunicationIncompleteException(
                    "rbio.closure.error_send_date_required",
                    "This complaint cannot be closed until the Date of Sending of the closure letter is "
                            + "recorded, because that date is what evidences when the complainant was informed.",
                    complaintNumber,
                    ClosureCommunicationIncompleteException.MISSING_SEND_DATE);
        }

        if (systemConfigService.getBoolean(CFG_REQUIRE_SIGNED_LETTER, false)
                && !hasSignedLetter(complaint)) {
            throw new ClosureCommunicationIncompleteException(
                    "rbio.closure.error_signed_letter_required",
                    "This complaint cannot be closed until the signed closure letter has been uploaded "
                            + "against it.",
                    complaintNumber,
                    ClosureCommunicationIncompleteException.MISSING_SIGNED_LETTER);
        }
    }

    /**
     * Whether a signed closure letter is attached to the complaint.
     *
     * <p>Checks {@code documentType} rather than filename: the type is set by the uploading endpoint and a
     * filename is chosen by whoever saved the file, so matching on the name would let any PDF satisfy a
     * statutory requirement.
     */
    @Transactional(readOnly = true)
    public boolean hasSignedLetter(Complaint complaint) {
        if (complaint == null || complaint.getId() == null) {
            return false;
        }
        List<ComplaintAttachment> attachments = attachmentRepository.findByComplaintId(complaint.getId());
        return attachments.stream()
                .anyMatch(a -> DOCUMENT_TYPE_SIGNED_LETTER.equalsIgnoreCase(a.getDocumentType()));
    }

    /**
     * Whether the transition closes the complaint.
     *
     * <p>Read from the row — {@code CLOSURE_CAUSE} is set on exactly the closing actions and
     * {@code IS_TERMINAL} on the final ones — so a closing action added as a table row is covered without
     * touching this class.
     */
    private boolean isClosure(RbioWorkflowTransition transition) {
        if (transition == null) {
            return false;
        }
        return transition.getClosureCause() != null || transition.terminal();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
