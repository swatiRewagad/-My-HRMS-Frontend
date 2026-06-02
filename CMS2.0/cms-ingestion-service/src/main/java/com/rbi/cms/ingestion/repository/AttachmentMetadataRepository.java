package com.rbi.cms.ingestion.repository;

import com.rbi.cms.ingestion.entity.AttachmentMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AttachmentMetadataRepository extends JpaRepository<AttachmentMetadata, Long> {

    List<AttachmentMetadata> findByComplaintId(String complaintId);
}
