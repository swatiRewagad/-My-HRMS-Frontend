package com.hrms.cms.repository;

import com.hrms.cms.entity.StaffDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Staff draft lookups.
 *
 * <h2>Every finder is owner-scoped, deliberately</h2>
 * There is no {@code findByComplaintNumber} or {@code findByMilestone} without an owner. UST674 requires
 * a draft to be visible only to the user who saved it, and the surest way to honour that is for the
 * unscoped query not to exist — the sibling {@code EmailDraftRepository} has a
 * {@code findAllByOrderByCreatedAtDesc}, and the listing endpoint duly calls it whenever the caller omits
 * the owner parameter, returning every draft in the system.
 */
public interface StaffDraftRepository extends JpaRepository<StaffDraft, Long> {

    List<StaffDraft> findByOwnerUserIdOrderByUpdatedAtDesc(String ownerUserId);

    Optional<StaffDraft> findByMilestoneAndOwnerUserIdAndComplaintNumber(
            String milestone, String ownerUserId, String complaintNumber);

    /** The REGISTER milestone has no complaint number, so it is keyed on owner alone. */
    Optional<StaffDraft> findFirstByMilestoneAndOwnerUserIdAndComplaintNumberIsNull(
            String milestone, String ownerUserId);

    Optional<StaffDraft> findByIdAndOwnerUserId(Long id, String ownerUserId);

    List<StaffDraft> findByOwnerUserIdAndComplaintNumber(String ownerUserId, String complaintNumber);
}
