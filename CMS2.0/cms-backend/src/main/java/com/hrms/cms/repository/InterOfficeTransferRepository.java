package com.hrms.cms.repository;

import com.hrms.cms.entity.InterOfficeTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface InterOfficeTransferRepository extends JpaRepository<InterOfficeTransfer, Long> {
    List<InterOfficeTransfer> findByStatusOrderByRequestedAtDesc(String status);
    List<InterOfficeTransfer> findByComplaintNumberOrderByRequestedAtDesc(String complaintNumber);
    long countByStatus(String status);
}
