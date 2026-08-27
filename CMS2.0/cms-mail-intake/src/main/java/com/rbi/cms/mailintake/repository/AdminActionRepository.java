package com.rbi.cms.mailintake.repository;

import com.rbi.cms.mailintake.entity.AdminAction;
import com.rbi.cms.mailintake.entity.AdminActionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdminActionRepository extends JpaRepository<AdminAction, Long> {

    Page<AdminAction> findByStatusOrderByRequestedAtAsc(AdminActionStatus status, Pageable pageable);

    List<AdminAction> findByEmailIdOrderByRequestedAtDesc(Long emailId);
}
