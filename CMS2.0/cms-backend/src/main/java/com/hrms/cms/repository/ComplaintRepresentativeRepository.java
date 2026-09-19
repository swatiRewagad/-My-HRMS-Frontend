package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintRepresentative;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ComplaintRepresentativeRepository extends JpaRepository<ComplaintRepresentative, Long> {

    Optional<ComplaintRepresentative> findByComplaintId(Long complaintId);
}
