package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintInternalNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComplaintInternalNoteRepository extends JpaRepository<ComplaintInternalNote, Long> {

    /**
     * Notes are always fetched with the entity code as part of the predicate, never by complaint id
     * alone, so a caller cannot accidentally read another entity's notes (UST860).
     */
    List<ComplaintInternalNote> findByComplaintIdAndEntityCodeOrderByCreatedAtDesc(Long complaintId, String entityCode);

    long countByComplaintIdAndEntityCode(Long complaintId, String entityCode);
}
