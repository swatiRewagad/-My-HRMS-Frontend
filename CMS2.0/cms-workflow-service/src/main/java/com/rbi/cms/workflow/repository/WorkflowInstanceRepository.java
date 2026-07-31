package com.rbi.cms.workflow.repository;

import com.rbi.cms.common.enums.ComplaintStatus;
import com.rbi.cms.workflow.entity.WorkflowInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance, Long> {

    Optional<WorkflowInstance> findByComplaintId(String complaintId);

    Optional<WorkflowInstance> findByProcessInstanceId(String processInstanceId);

    List<WorkflowInstance> findByDepartmentAndStatus(String department, ComplaintStatus status);

    List<WorkflowInstance> findByAssignedOfficerAndStatusIn(String officer, List<ComplaintStatus> statuses);

    List<WorkflowInstance> findByStatus(ComplaintStatus status);

    List<WorkflowInstance> findByDepartment(String department);

    @Query("SELECT w FROM WorkflowInstance w WHERE w.status NOT IN ('CLOSED', 'RESOLVED') " +
            "AND w.updatedAt < :threshold")
    List<WorkflowInstance> findStaleInstances(Instant threshold);

    @Modifying
    @Query("UPDATE WorkflowInstance w SET w.status = :status, w.currentTask = :task, w.updatedAt = :now " +
            "WHERE w.complaintId = :complaintId")
    int updateStatusAndTask(String complaintId, ComplaintStatus status, String task, Instant now);

    long countByDepartmentAndStatus(String department, ComplaintStatus status);

    long countByAssignedOfficer(String officer);
}
