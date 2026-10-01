package com.rbi.cms.ingestion.repository;

import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.ingestion.entity.ComplaintMaster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintMasterRepository extends JpaRepository<ComplaintMaster, Long> {

    Optional<ComplaintMaster> findByComplaintId(String complaintId);

    Page<ComplaintMaster> findByStatus(ComplaintStatus status, Pageable pageable);

    Page<ComplaintMaster> findByComplainantPhone(String phone, Pageable pageable);

    Page<ComplaintMaster> findByAssignedTo(String assignedTo, Pageable pageable);

    boolean existsByComplaintId(String complaintId);

    long countByStatus(ComplaintStatus status);

    @Query("SELECT c.status, COUNT(c) FROM ComplaintMaster c GROUP BY c.status")
    List<Object[]> countByStatusGrouped();

    @Query("SELECT c.category, COUNT(c) FROM ComplaintMaster c GROUP BY c.category")
    List<Object[]> countByCategoryGrouped();

    @Query("SELECT c.priority, COUNT(c) FROM ComplaintMaster c GROUP BY c.priority")
    List<Object[]> countByPriorityGrouped();

    @Query("SELECT c.assignedTeam, COUNT(c) FROM ComplaintMaster c WHERE c.assignedTeam IS NOT NULL GROUP BY c.assignedTeam")
    List<Object[]> countByTeamGrouped();

    long countBySlaDueDateBeforeAndStatusNotIn(Instant now, List<ComplaintStatus> excludedStatuses);

    @Query("SELECT c FROM ComplaintMaster c WHERE c.slaDueDate < :now AND c.status NOT IN :excludedStatuses ORDER BY c.slaDueDate ASC")
    List<ComplaintMaster> findSlaBreached(Instant now, List<ComplaintStatus> excludedStatuses);

    List<ComplaintMaster> findTop10ByOrderByCreatedAtDesc();
}
