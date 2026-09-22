package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintQueryDocItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintQueryDocItemRepository extends JpaRepository<ComplaintQueryDocItem, Long> {

    List<ComplaintQueryDocItem> findByQueryIdOrderByDisplayOrderAscIdAsc(Long queryId);

    List<ComplaintQueryDocItem> findByQueryIdInOrderByDisplayOrderAscIdAsc(List<Long> queryIds);

    long countByQueryIdAndResolvedFalse(Long queryId);
}
