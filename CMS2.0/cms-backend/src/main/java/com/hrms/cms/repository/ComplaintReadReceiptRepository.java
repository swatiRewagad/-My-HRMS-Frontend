package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ComplaintReadReceiptRepository extends JpaRepository<ComplaintReadReceipt, Long> {

    boolean existsByComplaintIdAndUsername(Long complaintId, String username);

    List<ComplaintReadReceipt> findByComplaintIdIn(Collection<Long> complaintIds);
}
