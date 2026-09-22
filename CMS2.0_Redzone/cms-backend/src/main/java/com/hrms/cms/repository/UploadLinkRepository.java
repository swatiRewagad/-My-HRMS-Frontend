package com.hrms.cms.repository;

import com.hrms.cms.entity.UploadLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UploadLinkRepository extends JpaRepository<UploadLink, Long> {

    Optional<UploadLink> findByComplaintNumberAndActiveTrue(String complaintNumber);

    Optional<UploadLink> findByToken(String token);

    List<UploadLink> findByActiveTrueAndExpiresAtBefore(LocalDateTime now);
}
