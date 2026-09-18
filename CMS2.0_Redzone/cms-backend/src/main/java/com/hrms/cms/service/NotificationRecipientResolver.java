package com.hrms.cms.service;

import com.hrms.cms.entity.AaOfficerPool;
import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.EntityUser;
import com.hrms.cms.repository.AaOfficerPoolRepository;
import com.hrms.cms.repository.EntityUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Expands a configured recipient list into the actual user ids to notify (UST663-668).
 *
 * <p>THE DEFECT THIS FIXES: {@link NotificationService#send} takes one opaque {@code targetUserId}
 * which is persisted to {@code IN_APP_NOTIFICATIONS.targetUserId} and passed to
 * {@code convertAndSendToUser}. The scheduled jobs passed ROLE names into it — {@code "RBIO_ADMIN"},
 * {@code "SYSTEM_ADMIN"}, {@code dept + "_ADMIN"} — and no role-to-user fan-out existed anywhere. So
 * those notifications were persisted addressed to a literal string that is nobody's login, and the
 * STOMP user destination could never match a principal. They were invisible: written, counted in the
 * log line, and delivered to no one.
 *
 * <p>Making recipients configurable does not fix that on its own, which is why this class exists
 * alongside {@link NotificationConfigService}. A configurable list of undeliverable role names is
 * still undeliverable.
 *
 * <p>Fan-out source is {@code wf_officer_pool} via {@link AaOfficerPoolRepository}, whose
 * {@code role_group} column is exactly the role→user mapping needed and which is already populated
 * for RBIO, CEPC, CRPC and AA groups. Deliberately not Keycloak: the {@code cms} realm has no
 * {@code RE_*} users at all (see {@link EntityUser}), so an identity-provider lookup would return
 * empty for part of the matrix and the feature would look built but notify nobody.
 *
 * <p>An unresolvable role is returned AS-IS rather than dropped. Dropping it would restore the
 * original silent-loss behaviour; keeping it means the notification is still recorded against a name
 * an operator can search for, and the warning log says which role has no members.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationRecipientResolver {

    private final AaOfficerPoolRepository officerPoolRepository;
    private final EntityUserRepository entityUserRepository;

    /**
     * Expands configured recipient tokens against one complaint.
     *
     * @param configured tokens from {@link NotificationConfigService} — role names, user ids, or the
     *                   per-complaint placeholders COMPLAINT_OWNER / COMPLAINANT / NODAL_OFFICER / PNO
     * @param complaint  the complaint in context; may be null for events with no complaint
     * @return distinct user ids, order preserved for reproducible delivery-log ordering
     */
    public Set<String> resolve(Collection<String> configured, Complaint complaint) {
        Set<String> out = new LinkedHashSet<>();
        if (configured == null) {
            return out;
        }

        for (String token : configured) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String t = token.trim();

            switch (t) {
                case NotificationConfigService.RECIPIENT_COMPLAINT_OWNER -> addIfPresent(out,
                        complaint == null ? null : complaint.getAssignedOfficer());
                case NotificationConfigService.RECIPIENT_COMPLAINANT -> addIfPresent(out,
                        citizenRecipient(complaint));
                case NotificationConfigService.RECIPIENT_NODAL_OFFICER -> out.addAll(
                        entityRoleMembers(complaint, EntityUser.ROLE_NODAL_OFFICER));
                case NotificationConfigService.RECIPIENT_PNO -> out.addAll(
                        entityRoleMembers(complaint, EntityUser.ROLE_PNO));
                default -> out.addAll(expandRole(t));
            }
        }
        return out;
    }

    /**
     * Members of a role group, or the token itself when it names no group.
     *
     * <p>A token that is already a user id falls through this path and is returned unchanged, so the
     * config value may mix roles and individuals without the caller distinguishing them.
     */
    public Set<String> expandRole(String roleOrUserId) {
        Set<String> out = new LinkedHashSet<>();
        try {
            List<AaOfficerPool> members = officerPoolRepository.findEligible(roleOrUserId);
            if (!members.isEmpty()) {
                members.forEach(m -> addIfPresent(out, m.getUserId()));
                return out;
            }
        } catch (Exception e) {
            log.warn("Officer-pool lookup failed for '{}': {}", roleOrUserId, e.getMessage());
        }

        // Not a role group with eligible members. Could be an individual user id, or a role whose
        // members are all inactive or on leave. Both are worth surfacing rather than silently losing.
        log.debug("Recipient '{}' resolved to no pool members — notifying the token itself", roleOrUserId);
        out.add(roleOrUserId);
        return out;
    }

    /**
     * The complainant's addressable id.
     *
     * <p>Citizens have no officer-pool row and no Keycloak account — they authenticate by OTP — so
     * the complaint number is the only stable handle the in-app store can key on. Returning it keeps
     * the notification recorded and auditable; actual citizen-facing delivery is email/SMS, which has
     * no transport in this module (see the batch report's blocker note), so a bell row addressed to a
     * citizen is a record of intent, not a claim of delivery.
     */
    private String citizenRecipient(Complaint complaint) {
        if (complaint == null || complaint.getComplaintNumber() == null) {
            return null;
        }
        return "COMPLAINANT:" + complaint.getComplaintNumber();
    }

    private Set<String> entityRoleMembers(Complaint complaint, String reRole) {
        Set<String> out = new LinkedHashSet<>();
        if (complaint == null || complaint.getEntityCode() == null || complaint.getEntityCode().isBlank()) {
            return out;
        }
        try {
            entityUserRepository
                    .findByEntityCodeAndReRoleAndActiveTrueOrderByDisplayNameAsc(
                            complaint.getEntityCode(), reRole)
                    .forEach(u -> addIfPresent(out, u.getUserId()));
        } catch (Exception e) {
            log.warn("Entity-user lookup failed for entity {} role {}: {}",
                    complaint.getEntityCode(), reRole, e.getMessage());
        }
        return out;
    }

    private void addIfPresent(Set<String> target, String value) {
        if (value != null && !value.isBlank()) {
            target.add(value.trim());
        }
    }
}
