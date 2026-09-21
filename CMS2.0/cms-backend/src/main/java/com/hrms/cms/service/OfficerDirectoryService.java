package com.hrms.cms.service;

import com.hrms.cms.entity.Complaint;
import com.hrms.cms.entity.WfOfficerPool;
import com.hrms.cms.repository.WfOfficerPoolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Resolves a login username to the officer's posting details, so that callers writing an audit row or
 * a complaint can store the human-readable name and office rather than only the credential.
 *
 * <p>Reads {@code WF_OFFICER_POOL} rather than Keycloak: {@link KeycloakUserService#getUserDirectory()}
 * fetches the entire realm over HTTP on every call and returns an empty map when Keycloak is down,
 * which is acceptable for decorating a screen but not for a value about to be persisted as an audit
 * fact.
 *
 * <p>Every method returns empty rather than throwing for an unknown username. Actors are not all real
 * people — the workflow writes timeline rows as {@code System}, {@code system} and {@code REVIEWER} —
 * so absence is the normal case, not an error, and callers fall back to the username.
 */
@Service
@RequiredArgsConstructor
public class OfficerDirectoryService {

    private final WfOfficerPoolRepository officerPoolRepository;

    @Transactional(readOnly = true)
    public Optional<WfOfficerPool> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        return officerPoolRepository.findFirstByUserIdIgnoreCase(username.trim());
    }

    /** The officer's display name, or empty when the username has no pool entry or a blank name. */
    @Transactional(readOnly = true)
    public Optional<String> displayNameFor(String username) {
        return findByUsername(username)
                .map(WfOfficerPool::getDisplayName)
                .filter(name -> !name.isBlank());
    }

    /** The display name, falling back to the username itself so the caller always has something to show. */
    @Transactional(readOnly = true)
    public String displayNameOrUsername(String username) {
        return displayNameFor(username).orElse(username);
    }

    /**
     * Stamps the complaint's denormalised holder columns from whoever now holds it, so the dashboard and
     * the search index can show a name and an office without joining the officer pool per row.
     *
     * <p>Does not touch {@code assignedOfficer} — the caller owns that — and leaves a column alone rather
     * than blanking it when the pool has nothing to offer, because a stale name is more useful on a grid
     * than an empty cell. {@code fallbackName} is the client-supplied name, used only when the pool has
     * no entry for the username; the pool wins when both are present, since the request body is not a
     * trustworthy source for a stored value.
     *
     * <p>Deliberately does not overwrite {@code department}: on {@code COMPLAINTS} that column is the
     * owning module ({@code RBIO}, {@code CEPC}) that {@code RbioHierarchyService.isRbio} branches on,
     * not the actor's posting, so copying the officer's department into it would silently disable the
     * hierarchy checks.
     */
    @Transactional(readOnly = true)
    public void applyAssigneeDetails(Complaint complaint, String username, String fallbackName) {
        Optional<WfOfficerPool> officer = findByUsername(username);

        officer.map(WfOfficerPool::getDisplayName)
                .filter(name -> !name.isBlank())
                .or(() -> Optional.ofNullable(fallbackName).filter(name -> !name.isBlank()))
                .ifPresent(complaint::setAssignedOfficerName);

        officer.map(WfOfficerPool::getRegionalOffice)
                .filter(office -> !office.isBlank())
                .ifPresent(complaint::setRegionalOffice);
    }
}
