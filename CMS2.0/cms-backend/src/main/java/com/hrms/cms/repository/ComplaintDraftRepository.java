package com.hrms.cms.repository;

import com.hrms.cms.entity.ComplaintDraft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComplaintDraftRepository extends JpaRepository<ComplaintDraft, Long> {

    List<ComplaintDraft> findByPhoneOrderByUpdatedAtDesc(String phone);

    Optional<ComplaintDraft> findByDraftId(String draftId);

    void deleteByDraftId(String draftId);
}
