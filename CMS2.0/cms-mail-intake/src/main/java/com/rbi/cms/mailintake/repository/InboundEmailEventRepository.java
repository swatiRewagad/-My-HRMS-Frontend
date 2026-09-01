package com.rbi.cms.mailintake.repository;

import com.rbi.cms.mailintake.entity.InboundEmailEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InboundEmailEventRepository extends JpaRepository<InboundEmailEvent, Long> {
    List<InboundEmailEvent> findByEmailIdOrderByEventAtAsc(Long emailId);
}
