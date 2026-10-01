package com.hrms.cms.repository;

import com.hrms.cms.entity.AssistanceRailMemory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Tier 0 lookups for the assistance rail.
 *
 * <h2>There is exactly one finder, and it is owner-scoped</h2>
 * No {@code findByComplaintNumber}, no {@code findAll} wrapper, no {@code findById}-without-owner read
 * path in the service. Tier 0 is one officer's continuity state, and the surest way to keep it that way
 * is for the unscoped query not to exist — {@code StaffDraftRepository} documents the same decision and
 * names the sibling repository where an unscoped finder duly became an unscoped endpoint.
 *
 * <p>{@code JpaRepository} still exposes {@code findAll} and {@code findById} by inheritance. That is
 * unavoidable without hand-rolling the interface, so the control is that
 * {@link com.hrms.cms.service.AssistanceRailService} calls neither.
 */
public interface AssistanceRailMemoryRepository extends JpaRepository<AssistanceRailMemory, Long> {

    /**
     * The caller's own memory for one complaint.
     *
     * <p>Both arguments must already be normalised by
     * {@link AssistanceRailMemory#normaliseOwner(String)} / {@link AssistanceRailMemory#normaliseComplaint(String)}.
     * On MySQL the comparison would fold case anyway, but on Oracle it would not, so passing raw values
     * would make the two engines disagree about which row this returns.
     */
    Optional<AssistanceRailMemory> findByOwnerUserIdAndComplaintNumber(
            String ownerUserId, String complaintNumber);
}
