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
}
