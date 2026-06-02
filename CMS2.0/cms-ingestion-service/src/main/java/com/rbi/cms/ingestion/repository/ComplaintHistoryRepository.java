package com.rbi.cms.ingestion.repository;

import com.rbi.cms.ingestion.entity.ComplaintHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ComplaintHistoryRepository extends JpaRepository<ComplaintHistory, Long> {

    List<ComplaintHistory> findByComplaintIdOrderByPerformedAtDesc(String complaintId);

    List<ComplaintHistory> findByComplaintIdOrderByPerformedAtAsc(String complaintId);
}
