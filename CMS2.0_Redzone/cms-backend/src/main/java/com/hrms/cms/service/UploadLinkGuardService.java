package com.hrms.cms.service;

import com.hrms.cms.entity.RbioWorkflowTransition;
import com.hrms.cms.entity.UploadLink;
import com.hrms.cms.exception.UploadLinkActiveException;
import com.hrms.cms.repository.UploadLinkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Server-side enforcement of the upload-link restrictions (UST603-604).
 *
 * <p>THE CONTROL THIS REPLACES WAS NOT A CONTROL. Blocking existed only in the Angular component,
 * which computed {@code isClosureBlocked()} and rendered a padlock — while leaving the click handler
 * enabled — and cms-backend contained no reference to UploadLink outside its own controller and the
 * expiry job. So a complaint could be closed by API while the complainant was still uploading the very
 * documents they had been asked to provide, and the evidence would arrive against a closed case.
 *
 * <p>UST604 permits forwarding to every role EXCEPT the Deputy Ombudsman and the Ombudsman while a
 * link is live. The reasoning is that routine handling should continue, but the case must not reach a
 * deciding authority while evidence is still outstanding — a decision taken on an incomplete record is
 * the harm being prevented.
 *
 * <p>The restricted-destination list is CONFIG, not a literal, so an operator can correct it without a
 * redeploy; the default reproduces the roles the frontend already exempted.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UploadLinkGuardService {

    /** Roles a complaint may not be forwarded to while a link is live (UST604). */
    public static final String KEY_RESTRICTED_FORWARD_ROLES =
            "notification.upload_link.restricted_forward_roles";

    static final Set<String> DEFAULT_RESTRICTED_ROLES = Set.of(
            "RBIO_DEPUTY_OMBUDSMAN", "RBIO_OMBUDSMAN", "DEPUTY_OMBUDSMAN", "OMBUDSMAN");

    private final UploadLinkRepository uploadLinkRepository;
    private final SystemConfigService systemConfigService;

    /**
     * Refuses the action if a live upload link forbids it.
     *
     * <p>Called from the transition path AFTER the transition is resolved but BEFORE any mutation, so a
     * refusal leaves the complaint exactly as it was. Ordering matters: refusing after a partial
     * mutation would leave the complaint in a state no transition produced.
     *
     * @param transition the resolved transition — its own metadata says whether this action closes the
     *                   complaint or forwards it, so the check needs no hardcoded action-name list
     * @param targetRole the role a REASSIGN names explicitly, which overrides the transition's own
     *                   destination
     */
    public void assertActionAllowed(String complaintNumber, RbioWorkflowTransition transition,
                                    String targetRole) {
        Optional<UploadLink> liveLink = activeLink(complaintNumber);
        if (liveLink.isEmpty()) {
            return;
        }
        UploadLink link = liveLink.get();

        if (isClosure(transition)) {
            throw new UploadLinkActiveException(
                    "workflow.error_upload_link_active_closure",
                    "This complaint cannot be closed while a secure document-upload link is active. "
                            + "The link expires on " + link.getExpiresAt()
                            + ". Revoke it first if the documents are no longer required.",
                    complaintNumber,
                    String.valueOf(link.getExpiresAt()));
        }

        String destination = destinationRole(transition, targetRole);
        if (destination != null && restrictedRoles().contains(destination.toUpperCase())) {
            throw new UploadLinkActiveException(
                    "workflow.error_upload_link_active_forward",
                    "This complaint cannot be forwarded to " + destination
                            + " while a secure document-upload link is active, because the case would "
                            + "reach a deciding authority with evidence still outstanding. The link "
                            + "expires on " + link.getExpiresAt() + ".",
                    complaintNumber,
                    String.valueOf(link.getExpiresAt()));
        }
    }

    /**
     * Whether a complaint has a link that is both active and not yet expired.
     *
     * <p>The expiry check is in code because {@code findByComplaintNumberAndActiveTrue} does not filter
     * on it — the daily expiry job flips {@code active} only once a day, so between lapse and sweep a
     * row is still {@code active = true} while being expired. Trusting the flag alone would block
     * closure for up to a day after the link genuinely died.
     */
    public Optional<UploadLink> activeLink(String complaintNumber) {
        if (complaintNumber == null || complaintNumber.isBlank()) {
            return Optional.empty();
        }
        return uploadLinkRepository.findByComplaintNumberAndActiveTrue(complaintNumber)
                .filter(link -> link.getExpiresAt() != null
                        && link.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    /**
     * Does this transition shut the file?
     *
     * <p>Read from the transition row — {@code CLOSURE_CAUSE} is set on exactly the closing actions and
     * {@code IS_TERMINAL} on the final ones — rather than from a hardcoded action list. A new closing
     * action added as a table row is then covered automatically, which is the point of the dispatch
     * being table-driven.
     */
    private boolean isClosure(RbioWorkflowTransition transition) {
        if (transition == null) {
            return false;
        }
        return transition.getClosureCause() != null || transition.terminal();
    }

    private String destinationRole(RbioWorkflowTransition transition, String targetRole) {
        if (targetRole != null && !targetRole.isBlank()) {
            return targetRole;
        }
        return transition == null ? null : transition.getAssignToRole();
    }

    private Set<String> restrictedRoles() {
        return systemConfigService.getSet(KEY_RESTRICTED_FORWARD_ROLES, DEFAULT_RESTRICTED_ROLES)
                .stream().map(String::toUpperCase).collect(Collectors.toSet());
    }
}
