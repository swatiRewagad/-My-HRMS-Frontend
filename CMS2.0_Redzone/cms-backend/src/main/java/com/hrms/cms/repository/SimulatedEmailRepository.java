package com.hrms.cms.repository;

import com.hrms.cms.entity.SimulatedEmail;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SimulatedEmailRepository extends JpaRepository<SimulatedEmail, Long> {
    List<SimulatedEmail> findByDirectionOrderBySentAtDesc(String direction);
    List<SimulatedEmail> findByThreadIdOrderBySentAtAsc(String threadId);
    Optional<SimulatedEmail> findByMessageId(String messageId);
    long countByDirectionAndStatus(String direction, String status);

    /**
     * Every email on one complaint, oldest first (UST593).
     *
     * <p>The {@code COMPLAINT_ID} column and its index {@code idx_email_complaint} already existed and
     * were populated on ingestion — but NO query ever read them, so a per-complaint email log was
     * unobtainable even though the data was sitting there. This is the missing half.
     *
     * <p>Ascending because UST593 asks for a chronological conversation; a thread read newest-first is
     * unreadable once replies quote what came before.
     */
    List<SimulatedEmail> findByComplaintIdOrderBySentAtAsc(Long complaintId);

    /** Fallback for rows linked by number rather than id, which some intake paths write. */
    List<SimulatedEmail> findByComplaintNumberOrderBySentAtAsc(String complaintNumber);

    /** Thread lookup used when re-linking an email after its subject was edited (UST657). */
    List<SimulatedEmail> findByThreadIdOrderBySentAtDesc(String threadId);
}
