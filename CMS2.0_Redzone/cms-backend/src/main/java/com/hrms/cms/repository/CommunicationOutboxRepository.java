package com.hrms.cms.repository;

import com.hrms.cms.entity.CommunicationOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommunicationOutboxRepository extends JpaRepository<CommunicationOutbox, Long> {

    /** The sender's queue: oldest first, so a backlog drains in the order the obligations arose. */
    List<CommunicationOutbox> findBySentFalseOrderByCreatedAtAsc(Pageable pageable);

    List<CommunicationOutbox> findByRelatedReferenceOrderByCreatedAtDesc(String relatedReference);

    /**
     * Whether a given communication has already been queued for a complaint. Used to keep the closure
     * gate idempotent: a retried close must not queue a second letter to the same citizen.
     */
    boolean existsByRelatedReferenceAndCommunicationTypeAndChannel(String relatedReference,
                                                                   String communicationType,
                                                                   String channel);

    long countBySentFalse();

    @Query("SELECT COUNT(c) FROM CommunicationOutbox c WHERE c.relatedReference = :ref AND c.sent = false")
    long countPendingForReference(@Param("ref") String ref);
}
