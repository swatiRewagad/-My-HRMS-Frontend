package com.hrms.cms.repository;

import com.hrms.cms.entity.RevealAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RevealAuditEntryRepository extends JpaRepository<RevealAuditEntry, Long> {

    Page<RevealAuditEntry> findByUserIdOrderByRevealedAtDesc(String userId, Pageable pageable);

    Page<RevealAuditEntry> findByComplaintNumberOrderByRevealedAtDesc(String complaintNumber, Pageable pageable);

    List<RevealAuditEntry> findByUserIdAndRevealedAtAfter(String userId, LocalDateTime after);

    long countByUserIdAndRevealedAtAfter(String userId, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM RevealAuditEntry r WHERE r.revealedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
