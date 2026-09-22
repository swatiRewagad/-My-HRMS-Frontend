package com.hrms.cms.repository;

import com.hrms.cms.entity.RbioStaffAudit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RbioStaffAuditRepository extends JpaRepository<RbioStaffAudit, Long> {

    List<RbioStaffAudit> findBySubjectUserIdOrderByPerformedAtDescIdDesc(String subjectUserId);

    List<RbioStaffAudit> findByComplaintNumberOrderByPerformedAtDescIdDesc(String complaintNumber);

    /**
     * UST459 requires the configuration-change record to be retrievable for audit. Paged and
     * date-bounded because an unbounded audit read grows without limit and would eventually time out the
     * very screen meant to prove compliance.
     */
    Page<RbioStaffAudit> findByPerformedAtBetweenOrderByPerformedAtDescIdDesc(
            LocalDateTime from, LocalDateTime to, Pageable pageable);

    Page<RbioStaffAudit> findByActionAndPerformedAtBetweenOrderByPerformedAtDescIdDesc(
            String action, LocalDateTime from, LocalDateTime to, Pageable pageable);
}
