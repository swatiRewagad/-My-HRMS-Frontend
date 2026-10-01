package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintQueryRead;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ComplaintQueryReadRepository extends JpaRepository<ComplaintQueryRead, Long> {

    Optional<ComplaintQueryRead> findByQueryIdAndUserId(Long queryId, String userId);

    List<ComplaintQueryRead> findByUserIdAndQueryIdIn(String userId, List<Long> queryIds);
}
