package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintQuerySlot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintQuerySlotRepository extends JpaRepository<ComplaintQuerySlot, Long> {

    List<ComplaintQuerySlot> findByQueryIdOrderByDisplayOrderAscIdAsc(Long queryId);

    List<ComplaintQuerySlot> findByQueryIdInOrderByDisplayOrderAscIdAsc(List<Long> queryIds);

    List<ComplaintQuerySlot> findByQueryIdAndSlotStatus(Long queryId, String slotStatus);
}
