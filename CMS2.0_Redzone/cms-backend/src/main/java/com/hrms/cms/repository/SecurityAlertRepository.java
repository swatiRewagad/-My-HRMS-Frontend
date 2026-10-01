package com.hrms.cms.repository;

import com.hrms.cms.entity.SecurityAlert;
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
public interface SecurityAlertRepository extends JpaRepository<SecurityAlert, Long> {

    Page<SecurityAlert> findByOrderByRaisedAtDesc(Pageable pageable);

    Page<SecurityAlert> findByStatusOrderByRaisedAtDesc(String status, Pageable pageable);

    List<SecurityAlert> findBySubjectAndAlertTypeAndStatus(String subject, String alertType, String status);

    long countByStatus(String status);

    /** Used to suppress duplicate alerts while the same condition is still firing. */
    boolean existsBySubjectAndAlertTypeAndRaisedAtAfter(String subject, String alertType, LocalDateTime after);

    @Modifying
    @Query("DELETE FROM SecurityAlert a WHERE a.raisedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
