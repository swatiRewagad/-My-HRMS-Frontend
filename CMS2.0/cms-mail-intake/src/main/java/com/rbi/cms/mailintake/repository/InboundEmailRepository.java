package com.rbi.cms.mailintake.repository;

import com.rbi.cms.mailintake.entity.InboundEmail;
import com.rbi.cms.mailintake.entity.InboundEmailStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InboundEmailRepository extends JpaRepository<InboundEmail, Long> {

    Optional<InboundEmail> findByContentSha256(String contentSha256);

    List<InboundEmail> findByStatus(InboundEmailStatus status);

    List<InboundEmail> findByStatusAndNextAttemptAtBefore(InboundEmailStatus status, Instant cutoff);

    List<InboundEmail> findByStatusOrderByReceivedAtDesc(InboundEmailStatus status);

    /** Paged variant of {@link #findByStatusOrderByReceivedAtDesc} — backs the Stage 5 admin
     *  "list quarantined mail" endpoint, which must not load an unbounded backlog in one response. */
    Page<InboundEmail> findByStatusOrderByReceivedAtDesc(InboundEmailStatus status, Pageable pageable);

    long countByStatus(InboundEmailStatus status);

    long countByStatusAndNextAttemptAtBefore(InboundEmailStatus status, Instant cutoff);

    /** Oldest still-unprocessed message — feeds the "oldest unprocessed age" health/metric. */
    Optional<InboundEmail> findFirstByStatusNotInOrderByReceivedAtAsc(List<InboundEmailStatus> terminalStatuses);

    /** Rows whose raw bytes are due for the Stage 5 retention purge — terminal-status only (an
     *  email still in flight is never swept mid-processing) and not already purged, oldest first
     *  so a capped batch size still makes steady progress against a large backlog. */
    List<InboundEmail> findByStatusInAndReceivedAtBeforeAndRawPurgedAtIsNullOrderByReceivedAtAsc(
            List<InboundEmailStatus> terminalStatuses, Instant cutoff, Pageable pageable);
}
