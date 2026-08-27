package com.rbi.cms.mailintake.repository;

import com.rbi.cms.mailintake.entity.InboundEmailAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundEmailAttachmentRepository extends JpaRepository<InboundEmailAttachment, Long> {
    List<InboundEmailAttachment> findByEmailId(Long emailId);
}
